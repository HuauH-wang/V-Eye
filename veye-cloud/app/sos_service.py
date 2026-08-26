from __future__ import annotations

import uuid
from datetime import date, datetime, timedelta, timezone

from sqlalchemy import or_
from sqlalchemy.orm import Session, joinedload

from .config import Settings
from .models import CameraDevice, ChatMessage, IdentifyRecord, SosEvent, Team, TeamMember, User
from .report_utils import day_bounds, report_timezone


def subject_device_ids(db: Session, user_id: uuid.UUID) -> set[str]:
    devices: set[str] = set()
    for (device_id,) in (
        db.query(IdentifyRecord.device_id)
        .filter(IdentifyRecord.user_id == user_id, IdentifyRecord.device_id.isnot(None))
        .distinct()
        .all()
    ):
        if device_id and device_id.strip():
            devices.add(device_id.strip())
    for (device_id,) in db.query(CameraDevice.device_id).filter(CameraDevice.user_id == user_id).all():
        if device_id and device_id.strip():
            devices.add(device_id.strip())
    return devices


def _ownership_filter(model, user_id: uuid.UUID, device_ids: set[str]):
    clauses = [model.user_id == user_id]
    if device_ids:
        clauses.append(
            (model.user_id.is_(None)) & (model.device_id.in_(list(device_ids))),
        )
    return or_(*clauses)


def query_identify_for_subject(
    db: Session,
    *,
    subject_user_id: uuid.UUID,
    start_utc: datetime,
    end_utc: datetime,
    device_ids: set[str] | None = None,
):
    if device_ids is None:
        device_ids = subject_device_ids(db, subject_user_id)
    return (
        db.query(IdentifyRecord)
        .filter(
            _ownership_filter(IdentifyRecord, subject_user_id, device_ids),
            IdentifyRecord.server_ts >= start_utc,
            IdentifyRecord.server_ts < end_utc,
        )
        .order_by(IdentifyRecord.server_ts.asc())
    )


def query_sos_for_subject(
    db: Session,
    *,
    subject_user_id: uuid.UUID,
    start_utc: datetime,
    end_utc: datetime,
    device_ids: set[str] | None = None,
):
    if device_ids is None:
        device_ids = subject_device_ids(db, subject_user_id)
    return (
        db.query(SosEvent)
        .filter(
            _ownership_filter(SosEvent, subject_user_id, device_ids),
            SosEvent.server_ts >= start_utc,
            SosEvent.server_ts < end_utc,
        )
        .order_by(SosEvent.server_ts.asc())
    )


def team_member_ids(db: Session, team_id: uuid.UUID) -> list[uuid.UUID]:
    return [m.user_id for m in db.query(TeamMember).filter(TeamMember.team_id == team_id).all()]


def query_sos_for_viewer(
    db: Session,
    *,
    viewer: User,
    team: Team | None,
    since_utc: datetime,
    limit: int,
) -> list[SosEvent]:
    q = db.query(SosEvent).filter(SosEvent.server_ts >= since_utc)
    if team is not None:
        member_ids = team_member_ids(db, team.id)
        if team.owner_id != viewer.id and viewer.id not in member_ids:
            raise ValueError("not_team_member")
        if team.owner_id == viewer.id:
            device_sets: list[set[str]] = []
            clauses = []
            for uid in member_ids:
                devices = subject_device_ids(db, uid)
                device_sets.append(devices)
                clauses.append(_ownership_filter(SosEvent, uid, devices))
            if not clauses:
                return []
            q = q.filter(or_(*clauses))
        else:
            devices = subject_device_ids(db, viewer.id)
            q = q.filter(_ownership_filter(SosEvent, viewer.id, devices))
    else:
        devices = subject_device_ids(db, viewer.id)
        q = q.filter(_ownership_filter(SosEvent, viewer.id, devices))
    return q.order_by(SosEvent.server_ts.desc()).limit(limit).all()


