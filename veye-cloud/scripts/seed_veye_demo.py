#!/usr/bin/env python3
"""Seed「veye小队」电子科大清水河校区三人演示数据：Euphoria / Paddi / HuauH(队长)。"""

from __future__ import annotations

import shutil
import uuid
from collections import deque
from datetime import datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

from app.auth import hash_password
from app.config import get_settings
from app.db import create_engine_from_settings, create_session_factory
from app.models import (
    ChatMessage,
    CompanionState,
    IdentifyRecord,
    MemberLocation,
    MemberTrackPoint,
    MotionSession,
    MotionSnapshot,
    SosEvent,
    Team,
    TeamMember,
    User,
)
from app.sensor_service import build_sensor_json
from app.veye_demo_static import EUPH_ENV, EUPH_MOTION, EUPHORIA_USERNAME

TEAM_NAME = "veye小队"
DEMO_DATE = datetime(2026, 7, 8).date()
TZ = ZoneInfo("Asia/Shanghai")
PASSWORD = "VeyeDemo1!"

# 队员 A / B / 队长 C
MEMBERS = [
    {"username": "Euphoria", "display_name": "Euphoria", "role": "member", "label": "A"},
    {"username": "Paddi", "display_name": "Paddi", "role": "member", "label": "B"},
]

# 电子科技大学清水河校区 WGS84 内缩边界（南门/西门参考，留校园边缘余量）
CAMPUS_BOUNDS = {
    "lat_min": 30.7448,
    "lat_max": 30.7545,
    "lng_min": 103.9248,
    "lng_max": 103.9335,
}

# 集合点（校区东北，图右上）+ 任务点（贴合地标）
TEAM_START = (30.7518, 103.9330)

TASK_POINTS: dict[str, tuple[float, float, str]] = {
    "Euphoria": (30.7482, 103.9323, "A · 识图（莲）"),
    "Paddi": (30.7512, 103.9332, "B · 报警 / LoRa"),
    "HuauH": (30.74945, 103.93240, "C · 指挥待命与救援"),
}

ROUTE_POINTS_PER_LEG = 2
BASE_ALTITUDE = 496.0

# 湖面范围（轨迹不得穿越）
LAKE_BOUNDS = {
    "lat_min": 30.7485,
    "lat_max": 30.7501,
    "lng_min": 103.9300,
    "lng_max": 103.9322,
}

# 环湖白线拐点（在湖面外侧道路上）
CAMPUS_NODES: dict[str, tuple[float, float]] = {
    "start": TEAM_START,
    "euph_task": TASK_POINTS["Euphoria"][:2],
    "paddi_task": TASK_POINTS["Paddi"][:2],
    "huauh_task": TASK_POINTS["HuauH"][:2],
    "lk_n": (30.7509, 103.9312),    # 湖北
    "lk_ne": (30.7507, 103.9331),   # 湖东北
    "lk_e": (30.7493, 103.9333),    # 湖东（道路外侧）
    "lk_se": (30.7478, 103.9324),   # 湖东南
    "lk_nw": (30.7508, 103.9287),   # 湖西北
    "euph_env": (30.74785, 103.93225),    # A · 查环境
    "euph_motion": (30.74745, 103.93215),  # A · 查运动
}

CAMPUS_EDGES: list[tuple[str, str]] = [
    ("lk_nw", "lk_n"),
    ("lk_n", "lk_ne"),
    ("lk_ne", "lk_e"),
    ("lk_e", "lk_se"),
    ("start", "lk_ne"),
    ("lk_e", "euph_task"),
    ("lk_se", "euph_task"),
    ("lk_ne", "paddi_task"),
    ("lk_e", "huauh_task"),
    ("euph_task", "euph_env"),
    ("euph_env", "euph_motion"),
]

# 只沿湖岸白线折行，禁止 start→湖对岸的斜穿
MEMBER_STREET_PATHS: dict[str, list[str]] = {
    "Euphoria": ["start", "lk_ne", "lk_e", "euph_task", "euph_env", "euph_motion"],
    "Paddi": ["start", "lk_ne", "paddi_task"],
    "HuauH": ["start", "lk_ne", "lk_e", "huauh_task"],
}
RESCUE_STREET_PATH = ["huauh_task", "lk_e", "lk_ne", "paddi_task"]

