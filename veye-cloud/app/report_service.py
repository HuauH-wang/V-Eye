from __future__ import annotations

import logging
import re
import uuid
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

from .report_utils import day_bounds

from sqlalchemy.orm import Session, joinedload, sessionmaker

from .config import Settings
from .model_scheduler import full_report_pipeline_guard
from .models import (
    ChatMessage,
    IdentifyRecord,
    MemberLocation,
    MemberTrackPoint,
    ReportJob,
    SosEvent,
    Team,
    TeamMember,
    User,
)
from .sos_service import find_last_activity_date, query_identify_for_subject, query_sos_for_subject
from .report_llm_client import ReportLlmError, chat_completions_text
from .report_prompt import build_report_system_prompt, build_report_user_prompt, ensure_report_images
from .vision_identify import re_identify_record

logger = logging.getLogger(__name__)

ACTIVE_STATUSES = frozenset({"pending", "aggregating", "identifying", "writing"})
MAX_TIMELINE_EVENTS = 80
SNIPPET_LEN = 80


def is_missing_identify(row: IdentifyRecord) -> bool:
    if not row.label_main or not str(row.label_main).strip():
        return True
    result = row.result_json if isinstance(row.result_json, dict) else {}
    summary = str(result.get("summary", "")).strip()
    if not summary:
        return True
    return False


def _truncate(text: str, n: int = SNIPPET_LEN) -> str:
    t = re.sub(r"\s+", " ", text.strip())
    if len(t) <= n:
        return t
    return t[: n - 1] + "…"


