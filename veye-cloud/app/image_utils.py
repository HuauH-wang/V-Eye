from __future__ import annotations

import base64
import io
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image


def _safe_ext(content_type: str | None, filename: str | None) -> str:
    if content_type:
        ct = content_type.lower()
        if ct in {"image/jpeg", "image/jpg"}:
            return ".jpg"
        if ct == "image/png":
            return ".png"
        if ct == "image/webp":
            return ".webp"
    if filename and "." in filename:
        ext = "." + filename.rsplit(".", 1)[-1].lower()
        if ext in {".jpg", ".jpeg", ".png", ".webp"}:
            return ".jpg" if ext == ".jpeg" else ext
    return ".jpg"


def load_and_resize(image_bytes: bytes, max_long_edge: int) -> tuple[bytes, str]:
    img = Image.open(io.BytesIO(image_bytes))
    img = img.convert("RGB")

    w, h = img.size
    long_edge = max(w, h)
    if long_edge > max_long_edge:
        scale = max_long_edge / float(long_edge)
        new_w = max(1, int(round(w * scale)))
        new_h = max(1, int(round(h * scale)))
        img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)

    out = io.BytesIO()
    img.save(out, format="JPEG", quality=92, optimize=True)
    return out.getvalue(), "image/jpeg"


def save_image(image_dir: str, image_bytes: bytes, ext: str) -> str:
    now = datetime.now(timezone.utc)
    day = now.strftime("%Y%m%d")
    base = Path(image_dir) / day
    os.makedirs(base, exist_ok=True)
    name = f"{uuid.uuid4().hex}{ext}"
    path = base / name
    path.write_bytes(image_bytes)
    return str(path)


def to_data_url(mime: str, image_bytes: bytes) -> str:
    b64 = base64.b64encode(image_bytes).decode("ascii")
    return f"data:{mime};base64,{b64}"


def load_and_resize_avatar(image_bytes: bytes, size: int = 256) -> tuple[bytes, str]:
    img = Image.open(io.BytesIO(image_bytes))
    img = img.convert("RGB")
    w, h = img.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    img = img.crop((left, top, left + side, top + side))
    img = img.resize((size, size), Image.Resampling.LANCZOS)
    out = io.BytesIO()
    img.save(out, format="JPEG", quality=90, optimize=True)
    return out.getvalue(), "image/jpeg"


def save_avatar(avatar_dir: str, user_id: str, image_bytes: bytes) -> str:
    base = Path(avatar_dir)
    os.makedirs(base, exist_ok=True)
    path = base / f"{user_id}.jpg"
    path.write_bytes(image_bytes)
    return str(path)


def save_team_avatar(avatar_dir: str, team_id: str, image_bytes: bytes) -> str:
    base = Path(avatar_dir) / "teams"
    os.makedirs(base, exist_ok=True)
    path = base / f"{team_id}.jpg"
    path.write_bytes(image_bytes)
    return str(path)

