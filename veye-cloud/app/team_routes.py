from __future__ import annotations

import uuid
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session, sessionmaker

from .config import Settings
from .deps import get_current_user, get_session_factory_dep, get_settings_dep
from .image_utils import load_and_resize_avatar, save_team_avatar
from .models import Team, TeamInvite, TeamMember, User, UserMail
from .schemas import (
    MailItem,
    MailListResponse,
    MailUnreadResponse,
    OkResponse,
    TeamCreateRequest,
    TeamDetail,
    TeamInviteRequest,
    TeamInviteResponse,
    TeamListResponse,
    TeamSummary,
    TeamUpdateRequest,
    UserPublicItem,
)
from .team_service import (
    MAIL_TYPE_DISBAND,
    MAIL_TYPE_INVITE,
    MAIL_TYPE_KICK,
    accept_team_invite,
    decline_team_invite,
    get_team_member,
    require_team_member,
    require_team_owner,
    resolve_user,
    send_mail,
    team_detail,
    team_has_avatar,
    team_member_count,
    team_summary,
    user_has_avatar,
    user_public,
    MAX_TEAM_SIZE,
)

router = APIRouter(prefix="/teams", tags=["teams"])


def _with_db(session_factory: sessionmaker[Session]):
    try:
        db = session_factory()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    return db


