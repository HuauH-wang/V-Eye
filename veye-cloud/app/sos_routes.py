from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session, sessionmaker

from .deps import get_current_user, get_session_factory_dep, get_settings_dep
from .models import SosEvent, Team, User
from .schemas import OkResponse, SosRecordItem, SosRecordsResponse
from .sos_service import assert_sos_delete_access, query_sos_for_viewer, sos_record_item
from .team_service import require_team_member

router = APIRouter(prefix="/sos", tags=["sos"])


def _with_db(session_factory: sessionmaker[Session]) -> Session:
    try:
        return session_factory()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


def _parse_team_id(raw: str | None) -> uuid.UUID | None:
    if not raw or not raw.strip():
        return None
    try:
        return uuid.UUID(raw.strip())
    except ValueError as e:
        raise HTTPException(status_code=400, detail="invalid team_id") from e


@router.get("/records", response_model=SosRecordsResponse)
def list_sos_records(
    team_id: str | None = Query(default=None),
    days: int = Query(default=90, ge=1, le=365),
    limit: int = Query(default=100, ge=1, le=500),
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> SosRecordsResponse:
    team_uuid = _parse_team_id(team_id)
    since = datetime.now(timezone.utc) - timedelta(days=days)
    db = _with_db(session_factory)
    try:
        team: Team | None = None
        if team_uuid is not None:
            team = db.get(Team, team_uuid)
            if team is None:
                raise HTTPException(status_code=404, detail="team_not_found")
            require_team_member(db, team, current_user)

        try:
            rows = query_sos_for_viewer(
                db,
                viewer=current_user,
                team=team,
                since_utc=since,
                limit=limit,
            )
        except ValueError as e:
            if str(e) == "not_team_member":
                raise HTTPException(status_code=403, detail="not_team_member") from e
            raise

        user_ids = {row.user_id for row in rows if row.user_id is not None}
        users: dict[uuid.UUID, User] = {}
        if user_ids:
            for user in db.query(User).filter(User.id.in_(user_ids)).all():
                users[user.id] = user

        items = [SosRecordItem(**sos_record_item(row, users)) for row in rows]
        return SosRecordsResponse(items=items, total=len(items))
    finally:
        db.close()


@router.delete("/records/{incident_id}", response_model=OkResponse)
def delete_sos_record(
    incident_id: str,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    try:
        iid = uuid.UUID(incident_id.strip())
    except ValueError as e:
        raise HTTPException(status_code=400, detail="invalid_incident_id") from e

    db = _with_db(session_factory)
    try:
        row = db.get(SosEvent, iid)
        if row is None:
            raise HTTPException(status_code=404, detail="record_not_found")
        try:
            assert_sos_delete_access(db, current_user, row)
        except PermissionError as e:
            raise HTTPException(status_code=403, detail="forbidden") from e
        db.delete(row)
        db.commit()
        return OkResponse(ok=True)
    finally:
        db.close()
