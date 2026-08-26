from __future__ import annotations

import asyncio
import uuid
from datetime import date, datetime

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session, sessionmaker

from .config import Settings
from .deps import get_current_user, get_session_factory_dep, get_settings_dep
from .model_scheduler import get_active_mode, is_report_generation_active
from .models import ReportJob, Team, User
from .report_service import ACTIVE_STATUSES, build_report_preview, job_to_dict, run_report_job, update_report_markdown
from .schemas import (
    ModelStatusResponse,
    OkResponse,
    ReportJobCreateRequest,
    ReportJobItem,
    ReportJobListResponse,
    ReportJobUpdateRequest,
    ReportPreviewResponse,
)
from .team_service import require_team_member, get_team_member

router = APIRouter(prefix="/reports", tags=["reports"])

_background_tasks: set[asyncio.Task] = set()


def _parse_uuid(raw: str, field: str) -> uuid.UUID:
    try:
        return uuid.UUID(raw.strip())
    except ValueError as e:
        raise HTTPException(status_code=400, detail=f"invalid_{field}") from e


def _parse_date(raw: str) -> date:
    try:
        return date.fromisoformat(raw.strip())
    except ValueError as e:
        raise HTTPException(status_code=400, detail="invalid_date") from e


def _with_db(session_factory: sessionmaker[Session]) -> Session:
    try:
        return session_factory()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


def _assert_report_access(db: Session, team: Team, current_user: User, subject_user_id: uuid.UUID) -> None:
    require_team_member(db, team, current_user)
    if subject_user_id == current_user.id:
        return
    if team.owner_id != current_user.id:
        raise HTTPException(status_code=403, detail="not_team_owner")
    if get_team_member(db, team.id, subject_user_id) is None:
        raise HTTPException(status_code=404, detail="subject_not_in_team")


def _schedule_job(job_id: uuid.UUID, settings: Settings, session_factory: sessionmaker[Session]) -> None:
    async def _runner() -> None:
        try:
            await run_report_job(job_id, settings, session_factory)
        finally:
            _background_tasks.discard(asyncio.current_task())  # type: ignore[arg-type]

    task = asyncio.create_task(_runner())
    _background_tasks.add(task)


@router.get("/model-status", response_model=ModelStatusResponse)
def model_status() -> ModelStatusResponse:
    mode = get_active_mode()
    return ModelStatusResponse(mode=mode, report_job_active=is_report_generation_active())  # type: ignore[arg-type]


