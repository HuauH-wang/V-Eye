from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, sessionmaker

from .companion_service import companion_to_response, get_or_create_companion, update_companion
from .deps import get_current_user, get_session_factory_dep
from .models import User
from .schemas import CompanionStateResponse, CompanionStateUpdateRequest

router = APIRouter(prefix="/companion", tags=["companion"])


def _with_db(session_factory: sessionmaker[Session]) -> Session:
    try:
        return session_factory()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.get("/xiaoou", response_model=CompanionStateResponse)
def get_xiaoou(
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> CompanionStateResponse:
    db = _with_db(session_factory)
    try:
        row = get_or_create_companion(db, current_user)
        return companion_to_response(row)
    finally:
        db.close()


@router.patch("/xiaoou", response_model=CompanionStateResponse)
def patch_xiaoou(
    body: CompanionStateUpdateRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> CompanionStateResponse:
    db = _with_db(session_factory)
    try:
        row = update_companion(db, current_user, body)
        return companion_to_response(row)
    finally:
        db.close()