# 剧本契合聊天（LoRa 坐标在 main 中按实际 SOS 点动态填充）
CHAT_SCRIPT_STATIC: list[tuple[str, str, str]] = [
    ("HuauH", "13:50", "今日veye校区演示，三人保持队形，注意补水。"),
    ("Euphoria", "13:52", "收到，我负责语音交互与识图演示。"),
    ("Paddi", "13:53", "明白，我演示报警与离线 LoRa 通讯。"),
    ("HuauH", "14:05", "集合点出发，三人分赴各自任务点。"),
    ("Euphoria", "14:22", "开始识图，校园内发现结香灌木。"),
    ("HuauH", "14:24", "注意记录坐标与环境数据。"),
    ("Euphoria", "14:26", "语音交互与环境监测演示完成。"),
    ("Paddi", "14:36", "[LoRa] 蜂窝信号中断，切换 LoRa 中继模式"),
    ("HuauH", "14:37", "收到，Paddi 显示蜂窝离线但 LoRa 仍在线。"),
    ("HuauH", "14:38", "有成员报警！Paddi 任务点告急，我过去支援。"),
    ("Euphoria", "14:39", "我在识图任务点保持联络，随时待命。"),
    ("HuauH", "14:42", "已确认 Paddi 位置，正在接近。"),
    ("HuauH", "14:50", "任务结束，报告自动上传。"),
    ("HuauH", "14:52", "报警、离线通讯、云端报告，演示完毕。"),
]

SAMPLE_IMAGES = Path("/root/veye-cloud/app/static/web/auth-collage")


def _ts(hour: int, minute: int, second: int = 0) -> datetime:
    return datetime(
        DEMO_DATE.year, DEMO_DATE.month, DEMO_DATE.day, hour, minute, second, tzinfo=TZ
    ).astimezone(timezone.utc)


def _clamp_campus(lat: float, lng: float) -> tuple[float, float]:
    return (
        max(CAMPUS_BOUNDS["lat_min"], min(CAMPUS_BOUNDS["lat_max"], lat)),
        max(CAMPUS_BOUNDS["lng_min"], min(CAMPUS_BOUNDS["lng_max"], lng)),
    )


def _in_lake(lat: float, lng: float) -> bool:
    return (
        LAKE_BOUNDS["lat_min"] <= lat <= LAKE_BOUNDS["lat_max"]
        and LAKE_BOUNDS["lng_min"] <= lng <= LAKE_BOUNDS["lng_max"]
    )


def _segment_crosses_lake(lat1: float, lng1: float, lat2: float, lng2: float, samples: int = 12) -> bool:
    for i in range(samples + 1):
        t = i / samples
        lat = lat1 + (lat2 - lat1) * t
        lng = lng1 + (lng2 - lng1) * t
        if _in_lake(lat, lng):
            return True
    return False


def _validate_street_paths() -> None:
    all_paths = {**MEMBER_STREET_PATHS, "rescue": RESCUE_STREET_PATH}
    for name, node_ids in all_paths.items():
        for a, b in zip(node_ids, node_ids[1:]):
            lat1, lng1 = CAMPUS_NODES[a]
            lat2, lng2 = CAMPUS_NODES[b]
            if _segment_crosses_lake(lat1, lng1, lat2, lng2):
                raise ValueError(f"路径 {name} 段 {a}→{b} 穿越湖面")


def _fmt_coord(lat: float, lng: float) -> str:
    return f"{lat:.4f}°N {lng:.4f}°E"


def _in_campus(lat: float, lng: float) -> bool:
    return (
        CAMPUS_BOUNDS["lat_min"] <= lat <= CAMPUS_BOUNDS["lat_max"]
        and CAMPUS_BOUNDS["lng_min"] <= lng <= CAMPUS_BOUNDS["lng_max"]
    )


