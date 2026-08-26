from __future__ import annotations

import logging
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from .static_mount import mount_web_ui
from sqlalchemy import String, cast, or_, text
from sqlalchemy.orm import Session, sessionmaker

from .auth_routes import router as auth_router
from .camera_routes import router as camera_router
from .device_routes import router as device_router
from .map_routes import router as map_router
from .mail_routes import router as mail_router
from .chat_routes import router as chat_router
from .team_routes import router as team_router
from .report_routes import router as report_router
from .sos_routes import router as sos_router
from .companion_routes import router as companion_router
from .motion_routes import router as motion_router
from .environment_routes import router as environment_router
from .vision_routes import router as vision_records_router
from .config import Settings, get_settings
from .db import create_engine_from_settings, create_session_factory
from .deps import bind_runtime, get_current_user_optional, get_session_factory_dep, get_settings_dep
from .image_utils import _safe_ext
from .models import Base, IdentifyRecord, SosEvent, User
from .schemas import (
    HealthResponse,
    IdentifyRecordItem,
    IdentifyRecordsResponse,
    IdentifyResponse,
    RecordSort,
    Scene,
    SosRequest,
    SosResponse,
)
from .vision_identify import run_identify
from .sensor_service import location_fields_from_record
from .model_scheduler import is_report_generation_active
from .tile_fetch import close_tile_client, init_tile_client

logger = logging.getLogger(__name__)

def require_api_key(
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
    settings: Settings = Depends(get_settings),
) -> None:
    if not settings.API_KEY:
        return
    if not x_api_key or x_api_key != settings.API_KEY:
        raise HTTPException(status_code=401, detail="unauthorized")


app = FastAPI(title="V-Eye Cloud API", version="0.1.0")
app.include_router(auth_router)
app.include_router(device_router)
app.include_router(camera_router)
app.include_router(team_router)
app.include_router(chat_router)
app.include_router(mail_router)
app.include_router(vision_records_router)
app.include_router(map_router)
app.include_router(report_router)
app.include_router(sos_router)
app.include_router(companion_router)
app.include_router(motion_router)
app.include_router(environment_router)

_cors_raw = get_settings().CORS_ALLOW_ORIGINS.strip()
if _cors_raw:
    _origins = [o.strip() for o in _cors_raw.split(",") if o.strip()]
    if _origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=_origins,
            allow_credentials=True,
            allow_methods=["*"],
            allow_headers=["*"],
        )

_settings: Settings | None = None
_session_factory: sessionmaker[Session] | None = None


