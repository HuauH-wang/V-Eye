from __future__ import annotations

import uuid
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session, sessionmaker

from .chat_service import list_conversations, list_messages, mark_team_read, send_message
from .deps import get_current_user, get_session_factory_dep
from .models import Team, User
from .schemas import (
    ChatConversationsResponse,
    ChatConversationItem,
    ChatMessageCreateRequest,
    ChatMessageItem,
    ChatMessagesResponse,
    OkResponse,
)

router = APIRouter(prefix="/teams", tags=["chat"])


def _with_db(session_factory: sessionmaker[Session]):
    try:
        db = session_factory()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    return db


@router.get("/chat/conversations", response_model=ChatConversationsResponse)
def get_conversations(
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> ChatConversationsResponse:
    db = _with_db(session_factory)
    try:
        items = list_conversations(db, current_user)
        total_unread = sum(i["unread_count"] for i in items)
        return ChatConversationsResponse(
            items=[ChatConversationItem(**i) for i in items],
            total_unread=total_unread,
        )
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.get("/{team_id}/messages", response_model=ChatMessagesResponse)
def get_messages(
    team_id: uuid.UUID,
    limit: int = Query(default=50, ge=1, le=100),
    before: datetime | None = Query(default=None),
    since: datetime | None = Query(default=None),
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> ChatMessagesResponse:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        items, has_more = list_messages(
            db, team, current_user, limit=limit, before=before, since=since
        )
        return ChatMessagesResponse(
            items=[ChatMessageItem(**i) for i in items],
            has_more=has_more,
        )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.post("/{team_id}/messages", response_model=ChatMessageItem)
def post_message(
    team_id: uuid.UUID,
    body: ChatMessageCreateRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> ChatMessageItem:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        item = send_message(db, team, current_user, body.body)
        db.commit()
        return ChatMessageItem(**item)
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.post("/{team_id}/messages/read", response_model=OkResponse)
def mark_read(
    team_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        mark_team_read(db, team, current_user)
        db.commit()
        return OkResponse()
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()