def assert_sos_delete_access(db: Session, viewer: User, row: SosEvent) -> None:
    if row.user_id == viewer.id:
        return
    devices = subject_device_ids(db, viewer.id)
    if row.user_id is None and row.device_id in devices:
        return
    if row.user_id is not None:
        owned = (
            db.query(Team.id)
            .join(TeamMember, TeamMember.team_id == Team.id)
            .filter(TeamMember.user_id == row.user_id, Team.owner_id == viewer.id)
            .first()
        )
        if owned is not None:
            return
    raise PermissionError("forbidden")


def sos_record_item(row: SosEvent, users: dict[uuid.UUID, User]) -> dict:
    user = users.get(row.user_id) if row.user_id else None
    return {
        "incident_id": str(row.id),
        "event_type": row.event_type,
        "device_id": row.device_id,
        "user_id": str(row.user_id) if row.user_id else None,
        "display_name": (user.display_name or user.username) if user else None,
        "username": user.username if user else None,
        "server_ts": row.server_ts,
        "client_ts": row.client_ts,
        "gps_lat": row.gps_lat,
        "gps_lng": row.gps_lng,
        "accuracy_m": row.accuracy_m,
        "has_gps": row.gps_lat is not None and row.gps_lng is not None,
        "attributed": row.user_id is not None,
    }


def count_activity_on_date(
    db: Session,
    *,
    team: Team,
    subject_user: User,
    report_date: date,
    settings: Settings,
) -> dict[str, int]:
    start, end = day_bounds(report_date, settings)
    start_utc = start.astimezone(timezone.utc)
    end_utc = end.astimezone(timezone.utc)
    device_ids = subject_device_ids(db, subject_user.id)

    identify_count = query_identify_for_subject(
        db,
        subject_user_id=subject_user.id,
        start_utc=start_utc,
        end_utc=end_utc,
        device_ids=device_ids,
    ).count()

    sos_count = query_sos_for_subject(
        db,
        subject_user_id=subject_user.id,
        start_utc=start_utc,
        end_utc=end_utc,
        device_ids=device_ids,
    ).count()

    chat_count = (
        db.query(ChatMessage)
        .filter(
            ChatMessage.team_id == team.id,
            ChatMessage.sender_id == subject_user.id,
            ChatMessage.created_at >= start_utc,
            ChatMessage.created_at < end_utc,
        )
        .count()
    )

    return {
        "identify_count": identify_count,
        "sos_count": sos_count,
        "chat_messages_by_subject": chat_count,
    }


def find_last_activity_date(
    db: Session,
    *,
    team: Team,
    subject_user: User,
    settings: Settings,
    lookback_days: int = 90,
) -> date | None:
    try:
        from zoneinfo import ZoneInfo

        today = datetime.now(report_timezone(settings)).date()
    except Exception:
        today = datetime.now(timezone.utc).date()

    device_ids = subject_device_ids(db, subject_user.id)
    since = datetime.now(timezone.utc) - timedelta(days=lookback_days)

    id_ts = (
        query_identify_for_subject(
            db,
            subject_user_id=subject_user.id,
            start_utc=since,
            end_utc=datetime.now(timezone.utc),
            device_ids=device_ids,
        )
        .with_entities(IdentifyRecord.server_ts)
        .order_by(IdentifyRecord.server_ts.desc())
        .limit(1)
        .scalar()
    )
    sos_ts = (
        query_sos_for_subject(
            db,
            subject_user_id=subject_user.id,
            start_utc=since,
            end_utc=datetime.now(timezone.utc),
            device_ids=device_ids,
        )
        .with_entities(SosEvent.server_ts)
        .order_by(SosEvent.server_ts.desc())
        .limit(1)
        .scalar()
    )
    chat_ts = (
        db.query(ChatMessage.created_at)
        .filter(
            ChatMessage.team_id == team.id,
            ChatMessage.sender_id == subject_user.id,
            ChatMessage.created_at >= since,
        )
        .order_by(ChatMessage.created_at.desc())
        .limit(1)
        .scalar()
    )

    candidates: list[datetime] = [ts for ts in (id_ts, sos_ts, chat_ts) if ts is not None]
    if not candidates:
        return None

    latest = max(candidates)
    if latest.tzinfo is None:
        latest = latest.replace(tzinfo=timezone.utc)
    try:
        return latest.astimezone(report_timezone(settings)).date()
    except Exception:
        return latest.date()