def _migrate_schema(engine) -> None:
    Base.metadata.create_all(bind=engine)
    migrations = [
        "ALTER TABLE identify_records ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id)",
        "CREATE INDEX IF NOT EXISTS ix_identify_records_user_id ON identify_records (user_id)",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_path TEXT",
        "ALTER TABLE identify_records ADD COLUMN IF NOT EXISTS image_original_path TEXT",
        "ALTER TABLE identify_records ADD COLUMN IF NOT EXISTS gps_lat DOUBLE PRECISION",
        "ALTER TABLE identify_records ADD COLUMN IF NOT EXISTS gps_lng DOUBLE PRECISION",
        "ALTER TABLE identify_records ADD COLUMN IF NOT EXISTS accuracy_m DOUBLE PRECISION",
        "ALTER TABLE sos_events ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id)",
        "CREATE INDEX IF NOT EXISTS ix_sos_events_user_id ON sos_events (user_id)",
        """CREATE TABLE IF NOT EXISTS camera_devices (
            id UUID PRIMARY KEY,
            device_id TEXT NOT NULL UNIQUE,
            secret_hash TEXT NOT NULL,
            user_id UUID REFERENCES users(id),
            name TEXT NOT NULL DEFAULT '',
            device_type TEXT NOT NULL DEFAULT 'maxicam',
            bound_at TIMESTAMPTZ,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )""",
        "CREATE INDEX IF NOT EXISTS ix_camera_devices_user_id ON camera_devices (user_id)",
        """CREATE TABLE IF NOT EXISTS chat_messages (
            id UUID PRIMARY KEY,
            team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
            sender_id UUID NOT NULL REFERENCES users(id),
            body TEXT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )""",
        "CREATE INDEX IF NOT EXISTS ix_chat_messages_team_id ON chat_messages (team_id)",
        "CREATE INDEX IF NOT EXISTS ix_chat_messages_sender_id ON chat_messages (sender_id)",
        "CREATE INDEX IF NOT EXISTS ix_chat_messages_created_at ON chat_messages (created_at)",
        "CREATE INDEX IF NOT EXISTS ix_chat_messages_team_created ON chat_messages (team_id, created_at DESC)",
        """CREATE TABLE IF NOT EXISTS team_chat_reads (
            id UUID PRIMARY KEY,
            team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
            user_id UUID NOT NULL REFERENCES users(id),
            last_read_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT uq_team_chat_reads_team_user UNIQUE (team_id, user_id)
        )""",
        "CREATE INDEX IF NOT EXISTS ix_team_chat_reads_team_id ON team_chat_reads (team_id)",
        "CREATE INDEX IF NOT EXISTS ix_team_chat_reads_user_id ON team_chat_reads (user_id)",
        """CREATE TABLE IF NOT EXISTS report_jobs (
            id UUID PRIMARY KEY,
            team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
            subject_user_id UUID NOT NULL REFERENCES users(id),
            requested_by_user_id UUID NOT NULL REFERENCES users(id),
            report_date DATE NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending',
            phase_detail JSONB NOT NULL DEFAULT '{}',
            timeline_json JSONB,
            markdown TEXT,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            finished_at TIMESTAMPTZ
        )""",
        "CREATE INDEX IF NOT EXISTS ix_report_jobs_team_id ON report_jobs (team_id)",
        "CREATE INDEX IF NOT EXISTS ix_report_jobs_subject_user_id ON report_jobs (subject_user_id)",
        "CREATE INDEX IF NOT EXISTS ix_report_jobs_status ON report_jobs (status)",
        "CREATE INDEX IF NOT EXISTS ix_report_jobs_report_date ON report_jobs (report_date)",
        "ALTER TABLE report_jobs ADD COLUMN IF NOT EXISTS report_type TEXT NOT NULL DEFAULT 'safety'",
        "ALTER TABLE report_jobs ADD COLUMN IF NOT EXISTS markdown_generated TEXT",
        "ALTER TABLE report_jobs ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ",
        "ALTER TABLE teams ADD COLUMN IF NOT EXISTS avatar_path TEXT",
        "ALTER TABLE identify_records ADD COLUMN IF NOT EXISTS sensor_json JSONB",
        """CREATE TABLE IF NOT EXISTS member_track_points (
            id UUID PRIMARY KEY,
            user_id UUID NOT NULL REFERENCES users(id),
            team_id UUID REFERENCES teams(id),
            source TEXT NOT NULL DEFAULT 'sensor',
            identify_record_id UUID REFERENCES identify_records(id),
            gps_lat DOUBLE PRECISION NOT NULL,
            gps_lng DOUBLE PRECISION NOT NULL,
            gps_speed DOUBLE PRECISION,
            gps_heading DOUBLE PRECISION,
            gps_altitude DOUBLE PRECISION,
            accuracy_m DOUBLE PRECISION,
            accel_x DOUBLE PRECISION,
            accel_y DOUBLE PRECISION,
            accel_z DOUBLE PRECISION,
            gyro_x DOUBLE PRECISION,
            gyro_y DOUBLE PRECISION,
            gyro_z DOUBLE PRECISION,
            mag_x DOUBLE PRECISION,
            mag_y DOUBLE PRECISION,
            mag_z DOUBLE PRECISION,
            pitch DOUBLE PRECISION,
            roll DOUBLE PRECISION,
            imu_heading DOUBLE PRECISION,
            baro_altitude DOUBLE PRECISION,
            client_ts TIMESTAMPTZ,
            server_ts TIMESTAMPTZ NOT NULL DEFAULT now()
        )""",
        "CREATE INDEX IF NOT EXISTS ix_member_track_points_user_id ON member_track_points (user_id)",
        "CREATE INDEX IF NOT EXISTS ix_member_track_points_team_id ON member_track_points (team_id)",
        "CREATE INDEX IF NOT EXISTS ix_member_track_points_server_ts ON member_track_points (server_ts)",
        "CREATE INDEX IF NOT EXISTS ix_member_track_points_identify_record_id ON member_track_points (identify_record_id)",
        """CREATE TABLE IF NOT EXISTS companion_states (
            user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
            name TEXT NOT NULL DEFAULT '小欧',
            mood TEXT NOT NULL DEFAULT 'idle',
            level INTEGER NOT NULL DEFAULT 1,
            energy INTEGER NOT NULL DEFAULT 100,
            total_steps INTEGER NOT NULL DEFAULT 0,
            message TEXT NOT NULL DEFAULT '你好，我是小欧！',
            pose_json JSONB NOT NULL DEFAULT '{}',
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )""",
        """CREATE TABLE IF NOT EXISTS motion_sessions (
            id UUID PRIMARY KEY,
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            status TEXT NOT NULL DEFAULT 'active',
            started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            ended_at TIMESTAMPTZ,
            steps INTEGER NOT NULL DEFAULT 0,
            fall_count INTEGER NOT NULL DEFAULT 0,
            pose_score DOUBLE PRECISION NOT NULL DEFAULT 0,
            summary_json JSONB NOT NULL DEFAULT '{}',
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )""",
        "CREATE INDEX IF NOT EXISTS ix_motion_sessions_user_id ON motion_sessions (user_id)",
        """CREATE TABLE IF NOT EXISTS motion_snapshots (
            id UUID PRIMARY KEY,
            session_id UUID NOT NULL REFERENCES motion_sessions(id) ON DELETE CASCADE,
            client_ts TIMESTAMPTZ,
            steps INTEGER NOT NULL DEFAULT 0,
            activity TEXT NOT NULL DEFAULT 'idle',
            fall_detected BOOLEAN NOT NULL DEFAULT false,
            pose_keypoints JSONB NOT NULL DEFAULT '{}',
            sensor_json JSONB NOT NULL DEFAULT '{}',
            server_ts TIMESTAMPTZ NOT NULL DEFAULT now()
        )""",
        "CREATE INDEX IF NOT EXISTS ix_motion_snapshots_session_id ON motion_snapshots (session_id)",
    ]
    try:
        with engine.begin() as conn:
            for stmt in migrations:
                conn.execute(text(stmt))
    except Exception:
        logger.exception("schema migration skipped or partially applied")


