from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session, sessionmaker

from .deps import get_current_user, get_session_factory_dep
from .models import User
from .motion_service import (
    append_motion_snapshots,
    create_motion_session,
    get_motion_session,
    list_motion_sessions,
    list_motion_snapshots,
    update_motion_session,
    _session_item,
)
from .schemas import (
    MotionSessionCreateRequest,
    MotionSessionItem,
    MotionSessionListResponse,
    MotionSessionUpdateRequest,
    MotionSnapshotBatchRequest,
    MotionSnapshotListResponse,
)

router = APIRouter(prefix="/motion", tags=["motion"])


def _with_db(session_factory: sessionmaker[Session]) -> Session:
    try:
        return session_factory()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


def _parse_uuid(raw: str) -> uuid.UUID:
    try:
        return uuid.UUID(raw.strip())
    except ValueError as e:
        raise HTTPException(status_code=400, detail="invalid_id") from e


@router.post("/sessions", response_model=MotionSessionItem)
def start_motion_session(
    body: MotionSessionCreateRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MotionSessionItem:
    db = _with_db(session_factory)
    try:
        row = create_motion_session(db, current_user, body)
        return _session_item(row)
    finally:
        db.close()


@router.get("/sessions", response_model=MotionSessionListResponse)
def get_motion_sessions(
    limit: int = Query(default=30, ge=1, le=200),
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MotionSessionListResponse:
    db = _with_db(session_factory)
    try:
        items = list_motion_sessions(db, current_user, limit=limit)
        return MotionSessionListResponse(items=items, total=len(items))
    finally:
        db.close()


@router.get("/sessions/{session_id}", response_model=MotionSessionItem)
def get_motion_session_detail(
    session_id: str,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MotionSessionItem:
    db = _with_db(session_factory)
    try:
        row = get_motion_session(db, current_user, _parse_uuid(session_id))
        return _session_item(row)
    except LookupError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e
    finally:
        db.close()


@router.patch("/sessions/{session_id}", response_model=MotionSessionItem)
def patch_motion_session(
    session_id: str,
    body: MotionSessionUpdateRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MotionSessionItem:
    db = _with_db(session_factory)
    try:
        row = update_motion_session(db, current_user, _parse_uuid(session_id), body)
        return _session_item(row)
    except LookupError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e
    finally:
        db.close()


@router.post("/sessions/{session_id}/snapshots", response_model=MotionSnapshotListResponse)
def upload_motion_snapshots(
    session_id: str,
    body: MotionSnapshotBatchRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MotionSnapshotListResponse:
    db = _with_db(session_factory)
    try:
        items = append_motion_snapshots(db, current_user, _parse_uuid(session_id), body)
        return MotionSnapshotListResponse(items=items, total=len(items))
    except LookupError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e
    finally:
        db.close()


@router.get("/sessions/{session_id}/snapshots", response_model=MotionSnapshotListResponse)
def get_motion_snapshots(
    session_id: str,
    limit: int = Query(default=100, ge=1, le=500),
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MotionSnapshotListResponse:
    db = _with_db(session_factory)
    try:
        items = list_motion_snapshots(db, current_user, _parse_uuid(session_id), limit=limit)
        return MotionSnapshotListResponse(items=items, total=len(items))
    finally:
        db.close()
