from __future__ import annotations

from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

from .config import Settings


def report_timezone(settings: Settings) -> ZoneInfo:
    try:
        return ZoneInfo(settings.REPORT_TIMEZONE)
    except Exception:
        return ZoneInfo("Asia/Shanghai")


def day_bounds(report_date: date, settings: Settings) -> tuple[datetime, datetime]:
    tz = report_timezone(settings)
    start = datetime.combine(report_date, time.min, tzinfo=tz)
    end = start + timedelta(days=1)
    return start, end
