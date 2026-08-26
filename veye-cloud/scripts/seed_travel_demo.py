#!/usr/bin/env python3
"""Seed a travel demo team with sensor tracks, images, chat, SOS and report."""

from __future__ import annotations

import math
import shutil
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

from app.auth import hash_password
from app.config import get_settings
from app.db import create_engine_from_settings, create_session_factory
from app.models import (
    ChatMessage,
    IdentifyRecord,
    MemberLocation,
    MemberTrackPoint,
    SosEvent,
    Team,
    TeamMember,
    User,
)
from app.report_service import aggregate_timeline
from app.sensor_service import build_sensor_json

TEAM_NAME = "云山征途小队"
DEMO_DATE = datetime(2026, 7, 8).date()
TZ = ZoneInfo("Asia/Shanghai")
PASSWORD = "TravelDemo1!"

MEMBERS = [
    {"username": "travel_mei", "display_name": "阿梅", "role": "member"},
    {"username": "travel_kai", "display_name": "凯子", "role": "member"},
    {"username": "travel_yun", "display_name": "小云", "role": "member"},
]

# 神农架林区附近徒步路线（WGS84）
BASE_LAT, BASE_LNG = 31.4528, 110.6715

CHAT_SCRIPT = [
    ("HuauH", "07:45", "各位早，今天走神农架西线，保持队形，注意补水。"),
    ("travel_mei", "07:48", "收到！我带了额外净水片。"),
    ("travel_kai", "07:50", "路边雾气很大，能见度一般，我会放慢脚步。"),
    ("travel_yun", "08:05", "看到一片高山杜鹃，我先拍几张。"),
    ("HuauH", "09:20", "前方林带变密，注意脚下苔藓。"),
    ("travel_mei", "10:15", "识图提示疑似毒菌，大家别碰野生菌类。"),
    ("travel_kai", "11:40", "午餐点到了，原地休整 20 分钟。"),
    ("travel_yun", "13:05", "脚踝有点扭到，我落后一点，@HuauH"),
    ("HuauH", "13:08", "小云你先停一下，我们折返接应。"),
    ("travel_yun", "13:12", "已发 SOS，坐标应该能上传。"),
    ("travel_mei", "13:20", "看到红星标记了，我们正往你那边赶。"),
    ("HuauH", "14:30", "汇合完成，伤势稳定，下午改走平缓路段。"),
    ("travel_kai", "16:10", "今天识别记录不少，晚上可以生成旅行报告。"),
    ("HuauH", "16:45", "收队回营地，今晚由我写报告。"),
]

IDENTIFY_ITEMS = [
    ("HuauH", "alpine.jpg", "高山草甸", "generic", 0, "开阔的高山草甸，植被低矮。"),
    ("travel_mei", "wildflower.jpg", "高山杜鹃", "toxic_plant", 1, "疑似高山杜鹃，请勿采摘。"),
    ("travel_kai", "forest-trail.jpg", "密林步道", "generic", 0, "林间步道湿滑，注意防滑。"),
    ("travel_yun", "leaf-macro.jpg", "未知菌类", "toxic_plant", 2, "检测到疑似有毒菌类，请勿食用。"),
    ("travel_mei", "mist-forest.jpg", "雾林", "generic", 0, "雾气弥漫的冷杉林。"),
    ("travel_kai", "alpine.jpg", "岩壁苔藓", "generic", 0, "岩壁苔藓茂密，路面潮湿。"),
]

SAMPLE_IMAGES = Path("/root/veye-cloud/app/static/web/auth-collage")


def _ts(hour: int, minute: int) -> datetime:
    return datetime(DEMO_DATE.year, DEMO_DATE.month, DEMO_DATE.day, hour, minute, tzinfo=TZ).astimezone(
        timezone.utc
    )


