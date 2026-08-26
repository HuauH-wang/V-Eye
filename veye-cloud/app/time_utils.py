from __future__ import annotations

from datetime import datetime, timezone
from zoneinfo import ZoneInfo

DISPLAY_TIMEZONE = "Asia/Shanghai"


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def display_timezone() -> ZoneInfo:
    try:
        return ZoneInfo(DISPLAY_TIMEZONE)
    except Exception:
        return ZoneInfo("Asia/Shanghai")


def as_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)
