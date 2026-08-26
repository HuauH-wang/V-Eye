from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session, sessionmaker

from .deps import get_current_user, get_session_factory_dep
from .models import User, UserMail
from .schemas import MailItem, MailListResponse, MailUnreadResponse, OkResponse, UserPublicItem
from .team_service import accept_team_invite, decline_team_invite, user_public

router = APIRouter(prefix="/mail", tags=["mail"])


def _mail_item(db: Session, row: UserMail) -> MailItem:
    sender = db.get(User, row.sender_id) if row.sender_id else None
    return MailItem(
        id=str(row.id),
        mail_type=row.mail_type,
        title=row.title,
        body=row.body,
        payload=row.payload if isinstance(row.payload, dict) else {},
        is_read=row.is_read,
        action_status=row.action_status,
        sender=UserPublicItem(**user_public(sender)) if sender else None,
        created_at=row.created_at,
    )


@router.get("", response_model=MailListResponse)
def list_mail(
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    unread_only: bool = Query(default=False),
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MailListResponse:
    try:
        with session_factory() as db:
            query = db.query(UserMail).filter(UserMail.recipient_id == current_user.id)
            if unread_only:
                query = query.filter(UserMail.is_read.is_(False))
            unread_count = (
                db.query(UserMail)
                .filter(UserMail.recipient_id == current_user.id, UserMail.is_read.is_(False))
                .count()
            )
            rows = query.order_by(UserMail.created_at.desc()).offset(offset).limit(limit).all()
            items = [_mail_item(db, r) for r in rows]
            return MailListResponse(items=items, unread_count=unread_count)
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.get("/unread-count", response_model=MailUnreadResponse)
def unread_count(
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MailUnreadResponse:
    try:
        with session_factory() as db:
            count = (
                db.query(UserMail)
                .filter(UserMail.recipient_id == current_user.id, UserMail.is_read.is_(False))
                .count()
            )
            return MailUnreadResponse(unread_count=count)
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.patch("/{mail_id}/read", response_model=MailItem)
def mark_read(
    mail_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> MailItem:
    try:
        with session_factory() as db:
            row = db.get(UserMail, mail_id)
            if row is None or row.recipient_id != current_user.id:
                raise HTTPException(status_code=404, detail="mail_not_found")
            row.is_read = True
            db.commit()
            db.refresh(row)
            return _mail_item(db, row)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.post("/{mail_id}/accept", response_model=OkResponse)
def accept_mail(
    mail_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    try:
        with session_factory() as db:
            row = db.get(UserMail, mail_id)
            if row is None or row.recipient_id != current_user.id:
                raise HTTPException(status_code=404, detail="mail_not_found")
            accept_team_invite(db, row, current_user)
            db.commit()
            return OkResponse()
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.post("/{mail_id}/decline", response_model=OkResponse)
def decline_mail(
    mail_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    try:
        with session_factory() as db:
            row = db.get(UserMail, mail_id)
            if row is None or row.recipient_id != current_user.id:
                raise HTTPException(status_code=404, detail="mail_not_found")
            decline_team_invite(db, row, current_user)
            db.commit()
            return OkResponse()
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.delete("/{mail_id}", response_model=OkResponse)
def delete_mail(
    mail_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    try:
        with session_factory() as db:
            row = db.get(UserMail, mail_id)
            if row is None or row.recipient_id != current_user.id:
                raise HTTPException(status_code=404, detail="mail_not_found")
            db.delete(row)
            db.commit()
            return OkResponse()
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