def _lora_chat_messages(sos_lat: float, sos_lng: float, fall_lat: float, fall_lng: float) -> list[tuple[str, str, str]]:
    sos = _fmt_coord(sos_lat, sos_lng)
    fall = f"{fall_lat:.4f}°N, {fall_lng:.4f}°E"
    return [
        ("Paddi", "14:36", f"[LoRa] 状态：在线 · 中继可用 · {sos}"),
        ("Paddi", "14:37", f"[LoRa] SOS | Paddi | {sos}"),
        ("Paddi", "14:38", f"[LoRa][SOS] 跌倒 · {fall} · 14:38:06"),
    ]


def _sensor_at(step: int, *, lat: float, lng: float, speed: float = 1.2, base_alt: float = BASE_ALTITUDE) -> dict:
    heading = (step * 17 + 40) % 360
    return build_sensor_json(
        {
            "gps_lat": lat,
            "gps_lng": lng,
            "gps_speed": speed,
            "gps_heading": heading,
            "gps_altitude": base_alt + step * 2.8,
            "accel_x": 0.05 + (step % 3) * 0.02,
            "accel_y": -0.12 + (step % 5) * 0.01,
            "accel_z": 9.78 + (step % 2) * 0.03,
            "gyro_x": 0.01 * (step % 4),
            "gyro_y": -0.02 * (step % 3),
            "gyro_z": 0.015 * (step % 5),
            "mag_x": 22.0 + step * 0.1,
            "mag_y": -5.0 + step * 0.05,
            "mag_z": 41.0,
            "pitch": 4.0 + (step % 6),
            "roll": -2.0 + (step % 4) * 0.5,
            "imu_heading": (heading + 5) % 360,
            "baro_altitude": base_alt - 3 + step * 2.5,
        }
    )


def _build_campus_graph() -> dict[str, list[str]]:
    graph: dict[str, list[str]] = {name: [] for name in CAMPUS_NODES}
    for a, b in CAMPUS_EDGES:
        graph[a].append(b)
        graph[b].append(a)
    return graph


def _street_path(start_id: str, end_id: str) -> list[str]:
    """沿校园道路图最短路径（BFS）。"""
    if start_id == end_id:
        return [start_id]
    graph = _build_campus_graph()

    queue: deque[tuple[str, list[str]]] = deque([(start_id, [start_id])])
    seen = {start_id}
    while queue:
        node, path = queue.popleft()
        for nxt in graph.get(node, []):
            if nxt in seen:
                continue
            npath = path + [nxt]
            if nxt == end_id:
                return npath
            seen.add(nxt)
            queue.append((nxt, npath))
    return [start_id, end_id]


def _route_along_streets(node_ids: list[str], points_per_leg: int = ROUTE_POINTS_PER_LEG) -> list[tuple[float, float]]:
    """沿道路节点折线；默认每段仅起终点，保持轨迹简洁。"""
    if not node_ids:
        return []
    if points_per_leg <= 2:
        return [_clamp_campus(*CAMPUS_NODES[nid]) for nid in node_ids]
    coords = [CAMPUS_NODES[nid] for nid in node_ids]
    out: list[tuple[float, float]] = []
    for i in range(len(coords) - 1):
        lat1, lng1 = coords[i]
        lat2, lng2 = coords[i + 1]
        seg = _route_walk(lat1, lng1, lat2, lng2, steps=max(2, points_per_leg))
        if out:
            seg = seg[1:]
        out.extend(seg)
    return out


def _dedupe_route(points: list[tuple[float, float]]) -> list[tuple[float, float]]:
    if not points:
        return []
    out = [points[0]]
    for lat, lng in points[1:]:
        prev = out[-1]
        if abs(lat - prev[0]) > 1e-7 or abs(lng - prev[1]) > 1e-7:
            out.append((lat, lng))
    return out


def _route_walk(
    start_lat: float,
    start_lng: float,
    end_lat: float,
    end_lng: float,
    steps: int = 6,
) -> list[tuple[float, float]]:
    """单段道路直线插值（用于街道折线的每一段）。"""
    if steps < 2:
        return [_clamp_campus(start_lat, start_lng)]
    pts: list[tuple[float, float]] = []
    for i in range(steps):
        t = i / (steps - 1)
        lat = start_lat + (end_lat - start_lat) * t
        lng = start_lng + (end_lng - start_lng) * t
        pts.append(_clamp_campus(lat, lng))
    return pts