@router.post("", response_model=TeamDetail)
def create_team(
    body: TeamCreateRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> TeamDetail:
    name = body.name.strip()
    if not name:
        raise HTTPException(status_code=400, detail="team_name_required")

    db = _with_db(session_factory)
    try:
        team = Team(
            id=uuid.uuid4(),
            name=name,
            description=(body.description or "").strip()[:500],
            owner_id=current_user.id,
        )
        db.add(team)
        db.add(
            TeamMember(
                id=uuid.uuid4(),
                team_id=team.id,
                user_id=current_user.id,
                role="owner",
            )
        )
        db.commit()
        db.refresh(team)
        return TeamDetail(**team_detail(db, team, current_user))
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.get("", response_model=TeamListResponse)
def list_my_teams(
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> TeamListResponse:
    db = _with_db(session_factory)
    try:
        rows = (
            db.query(Team)
            .join(TeamMember, TeamMember.team_id == Team.id)
            .filter(TeamMember.user_id == current_user.id)
            .order_by(Team.created_at.desc())
            .all()
        )
        items = [TeamSummary(**team_summary(db, t, current_user)) for t in rows]
        return TeamListResponse(items=items)
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.get("/users/lookup", response_model=list[UserPublicItem])
def lookup_users(
    q: str = Query(min_length=1, max_length=128),
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> list[UserPublicItem]:
    db = _with_db(session_factory)
    try:
        needle = q.strip()
        user = resolve_user(db, needle, exclude_id=current_user.id)
        if user:
            return [UserPublicItem(**user_public(user))]

        rows = (
            db.query(User)
            .filter(
                (User.username.ilike(f"%{needle}%")) | (User.display_name.ilike(f"%{needle}%"))
            )
            .filter(User.id != current_user.id)
            .limit(8)
            .all()
        )
        return [UserPublicItem(**user_public(u)) for u in rows]
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.get("/{team_id}", response_model=TeamDetail)
def get_team(
    team_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> TeamDetail:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_member(db, team, current_user)
        return TeamDetail(**team_detail(db, team, current_user))
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.patch("/{team_id}", response_model=TeamDetail)
def update_team(
    team_id: uuid.UUID,
    body: TeamUpdateRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> TeamDetail:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_owner(db, team, current_user)
        if body.name is not None:
            name = body.name.strip()
            if not name:
                raise HTTPException(status_code=400, detail="team_name_required")
            team.name = name
        if body.description is not None:
            team.description = body.description.strip()[:500]
        db.commit()
        db.refresh(team)
        return TeamDetail(**team_detail(db, team, current_user))
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.delete("/{team_id}", response_model=OkResponse)
def disband_team(
    team_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_owner(db, team, current_user)

        members = db.query(TeamMember).filter(TeamMember.team_id == team.id).all()
        for m in members:
            if m.user_id == current_user.id:
                continue
            send_mail(
                db,
                recipient_id=m.user_id,
                sender_id=current_user.id,
                mail_type=MAIL_TYPE_DISBAND,
                title=f"小队「{team.name}」已解散",
                body=f"队长 {current_user.display_name or current_user.username} 解散了小队「{team.name}」。",
                payload={"team_id": str(team.id)},
            )
        if team.avatar_path:
            path = Path(team.avatar_path)
            if path.is_file():
                path.unlink(missing_ok=True)
        db.delete(team)
        db.commit()
        return OkResponse()
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.post("/{team_id}/invite", response_model=TeamInviteResponse)
def invite_member(
    team_id: uuid.UUID,
    body: TeamInviteRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> TeamInviteResponse:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_owner(db, team, current_user)

        if team_member_count(db, team.id) >= MAX_TEAM_SIZE:
            raise HTTPException(status_code=409, detail="team_full")

        invitee = resolve_user(db, body.query, exclude_id=current_user.id)
        if invitee is None:
            raise HTTPException(status_code=404, detail="user_not_found")

        if get_team_member(db, team.id, invitee.id) is not None:
            raise HTTPException(status_code=409, detail="already_team_member")

        pending = (
            db.query(TeamInvite)
            .filter(
                TeamInvite.team_id == team.id,
                TeamInvite.invitee_id == invitee.id,
                TeamInvite.status == "pending",
            )
            .one_or_none()
        )
        if pending:
            raise HTTPException(status_code=409, detail="invite_already_sent")

        invite = TeamInvite(
            id=uuid.uuid4(),
            team_id=team.id,
            inviter_id=current_user.id,
            invitee_id=invitee.id,
            status="pending",
        )
        db.add(invite)
        db.flush()

        inviter_label = current_user.display_name or current_user.username
        mail = send_mail(
            db,
            recipient_id=invitee.id,
            sender_id=current_user.id,
            mail_type=MAIL_TYPE_INVITE,
            title=f"邀请加入小队「{team.name}」",
            body=f"{inviter_label} 邀请你加入小队「{team.name}」。可在邮件中接受或拒绝。",
            payload={
                "team_id": str(team.id),
                "team_name": team.name,
                "invite_id": str(invite.id),
                "inviter_name": inviter_label,
            },
            action_status="pending",
        )
        db.commit()
        return TeamInviteResponse(
            invite_id=str(invite.id),
            mail_id=str(mail.id),
            invitee=UserPublicItem(**user_public(invitee)),
        )
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.post("/{team_id}/members/{member_id}/kick", response_model=OkResponse)
def kick_member(
    team_id: uuid.UUID,
    member_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_owner(db, team, current_user)

        if member_id == current_user.id:
            raise HTTPException(status_code=400, detail="cannot_kick_self")

        member = get_team_member(db, team.id, member_id)
        if member is None:
            raise HTTPException(status_code=404, detail="member_not_found")

        target = db.get(User, member_id)
        db.delete(member)

        if target:
            send_mail(
                db,
                recipient_id=target.id,
                sender_id=current_user.id,
                mail_type=MAIL_TYPE_KICK,
                title=f"你已被移出小队「{team.name}」",
                body=f"队长 {current_user.display_name or current_user.username} 将你移出了小队「{team.name}」。",
                payload={"team_id": str(team.id), "team_name": team.name},
            )

        for inv in (
            db.query(TeamInvite)
            .filter(
                TeamInvite.team_id == team.id,
                TeamInvite.invitee_id == member_id,
                TeamInvite.status == "pending",
            )
            .all()
        ):
            inv.status = "cancelled"

        db.commit()
        return OkResponse()
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.post("/{team_id}/leave", response_model=OkResponse)
def leave_team(
    team_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        if team.owner_id == current_user.id:
            raise HTTPException(status_code=400, detail="owner_cannot_leave")

        member = get_team_member(db, team.id, current_user.id)
        if member is None:
            raise HTTPException(status_code=404, detail="not_team_member")

        db.delete(member)
        send_mail(
            db,
            recipient_id=team.owner_id,
            sender_id=current_user.id,
            mail_type="team_leave",
            title=f"{current_user.display_name or current_user.username} 离开了小队",
            body=f"成员 {current_user.display_name or current_user.username} 主动离开了「{team.name}」。",
            payload={"team_id": str(team.id)},
        )
        db.commit()
        return OkResponse()
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.post("/{team_id}/avatar", response_model=TeamDetail)
async def upload_team_avatar(
    team_id: uuid.UUID,
    avatar: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> TeamDetail:
    body = await avatar.read()
    if len(body) > settings.MAX_AVATAR_BYTES:
        raise HTTPException(status_code=413, detail="avatar too large")
    if not body:
        raise HTTPException(status_code=400, detail="empty file")

    try:
        resized, _mime = load_and_resize_avatar(body)
        avatar_path = save_team_avatar(settings.AVATAR_DIR, str(team_id), resized)
    except Exception as e:
        raise HTTPException(status_code=400, detail="invalid image") from e

    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_owner(db, team, current_user)
        if team.avatar_path:
            old = Path(team.avatar_path)
            if old.is_file():
                old.unlink(missing_ok=True)
        team.avatar_path = avatar_path
        db.commit()
        db.refresh(team)
        return TeamDetail(**team_detail(db, team, current_user))
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.get("/{team_id}/avatar")
def get_team_avatar(
    team_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> FileResponse:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_member(db, team, current_user)
        if not team_has_avatar(team):
            raise HTTPException(status_code=404, detail="avatar_not_found")
        return FileResponse(Path(team.avatar_path), media_type="image/jpeg")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.delete("/{team_id}/avatar", response_model=TeamDetail)
def delete_team_avatar(
    team_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> TeamDetail:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_owner(db, team, current_user)
        if team.avatar_path:
            path = Path(team.avatar_path)
            if path.is_file():
                path.unlink(missing_ok=True)
            team.avatar_path = None
        db.commit()
        db.refresh(team)
        return TeamDetail(**team_detail(db, team, current_user))
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()


@router.get("/{team_id}/members/{member_id}/avatar")
def get_member_avatar(
    team_id: uuid.UUID,
    member_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> FileResponse:
    db = _with_db(session_factory)
    try:
        team = db.get(Team, team_id)
        if team is None:
            raise HTTPException(status_code=404, detail="team_not_found")
        require_team_member(db, team, current_user)
        if get_team_member(db, team.id, member_id) is None:
            raise HTTPException(status_code=404, detail="member_not_found")
        target = db.get(User, member_id)
        if target is None or not user_has_avatar(target):
            raise HTTPException(status_code=404, detail="avatar_not_found")
        return FileResponse(Path(target.avatar_path), media_type="image/jpeg")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    finally:
        db.close()
