from __future__ import annotations

import uuid
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from .models import MotionSession, MotionSnapshot, User
from .schemas import (
    MotionSessionCreateRequest,
    MotionSessionItem,
    MotionSessionUpdateRequest,
    MotionSnapshotBatchRequest,
    MotionSnapshotItem,
    MotionSnapshotResponseItem,
)


def _session_item(row: MotionSession) -> MotionSessionItem:
    return MotionSessionItem(
        id=str(row.id),
        status=row.status,
        started_at=row.started_at,
        ended_at=row.ended_at,
        steps=row.steps,
        fall_count=row.fall_count,
        pose_score=row.pose_score,
        summary_json=row.summary_json if isinstance(row.summary_json, dict) else {},
        created_at=row.created_at,
        updated_at=row.updated_at,
    )


def _snapshot_item(row: MotionSnapshot) -> MotionSnapshotResponseItem:
    return MotionSnapshotResponseItem(
        id=str(row.id),
        session_id=str(row.session_id),
        client_ts=row.client_ts,
        steps=row.steps,
        activity=row.activity,
        fall_detected=row.fall_detected,
        pose_keypoints=row.pose_keypoints if isinstance(row.pose_keypoints, dict) else {},
        sensor_json=row.sensor_json if isinstance(row.sensor_json, dict) else {},
        server_ts=row.server_ts,
    )


def create_motion_session(
    db: Session,
    user: User,
    body: MotionSessionCreateRequest,
) -> MotionSession:
    now = datetime.now(timezone.utc)
    row = MotionSession(
        user_id=user.id,
        status="active",
        started_at=body.client_ts or now,
        summary_json={"source": "veye-android"},
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def update_motion_session(
    db: Session,
    user: User,
    session_id: uuid.UUID,
    body: MotionSessionUpdateRequest,
) -> MotionSession:
    row = db.get(MotionSession, session_id)
    if row is None or row.user_id != user.id:
        raise LookupError("session_not_found")
    if body.status is not None:
        row.status = body.status.strip() or row.status
    if body.steps is not None:
        row.steps = body.steps
    if body.fall_count is not None:
        row.fall_count = body.fall_count
    if body.pose_score is not None:
        row.pose_score = body.pose_score
    if body.summary_json is not None:
        row.summary_json = body.summary_json
    if body.ended_at is not None:
        row.ended_at = body.ended_at
    row.updated_at = datetime.now(timezone.utc)
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def list_motion_sessions(db: Session, user: User, limit: int = 30) -> list[MotionSessionItem]:
    rows = (
        db.query(MotionSession)
        .filter(MotionSession.user_id == user.id)
        .order_by(MotionSession.started_at.desc())
        .limit(limit)
        .all()
    )
    return [_session_item(row) for row in rows]


def get_motion_session(db: Session, user: User, session_id: uuid.UUID) -> MotionSession:
    row = db.get(MotionSession, session_id)
    if row is None or row.user_id != user.id:
        raise LookupError("session_not_found")
    return row


def append_motion_snapshots(
    db: Session,
    user: User,
    session_id: uuid.UUID,
    body: MotionSnapshotBatchRequest,
) -> list[MotionSnapshotResponseItem]:
    session = get_motion_session(db, user, session_id)
    created: list[MotionSnapshot] = []
    for item in body.items:
        snap = MotionSnapshot(
            session_id=session.id,
            client_ts=item.client_ts,
            steps=item.steps,
            activity=item.activity,
            fall_detected=item.fall_detected,
            pose_keypoints=item.pose_keypoints,
            sensor_json=item.sensor_json,
        )
        db.add(snap)
        created.append(snap)
        if item.fall_detected:
            session.fall_count += 1
        session.steps = max(session.steps, item.steps)
    session.updated_at = datetime.now(timezone.utc)
    db.add(session)
    db.commit()
    for snap in created:
        db.refresh(snap)
    return [_snapshot_item(snap) for snap in created]


def list_motion_snapshots(
    db: Session,
    user: User,
    session_id: uuid.UUID,
    limit: int = 100,
) -> list[MotionSnapshotResponseItem]:
    session = get_motion_session(db, user, session_id)
    rows = (
        db.query(MotionSnapshot)
        .filter(MotionSnapshot.session_id == session.id)
        .order_by(MotionSnapshot.server_ts.desc())
        .limit(limit)
        .all()
    )
    return [_snapshot_item(row) for row in rows]