@router.get("/preview", response_model=ReportPreviewResponse)
def preview_report(
    team_id: str = Query(...),
    user_id: str = Query(...),
    date: str = Query(..., description="YYYY-MM-DD"),
    report_type: str = Query(default="safety"),
    current_user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> ReportPreviewResponse:
    tid = _parse_uuid(team_id, "team_id")
    uid = _parse_uuid(user_id, "user_id")
    report_date = _parse_date(date)

    db = _with_db(session_factory)
    try:
        team = db.get(Team, tid)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        subject = db.get(User, uid)
        if subject is None:
            raise HTTPException(status_code=404, detail="user_not_found")
        _assert_report_access(db, team, current_user, uid)
        preview = build_report_preview(
            db,
            team=team,
            subject_user=subject,
            report_date=report_date,
            settings=settings,
            report_type=report_type if report_type in ("safety", "travel") else "safety",
        )
        return ReportPreviewResponse(**preview)
    finally:
        db.close()


@router.post("/jobs", response_model=ReportJobItem)
async def create_report_job(
    body: ReportJobCreateRequest,
    current_user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> ReportJobItem:
    team_id = _parse_uuid(body.team_id, "team_id")
    subject_user_id = _parse_uuid(body.user_id, "user_id")
    report_date = _parse_date(body.date)

    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")

        subject = db.get(User, subject_user_id)
        if subject is None:
            raise HTTPException(status_code=404, detail="user_not_found")

        _assert_report_access(db, team, current_user, subject_user_id)

        active = (
            db.query(ReportJob)
            .filter(ReportJob.status.in_(list(ACTIVE_STATUSES)))
            .first()
        )
        if active is not None:
            raise HTTPException(status_code=409, detail="model_busy")

        job = ReportJob(
            id=uuid.uuid4(),
            team_id=team_id,
            subject_user_id=subject_user_id,
            requested_by_user_id=current_user.id,
            report_date=report_date,
            report_type=body.report_type,
            status="pending",
            phase_detail={"force_reidentify": body.force_reidentify},
        )
        db.add(job)
        db.commit()
        db.refresh(job)
        item = job_to_dict(job, db)
        job_id = job.id
    finally:
        db.close()

    _schedule_job(job_id, settings, session_factory)
    return ReportJobItem(**item)


@router.get("/jobs/{job_id}", response_model=ReportJobItem)
def get_report_job(
    job_id: str,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> ReportJobItem:
    jid = _parse_uuid(job_id, "job_id")
    db = _with_db(session_factory)
    try:
        job = db.get(ReportJob, jid)
        if job is None:
            raise HTTPException(status_code=404, detail="job_not_found")
        team = db.get(Team, job.team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        _assert_report_access(db, team, current_user, job.subject_user_id)
        return ReportJobItem(**job_to_dict(job, db))
    finally:
        db.close()


@router.get("/jobs", response_model=ReportJobListResponse)
def list_report_jobs(
    team_id: str | None = Query(default=None),
    user_id: str | None = Query(default=None),
    date: str | None = Query(default=None),
    status: str | None = Query(default=None),
    limit: int = Query(default=20, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> ReportJobListResponse:
    db = _with_db(session_factory)
    try:
        q = db.query(ReportJob).order_by(ReportJob.created_at.desc())
        if team_id:
            tid = _parse_uuid(team_id, "team_id")
            team = db.get(Team, tid)
            if team is None:
                raise HTTPException(status_code=404, detail="team_not_found")
            require_team_member(db, team, current_user)
            q = q.filter(ReportJob.team_id == tid)
            if team.owner_id != current_user.id:
                q = q.filter(ReportJob.subject_user_id == current_user.id)
        else:
            q = q.filter(ReportJob.subject_user_id == current_user.id)

        if user_id:
            uid = _parse_uuid(user_id, "user_id")
            if team_id:
                team_for_access = db.get(Team, _parse_uuid(team_id, "team_id"))
                if team_for_access is not None:
                    _assert_report_access(db, team_for_access, current_user, uid)
            q = q.filter(ReportJob.subject_user_id == uid)
        if date:
            d = _parse_date(date)
            q = q.filter(ReportJob.report_date == d)
        if status:
            q = q.filter(ReportJob.status == status.strip())

        rows = q.limit(limit).all()
        items = [ReportJobItem(**job_to_dict(j, db)) for j in rows]
        return ReportJobListResponse(items=items)
    finally:
        db.close()


@router.patch("/jobs/{job_id}", response_model=ReportJobItem)
def update_report_job(
    job_id: str,
    body: ReportJobUpdateRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> ReportJobItem:
    jid = _parse_uuid(job_id, "job_id")
    db = _with_db(session_factory)
    try:
        job = db.get(ReportJob, jid)
        if job is None:
            raise HTTPException(status_code=404, detail="job_not_found")
        team = db.get(Team, job.team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        _assert_report_access(db, team, current_user, job.subject_user_id)
        if job.status != "done":
            raise HTTPException(status_code=409, detail="job_not_editable")
        update_report_markdown(db, job=job, markdown=body.markdown.strip())
        return ReportJobItem(**job_to_dict(job, db))
    finally:
        db.close()


@router.delete("/jobs/{job_id}", response_model=OkResponse)
def delete_report_job(
    job_id: str,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    jid = _parse_uuid(job_id, "job_id")
    db = _with_db(session_factory)
    try:
        job = db.get(ReportJob, jid)
        if job is None:
            raise HTTPException(status_code=404, detail="job_not_found")
        team = db.get(Team, job.team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        _assert_report_access(db, team, current_user, job.subject_user_id)
        if job.status in ("aggregating", "identifying", "writing"):
            raise HTTPException(status_code=409, detail="job_not_deletable")
        db.delete(job)
        db.commit()
        return OkResponse(ok=True)
    finally:
        db.close()
