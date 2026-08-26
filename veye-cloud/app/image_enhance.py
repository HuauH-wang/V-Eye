from __future__ import annotations

import io
from typing import Literal

from PIL import Image, ImageEnhance, ImageFilter

EnhanceMode = Literal["denoise", "enhance", "both"]
EnhanceStrength = Literal["light", "normal", "strong"]

_STRENGTH = {
    "light": {"contrast": 1.08, "sharpness": 1.15, "color": 1.05, "median": 3, "cv_h": 6},
    "normal": {"contrast": 1.15, "sharpness": 1.35, "color": 1.1, "median": 5, "cv_h": 10},
    "strong": {"contrast": 1.25, "sharpness": 1.55, "color": 1.18, "median": 7, "cv_h": 14},
}


def _load_rgb(image_bytes: bytes) -> Image.Image:
    img = Image.open(io.BytesIO(image_bytes))
    return img.convert("RGB")


def _to_jpeg_bytes(img: Image.Image, quality: int = 92) -> bytes:
    out = io.BytesIO()
    img.save(out, format="JPEG", quality=quality, optimize=True)
    return out.getvalue()


def _denoise_pil(img: Image.Image, strength: EnhanceStrength) -> Image.Image:
    size = _STRENGTH[strength]["median"]
    if size % 2 == 0:
        size += 1
    return img.filter(ImageFilter.MedianFilter(size=size))


def _denoise_cv2(img: Image.Image, strength: EnhanceStrength) -> Image.Image:
    import cv2
    import numpy as np

    arr = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    h = _STRENGTH[strength]["cv_h"]
    denoised = cv2.fastNlMeansDenoisingColored(arr, None, h, h, 7, 21)
    rgb = cv2.cvtColor(denoised, cv2.COLOR_BGR2RGB)
    return Image.fromarray(rgb)


def denoise_image(image_bytes: bytes, strength: EnhanceStrength = "normal") -> bytes:
    img = _load_rgb(image_bytes)
    try:
        img = _denoise_cv2(img, strength)
    except Exception:
        img = _denoise_pil(img, strength)
    return _to_jpeg_bytes(img)


def enhance_image(image_bytes: bytes, strength: EnhanceStrength = "normal") -> bytes:
    img = _load_rgb(image_bytes)
    cfg = _STRENGTH[strength]
    img = ImageEnhance.Contrast(img).enhance(cfg["contrast"])
    img = ImageEnhance.Color(img).enhance(cfg["color"])
    img = ImageEnhance.Sharpness(img).enhance(cfg["sharpness"])
    img = img.filter(ImageFilter.UnsharpMask(radius=1.2, percent=120, threshold=3))
    return _to_jpeg_bytes(img)


def process_image(
    image_bytes: bytes,
    mode: EnhanceMode,
    strength: EnhanceStrength = "normal",
) -> bytes:
    current = image_bytes
    if mode in {"denoise", "both"}:
        current = denoise_image(current, strength)
    if mode in {"enhance", "both"}:
        current = enhance_image(current, strength)
    return current
