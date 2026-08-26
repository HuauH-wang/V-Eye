from __future__ import annotations

import uuid
from pathlib import Path

from fastapi import HTTPException
from sqlalchemy.orm import Session

from .models import Team, TeamInvite, TeamMember, User, UserMail

MAX_TEAM_SIZE = 20
MAX_COLLAGE_MEMBERS = 9
MAIL_TYPE_INVITE = "team_invite"
MAIL_TYPE_KICK = "team_kick"
MAIL_TYPE_JOIN = "team_join"
MAIL_TYPE_DISBAND = "team_disband"


def user_has_avatar(user: User) -> bool:
    return bool(user.avatar_path and Path(user.avatar_path).is_file())


def team_has_avatar(team: Team) -> bool:
    return bool(team.avatar_path and Path(team.avatar_path).is_file())


def user_public(user: User) -> dict:
    return {
        "id": str(user.id),
        "username": user.username,
        "display_name": user.display_name or user.username,
        "has_avatar": user_has_avatar(user),
    }


def resolve_user(db: Session, query: str, *, exclude_id: uuid.UUID | None = None) -> User | None:
    q = query.strip()
    if not q:
        return None

    try:
        uid = uuid.UUID(q)
        user = db.get(User, uid)
        if user and (exclude_id is None or user.id != exclude_id):
            return user
    except ValueError:
        pass

    user = db.query(User).filter(User.username == q).one_or_none()
    if user and (exclude_id is None or user.id != exclude_id):
        return user

    user = db.query(User).filter(User.display_name == q).one_or_none()
    if user and (exclude_id is None or user.id != exclude_id):
        return user

    matches = (
        db.query(User)
        .filter(User.display_name.ilike(f"%{q}%"))
        .limit(5)
        .all()
    )
    matches = [u for u in matches if exclude_id is None or u.id != exclude_id]
    if len(matches) == 1:
        return matches[0]
    return None


def get_team_member(db: Session, team_id: uuid.UUID, user_id: uuid.UUID) -> TeamMember | None:
    return (
        db.query(TeamMember)
        .filter(TeamMember.team_id == team_id, TeamMember.user_id == user_id)
        .one_or_none()
    )


def is_team_owner_of_user(db: Session, owner: User, member_user_id: uuid.UUID) -> bool:
    row = (
        db.query(Team.id)
        .join(TeamMember, TeamMember.team_id == Team.id)
        .filter(Team.owner_id == owner.id, TeamMember.user_id == member_user_id)
        .first()
    )
    return row is not None


def can_view_user_identify_record(db: Session, viewer: User, record_user_id: uuid.UUID | None) -> bool:
    if record_user_id is None:
        return True
    if record_user_id == viewer.id:
        return True
    return is_team_owner_of_user(db, viewer, record_user_id)


def require_team_member(db: Session, team: Team, user: User) -> TeamMember:
    member = get_team_member(db, team.id, user.id)
    if member is None:
        raise HTTPException(status_code=403, detail="not_team_member")
    return member


def require_team_owner(db: Session, team: Team, user: User) -> TeamMember:
    member = require_team_member(db, team, user)
    if team.owner_id != user.id:
        raise HTTPException(status_code=403, detail="not_team_owner")
    return member


def team_member_count(db: Session, team_id: uuid.UUID) -> int:
    return db.query(TeamMember).filter(TeamMember.team_id == team_id).count()


def send_mail(
    db: Session,
    *,
    recipient_id: uuid.UUID,
    sender_id: uuid.UUID | None,
    mail_type: str,
    title: str,
    body: str,
    payload: dict | None = None,
    action_status: str | None = None,
) -> UserMail:
    mail = UserMail(
        id=uuid.uuid4(),
        recipient_id=recipient_id,
        sender_id=sender_id,
        mail_type=mail_type,
        title=title,
        body=body,
        payload=payload or {},
        action_status=action_status,
    )
    db.add(mail)
    return mail


