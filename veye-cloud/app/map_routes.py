from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import FileResponse, Response
from httpx import HTTPError
from sqlalchemy.orm import Session, sessionmaker

from .deps import get_current_user, get_session_factory_dep
from .models import IdentifyRecord, MemberLocation, MemberTrackPoint, SosEvent, Team, TeamMember, User
from .schemas import (
    LocationReportRequest,
    LocationReportResponse,
    MapIdentifyPoint,
    MapLayersResponse,
    MapMemberPoint,
    MapSosPoint,
    MapTrackPoint,
    MapTrajectoriesResponse,
    MapTrajectory,
)
from .sensor_service import save_track_point, upsert_member_location
from .team_service import require_team_member
from .tile_fetch import BROWSER_TILE_CACHE, disk_fallback_hit, disk_tile_hit, fallback_provider_for, fetch_map_tile

router = APIRouter(prefix="/map", tags=["map"])

TILE_PROVIDERS: dict[str, str] = {
    "amap": "https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}",
    "amap_satellite": "https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}",
    "street": "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}",
    "osm": "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    "topo": "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png",
    "satellite": "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
    "carto": "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
}

_AMAP_PROVIDERS = frozenset({"amap", "amap_satellite"})


def _build_tile_url(provider: str, z: int, x: int, y: int, *, subdomain: str | None = None) -> str:
    template = TILE_PROVIDERS[provider]
    s = subdomain if subdomain is not None else _tile_subdomain(x, y, provider)
    return template.format(z=z, x=x, y=y, s=s)


def _build_tile_urls(provider: str, z: int, x: int, y: int) -> list[str]:
    if provider in _AMAP_PROVIDERS:
        return [_build_tile_url(provider, z, x, y, subdomain=str(n)) for n in range(1, 5)]
    return [_build_tile_url(provider, z, x, y)]


def _fallback_tile_url(provider: str, z: int, x: int, y: int) -> str | None:
    alt = fallback_provider_for(provider)
    if alt is None or alt not in TILE_PROVIDERS:
        return None
    return _build_tile_url(alt, z, x, y)


def _tile_subdomain(x: int, y: int, provider: str) -> str:
    if provider in _AMAP_PROVIDERS:
        return str((x + y) % 4 + 1)
    return "a"


def _with_db(session_factory: sessionmaker[Session]) -> Session:
    try:
        return session_factory()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


def _parse_team_id(raw: str | None) -> uuid.UUID | None:
    if not raw or not raw.strip():
        return None
    try:
        return uuid.UUID(raw.strip())
    except ValueError as e:
        raise HTTPException(status_code=400, detail="invalid team_id") from e


@router.get("/tiles/{provider}/{z}/{x}/{y}.png")
async def proxy_map_tile(provider: str, z: int, x: int, y: int) -> Response:
    if provider not in TILE_PROVIDERS:
        raise HTTPException(status_code=404, detail="tile_provider_not_found")
    if z < 0 or z > 22 or x < 0 or y < 0:
        raise HTTPException(status_code=400, detail="invalid_tile_coords")

    headers = {"Cache-Control": BROWSER_TILE_CACHE}
    disk = disk_tile_hit(provider, z, x, y)
    if disk is not None:
        return FileResponse(disk, media_type="image/png", headers=headers)

    urls = _build_tile_urls(provider, z, x, y)
    try:
        payload = await fetch_map_tile(
            provider,
            z,
            x,
            y,
            urls,
            fallback_url=_fallback_tile_url(provider, z, x, y),
        )
    except HTTPError as e:
        alt_disk = disk_fallback_hit(provider, z, x, y)
        if alt_disk is not None:
            return FileResponse(alt_disk, media_type="image/png", headers=headers)
        raise HTTPException(status_code=502, detail="tile_fetch_failed") from e

    if payload.path is not None:
        return FileResponse(payload.path, media_type=payload.media_type, headers=headers)
    return Response(content=payload.content or b"", media_type=payload.media_type, headers=headers)


