from __future__ import annotations

from datetime import datetime, timezone
from urllib.parse import quote

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session, sessionmaker

from .auth import verify_password
from .deps import get_current_user, get_session_factory_dep
from .models import CameraDevice, User
from .schemas import (
    CameraBindRequest,
    CameraBindResponse,
    CameraDeviceItem,
    CameraDeviceListResponse,
    OkResponse,
)

router = APIRouter(prefix="/devices", tags=["devices"])


def build_bind_uri(*, device_id: str, device_secret: str, base_url: str | None = None) -> str:
    """Static bind QR (v2): device credentials only; App uses its own BASE_URL."""
    _ = base_url
    return (
        f"veye://bind?v=2&did={quote(device_id, safe='')}"
        f"&sec={quote(device_secret, safe='')}"
    )


def _to_item(dev: CameraDevice) -> CameraDeviceItem:
    return CameraDeviceItem(
        device_id=dev.device_id,
        name=dev.name,
        device_type=dev.device_type,
        is_bound=dev.user_id is not None,
        bound_at=dev.bound_at,
        created_at=dev.created_at,
    )


@router.get("", response_model=CameraDeviceListResponse)
def list_devices(
    user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> CameraDeviceListResponse:
    try:
        with session_factory() as db:
            rows = (
                db.query(CameraDevice)
                .filter(CameraDevice.user_id == user.id)
                .order_by(CameraDevice.bound_at.desc().nullslast())
                .all()
            )
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    return CameraDeviceListResponse(items=[_to_item(r) for r in rows])


@router.post("/bind", response_model=CameraBindResponse)
def bind_device(
    body: CameraBindRequest,
    user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> CameraBindResponse:
    device_id = body.device_id.strip()
    device_secret = body.device_secret.strip()
    if not device_id or not device_secret:
        raise HTTPException(status_code=400, detail="device_credentials_required")

    try:
        with session_factory() as db:
            dev = db.query(CameraDevice).filter(CameraDevice.device_id == device_id).one_or_none()
            if dev is None or not verify_password(device_secret, dev.secret_hash):
                raise HTTPException(status_code=401, detail="invalid_device_credentials")
            if dev.user_id is not None and dev.user_id != user.id:
                raise HTTPException(status_code=409, detail="device_already_bound")
            if body.name and body.name.strip():
                dev.name = body.name.strip()[:64]
            dev.user_id = user.id
            dev.bound_at = datetime.now(timezone.utc)
            db.commit()
            db.refresh(dev)
            return CameraBindResponse(ok=True, device=_to_item(dev))
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.delete("/{device_id}", response_model=OkResponse)
def unbind_device(
    device_id: str,
    user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    did = device_id.strip()
    try:
        with session_factory() as db:
            dev = db.query(CameraDevice).filter(CameraDevice.device_id == did).one_or_none()
            if dev is None:
                raise HTTPException(status_code=404, detail="device_not_found")
            if dev.user_id != user.id:
                raise HTTPException(status_code=403, detail="not_device_owner")
            dev.user_id = None
            dev.bound_at = None
            db.commit()
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e
    return OkResponse(ok=True)
