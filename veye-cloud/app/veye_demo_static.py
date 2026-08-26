"""veye小队演示静态快照（与 scripts/seed_veye_demo.py、frontend veyeDemoGeo 坐标一致）。"""

from __future__ import annotations

from typing import Any

EUPHORIA_USERNAME = "Euphoria"

# A · 查环境（环）
EUPH_ENV: dict[str, Any] = {
    "label": "查环境",
    "gps_lat": 30.74785,
    "gps_lng": 103.93225,
    "accuracy_m": 4.0,
    "temperature": 26.4,
    "humidity": 64.0,
    "gps_altitude": 494.2,
    "baro_altitude": 491.8,
    "gps_speed": 0.3,
    "gps_heading": 168.0,
    "imu_heading": 172.0,
    "pitch": 2.0,
    "roll": -1.0,
    "accel_x": 0.04,
    "accel_y": -0.08,
    "accel_z": 9.79,
    "gyro_x": 0.008,
    "gyro_y": -0.015,
    "gyro_z": 0.01,
    "mag_x": 22.4,
    "mag_y": -4.8,
    "mag_z": 41.2,
}

# A · 查运动（动）
EUPH_MOTION: dict[str, Any] = {
    "label": "veye小队演示",
    "gps_lat": 30.74745,
    "gps_lng": 103.93215,
    "accuracy_m": 4.5,
    "gps_altitude": 492.6,
    "baro_altitude": 490.1,
    "gps_speed": 1.2,
    "gps_heading": 175.0,
    "imu_heading": 178.0,
    "pitch": 6.0,
    "roll": 1.5,
    "accel_x": 0.12,
    "accel_y": -0.18,
    "accel_z": 9.72,
    "gyro_x": 0.04,
    "gyro_y": -0.03,
    "gyro_z": 0.02,
    "session_steps": 486,
    "total_steps": 3426,
    "pose_score": 88.0,
    "fall_count": 0,
    "activity": "walking",
}


def demo_environment_for_user(username: str) -> dict[str, Any] | None:
    if username == EUPHORIA_USERNAME:
        return dict(EUPH_ENV)
    return None


def demo_motion_summary_for_user(username: str) -> dict[str, Any] | None:
    if username == EUPHORIA_USERNAME:
        return dict(EUPH_MOTION)
    return None