@router.get("/layers", response_model=MapLayersResponse)
def get_map_layers(
    team_id: str | None = Query(default=None),
    days: int = Query(default=30, ge=1, le=365),
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MapLayersResponse:
    team_uuid = _parse_team_id(team_id)
    since = datetime.now(timezone.utc) - timedelta(days=days)
    db = _with_db(session_factory)
    try:
        if team_uuid is not None:
            team = db.get(Team, team_uuid)
            if team is None:
                raise HTTPException(status_code=404, detail="team_not_found")
            require_team_member(db, team, current_user)

        if team_uuid is not None:
            team = db.get(Team, team_uuid)
            if team is None:
                raise HTTPException(status_code=404, detail="team_not_found")
            require_team_member(db, team, current_user)
            members_rows = db.query(TeamMember).filter(TeamMember.team_id == team_uuid).all()
            member_ids = [m.user_id for m in members_rows]
            identify_query = db.query(IdentifyRecord).filter(
                IdentifyRecord.user_id.in_(member_ids),
                IdentifyRecord.gps_lat.isnot(None),
                IdentifyRecord.gps_lng.isnot(None),
                IdentifyRecord.server_ts >= since,
            )
        else:
            identify_query = db.query(IdentifyRecord).filter(
                IdentifyRecord.user_id == current_user.id,
                IdentifyRecord.gps_lat.isnot(None),
                IdentifyRecord.gps_lng.isnot(None),
                IdentifyRecord.server_ts >= since,
            )
        identify_rows = identify_query.order_by(IdentifyRecord.server_ts.desc()).limit(500).all()

        sos_query = db.query(SosEvent).filter(
            SosEvent.gps_lat.isnot(None),
            SosEvent.gps_lng.isnot(None),
            SosEvent.server_ts >= since,
        )
        if team_uuid is not None:
            members_rows = db.query(TeamMember).filter(TeamMember.team_id == team_uuid).all()
            member_ids = [m.user_id for m in members_rows]
            sos_query = sos_query.filter(SosEvent.user_id.in_(member_ids))
        else:
            sos_query = sos_query.filter(SosEvent.user_id == current_user.id)
        sos_rows = sos_query.order_by(SosEvent.server_ts.desc()).limit(200).all()

        members: list[MapMemberPoint] = []
        if team_uuid is not None:
            members_rows = db.query(TeamMember).filter(TeamMember.team_id == team_uuid).all()
            member_ids = [m.user_id for m in members_rows]
            stale_before = datetime.now(timezone.utc) - timedelta(hours=24)
            loc_rows = (
                db.query(MemberLocation, User)
                .join(User, User.id == MemberLocation.user_id)
                .filter(
                    MemberLocation.user_id.in_(member_ids),
                    MemberLocation.server_ts >= stale_before,
                )
                .all()
            )
            altitudes: dict[uuid.UUID, tuple[float | None, float | None]] = {}
            for mid in member_ids:
                track = (
                    db.query(MemberTrackPoint)
                    .filter(
                        MemberTrackPoint.user_id == mid,
                        MemberTrackPoint.team_id == team_uuid,
                        MemberTrackPoint.gps_altitude.isnot(None),
                    )
                    .order_by(MemberTrackPoint.server_ts.desc())
                    .first()
                )
                if track is not None:
                    altitudes[mid] = (track.gps_altitude, track.baro_altitude)
            for loc, user in loc_rows:
                gps_alt, baro_alt = altitudes.get(user.id, (None, None))
                members.append(
                    MapMemberPoint(
                        user_id=str(user.id),
                        username=user.username,
                        display_name=user.display_name or user.username,
                        team_id=str(loc.team_id) if loc.team_id else None,
                        server_ts=loc.server_ts,
                        client_ts=loc.client_ts,
                        gps_lat=loc.gps_lat,
                        gps_lng=loc.gps_lng,
                        accuracy_m=loc.accuracy_m,
                        gps_altitude=gps_alt,
                        baro_altitude=baro_alt,
                    )
                )

        sos_user_ids = {row.user_id for row in sos_rows if row.user_id is not None}
        sos_users: dict[uuid.UUID, User] = {}
        if sos_user_ids:
            for user in db.query(User).filter(User.id.in_(sos_user_ids)).all():
                sos_users[user.id] = user

        identify = [
            MapIdentifyPoint(
                request_id=str(row.id),
                label_main=row.label_main,
                scene=row.scene,
                risk_level=row.risk_level,
                summary=str((row.result_json or {}).get("summary", ""))[:500],
                server_ts=row.server_ts,
                gps_lat=float(row.gps_lat),
                gps_lng=float(row.gps_lng),
                accuracy_m=row.accuracy_m,
            )
            for row in identify_rows
        ]

        sos = []
        for row in sos_rows:
            user = sos_users.get(row.user_id) if row.user_id else None
            sos.append(
                MapSosPoint(
                    incident_id=str(row.id),
                    event_type=row.event_type,
                    device_id=row.device_id,
                    user_id=str(row.user_id) if row.user_id else None,
                    display_name=(user.display_name or user.username) if user else None,
                    server_ts=row.server_ts,
                    client_ts=row.client_ts,
                    gps_lat=float(row.gps_lat),
                    gps_lng=float(row.gps_lng),
                    accuracy_m=row.accuracy_m,
                )
            )

        return MapLayersResponse(identify=identify, sos=sos, members=members)
    finally:
        db.close()


@router.get("/trajectories", response_model=MapTrajectoriesResponse)
def get_map_trajectories(
    team_id: str = Query(...),
    days: int = Query(default=1, ge=1, le=30),
    user_id: str | None = Query(default=None),
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MapTrajectoriesResponse:
    team_uuid = _parse_team_id(team_id)
    if team_uuid is None:
        raise HTTPException(status_code=400, detail="invalid team_id")
    since = datetime.now(timezone.utc) - timedelta(days=days)
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_uuid)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_member(db, team, current_user)

        members_rows = db.query(TeamMember).filter(TeamMember.team_id == team_uuid).all()
        member_ids = [m.user_id for m in members_rows]
        if user_id:
            try:
                filter_uid = uuid.UUID(user_id.strip())
            except ValueError as e:
                raise HTTPException(status_code=400, detail="invalid user_id") from e
            if filter_uid not in member_ids:
                raise HTTPException(status_code=403, detail="not_team_member")
            member_ids = [filter_uid]

        users = {u.id: u for u in db.query(User).filter(User.id.in_(member_ids)).all()}
        trajectories: list[MapTrajectory] = []
        for mid in member_ids:
            user = users.get(mid)
            if user is None:
                continue
            rows = (
                db.query(MemberTrackPoint)
                .filter(
                    MemberTrackPoint.user_id == mid,
                    MemberTrackPoint.team_id == team_uuid,
                    MemberTrackPoint.server_ts >= since,
                )
                .order_by(MemberTrackPoint.server_ts.asc())
                .limit(5000)
                .all()
            )
            if not rows:
                continue
            trajectories.append(
                MapTrajectory(
                    user_id=str(user.id),
                    username=user.username,
                    display_name=user.display_name or user.username,
                    points=[
                        MapTrackPoint(
                            gps_lat=row.gps_lat,
                            gps_lng=row.gps_lng,
                            server_ts=row.server_ts,
                            source=row.source,
                            gps_speed=row.gps_speed,
                            gps_altitude=row.gps_altitude,
                        )
                        for row in rows
                    ],
                )
            )
        return MapTrajectoriesResponse(items=trajectories)
    finally:
        db.close()


@router.post("/sensor", response_model=LocationReportResponse)
def report_sensor(
    body: LocationReportRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> LocationReportResponse:
    return report_location(body, current_user, session_factory)


@router.post("/location", response_model=LocationReportResponse)
def report_location(
    body: LocationReportRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> LocationReportResponse:
    team_uuid = _parse_team_id(body.team_id)
    db = _with_db(session_factory)
    try:
        if team_uuid is not None:
            team = db.get(Team, team_uuid)
            if team is None:
                raise HTTPException(status_code=404, detail="team_not_found")
            require_team_member(db, team, current_user)

        server_ts = upsert_member_location(
            db,
            user=current_user,
            team_id=team_uuid,
            gps_lat=body.gps_lat,
            gps_lng=body.gps_lng,
            accuracy_m=body.accuracy_m,
            client_ts=body.client_ts,
        )
        save_track_point(
            db,
            user=current_user,
            team_id=team_uuid,
            gps_lat=body.gps_lat,
            gps_lng=body.gps_lng,
            accuracy_m=body.accuracy_m,
            client_ts=body.client_ts,
            source="sensor",
            payload=body.model_dump(),
        )
        db.commit()
        return LocationReportResponse(ok=True, server_ts=server_ts)
    finally:
        db.close()
