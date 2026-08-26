from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Any

from sqlalchemy.orm import Session

from .models import MemberLocation, MemberTrackPoint, User


def sensor_fields_from_payload(payload: dict[str, Any]) -> dict[str, Any]:
    """Extract IMU + extended GPS fields (excludes satellite count and UTC)."""
    return {
        "gps_speed": payload.get("gps_speed"),
        "gps_heading": payload.get("gps_heading"),
        "gps_altitude": payload.get("gps_altitude"),
        "accel_x": payload.get("accel_x"),
        "accel_y": payload.get("accel_y"),
        "accel_z": payload.get("accel_z"),
        "gyro_x": payload.get("gyro_x"),
        "gyro_y": payload.get("gyro_y"),
        "gyro_z": payload.get("gyro_z"),
        "mag_x": payload.get("mag_x"),
        "mag_y": payload.get("mag_y"),
        "mag_z": payload.get("mag_z"),
        "pitch": payload.get("pitch"),
        "roll": payload.get("roll"),
        "imu_heading": payload.get("imu_heading"),
        "baro_altitude": payload.get("baro_altitude"),
    }


def build_sensor_json(payload: dict[str, Any]) -> dict[str, Any]:
    fields = sensor_fields_from_payload(payload)
    return {k: v for k, v in fields.items() if v is not None}


def _to_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def location_fields_from_record(
    *,
    gps_lat: float | None,
    gps_lng: float | None,
    accuracy_m: float | None,
    sensor_json: dict | None,
) -> dict[str, Any]:
    sensor = sensor_json if isinstance(sensor_json, dict) else {}
    gps_altitude = _to_float(sensor.get("gps_altitude"))
    baro_altitude = _to_float(sensor.get("baro_altitude"))
    has_gps = gps_lat is not None and gps_lng is not None
    return {
        "has_gps": has_gps,
        "gps_altitude": gps_altitude,
        "baro_altitude": baro_altitude,
        "accuracy_m": accuracy_m,
    }


def save_track_point(
    db: Session,
    *,
    user: User,
    team_id: uuid.UUID | None,
    gps_lat: float,
    gps_lng: float,
    accuracy_m: float | None,
    client_ts: datetime | None,
    source: str,
    identify_record_id: uuid.UUID | None = None,
    payload: dict[str, Any] | None = None,
) -> MemberTrackPoint:
    extra = sensor_fields_from_payload(payload or {})
    server_ts = datetime.now(timezone.utc)
    row = MemberTrackPoint(
        user_id=user.id,
        team_id=team_id,
        source=source,
        identify_record_id=identify_record_id,
        gps_lat=gps_lat,
        gps_lng=gps_lng,
        accuracy_m=accuracy_m,
        client_ts=client_ts,
        server_ts=server_ts,
        **extra,
    )
    db.add(row)
    return row


def upsert_member_location(
    db: Session,
    *,
    user: User,
    team_id: uuid.UUID | None,
    gps_lat: float,
    gps_lng: float,
    accuracy_m: float | None,
    client_ts: datetime | None,
) -> datetime:
    server_ts = datetime.now(timezone.utc)
    row = db.get(MemberLocation, user.id)
    if row is None:
        row = MemberLocation(
            user_id=user.id,
            team_id=team_id,
            gps_lat=gps_lat,
            gps_lng=gps_lng,
            accuracy_m=accuracy_m,
            client_ts=client_ts,
            server_ts=server_ts,
        )
        db.add(row)
    else:
        row.team_id = team_id
        row.gps_lat = gps_lat
        row.gps_lng = gps_lng
        row.accuracy_m = accuracy_m
        row.client_ts = client_ts
        row.server_ts = server_ts
    return server_ts
