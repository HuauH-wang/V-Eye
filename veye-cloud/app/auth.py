from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
from fastapi import Header, HTTPException

from .config import Settings


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("utf-8"))
    except Exception:  # noqa: BLE001
        return False


def create_access_token(*, user_id: uuid.UUID, settings: Settings) -> str:
    expire = datetime.now(timezone.utc) + timedelta(hours=settings.JWT_EXPIRE_HOURS)
    payload = {"sub": str(user_id), "exp": expire}
    return jwt.encode(payload, settings.JWT_SECRET, algorithm="HS256")


def decode_access_token(token: str, settings: Settings) -> uuid.UUID:
    try:
        payload = jwt.decode(token, settings.JWT_SECRET, algorithms=["HS256"])
        sub = payload.get("sub")
        if not sub:
            raise HTTPException(status_code=401, detail="invalid_token")
        return uuid.UUID(str(sub))
    except jwt.ExpiredSignatureError as e:
        raise HTTPException(status_code=401, detail="token_expired") from e
    except (jwt.InvalidTokenError, ValueError) as e:
        raise HTTPException(status_code=401, detail="invalid_token") from e


def extract_bearer_token(authorization: str | None = Header(default=None)) -> str | None:
    if not authorization:
        return None
    parts = authorization.split(" ", 1)
    if len(parts) != 2 or parts[0].lower() != "bearer" or not parts[1].strip():
        return None
    return parts[1].strip()