def _sensor_from_demo_snapshot(snapshot: dict, step: int) -> dict:
    """将演示静态快照合并为轨迹点传感器字段。"""
    lat = snapshot["gps_lat"]
    lng = snapshot["gps_lng"]
    speed = float(snapshot.get("gps_speed", 1.0))
    base_alt = float(snapshot.get("gps_altitude", BASE_ALTITUDE))
    base = _sensor_at(step, lat=lat, lng=lng, speed=speed, base_alt=base_alt - step * 2.8)
    skip = {
        "label",
        "session_steps",
        "total_steps",
        "pose_score",
        "fall_count",
        "activity",
    }
    for key, value in snapshot.items():
        if key not in skip and value is not None:
            base[key] = value
    return base


def _seed_euphoria_track(
    db,
    *,
    user: User,
    team_id: uuid.UUID,
    path_nodes: list[str],
) -> None:
    """Euphoria：识图 → 查环境 → 查运动，静态传感器对齐地图折点。"""
    for idx, node_id in enumerate(path_nodes):
        lat, lng = CAMPUS_NODES[node_id]
        minute = 14 * 60 + 5 + idx * 3
        ts = _ts(minute // 60, minute % 60)
        if node_id == "euph_env":
            sensor = _sensor_from_demo_snapshot(EUPH_ENV, idx)
            source = "demo_env"
        elif node_id == "euph_motion":
            sensor = _sensor_from_demo_snapshot(EUPH_MOTION, idx)
            source = "demo_motion"
        else:
            sensor = _sensor_at(idx, lat=lat, lng=lng, speed=1.1)
            source = "sensor"
        _add_track_point(
            db,
            user=user,
            team_id=team_id,
            lat=lat,
            lng=lng,
            ts=ts,
            sensor=sensor,
            source=source,
        )


def _seed_euphoria_motion(db, *, user: User) -> None:
    """Euphoria 查运动：静态运动会话与快照。"""
    started = _ts(14, 28)
    ended = _ts(14, 32)
    session = MotionSession(
        id=uuid.uuid4(),
        user_id=user.id,
        status="completed",
        started_at=started,
        ended_at=ended,
        steps=int(EUPH_MOTION["session_steps"]),
        fall_count=int(EUPH_MOTION["fall_count"]),
        pose_score=float(EUPH_MOTION["pose_score"]),
        summary_json={
            "label": EUPH_MOTION["label"],
            "waypoint": "euph_motion",
            "gps_lat": EUPH_MOTION["gps_lat"],
            "gps_lng": EUPH_MOTION["gps_lng"],
            "activity": EUPH_MOTION["activity"],
            "source": "veye-demo",
        },
        created_at=started,
        updated_at=ended,
    )
    db.add(session)
    db.flush()
    db.add(
        MotionSnapshot(
            id=uuid.uuid4(),
            session_id=session.id,
            client_ts=ended,
            steps=int(EUPH_MOTION["session_steps"]),
            activity=str(EUPH_MOTION["activity"]),
            fall_detected=False,
            pose_keypoints={},
            sensor_json={
                "gps_lat": EUPH_MOTION["gps_lat"],
                "gps_lng": EUPH_MOTION["gps_lng"],
                "gps_altitude": EUPH_MOTION["gps_altitude"],
                "baro_altitude": EUPH_MOTION["baro_altitude"],
                "label": EUPH_MOTION["label"],
            },
            server_ts=ended,
        )
    )


def _add_track_point(
    db,
    *,
    user: User,
    team_id: uuid.UUID,
    lat: float,
    lng: float,
    ts: datetime,
    sensor: dict,
    source: str = "sensor",
    identify_record_id: uuid.UUID | None = None,
    accuracy_m: float = 5.0,
) -> None:
    db.add(
        MemberTrackPoint(
            id=uuid.uuid4(),
            user_id=user.id,
            team_id=team_id,
            source=source,
            identify_record_id=identify_record_id,
            gps_lat=lat,
            gps_lng=lng,
            gps_speed=sensor.get("gps_speed"),
            gps_heading=sensor.get("gps_heading"),
            gps_altitude=sensor.get("gps_altitude"),
            accuracy_m=accuracy_m,
            accel_x=sensor.get("accel_x"),
            accel_y=sensor.get("accel_y"),
            accel_z=sensor.get("accel_z"),
            gyro_x=sensor.get("gyro_x"),
            gyro_y=sensor.get("gyro_y"),
            gyro_z=sensor.get("gyro_z"),
            mag_x=sensor.get("mag_x"),
            mag_y=sensor.get("mag_y"),
            mag_z=sensor.get("mag_z"),
            pitch=sensor.get("pitch"),
            roll=sensor.get("roll"),
            imu_heading=sensor.get("imu_heading"),
            baro_altitude=sensor.get("baro_altitude"),
            client_ts=ts,
            server_ts=ts,
        )
    )


def ensure_user(db, username: str, display_name: str, *, reset_password: bool = False) -> User:
    user = db.query(User).filter(User.username == username).one_or_none()
    if user:
        if user.display_name != display_name:
            user.display_name = display_name
        if reset_password:
            user.password_hash = hash_password(PASSWORD)
        return user
    user = User(
        id=uuid.uuid4(),
        username=username,
        display_name=display_name,
        password_hash=hash_password(PASSWORD),
    )
    db.add(user)
    db.flush()
    return user


def copy_image(settings, filename: str) -> str:
    src = SAMPLE_IMAGES / filename
    if not src.is_file():
        raise FileNotFoundError(src)
    dest_dir = Path(settings.IMAGE_DIR)
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / f"veye-demo-{uuid.uuid4().hex[:10]}-{filename}"
    shutil.copy2(src, dest)
    return str(dest)


def main() -> None:
    _validate_street_paths()
    settings = get_settings()
    sf = create_session_factory(create_engine_from_settings(settings))
    with sf() as db:
        leader = db.query(User).filter(User.username == "HuauH").one_or_none()
        if leader is None:
            leader = ensure_user(db, "HuauH", "HuauH")
        else:
            leader = ensure_user(db, "HuauH", "HuauH", reset_password=True)
        users: dict[str, User] = {"HuauH": leader}
        for spec in MEMBERS:
            users[spec["username"]] = ensure_user(
                db, spec["username"], spec["display_name"], reset_password=True
            )

        team = db.query(Team).filter(Team.name == TEAM_NAME, Team.owner_id == leader.id).one_or_none()
        if team is None:
            team = Team(
                id=uuid.uuid4(),
                name=TEAM_NAME,
                description="V-Eye 清水河校区三人演示小队 · Euphoria / Paddi / HuauH",
                owner_id=leader.id,
            )
            db.add(team)
            db.flush()
        else:
            team.description = "V-Eye 清水河校区三人演示小队 · Euphoria / Paddi / HuauH"

        member_rows = (
            db.query(TeamMember).filter(TeamMember.team_id == team.id).all()
        )
        existing_user_ids = {m.user_id for m in member_rows}
        if leader.id not in existing_user_ids:
            db.add(TeamMember(id=uuid.uuid4(), team_id=team.id, user_id=leader.id, role="owner"))
        for spec in MEMBERS:
            u = users[spec["username"]]
            if u.id not in existing_user_ids:
                db.add(
                    TeamMember(
                        id=uuid.uuid4(),
                        team_id=team.id,
                        user_id=u.id,
                        role=spec["role"],
                    )
                )

        member_ids = [m.user_id for m in db.query(TeamMember).filter(TeamMember.team_id == team.id).all()]
        db.query(ChatMessage).filter(ChatMessage.team_id == team.id).delete(synchronize_session=False)
        db.query(MemberTrackPoint).filter(MemberTrackPoint.team_id == team.id).delete(synchronize_session=False)
        db.query(SosEvent).filter(
            SosEvent.user_id.in_(member_ids),
            SosEvent.server_ts >= _ts(0, 0),
        ).delete(synchronize_session=False)
        db.query(IdentifyRecord).filter(
            IdentifyRecord.user_id.in_(member_ids),
            IdentifyRecord.device_id.like("veye-demo-%"),
        ).delete(synchronize_session=False)
        db.query(MotionSession).filter(
            MotionSession.user_id.in_(member_ids),
            MotionSession.started_at >= _ts(0, 0),
        ).delete(synchronize_session=False)

        routes: dict[str, list[tuple[float, float]]] = {}
        street_paths: dict[str, list[str]] = dict(MEMBER_STREET_PATHS)
        for username, path in MEMBER_STREET_PATHS.items():
            routes[username] = _dedupe_route(_route_along_streets(path))

        paddi_task_lat, paddi_task_lng, _ = TASK_POINTS["Paddi"]
        sos_lat, sos_lng = paddi_task_lat, paddi_task_lng
        fall_lat, fall_lng = _clamp_campus(sos_lat - 0.00012, sos_lng + 0.00010)

        chat_script = list(CHAT_SCRIPT_STATIC)
        insert_at = next(
            i
            for i, (_, hm, body) in enumerate(chat_script)
            if hm == "14:37" and body == "收到，Paddi 显示蜂窝离线但 LoRa 仍在线。"
        )
        for j, msg in enumerate(_lora_chat_messages(sos_lat, sos_lng, fall_lat, fall_lng)):
            chat_script.insert(insert_at + j, msg)

        for username, hm, body in chat_script:
            h, m = map(int, hm.split(":"))
            db.add(
                ChatMessage(
                    id=uuid.uuid4(),
                    team_id=team.id,
                    sender_id=users[username].id,
                    body=body,
                    created_at=_ts(h, m),
                )
            )

        for username in TASK_POINTS:
            user = users[username]
            route = routes[username]
            n = len(route)

            if username == EUPHORIA_USERNAME:
                _seed_euphoria_track(
                    db,
                    user=user,
                    team_id=team.id,
                    path_nodes=street_paths[username],
                )
            else:
                for idx, (rlat, rlng) in enumerate(route):
                    if username == "Paddi" and idx >= n - 2:
                        speed = 0.5
                    else:
                        speed = 1.1
                    minute = 14 * 60 + 5 + idx * 3
                    ts = _ts(minute // 60, minute % 60)
                    sensor = _sensor_at(idx, lat=rlat, lng=rlng, speed=speed)
                    _add_track_point(
                        db,
                        user=user,
                        team_id=team.id,
                        lat=rlat,
                        lng=rlng,
                        ts=ts,
                        sensor=sensor,
                    )

            loc = db.get(MemberLocation, user.id)
            task_lat, task_lng, _ = TASK_POINTS[username]
            last_lat, last_lng = task_lat, task_lng
            last_ts = _ts(14, 45)
            if loc is None:
                db.add(
                    MemberLocation(
                        user_id=user.id,
                        team_id=team.id,
                        gps_lat=last_lat,
                        gps_lng=last_lng,
                        accuracy_m=5.0,
                        client_ts=last_ts,
                        server_ts=last_ts,
                    )
                )
            else:
                loc.team_id = team.id
                loc.gps_lat = last_lat
                loc.gps_lng = last_lng
                loc.accuracy_m = 5.0
                loc.client_ts = last_ts
                loc.server_ts = last_ts

        # 队长 14:38 沿校园道路从指挥任务点支援 Paddi 任务点
        rescue_path = list(RESCUE_STREET_PATH)
        rescue_pts = _dedupe_route(_route_along_streets(rescue_path))
        leader = users["HuauH"]
        huauh_steps = len(routes["HuauH"])
        for j, (rlat, rlng) in enumerate(rescue_pts):
            minute = 14 * 60 + 38 + j
            ts = _ts(minute // 60, minute % 60)
            sensor = _sensor_at(huauh_steps + j, lat=rlat, lng=rlng, speed=1.7)
            _add_track_point(db, user=leader, team_id=team.id, lat=rlat, lng=rlng, ts=ts, sensor=sensor)
        # 队长最终位置保持在指挥任务点（支援轨迹仅作历史折线）
        euph = users["Euphoria"]
        euph_task_lat, euph_task_lng, _ = TASK_POINTS["Euphoria"]
        identify_lat, identify_lng = euph_task_lat, euph_task_lng
        identify_ts = _ts(14, 24)
        image_path = copy_image(settings, "wildflower.jpg")
        rec_id = uuid.uuid4()
        sensor = _sensor_at(len(routes["Euphoria"]) - 1, lat=identify_lat, lng=identify_lng)
        sensor = {**sensor, "temperature": EUPH_ENV["temperature"], "humidity": EUPH_ENV["humidity"]}
        db.add(
            IdentifyRecord(
                id=rec_id,
                user_id=euph.id,
                device_id="veye-demo-Euphoria",
                scene="toxic_plant",
                image_path=image_path,
                result_json={
                    "label_main": "结香",
                    "confidence": 0.942,
                    "summary": "结香，瑞香科灌木，春季开花，全株无毒。",
                    "advice": "安全（无毒），可观赏，请勿过度采摘。",
                    "risk_level": 0,
                    "risk_tags": ["demo", "safe"],
                },
                risk_level=0,
                label_main="结香",
                server_ts=identify_ts,
                gps_lat=None,
                gps_lng=None,
                accuracy_m=None,
                sensor_json=sensor,
            )
        )

        _seed_euphoria_motion(db, user=euph)

        paddi = users["Paddi"]
        db.add(
            SosEvent(
                id=uuid.uuid4(),
                user_id=paddi.id,
                device_id="veye-demo-Paddi",
                event_type="manual_sos",
                gps_lat=sos_lat,
                gps_lng=sos_lng,
                accuracy_m=6.0,
                client_ts=_ts(14, 37, 30),
                server_ts=_ts(14, 37, 30),
            )
        )
        db.add(
            SosEvent(
                id=uuid.uuid4(),
                user_id=paddi.id,
                device_id="veye-demo-Paddi",
                event_type="fall_detected",
                gps_lat=fall_lat,
                gps_lng=fall_lng,
                accuracy_m=8.0,
                client_ts=_ts(14, 38, 6),
                server_ts=_ts(14, 38, 6),
            )
        )

        for username in ("Euphoria", "Paddi", "HuauH"):
            user = users[username]
            row = db.get(CompanionState, user.id)
            if username == EUPHORIA_USERNAME:
                companion_defaults = {
                    "mood": "active",
                    "level": 2,
                    "energy": 86,
                    "total_steps": int(EUPH_MOTION["total_steps"]),
                    "message": "查运动完成，姿态良好 · veye小队演示",
                    "pose_json": {"animation": "happy", "facing": "right", "waypoint": "euph_motion"},
                }
            else:
                companion_defaults = {
                    "mood": "idle",
                    "level": 1,
                    "energy": 100,
                    "total_steps": 1200,
                    "message": "演示就绪",
                    "pose_json": {"animation": "idle", "facing": "right"},
                }
            if row is None:
                db.add(
                    CompanionState(
                        user_id=user.id,
                        **companion_defaults,
                    )
                )
            else:
                for key, value in companion_defaults.items():
                    setattr(row, key, value)

        db.commit()

        print("=== veye小队 Demo Seeded ===")
        print(f"Team: {TEAM_NAME} ({team.id})")
        print("Captain: HuauH")
        print(f"All demo logins — password: {PASSWORD}")
        print(f"Date: {DEMO_DATE}")
        print(f"Campus bounds: lat {CAMPUS_BOUNDS['lat_min']}-{CAMPUS_BOUNDS['lat_max']}, lng {CAMPUS_BOUNDS['lng_min']}-{CAMPUS_BOUNDS['lng_max']}")
        print(f"Team start: {TEAM_START}")
        for username, (lat, lng, label) in TASK_POINTS.items():
            path = " → ".join(street_paths[username])
            print(f"Task {username}: ({lat}, {lng}) — {label}")
            print(f"  Street: {path}")
        rescue_street = " → ".join(rescue_path)
        print(f"Rescue street: {rescue_street}")
        track_total = sum(len(r) for r in routes.values()) + len(rescue_pts)
        print(f"Track points: {track_total} (沿湖岸白线 · 不穿越湖面)")
        print(f"Chat messages: {len(chat_script)} (含 Paddi [LoRa] 离线模拟)")
        print("\nNext: Web 地图/组队 Tab 选择 veye小队")


if __name__ == "__main__":
    main()
