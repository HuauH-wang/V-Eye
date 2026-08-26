from __future__ import annotations

import uuid
from datetime import datetime, timezone

from fastapi import HTTPException
from sqlalchemy import func
from sqlalchemy.orm import Session, joinedload

from .models import ChatMessage, Team, TeamChatRead, TeamMember, User
from .team_service import require_team_member, team_member_avatars, team_member_count, team_has_avatar, user_public
from .time_utils import as_utc, utc_now

MAX_MESSAGE_LEN = 2000
DEFAULT_PAGE_SIZE = 50


def _message_item(msg: ChatMessage, current_user: User) -> dict:
    sender = msg.sender
    created_at = as_utc(msg.created_at)
    return {
        "id": str(msg.id),
        "team_id": str(msg.team_id),
        "sender": user_public(sender),
        "body": msg.body,
        "created_at": created_at,
        "is_mine": msg.sender_id == current_user.id,
    }


def get_last_read(db: Session, team_id: uuid.UUID, user_id: uuid.UUID) -> datetime | None:
    row = (
        db.query(TeamChatRead)
        .filter(TeamChatRead.team_id == team_id, TeamChatRead.user_id == user_id)
        .one_or_none()
    )
    return row.last_read_at if row else None


def mark_team_read(db: Session, team: Team, user: User) -> None:
    now = utc_now()
    row = (
        db.query(TeamChatRead)
        .filter(TeamChatRead.team_id == team.id, TeamChatRead.user_id == user.id)
        .one_or_none()
    )
    if row:
        row.last_read_at = now
    else:
        db.add(
            TeamChatRead(
                id=uuid.uuid4(),
                team_id=team.id,
                user_id=user.id,
                last_read_at=now,
            )
        )


def unread_count(db: Session, team_id: uuid.UUID, user_id: uuid.UUID) -> int:
    last_read = get_last_read(db, team_id, user_id)
    q = db.query(func.count(ChatMessage.id)).filter(
        ChatMessage.team_id == team_id,
        ChatMessage.sender_id != user_id,
    )
    if last_read is not None:
        q = q.filter(ChatMessage.created_at > last_read)
    return int(q.scalar() or 0)


def list_conversations(db: Session, user: User) -> list[dict]:
    teams = (
        db.query(Team)
        .join(TeamMember, TeamMember.team_id == Team.id)
        .filter(TeamMember.user_id == user.id)
        .order_by(Team.created_at.desc())
        .all()
    )
    items: list[dict] = []
    for team in teams:
        last_msg = (
            db.query(ChatMessage)
            .options(joinedload(ChatMessage.sender))
            .filter(ChatMessage.team_id == team.id)
            .order_by(ChatMessage.created_at.desc())
            .limit(1)
            .one_or_none()
        )
        items.append(
            {
                "team_id": str(team.id),
                "team_name": team.name,
                "member_count": team_member_count(db, team.id),
                "has_avatar": team_has_avatar(team),
                "member_avatars": team_member_avatars(db, team.id),
                "last_message": _message_item(last_msg, user) if last_msg else None,
                "unread_count": unread_count(db, team.id, user.id),
            }
        )

    items.sort(
        key=lambda x: (
            x["last_message"]["created_at"] if x["last_message"] else datetime.min.replace(tzinfo=timezone.utc)
        ),
        reverse=True,
    )
    return items


def list_messages(
    db: Session,
    team: Team,
    user: User,
    *,
    limit: int = DEFAULT_PAGE_SIZE,
    before: datetime | None = None,
    since: datetime | None = None,
) -> tuple[list[dict], bool]:
    require_team_member(db, team, user)
    limit = max(1, min(limit, 100))

    q = db.query(ChatMessage).options(joinedload(ChatMessage.sender)).filter(ChatMessage.team_id == team.id)
    if before is not None:
        q = q.filter(ChatMessage.created_at < before)
    if since is not None:
        q = q.filter(ChatMessage.created_at > since)

    rows = q.order_by(ChatMessage.created_at.desc()).limit(limit + 1).all()
    has_more = len(rows) > limit
    rows = rows[:limit]
    rows.reverse()
    return [_message_item(m, user) for m in rows], has_more


def send_message(db: Session, team: Team, user: User, body: str) -> dict:
    require_team_member(db, team, user)
    text = body.strip()
    if not text:
        raise HTTPException(status_code=400, detail="message_empty")
    if len(text) > MAX_MESSAGE_LEN:
        raise HTTPException(status_code=400, detail="message_too_long")

    msg = ChatMessage(
        id=uuid.uuid4(),
        team_id=team.id,
        sender_id=user.id,
        body=text,
        created_at=utc_now(),
    )
    db.add(msg)
    db.flush()
    msg.sender = user
    mark_team_read(db, team, user)
    return _message_item(msg, user)
