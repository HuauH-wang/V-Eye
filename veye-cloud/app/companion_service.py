from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy.orm import Session

from .models import CompanionState, User
from .schemas import CompanionStateResponse, CompanionStateUpdateRequest


def _default_companion() -> CompanionState:
    return CompanionState(
        mood="idle",
        level=1,
        energy=100,
        total_steps=0,
        message="你好，我是小欧！一起运动吧～",
        pose_json={
            "animation": "idle",
            "facing": "right",
            "accessory": "leaf_hat",
        },
    )


def level_from_steps(total_steps: int) -> int:
    return max(1, min(99, 1 + total_steps // 5000))


def apply_growth_rules(row: CompanionState) -> None:
    """Recompute level from cumulative steps and clamp energy."""
    row.level = level_from_steps(row.total_steps)
    row.energy = max(0, min(100, row.energy))


def get_or_create_companion(db: Session, user: User) -> CompanionState:
    row = db.get(CompanionState, user.id)
    if row is None:
        row = _default_companion()
        row.user_id = user.id
        db.add(row)
        db.commit()
        db.refresh(row)
    return row


def companion_to_response(row: CompanionState) -> CompanionStateResponse:
    return CompanionStateResponse(
        name=row.name,
        mood=row.mood,
        level=row.level,
        energy=row.energy,
        total_steps=row.total_steps,
        message=row.message,
        pose_json=row.pose_json if isinstance(row.pose_json, dict) else {},
        updated_at=row.updated_at,
    )


def update_companion(
    db: Session,
    user: User,
    body: CompanionStateUpdateRequest,
) -> CompanionState:
    row = get_or_create_companion(db, user)
    if body.mood is not None:
        row.mood = body.mood.strip() or row.mood
    if body.level is not None:
        row.level = body.level
    if body.energy is not None:
        row.energy = body.energy
    if body.total_steps is not None:
        row.total_steps = max(row.total_steps, body.total_steps)
    if body.message is not None:
        row.message = body.message.strip()
    if body.pose_json is not None:
        row.pose_json = body.pose_json
    apply_growth_rules(row)
    row.updated_at = datetime.now(timezone.utc)
    db.add(row)
    db.commit()
    db.refresh(row)
    return row
