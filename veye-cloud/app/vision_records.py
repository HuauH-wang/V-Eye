from __future__ import annotations

import uuid
from pathlib import Path

from fastapi import HTTPException
from sqlalchemy.orm import Session

from .models import IdentifyRecord, MemberTrackPoint, User


def require_owned_record(db: Session, request_id: str, user: User) -> IdentifyRecord:
    try:
        rec_id = uuid.UUID(request_id)
    except ValueError as e:
        raise HTTPException(status_code=400, detail="invalid request_id") from e

    row = db.get(IdentifyRecord, rec_id)
    if row is None:
        raise HTTPException(status_code=404, detail="record_not_found")
    if row.user_id is None or row.user_id != user.id:
        raise HTTPException(status_code=403, detail="forbidden")
    return row


def read_record_image_bytes(row: IdentifyRecord) -> bytes:
    path = Path(row.image_path)
    if not path.is_file():
        raise HTTPException(status_code=404, detail="image_not_found")
    return path.read_bytes()


def record_meta(row: IdentifyRecord) -> tuple[bool, str | None]:
    result = row.result_json if isinstance(row.result_json, dict) else {}
    last_mode = result.get("last_enhance_mode")
    if isinstance(last_mode, str):
        last_mode = last_mode.strip() or None
    else:
        last_mode = None
    has_original = bool(row.image_original_path and Path(row.image_original_path).is_file())
    return has_original, last_mode


def unlink_track_points(db: Session, record_id: uuid.UUID) -> None:
    db.query(MemberTrackPoint).filter(MemberTrackPoint.identify_record_id == record_id).update(
        {MemberTrackPoint.identify_record_id: None},
        synchronize_session=False,
    )


def delete_image_file(path_str: str | None) -> None:
    if not path_str:
        return
    path = Path(path_str)
    if path.is_file():
        try:
            path.unlink()
        except OSError:
            pass
