from __future__ import annotations

import re
import uuid
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy import func
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from .auth import create_access_token, hash_password, verify_password
from .config import Settings
from .deps import get_current_user, get_session_factory_dep, get_settings_dep
from .image_utils import load_and_resize_avatar, save_avatar
from .models import IdentifyRecord, User
from .schemas import (
    AuthTokenResponse,
    UserLoginRequest,
    UserProfileResponse,
    UserProfileUpdateRequest,
    UserRegisterRequest,
    UserStats,
)

router = APIRouter(prefix="/auth", tags=["auth"])

_USERNAME_RE = re.compile(r"^[a-zA-Z0-9_]{3,32}$")


@router.post("/register", response_model=AuthTokenResponse)
def register(
    body: UserRegisterRequest,
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> AuthTokenResponse:
    username = body.username.strip()
    if not _USERNAME_RE.match(username):
        raise HTTPException(
            status_code=400,
            detail="username must be 3-32 chars: letters, digits, underscore",
        )
    if len(body.password) < 6:
        raise HTTPException(status_code=400, detail="password must be at least 6 chars")

    display_name = (body.display_name or username).strip()[:64] or username
    email = body.email.strip()[:254] if body.email and body.email.strip() else None

    user = User(
        id=uuid.uuid4(),
        username=username,
        email=email,
        password_hash=hash_password(body.password),
        display_name=display_name,
    )
    try:
        with session_factory() as db:
            db.add(user)
            db.commit()
            db.refresh(user)
    except IntegrityError as e:
        raise HTTPException(status_code=409, detail="username_or_email_taken") from e
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e

    token = create_access_token(user_id=user.id, settings=settings)
    return AuthTokenResponse(access_token=token, user=_to_profile(user, session_factory))


@router.post("/login", response_model=AuthTokenResponse)
def login(
    body: UserLoginRequest,
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> AuthTokenResponse:
    username = body.username.strip()
    try:
        with session_factory() as db:
            user = db.query(User).filter(User.username == username).one_or_none()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e

    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(status_code=401, detail="invalid_credentials")

    token = create_access_token(user_id=user.id, settings=settings)
    return AuthTokenResponse(access_token=token, user=_to_profile(user, session_factory))


@router.get("/me", response_model=UserProfileResponse)
def me(
    user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> UserProfileResponse:
    return _to_profile(user, session_factory)


@router.patch("/me", response_model=UserProfileResponse)
def update_me(
    body: UserProfileUpdateRequest,
    user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> UserProfileResponse:
    try:
        with session_factory() as db:
            row = db.get(User, user.id)
            if row is None:
                raise HTTPException(status_code=401, detail="user_not_found")
            if body.display_name is not None:
                name = body.display_name.strip()[:64]
                if not name:
                    raise HTTPException(status_code=400, detail="display_name_empty")
                row.display_name = name
            db.commit()
            db.refresh(row)
            return _to_profile(row, session_factory)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.post("/me/avatar", response_model=UserProfileResponse)
async def upload_avatar(
    avatar: UploadFile = File(...),
    user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> UserProfileResponse:
    body = await avatar.read()
    if len(body) > settings.MAX_AVATAR_BYTES:
        raise HTTPException(status_code=413, detail="avatar too large")
    if not body:
        raise HTTPException(status_code=400, detail="empty file")

    try:
        resized, _mime = load_and_resize_avatar(body)
        avatar_path = save_avatar(settings.AVATAR_DIR, str(user.id), resized)
    except Exception as e:
        raise HTTPException(status_code=400, detail="invalid image") from e

    try:
        with session_factory() as db:
            row = db.get(User, user.id)
            if row is None:
                raise HTTPException(status_code=401, detail="user_not_found")
            row.avatar_path = avatar_path
            db.commit()
            db.refresh(row)
            return _to_profile(row, session_factory)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.get("/me/avatar")
def get_avatar(user: User = Depends(get_current_user)) -> FileResponse:
    if not user.avatar_path:
        raise HTTPException(status_code=404, detail="avatar_not_found")
    path = Path(user.avatar_path)
    if not path.is_file():
        raise HTTPException(status_code=404, detail="avatar_not_found")
    return FileResponse(path, media_type="image/jpeg")


@router.delete("/me/avatar", response_model=UserProfileResponse)
def delete_avatar(
    user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> UserProfileResponse:
    try:
        with session_factory() as db:
            row = db.get(User, user.id)
            if row is None:
                raise HTTPException(status_code=401, detail="user_not_found")
            if row.avatar_path:
                path = Path(row.avatar_path)
                if path.is_file():
                    path.unlink(missing_ok=True)
                row.avatar_path = None
            db.commit()
            db.refresh(row)
            return _to_profile(row, session_factory)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


def _to_profile(user: User, session_factory: sessionmaker[Session]) -> UserProfileResponse:
    stats = UserStats(identify_count=0)
    try:
        with session_factory() as db:
            count = (
                db.query(func.count(IdentifyRecord.id))
                .filter(IdentifyRecord.user_id == user.id)
                .scalar()
            )
            stats = UserStats(identify_count=int(count or 0))
    except Exception:
        pass
    return UserProfileResponse(
        id=str(user.id),
        username=user.username,
        email=user.email,
        display_name=user.display_name,
        has_avatar=bool(user.avatar_path and Path(user.avatar_path).is_file()),
        created_at=user.created_at,
        stats=stats,
    )