@app.on_event("startup")
def _startup() -> None:
    global _settings, _session_factory
    _settings = get_settings()
    engine = create_engine_from_settings(_settings)
    _session_factory = create_session_factory(engine)
    bind_runtime(_settings, _session_factory)
    init_tile_client()
    try:
        _migrate_schema(engine)
    except Exception:
        # DB 不可用时允许服务先启动；落库接口会返回 503
        pass


@app.on_event("shutdown")
async def _shutdown() -> None:
    await close_tile_client()


@app.get("/health", response_model=HealthResponse)
def health(settings: Settings = Depends(get_settings_dep)) -> HealthResponse:
    return HealthResponse(model=settings.VLLM_MODEL, vllm={"base_url": settings.VLLM_BASE_URL})


@app.get("/vision/records", response_model=IdentifyRecordsResponse, dependencies=[Depends(require_api_key)])
def list_identify_records(
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    q: str | None = Query(default=None, max_length=200),
    scene: str | None = Query(default=None),
    risk_level: int | None = Query(default=None, ge=0, le=3),
    sort: RecordSort = Query(default="time_desc"),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
    current_user: User | None = Depends(get_current_user_optional),
) -> IdentifyRecordsResponse:
    try:
        with session_factory() as db:
            query = db.query(IdentifyRecord)
            if current_user is not None:
                query = query.filter(IdentifyRecord.user_id == current_user.id)
            else:
                query = query.filter(IdentifyRecord.user_id.is_(None))

            if scene and scene.strip():
                query = query.filter(IdentifyRecord.scene == scene.strip())

            if risk_level is not None:
                query = query.filter(IdentifyRecord.risk_level == risk_level)

            if q and q.strip():
                needle = f"%{q.strip()}%"
                query = query.filter(
                    or_(
                        IdentifyRecord.label_main.ilike(needle),
                        cast(IdentifyRecord.result_json["summary"], String).ilike(needle),
                        IdentifyRecord.scene.ilike(needle),
                    )
                )

            total_all = db.query(IdentifyRecord)
            if current_user is not None:
                total_all = total_all.filter(IdentifyRecord.user_id == current_user.id)
            else:
                total_all = total_all.filter(IdentifyRecord.user_id.is_(None))
            total_unfiltered = total_all.count()
            matched = query.count()

            order_map = {
                "time_desc": IdentifyRecord.server_ts.desc(),
                "time_asc": IdentifyRecord.server_ts.asc(),
                "confidence_desc": text("(result_json->>'confidence')::float DESC NULLS LAST"),
                "confidence_asc": text("(result_json->>'confidence')::float ASC NULLS LAST"),
                "risk_desc": IdentifyRecord.risk_level.desc(),
                "risk_asc": IdentifyRecord.risk_level.asc(),
                "label_asc": IdentifyRecord.label_main.asc(),
                "label_desc": IdentifyRecord.label_main.desc(),
            }
            order = order_map.get(sort, IdentifyRecord.server_ts.desc())
            rows = query.order_by(order).offset(offset).limit(limit).all()
    except Exception:
        raise HTTPException(status_code=503, detail="database_unavailable")

    items: list[IdentifyRecordItem] = []
    for row in rows:
        result = row.result_json if isinstance(row.result_json, dict) else {}
        try:
            confidence = float(result.get("confidence", 0.0))
        except Exception:  # noqa: BLE001
            confidence = 0.0
        confidence = max(0.0, min(1.0, confidence))
        summary = str(result.get("summary", "")).strip()[:500]
        last_mode = result.get("last_enhance_mode")
        if not isinstance(last_mode, str):
            last_mode = None
        has_original = bool(
            getattr(row, "image_original_path", None)
            and Path(str(row.image_original_path)).is_file()
        )
        has_image = bool(getattr(row, "image_path", None) and Path(str(row.image_path)).is_file())
        loc = location_fields_from_record(
            gps_lat=row.gps_lat,
            gps_lng=row.gps_lng,
            accuracy_m=row.accuracy_m,
            sensor_json=row.sensor_json if isinstance(getattr(row, "sensor_json", None), dict) else None,
        )
        items.append(
            IdentifyRecordItem(
                request_id=str(row.id),
                device_id=row.device_id,
                scene=row.scene,
                label_main=row.label_main,
                confidence=confidence,
                risk_level=row.risk_level,
                summary=summary,
                server_ts=row.server_ts,
                has_original=has_original,
                has_image=has_image,
                last_enhance_mode=last_mode,
                gps_lat=row.gps_lat,
                gps_lng=row.gps_lng,
                accuracy_m=loc["accuracy_m"],
                has_gps=loc["has_gps"],
                gps_altitude=loc["gps_altitude"],
                baro_altitude=loc["baro_altitude"],
            )
        )
    return IdentifyRecordsResponse(items=items, total=total_unfiltered, matched=matched)