def team_summary(db: Session, team: Team, user: User) -> dict:
    member = get_team_member(db, team.id, user.id)
    return {
        "id": str(team.id),
        "name": team.name,
        "description": team.description,
        "owner_id": str(team.owner_id),
        "member_count": team_member_count(db, team.id),
        "my_role": member.role if member else "none",
        "has_avatar": team_has_avatar(team),
        "created_at": team.created_at,
    }


def team_member_avatars(db: Session, team_id: uuid.UUID, *, limit: int = MAX_COLLAGE_MEMBERS) -> list[dict]:
    rows = (
        db.query(TeamMember, User)
        .join(User, TeamMember.user_id == User.id)
        .filter(TeamMember.team_id == team_id)
        .order_by(TeamMember.joined_at.asc())
        .limit(limit)
        .all()
    )
    return [
        {
            "user_id": str(u.id),
            "display_name": u.display_name or u.username,
            "has_avatar": user_has_avatar(u),
        }
        for _m, u in rows
    ]


def team_detail(db: Session, team: Team, user: User) -> dict:
    summary = team_summary(db, team, user)
    members = (
        db.query(TeamMember, User)
        .join(User, TeamMember.user_id == User.id)
        .filter(TeamMember.team_id == team.id)
        .order_by(TeamMember.joined_at.asc())
        .all()
    )
    summary["members"] = [
        {
            "user_id": str(u.id),
            "username": u.username,
            "display_name": u.display_name or u.username,
            "role": m.role,
            "joined_at": m.joined_at,
            "has_avatar": user_has_avatar(u),
        }
        for m, u in members
    ]
    return summary


def accept_team_invite(db: Session, mail: UserMail, user: User) -> Team:
    if mail.mail_type != MAIL_TYPE_INVITE:
        raise HTTPException(status_code=400, detail="mail_not_actionable")
    if mail.action_status and mail.action_status != "pending":
        raise HTTPException(status_code=409, detail="invite_already_handled")

    invite_id = mail.payload.get("invite_id")
    team_id = mail.payload.get("team_id")
    if not invite_id or not team_id:
        raise HTTPException(status_code=400, detail="invalid_mail_payload")

    invite = db.get(TeamInvite, uuid.UUID(str(invite_id)))
    team = db.get(Team, uuid.UUID(str(team_id)))
    if invite is None or team is None:
        raise HTTPException(status_code=404, detail="invite_not_found")
    if invite.invitee_id != user.id:
        raise HTTPException(status_code=403, detail="invite_not_for_you")
    if invite.status != "pending":
        raise HTTPException(status_code=409, detail="invite_already_handled")

    if get_team_member(db, team.id, user.id) is not None:
        invite.status = "accepted"
        mail.action_status = "accepted"
        mail.is_read = True
        return team

    if team_member_count(db, team.id) >= MAX_TEAM_SIZE:
        raise HTTPException(status_code=409, detail="team_full")

    db.add(
        TeamMember(
            id=uuid.uuid4(),
            team_id=team.id,
            user_id=user.id,
            role="member",
        )
    )
    invite.status = "accepted"
    mail.action_status = "accepted"
    mail.is_read = True

    inviter_name = mail.payload.get("inviter_name", "队长")
    send_mail(
        db,
        recipient_id=team.owner_id,
        sender_id=user.id,
        mail_type=MAIL_TYPE_JOIN,
        title=f"{user.display_name or user.username} 加入了小队",
        body=f"「{team.name}」新增成员 {user.display_name or user.username}（@{user.username}）。",
        payload={"team_id": str(team.id), "user_id": str(user.id)},
    )
    return team


def decline_team_invite(db: Session, mail: UserMail, user: User) -> None:
    if mail.mail_type != MAIL_TYPE_INVITE:
        raise HTTPException(status_code=400, detail="mail_not_actionable")
    if mail.action_status and mail.action_status != "pending":
        raise HTTPException(status_code=409, detail="invite_already_handled")

    invite_id = mail.payload.get("invite_id")
    if invite_id:
        invite = db.get(TeamInvite, uuid.UUID(str(invite_id)))
        if invite and invite.invitee_id == user.id:
            invite.status = "declined"
    mail.action_status = "declined"
    mail.is_read = True
