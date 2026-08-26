from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from sqlalchemy.orm import Session

from .models import IdentifyRecord, MemberLocation, MemberTrackPoint, User
from .schemas import EnvironmentLatestResponse
from .veye_demo_static import demo_environment_for_user


def _to_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _climate_from_altitude(baro_alt: float | None, gps_alt: float | None) -> tuple[float, float]:
    """演示用：由海拔估算温湿度（无传感器字段时）。"""
    ref = baro_alt if baro_alt is not None else gps_alt if gps_alt is not None else 496.0
    temperature = round(26.8 - (ref - 496.0) * 0.008, 1)
    humidity = round(max(38.0, min(88.0, 62.0 + (temperature - 24.0) * 1.8)), 0)
    return temperature, humidity


def get_latest_environment(db: Session, user: User) -> EnvironmentLatestResponse:
    demo = demo_environment_for_user(user.username)
    if demo is not None:
        track = (
            db.query(MemberTrackPoint)
            .filter(
                MemberTrackPoint.user_id == user.id,
                MemberTrackPoint.source == "demo_env",
            )
            .order_by(MemberTrackPoint.server_ts.desc())
            .first()
        )
        server_ts = track.server_ts if track else datetime.now(timezone.utc)
        return EnvironmentLatestResponse(
            server_ts=server_ts,
            gps_lat=demo["gps_lat"],
            gps_lng=demo["gps_lng"],
            accuracy_m=demo.get("accuracy_m"),
            gps_altitude=demo.get("gps_altitude"),
            baro_altitude=demo.get("baro_altitude"),
            temperature=demo.get("temperature"),
            humidity=demo.get("humidity"),
            gps_speed=demo.get("gps_speed"),
            gps_heading=demo.get("gps_heading"),
            imu_heading=demo.get("imu_heading"),
            pitch=demo.get("pitch"),
            roll=demo.get("roll"),
            accel_x=demo.get("accel_x"),
            accel_y=demo.get("accel_y"),
            accel_z=demo.get("accel_z"),
            gyro_x=demo.get("gyro_x"),
            gyro_y=demo.get("gyro_y"),
            gyro_z=demo.get("gyro_z"),
            source="demo_env",
            label=demo.get("label"),
        )

    track = (
        db.query(MemberTrackPoint)
        .filter(MemberTrackPoint.user_id == user.id)
        .order_by(MemberTrackPoint.server_ts.desc())
        .first()
    )
    identify = (
        db.query(IdentifyRecord)
        .filter(IdentifyRecord.user_id == user.id)
        .order_by(IdentifyRecord.server_ts.desc())
        .first()
    )
    loc = db.get(MemberLocation, user.id)
    sensor: dict[str, Any] = {}
    if identify and isinstance(identify.sensor_json, dict):
        sensor.update(identify.sensor_json)

    gps_lat = track.gps_lat if track else (loc.gps_lat if loc else None)
    gps_lng = track.gps_lng if track else (loc.gps_lng if loc else None)
    accuracy_m = track.accuracy_m if track else (loc.accuracy_m if loc else None)
    gps_altitude = track.gps_altitude if track else _to_float(sensor.get("gps_altitude"))
    baro_altitude = track.baro_altitude if track else _to_float(sensor.get("baro_altitude"))
    temperature = _to_float(sensor.get("temperature"))
    humidity = _to_float(sensor.get("humidity"))
    if temperature is None or humidity is None:
        derived_temp, derived_hum = _climate_from_altitude(baro_altitude, gps_altitude)
        temperature = temperature if temperature is not None else derived_temp
        humidity = humidity if humidity is not None else derived_hum

    server_ts = track.server_ts if track else (identify.server_ts if identify else datetime.now(timezone.utc))

    return EnvironmentLatestResponse(
        server_ts=server_ts,
        gps_lat=gps_lat,
        gps_lng=gps_lng,
        accuracy_m=accuracy_m,
        gps_altitude=gps_altitude,
        baro_altitude=baro_altitude,
        temperature=temperature,
        humidity=humidity,
        gps_speed=track.gps_speed if track else _to_float(sensor.get("gps_speed")),
        gps_heading=track.gps_heading if track else _to_float(sensor.get("gps_heading")),
        imu_heading=track.imu_heading if track else _to_float(sensor.get("imu_heading")),
        pitch=track.pitch if track else _to_float(sensor.get("pitch")),
        roll=track.roll if track else _to_float(sensor.get("roll")),
        accel_x=track.accel_x if track else _to_float(sensor.get("accel_x")),
        accel_y=track.accel_y if track else _to_float(sensor.get("accel_y")),
        accel_z=track.accel_z if track else _to_float(sensor.get("accel_z")),
        gyro_x=track.gyro_x if track else _to_float(sensor.get("gyro_x")),
        gyro_y=track.gyro_y if track else _to_float(sensor.get("gyro_y")),
        gyro_z=track.gyro_z if track else _to_float(sensor.get("gyro_z")),
        source="track" if track else ("identify" if identify else "none"),
    )