def _haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    from math import asin, cos, radians, sin, sqrt

    r = 6371000.0
    dlat = radians(lat2 - lat1)
    dlng = radians(lng2 - lng1)
    a = sin(dlat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(dlng / 2) ** 2
    return 2 * r * asin(sqrt(a))


def _summarize_trajectory(rows: list[MemberTrackPoint]) -> dict | None:
    if not rows:
        return None
    distance_m = 0.0
    prev = rows[0]
    for row in rows[1:]:
        distance_m += _haversine_m(prev.gps_lat, prev.gps_lng, row.gps_lat, row.gps_lng)
        prev = row
    first, last = rows[0], rows[-1]
    return {
        "point_count": len(rows),
        "distance_km": round(distance_m / 1000, 2),
        "start": {"lat": first.gps_lat, "lng": first.gps_lng, "ts": first.server_ts.isoformat()},
        "end": {"lat": last.gps_lat, "lng": last.gps_lng, "ts": last.server_ts.isoformat()},
        "note": "基于 GPS 轨迹点连线估算",
    }


def aggregate_timeline(
    db: Session,
    *,
    team: Team,
    subject_user: User,
    report_date: date,
    settings: Settings,
    report_type: str = "safety",
) -> dict:
    start, end = day_bounds(report_date, settings)
    start_utc = start.astimezone(timezone.utc)
    end_utc = end.astimezone(timezone.utc)

    member_rows = db.query(TeamMember).filter(TeamMember.team_id == team.id).all()
    member_ids = [m.user_id for m in member_rows]
    member_users = {u.id: u for u in db.query(User).filter(User.id.in_(member_ids)).all()}
    team_scope = report_type == "travel"

    if team_scope:
        identify_rows = (
            db.query(IdentifyRecord)
            .filter(
                IdentifyRecord.user_id.in_(member_ids),
                IdentifyRecord.server_ts >= start_utc,
                IdentifyRecord.server_ts < end_utc,
            )
            .order_by(IdentifyRecord.server_ts.asc())
            .all()
        )
        sos_rows = (
            db.query(SosEvent)
            .filter(
                SosEvent.user_id.in_(member_ids),
                SosEvent.server_ts >= start_utc,
                SosEvent.server_ts < end_utc,
            )
            .order_by(SosEvent.server_ts.asc())
            .all()
        )
    else:
        identify_rows = query_identify_for_subject(
            db,
            subject_user_id=subject_user.id,
            start_utc=start_utc,
            end_utc=end_utc,
        ).all()
        sos_rows = query_sos_for_subject(
            db,
            subject_user_id=subject_user.id,
            start_utc=start_utc,
            end_utc=end_utc,
        ).all()

    chat_rows = (
        db.query(ChatMessage)
        .options(joinedload(ChatMessage.sender))
        .filter(
            ChatMessage.team_id == team.id,
            ChatMessage.sender_id == subject_user.id,
            ChatMessage.created_at >= start_utc,
            ChatMessage.created_at < end_utc,
        )
        .order_by(ChatMessage.created_at.asc())
        .all()
    )

    team_chat_rows = (
        db.query(ChatMessage)
        .options(joinedload(ChatMessage.sender))
        .filter(
            ChatMessage.team_id == team.id,
            ChatMessage.created_at >= start_utc,
            ChatMessage.created_at < end_utc,
        )
        .order_by(ChatMessage.created_at.asc())
        .all()
    )

    team_chat_count = len(team_chat_rows)

    loc = db.get(MemberLocation, subject_user.id)
    if team_scope:
        track_rows = (
            db.query(MemberTrackPoint)
            .filter(
                MemberTrackPoint.team_id == team.id,
                MemberTrackPoint.server_ts >= start_utc,
                MemberTrackPoint.server_ts < end_utc,
            )
            .order_by(MemberTrackPoint.server_ts.asc())
            .limit(20000)
            .all()
        )
        trajectories_by_user: dict[uuid.UUID, list[MemberTrackPoint]] = {}
        for row in track_rows:
            trajectories_by_user.setdefault(row.user_id, []).append(row)
        member_tracks = []
        for uid, rows in trajectories_by_user.items():
            summary = _summarize_trajectory(rows)
            if not summary:
                continue
            user = member_users.get(uid)
            member_tracks.append(
                {
                    "display_name": (user.display_name or user.username) if user else str(uid),
                    "username": user.username if user else None,
                    **summary,
                }
            )
        trajectory = {"members": member_tracks, "note": "小队各成员 GPS 轨迹汇总"} if member_tracks else None
    else:
        track_rows = (
            db.query(MemberTrackPoint)
            .filter(
                MemberTrackPoint.user_id == subject_user.id,
                MemberTrackPoint.team_id == team.id,
                MemberTrackPoint.server_ts >= start_utc,
                MemberTrackPoint.server_ts < end_utc,
            )
            .order_by(MemberTrackPoint.server_ts.asc())
            .limit(5000)
            .all()
        )
        trajectory = _summarize_trajectory(track_rows)

    events: list[dict] = []
    images: list[dict] = []
    for row in identify_rows:
        result = row.result_json if isinstance(row.result_json, dict) else {}
        has_image = Path(str(row.image_path)).is_file()
        caption = (row.label_main or str(result.get("summary", ""))[:80] or "识图记录").strip()
        record_id = str(row.id)
        owner = member_users.get(row.user_id) if row.user_id else None
        event = {
            "type": "identify",
            "ts": row.server_ts.isoformat(),
            "record_id": record_id,
            "has_image": has_image,
            "image_caption": caption,
            "label": row.label_main,
            "risk_level": row.risk_level,
            "scene": row.scene,
            "summary": str(result.get("summary", ""))[:200],
            "gps": {"lat": row.gps_lat, "lng": row.gps_lng} if row.gps_lat is not None else None,
            "sensor": row.sensor_json if isinstance(row.sensor_json, dict) else None,
            "member": (owner.display_name or owner.username) if owner else None,
        }
        events.append(event)
        if has_image:
            images.append({"record_id": record_id, "caption": caption, "ts": row.server_ts.isoformat()})

    for row in sos_rows:
        owner = member_users.get(row.user_id) if row.user_id else None
        events.append(
            {
                "type": "sos",
                "ts": row.server_ts.isoformat(),
                "event_type": row.event_type,
                "gps": {"lat": row.gps_lat, "lng": row.gps_lng},
                "member": (owner.display_name or owner.username) if owner else None,
            }
        )

    for msg in team_chat_rows:
        sender = msg.sender
        events.append(
            {
                "type": "chat",
                "ts": msg.created_at.isoformat(),
                "author": (sender.display_name or sender.username) if sender else "未知",
                "author_username": sender.username if sender else None,
                "body": msg.body[:500],
                "is_subject": msg.sender_id == subject_user.id,
            }
        )

    events.sort(key=lambda e: e.get("ts", ""))

    mention_count = sum(1 for m in chat_rows if "@" in m.body)
    snippets = [
        {
            "author": (m.sender.display_name or m.sender.username) if m.sender else "未知",
            "body": _truncate(m.body),
        }
        for m in chat_rows[:8]
    ]

    stats = {
        "identify_count": len(identify_rows),
        "high_risk_count": sum(1 for r in identify_rows if r.risk_level >= 3),
        "sos_count": len(sos_rows),
        "chat_messages_by_subject": len(chat_rows),
        "team_chat_messages_total": team_chat_count,
        "chat_mention_count": mention_count,
        "chat_snippets": snippets,
    }
    if team_scope:
        stats["team_identify_count"] = len(identify_rows)
        stats["team_member_count"] = len(member_ids)

    location_note = None
    if trajectory is not None:
        location_note = trajectory
    elif loc is not None:
        location_note = {
            "gps": {"lat": loc.gps_lat, "lng": loc.gps_lng},
            "server_ts": loc.server_ts.isoformat(),
            "note": "仅最新位置点，非全天轨迹",
        }

    if len(events) > MAX_TIMELINE_EVENTS:
        events = events[:MAX_TIMELINE_EVENTS]
        stats["events_truncated"] = True

    meta: dict = {
        "team_name": team.name,
        "subject_display_name": subject_user.display_name or subject_user.username,
        "subject_username": subject_user.username,
        "report_date": report_date.isoformat(),
        "timezone": settings.REPORT_TIMEZONE,
    }
    if not events and stats.get("identify_count", 0) == 0 and stats.get("sos_count", 0) == 0:
        suggested = find_last_activity_date(db, team=team, subject_user=subject_user, settings=settings)
        if suggested and suggested != report_date:
            meta["suggested_report_date"] = suggested.isoformat()
            meta["empty_hint"] = (
                f"所选日期（{report_date.isoformat()}）内无识图、SOS 或群聊记录；"
                f"最近有活动的日期为 {suggested.isoformat()}，可切换后重新生成。"
            )
        else:
            meta["empty_hint"] = f"所选日期（{report_date.isoformat()}）内无识图、SOS 或群聊记录。"

    return {
        "meta": meta,
        "stats": stats,
        "events": events,
        "images": images,
        "location": location_note,
        "trajectory": trajectory,
    }


def build_report_preview(
    db: Session,
    *,
    team: Team,
    subject_user: User,
    report_date: date,
    settings: Settings,
    report_type: str = "safety",
) -> dict:
    timeline = aggregate_timeline(
        db,
        team=team,
        subject_user=subject_user,
        report_date=report_date,
        settings=settings,
        report_type=report_type,
    )
    counts = timeline["stats"]
    has_data = bool(timeline["events"]) or counts.get("chat_messages_by_subject", 0) > 0
    return {
        "report_date": report_date.isoformat(),
        "identify_count": counts.get("identify_count", 0),
        "sos_count": counts.get("sos_count", 0),
        "chat_messages_by_subject": counts.get("chat_messages_by_subject", 0),
        "has_data": has_data,
        "suggested_report_date": timeline["meta"].get("suggested_report_date"),
        "empty_hint": timeline["meta"].get("empty_hint"),
    }


def find_missing_identify_records(
    db: Session,
    *,
    subject_user_id: uuid.UUID,
    report_date: date,
    settings: Settings,
    force_reidentify: bool = False,
) -> list[IdentifyRecord]:
    start, end = day_bounds(report_date, settings)
    start_utc = start.astimezone(timezone.utc)
    end_utc = end.astimezone(timezone.utc)
    rows = query_identify_for_subject(
        db,
        subject_user_id=subject_user_id,
        start_utc=start_utc,
        end_utc=end_utc,
    ).order_by(IdentifyRecord.server_ts.asc()).all()
    if force_reidentify:
        return [r for r in rows if Path(str(r.image_path)).is_file()]
    return [r for r in rows if is_missing_identify(r)]


def _update_job(
    db: Session,
    job: ReportJob,
    *,
    status: str | None = None,
    phase_detail: dict | None = None,
    timeline_json: dict | None = None,
    markdown: str | None = None,
    markdown_generated: str | None = None,
    edited_at: datetime | None = None,
    finished: bool = False,
) -> None:
    if status is not None:
        job.status = status
    if phase_detail is not None:
        job.phase_detail = phase_detail
    if timeline_json is not None:
        job.timeline_json = timeline_json
    if markdown is not None:
        job.markdown = markdown
    if markdown_generated is not None:
        job.markdown_generated = markdown_generated
    if edited_at is not None:
        job.edited_at = edited_at
    job.updated_at = datetime.now(timezone.utc)
    if finished:
        job.finished_at = datetime.now(timezone.utc)
    db.add(job)
    db.commit()


async def run_report_job(
    job_id: uuid.UUID,
    settings: Settings,
    session_factory: sessionmaker[Session],
) -> None:
    def _is_cancelled() -> bool:
        with session_factory() as db:
            job = db.get(ReportJob, job_id)
            return job is None or job.status == "cancelled"

    try:
        with session_factory() as db:
            job = db.get(ReportJob, job_id)
            if job is None or job.status == "cancelled":
                return
            team = db.get(Team, job.team_id)
            subject = db.get(User, job.subject_user_id)
            if team is None or subject is None:
                _update_job(
                    db,
                    job,
                    status="failed",
                    phase_detail={"error": "team_or_user_not_found"},
                    finished=True,
                )
                return
            force_reidentify = bool((job.phase_detail or {}).get("force_reidentify"))
            _update_job(db, job, status="aggregating", phase_detail=job.phase_detail or {})

        if _is_cancelled():
            return

        async with full_report_pipeline_guard(settings) as guard:
            with session_factory() as db:
                job = db.get(ReportJob, job_id)
                if job is None or job.status == "cancelled":
                    return
                team = db.get(Team, job.team_id)
                subject = db.get(User, job.subject_user_id)
                report_date = job.report_date.date() if hasattr(job.report_date, "date") else job.report_date
                force_reidentify = bool((job.phase_detail or {}).get("force_reidentify"))

                missing = find_missing_identify_records(
                    db,
                    subject_user_id=subject.id,
                    report_date=report_date,
                    settings=settings,
                    force_reidentify=force_reidentify,
                )

            if missing:
                await guard.switch_to_vision()
                total = len(missing)
                done = 0
                with session_factory() as db:
                    job = db.get(ReportJob, job_id)
                    _update_job(
                        db,
                        job,
                        status="identifying",
                        phase_detail={"missing_total": total, "missing_done": 0},
                    )

                for rec in missing:
                    if _is_cancelled():
                        return
                    try:
                        with session_factory() as db:
                            row = db.get(IdentifyRecord, rec.id)
                            if row is None:
                                continue
                            await re_identify_record(
                                settings=settings,
                                session_factory=session_factory,
                                record=row,
                            )
                    except Exception as e:
                        logger.warning("backfill identify failed for %s: %s", rec.id, e)
                    done += 1
                    with session_factory() as db:
                        job = db.get(ReportJob, job_id)
                        _update_job(
                            db,
                            job,
                            status="identifying",
                            phase_detail={"missing_total": total, "missing_done": done},
                        )

            with session_factory() as db:
                job = db.get(ReportJob, job_id)
                if job is None or job.status == "cancelled":
                    return
                team = db.get(Team, job.team_id)
                subject = db.get(User, job.subject_user_id)
                report_date = job.report_date.date() if isinstance(job.report_date, datetime) else job.report_date
                report_type = job.report_type if job.report_type in ("safety", "travel") else "safety"
                timeline = aggregate_timeline(
                    db,
                    team=team,
                    subject_user=subject,
                    report_date=report_date,
                    settings=settings,
                    report_type=report_type,
                )
                requested_by = db.get(User, job.requested_by_user_id)
                if requested_by:
                    timeline["meta"]["author_display_name"] = (
                        requested_by.display_name or requested_by.username
                    )
                    timeline["meta"]["author_username"] = requested_by.username
                _update_job(db, job, timeline_json=timeline, status="writing", phase_detail={})

            await guard.switch_to_report()

            system_prompt = build_report_system_prompt(report_type)  # type: ignore[arg-type]
            user_prompt = build_report_user_prompt(timeline["meta"], timeline)
            markdown = await chat_completions_text(
                settings=settings,
                system_prompt=system_prompt,
                user_prompt=user_prompt,
            )
            markdown = ensure_report_images(markdown, timeline, report_type)  # type: ignore[arg-type]

            with session_factory() as db:
                job = db.get(ReportJob, job_id)
                _update_job(
                    db,
                    job,
                    status="done",
                    markdown=markdown,
                    markdown_generated=markdown,
                    phase_detail={},
                    finished=True,
                )

    except ReportLlmError as e:
        logger.exception("report job LLM failed: %s", job_id)
        with session_factory() as db:
            job = db.get(ReportJob, job_id)
            if job:
                _update_job(
                    db,
                    job,
                    status="failed",
                    phase_detail={"error": str(e)},
                    finished=True,
                )
    except Exception as e:
        logger.exception("report job failed: %s", job_id)
        with session_factory() as db:
            job = db.get(ReportJob, job_id)
            if job:
                _update_job(
                    db,
                    job,
                    status="failed",
                    phase_detail={"error": str(e)},
                    finished=True,
                )


def job_to_dict(job: ReportJob, db: Session) -> dict:
    subject = db.get(User, job.subject_user_id)
    requested_by = db.get(User, job.requested_by_user_id)
    team = db.get(Team, job.team_id)
    report_date = job.report_date
    if isinstance(report_date, datetime):
        report_date_str = report_date.date().isoformat()
    elif isinstance(report_date, date):
        report_date_str = report_date.isoformat()
    else:
        report_date_str = str(report_date)

    return {
        "id": str(job.id),
        "team_id": str(job.team_id),
        "team_name": team.name if team else "",
        "subject_user_id": str(job.subject_user_id),
        "subject_display_name": (subject.display_name or subject.username) if subject else "",
        "requested_by_user_id": str(job.requested_by_user_id),
        "requested_by_display_name": (requested_by.display_name or requested_by.username) if requested_by else "",
        "report_date": report_date_str,
        "report_type": job.report_type or "safety",
        "status": job.status,
        "phase_detail": job.phase_detail or {},
        "timeline_json": job.timeline_json,
        "markdown": job.markdown,
        "markdown_generated": job.markdown_generated,
        "is_edited": bool(
            job.markdown_generated
            and job.markdown
            and job.markdown.strip() != job.markdown_generated.strip()
        ),
        "edited_at": job.edited_at,
        "created_at": job.created_at,
        "updated_at": job.updated_at,
        "finished_at": job.finished_at,
    }


def update_report_markdown(
    db: Session,
    *,
    job: ReportJob,
    markdown: str,
) -> ReportJob:
    job.markdown = markdown
    now = datetime.now(timezone.utc)
    job.updated_at = now
    if job.markdown_generated and markdown.strip() != job.markdown_generated.strip():
        job.edited_at = now
    elif job.markdown_generated and markdown.strip() == job.markdown_generated.strip():
        job.edited_at = None
    db.add(job)
    db.commit()
    db.refresh(job)
    return job
