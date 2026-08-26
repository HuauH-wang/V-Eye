from __future__ import annotations

import uuid

from fastapi import Depends, Header, HTTPException
from sqlalchemy.orm import Session, sessionmaker

from .auth import verify_password
from .deps import get_session_factory_dep
from .models import CameraDevice, User


def get_camera_device_by_headers(
    x_device_id: str | None = Header(default=None, alias="X-Device-Id"),
    x_device_secret: str | None = Header(default=None, alias="X-Device-Secret"),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> CameraDevice:
    device_id = (x_device_id or "").strip()
    device_secret = (x_device_secret or "").strip()
    if not device_id or not device_secret:
        raise HTTPException(status_code=401, detail="device_credentials_required")

    try:
        with session_factory() as db:
            dev = db.query(CameraDevice).filter(CameraDevice.device_id == device_id).one_or_none()
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e

    if dev is None or not verify_password(device_secret, dev.secret_hash):
        raise HTTPException(status_code=401, detail="invalid_device_credentials")
    return dev


def get_bound_camera_device(
    dev: CameraDevice = Depends(get_camera_device_by_headers),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> tuple[CameraDevice, User]:
    if dev.user_id is None:
        raise HTTPException(status_code=403, detail="device_not_bound")

    try:
        with session_factory() as db:
            user = db.get(User, dev.user_id)
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e

    if user is None:
        raise HTTPException(status_code=403, detail="device_owner_missing")
    return dev, user


def get_camera_device_user_id(dev: CameraDevice) -> uuid.UUID | None:
    return dev.user_id