def _sensor_at(step: int, *, lat: float, lng: float, speed: float = 1.2) -> dict:
    heading = (step * 17 + 40) % 360
    return build_sensor_json(
        {
            "gps_lat": lat,
            "gps_lng": lng,
            "gps_speed": speed,
            "gps_heading": heading,
            "gps_altitude": 1680 + step * 3.5,
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
            "baro_altitude": 1675 + step * 3.2,
        }
    )


def _route_points(start_offset: float, steps: int = 18) -> list[tuple[float, float]]:
    pts: list[tuple[float, float]] = []
    for i in range(steps):
        angle = math.radians(35 + start_offset + i * 11)
        dist = 0.0012 * (i + 1)
        lat = BASE_LAT + math.cos(angle) * dist
        lng = BASE_LNG + math.sin(angle) * dist * 1.15
        pts.append((lat, lng))
    return pts


def ensure_user(db, username: str, display_name: str) -> User:
    user = db.query(User).filter(User.username == username).one_or_none()
    if user:
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
    dest = dest_dir / f"demo-{uuid.uuid4().hex[:12]}-{filename}"
    shutil.copy2(src, dest)
    return str(dest)


def main() -> None:
    settings = get_settings()
    sf = create_session_factory(create_engine_from_settings(settings))
    with sf() as db:
        leader = db.query(User).filter(User.username == "HuauH").one()
        users: dict[str, User] = {"HuauH": leader}
        for spec in MEMBERS:
            users[spec["username"]] = ensure_user(db, spec["username"], spec["display_name"])

        team = db.query(Team).filter(Team.name == TEAM_NAME, Team.owner_id == leader.id).one_or_none()
        if team is None:
            team = Team(
                id=uuid.uuid4(),
                name=TEAM_NAME,
                description="V-Eye 旅行 Demo：神农架西线一日徒步",
                owner_id=leader.id,
            )
            db.add(team)
            db.flush()
            db.add(
                TeamMember(
                    id=uuid.uuid4(),
                    team_id=team.id,
                    user_id=leader.id,
                    role="owner",
                )
            )

        for spec in MEMBERS:
            u = users[spec["username"]]
            exists = (
                db.query(TeamMember)
                .filter(TeamMember.team_id == team.id, TeamMember.user_id == u.id)
                .one_or_none()
            )
            if not exists:
                db.add(
                    TeamMember(
                        id=uuid.uuid4(),
                        team_id=team.id,
                        user_id=u.id,
                        role=spec["role"],
                    )
                )

        # Clean prior demo data for idempotent re-run
        member_ids = [m.user_id for m in db.query(TeamMember).filter(TeamMember.team_id == team.id).all()]
        db.query(ChatMessage).filter(ChatMessage.team_id == team.id).delete(synchronize_session=False)
        db.query(MemberTrackPoint).filter(MemberTrackPoint.team_id == team.id).delete(synchronize_session=False)
        db.query(SosEvent).filter(SosEvent.user_id.in_(member_ids), SosEvent.server_ts >= _ts(0, 0)).delete(
            synchronize_session=False
        )
        db.query(IdentifyRecord).filter(
            IdentifyRecord.user_id.in_(member_ids),
            IdentifyRecord.server_ts >= _ts(0, 0),
            IdentifyRecord.server_ts < _ts(23, 59),
            IdentifyRecord.device_id.like("demo-%"),
        ).delete(synchronize_session=False)
        db.query(IdentifyRecord).filter(
            IdentifyRecord.user_id.in_(member_ids),
            IdentifyRecord.device_id.like("demo-%"),
        ).delete(synchronize_session=False)

        # Chat
        for username, hm, body in CHAT_SCRIPT:
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

        # Tracks + periodic sensor + member locations
        offsets = {"HuauH": 0, "travel_mei": 12, "travel_kai": 24, "travel_yun": 36}
        for username, offset in offsets.items():
            user = users[username]
            route = _route_points(offset, steps=20 if username != "travel_yun" else 14)
            for idx, (lat, lng) in enumerate(route):
                minute = 8 * 60 + idx * 18 + int(offset / 3)
                ts = _ts(minute // 60, minute % 60)
                sensor = _sensor_at(idx, lat=lat, lng=lng, speed=0.8 if username == "travel_yun" else 1.3)
                db.add(
                    MemberTrackPoint(
                        id=uuid.uuid4(),
                        user_id=user.id,
                        team_id=team.id,
                        source="sensor",
                        gps_lat=lat,
                        gps_lng=lng,
                        gps_speed=sensor.get("gps_speed"),
                        gps_heading=sensor.get("gps_heading"),
                        gps_altitude=sensor.get("gps_altitude"),
                        accuracy_m=6.0,
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
            last_lat, last_lng = route[-1]
            loc = db.get(MemberLocation, user.id)
            last_ts = _ts(16, 30)
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

        # Identify records with sensor snapshots
        photo_times = [("HuauH", 9, 5), ("travel_mei", 10, 10), ("travel_kai", 11, 0), ("travel_yun", 12, 40), ("travel_mei", 14, 0), ("travel_kai", 15, 20)]
        for (username, h, m), (uname, img, label, scene, risk, summary) in zip(photo_times, IDENTIFY_ITEMS):
            assert username == uname
            user = users[username]
            route = _route_points(offsets[username], steps=20)
            idx = min(len(route) - 1, (h - 8) * 2)
            lat, lng = route[idx]
            ts = _ts(h, m)
            sensor = _sensor_at(idx, lat=lat, lng=lng)
            rec_id = uuid.uuid4()
            image_path = copy_image(settings, img)
            rec = IdentifyRecord(
                id=rec_id,
                user_id=user.id,
                device_id=f"demo-{username}",
                scene=scene,
                image_path=image_path,
                result_json={
                    "label_main": label,
                    "confidence": 0.86,
                    "summary": summary,
                    "advice": "保持观察距离，勿触碰未知物种。",
                    "risk_level": risk,
                    "risk_tags": ["demo"],
                },
                risk_level=risk,
                label_main=label,
                server_ts=ts,
                gps_lat=lat,
                gps_lng=lng,
                accuracy_m=4.5,
                sensor_json=sensor,
            )
            db.add(rec)
            db.add(
                MemberTrackPoint(
                    id=uuid.uuid4(),
                    user_id=user.id,
                    team_id=team.id,
                    source="identify",
                    identify_record_id=rec_id,
                    gps_lat=lat,
                    gps_lng=lng,
                    gps_speed=sensor.get("gps_speed"),
                    gps_heading=sensor.get("gps_heading"),
                    gps_altitude=sensor.get("gps_altitude"),
                    accuracy_m=4.5,
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

        # SOS from travel_yun
        yun_route = _route_points(offsets["travel_yun"], steps=14)
        sos_lat, sos_lng = yun_route[10]
        sos_ts = _ts(13, 12)
        db.add(
            SosEvent(
                id=uuid.uuid4(),
                user_id=users["travel_yun"].id,
                device_id="demo-travel_yun",
                event_type="injury",
                gps_lat=sos_lat,
                gps_lng=sos_lng,
                accuracy_m=8.0,
                client_ts=sos_ts,
                server_ts=sos_ts,
            )
        )

        db.commit()

        timeline = aggregate_timeline(
            db,
            team=team,
            subject_user=leader,
            report_date=DEMO_DATE,
            settings=settings,
            report_type="travel",
        )
        print("=== Travel Demo Seeded ===")
        print(f"Team: {TEAM_NAME} ({team.id})")
        print(f"Leader: HuauH (password unchanged)")
        print(f"Members password: {PASSWORD}")
        print(f"Date: {DEMO_DATE}")
        print(f"Stats: {timeline['stats']}")
        print(f"Trajectory: {timeline.get('trajectory')}")
        print(f"Events: {len(timeline['events'])}")
        print("\nNext steps:")
        print("1. Web 地图 → 选择「云山征途小队」→ 开启轨迹/SOS 图层")
        print("2. 工作报告 → 类型 travel → 主体 HuauH → 日期 2026-07-08 → 生成")


if __name__ == "__main__":
    main()
