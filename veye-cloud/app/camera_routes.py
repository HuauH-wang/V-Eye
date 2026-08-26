from __future__ import annotations

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from sqlalchemy.orm import Session, sessionmaker

from .config import Settings
from .deps import get_session_factory_dep, get_settings_dep
from .device_auth import get_bound_camera_device
from .models import CameraDevice, User
from .schemas import CameraUploadResponse, Scene
from .vision_identify import run_identify

router = APIRouter(prefix="/camera", tags=["camera"])


@router.post("/upload", response_model=CameraUploadResponse)
async def camera_upload(
    image: UploadFile | None = File(default=None),
    file: UploadFile | None = File(default=None),
    scene: Scene = Form(default="toxic_plant"),
    lang: str = Form(default="zh-CN"),
    gps_lat: float | None = Form(default=None),
    gps_lng: float | None = Form(default=None),
    accuracy_m: float | None = Form(default=None),
    settings: Settings = Depends(get_settings_dep),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
    bound: tuple[CameraDevice, User] = Depends(get_bound_camera_device),
) -> CameraUploadResponse:
    dev, user = bound
    upload = image or file
    if upload is None:
        raise HTTPException(status_code=400, detail="image_required")

    body = await upload.read()
    result = await run_identify(
        settings=settings,
        session_factory=session_factory,
        body=body,
        scene=scene,
        lang=lang,
        device_id=dev.device_id,
        filename=upload.filename,
        user=user,
        gps_lat=gps_lat,
        gps_lng=gps_lng,
        accuracy_m=accuracy_m,
    )
    return CameraUploadResponse(
        ok=True,
        request_id=result.request_id,
        label_main=result.label_main,
        risk_level=result.risk_level,
        summary=result.summary,
        latency_ms=result.latency_ms,
    )