@app.get(
    "/vision/records/{request_id}/image",
    dependencies=[Depends(require_api_key)],
)
def get_identify_record_image(
    request_id: str,
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
    current_user: User | None = Depends(get_current_user_optional),
) -> FileResponse:
    try:
        rec_id = uuid.UUID(request_id)
    except ValueError as e:
        raise HTTPException(status_code=400, detail="invalid request_id") from e

    try:
        with session_factory() as db:
            row = db.get(IdentifyRecord, rec_id)
            if row is None:
                raise HTTPException(status_code=404, detail="record_not_found")
            if row.user_id is not None:
                if current_user is None:
                    raise HTTPException(status_code=401, detail="not_authenticated")
                from .team_service import can_view_user_identify_record

                if not can_view_user_identify_record(db, current_user, row.user_id):
                    raise HTTPException(status_code=403, detail="forbidden")
            image_path = Path(row.image_path)
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=503, detail="database_unavailable")

    path = image_path
    if not path.is_file():
        raise HTTPException(status_code=404, detail="image_not_found")

    media = "image/jpeg"
    if path.suffix.lower() == ".png":
        media = "image/png"
    elif path.suffix.lower() == ".webp":
        media = "image/webp"
    return FileResponse(path, media_type=media)


@app.post("/vision/identify", response_model=IdentifyResponse, dependencies=[Depends(require_api_key)])
async def vision_identify(
    image: UploadFile = File(...),
    scene: Scene = Form(...),
    lang: str = Form(default="zh-CN"),
    device_id: str | None = Form(default=None),
    client_ts: str | None = Form(default=None),
    team_id: str | None = Form(default=None),
    gps_lat: float | None = Form(default=None),
    gps_lng: float | None = Form(default=None),
    accuracy_m: float | None = Form(default=None),
    gps_speed: float | None = Form(default=None),
    gps_heading: float | None = Form(default=None),
    gps_altitude: float | None = Form(default=None),
    accel_x: float | None = Form(default=None),
    accel_y: float | None = Form(default=None),
    accel_z: float | None = Form(default=None),
    gyro_x: float | None = Form(default=None),
    gyro_y: float | None = Form(default=None),
    gyro_z: float | None = Form(default=None),
    mag_x: float | None = Form(default=None),
    mag_y: float | None = Form(default=None),
    mag_z: float | None = Form(default=None),
    pitch: float | None = Form(default=None),
    roll: float | None = Form(default=None),
    imu_heading: float | None = Form(default=None),
    baro_altitude: float | None = Form(default=None),
    sensor_json: str | None = Form(default=None),
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
    current_user: User | None = Depends(get_current_user_optional),
) -> IdentifyResponse:
    if is_report_generation_active():
        raise HTTPException(status_code=503, detail="report_generation_in_progress")
    parsed_client_ts = None
    if client_ts:
        try:
            parsed_client_ts = datetime.fromisoformat(client_ts.replace("Z", "+00:00"))
        except ValueError:
            parsed_client_ts = None
    sensor_payload: dict = {}
    if sensor_json:
        try:
            import json as _json

            raw = _json.loads(sensor_json)
            if isinstance(raw, dict):
                sensor_payload = raw
        except Exception:
            pass
    for key, val in {
        "gps_lat": gps_lat,
        "gps_lng": gps_lng,
        "accuracy_m": accuracy_m,
        "gps_speed": gps_speed,
        "gps_heading": gps_heading,
        "gps_altitude": gps_altitude,
        "accel_x": accel_x,
        "accel_y": accel_y,
        "accel_z": accel_z,
        "gyro_x": gyro_x,
        "gyro_y": gyro_y,
        "gyro_z": gyro_z,
        "mag_x": mag_x,
        "mag_y": mag_y,
        "mag_z": mag_z,
        "pitch": pitch,
        "roll": roll,
        "imu_heading": imu_heading,
        "baro_altitude": baro_altitude,
    }.items():
        if val is not None:
            sensor_payload[key] = val
    body = await image.read()
    return await run_identify(
        settings=settings,
        session_factory=session_factory,
        body=body,
        scene=scene,
        lang=lang,
        device_id=device_id,
        filename=image.filename,
        user=current_user,
        gps_lat=gps_lat,
        gps_lng=gps_lng,
        accuracy_m=accuracy_m,
        team_id=team_id,
        client_ts=parsed_client_ts,
        sensor_payload=sensor_payload or None,
    )


@app.post("/sos", response_model=SosResponse, dependencies=[Depends(require_api_key)])
def sos(
    body: SosRequest,
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
    current_user: User | None = Depends(get_current_user_optional),
) -> SosResponse:
    _ = settings
    incident_id = str(uuid.uuid4())
    try:
        with session_factory() as db:
            ev = SosEvent(
                id=uuid.UUID(incident_id),
                user_id=current_user.id if current_user else None,
                device_id=body.device_id,
                event_type=body.event_type,
                gps_lat=body.gps_lat,
                gps_lng=body.gps_lng,
                accuracy_m=body.accuracy_m,
                client_ts=body.client_ts,
                server_ts=datetime.now(timezone.utc),
            )
            db.add(ev)
            db.commit()
    except Exception:
        raise HTTPException(status_code=503, detail="database_unavailable")
    return SosResponse(ok=True, incident_id=incident_id)


@app.exception_handler(HTTPException)
def http_exception_handler(_, exc: HTTPException) -> JSONResponse:
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})


# 可选：在 frontend 执行 npm run build 后，产物输出到 app/static/web，与 API 同端口提供 SPA
_web_root = Path(__file__).resolve().parent / "static" / "web"
mount_web_ui(app, _web_root)

