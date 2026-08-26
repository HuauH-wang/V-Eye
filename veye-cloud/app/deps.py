from __future__ import annotations

import uuid

from fastapi import Depends, Header, HTTPException
from sqlalchemy.orm import Session, sessionmaker

from .auth import decode_access_token, extract_bearer_token
from .config import Settings, get_settings
from .models import User

_settings: Settings | None = None
_session_factory: sessionmaker[Session] | None = None


def bind_runtime(settings: Settings, session_factory: sessionmaker[Session]) -> None:
    global _settings, _session_factory
    _settings = settings
    _session_factory = session_factory


def get_settings_dep() -> Settings:
    return _settings if _settings is not None else get_settings()


def get_session_factory_dep() -> sessionmaker[Session]:
    if _session_factory is None:
        raise RuntimeError("session factory not initialized")
    return _session_factory


def get_current_user(
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> User:
    token = extract_bearer_token(authorization)
    if not token:
        raise HTTPException(status_code=401, detail="not_authenticated")
    user_id = decode_access_token(token, settings)
    try:
        with session_factory() as db:
            user = db.get(User, user_id)
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    if user is None:
        raise HTTPException(status_code=401, detail="user_not_found")
    return user


def get_current_user_optional(
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> User | None:
    token = extract_bearer_token(authorization)
    if not token:
        return None
    try:
        user_id = decode_access_token(token, settings)
    except HTTPException:
        return None
    try:
        with session_factory() as db:
            return db.get(User, user_id)
    except Exception:
        return None
