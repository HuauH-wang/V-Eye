from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session, sessionmaker

from .config import Settings
from .deps import get_current_user, get_session_factory_dep, get_settings_dep
from .image_enhance import process_image
from .image_utils import save_image, to_data_url
from .models import User
from .schemas import EnhanceApplyResponse, EnhancePreviewResponse, EnhanceRequest, OkResponse
from .vision_records import (
    delete_image_file,
    read_record_image_bytes,
    record_meta,
    require_owned_record,
    unlink_track_points,
)

router = APIRouter(prefix="/vision/records", tags=["vision-records"])


def _media_type(path: Path) -> str:
    ext = path.suffix.lower()
    if ext == ".png":
        return "image/png"
    if ext == ".webp":
        return "image/webp"
    return "image/jpeg"


@router.delete("/{request_id}", response_model=OkResponse)
def delete_identify_record(
    request_id: str,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    try:
        with session_factory() as db:
            row = require_owned_record(db, request_id, current_user)
            unlink_track_points(db, row.id)
            delete_image_file(row.image_path)
            if row.image_original_path and row.image_original_path != row.image_path:
                delete_image_file(row.image_original_path)
            db.delete(row)
            db.commit()
        return OkResponse()
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.post("/{request_id}/enhance/preview", response_model=EnhancePreviewResponse)
def preview_enhance_record(
    request_id: str,
    body: EnhanceRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> EnhancePreviewResponse:
    try:
        with session_factory() as db:
            row = require_owned_record(db, request_id, current_user)
            raw = read_record_image_bytes(row)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e

    try:
        processed = process_image(raw, body.mode, body.strength)
    except Exception as e:
        raise HTTPException(status_code=500, detail="enhance_failed") from e

    return EnhancePreviewResponse(
        preview_data_url=to_data_url("image/jpeg", processed),
        mode=body.mode,
        strength=body.strength,
    )


@router.post("/{request_id}/enhance/apply", response_model=EnhanceApplyResponse)
def apply_enhance_record(
    request_id: str,
    body: EnhanceRequest,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
    settings: Settings = Depends(get_settings_dep),
) -> EnhanceApplyResponse:
    try:
        with session_factory() as db:
            row = require_owned_record(db, request_id, current_user)
            raw = read_record_image_bytes(row)

            if not row.image_original_path:
                row.image_original_path = row.image_path

            processed = process_image(raw, body.mode, body.strength)
            new_path = save_image(settings.IMAGE_DIR, processed, ".jpg")

            old_path = row.image_path
            row.image_path = new_path
            result = dict(row.result_json) if isinstance(row.result_json, dict) else {}
            result["last_enhance_mode"] = body.mode
            result["last_enhance_strength"] = body.strength
            row.result_json = result

            db.commit()

            if old_path != row.image_original_path:
                delete_image_file(old_path)

            has_original, _ = record_meta(row)
            return EnhanceApplyResponse(
                has_original=has_original,
                mode=body.mode,
                strength=body.strength,
            )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.post("/{request_id}/enhance/restore", response_model=OkResponse)
def restore_original_record(
    request_id: str,
    current_user: User = Depends(get_current_user),
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
) -> OkResponse:
    try:
        with session_factory() as db:
            row = require_owned_record(db, request_id, current_user)
            if not row.image_original_path:
                raise HTTPException(status_code=400, detail="no_original_backup")

            original = Path(row.image_original_path)
            if not original.is_file():
                raise HTTPException(status_code=404, detail="original_not_found")

            current = row.image_path
            row.image_path = row.image_original_path
            row.image_original_path = None
            result = dict(row.result_json) if isinstance(row.result_json, dict) else {}
            result.pop("last_enhance_mode", None)
            result.pop("last_enhance_strength", None)
            row.result_json = result
            db.commit()

            if current and current != row.image_path:
                delete_image_file(current)

        return OkResponse()
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e


@router.get("/{request_id}/image/original")
def get_identify_record_original_image(
    request_id: str,
    session_factory: sessionmaker[Session] = Depends(get_session_factory_dep),
    current_user: User = Depends(get_current_user),
) -> FileResponse:
    try:
        with session_factory() as db:
            row = require_owned_record(db, request_id, current_user)
            if not row.image_original_path:
                raise HTTPException(status_code=404, detail="no_original_backup")
            path = Path(row.image_original_path)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=503, detail="database_unavailable") from e

    if not path.is_file():
        raise HTTPException(status_code=404, detail="original_not_found")
    return FileResponse(path, media_type=_media_type(path))
