#!/usr/bin/env python3
"""Build Xiao Ou sprite sheets from a single base portrait with local补帧."""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DESIGN_SHEET = ROOT / "app/src/main/assets/xiaoou/61b4e73a-1b8e-4fb6-a0e0-1ce648b76b99.jpg"
BASE_PORTRAIT = ROOT / "app/src/main/assets/xiaoou/xiaoou_base.png"
OUT_DRAWABLE = ROOT / "app/src/main/res/drawable-nodpi"
OUT_ASSETS = ROOT / "app/src/main/assets/xiaoou"
OUT_WEB = Path("/root/veye-cloud/frontend/public/xiaoou")
OUT_WEB_STATIC = Path("/root/veye-cloud/app/static/web/xiaoou")

FRAME = 128
FRAMES_PER_ANIM = 6

ANIMATIONS = ("idle", "wave", "happy", "exercising", "worried", "think", "point", "sleep")

ANIMATION_FPS: dict[str, int] = {
    "idle": 6,
    "wave": 8,
    "happy": 8,
    "exercising": 10,
    "worried": 6,
    "think": 5,
    "point": 7,
    "sleep": 4,
}


def remove_background(img: Image.Image, tolerance: int = 42) -> Image.Image:
    img = img.convert("RGBA")
    samples = [img.getpixel((8, 8)), img.getpixel((img.width - 9, 8))]
    width, height = img.size
    pixels = img.load()
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if any(abs(r - s[0]) + abs(g - s[1]) + abs(b - s[2]) < tolerance for s in samples):
                pixels[x, y] = (r, g, b, 0)
            elif r > 228 and g > 238 and b > 245:
                pixels[x, y] = (r, g, b, 0)
    return remove_blue_disc(img)


def remove_blue_disc(img: Image.Image) -> Image.Image:
    """Strip the light-blue circular plate baked into the design sheet crops."""
    pixels = img.load()
    width, height = img.size
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            if b >= 215 and g >= 195 and r >= 145 and (b - r) >= 28 and (g - r) >= 18:
                pixels[x, y] = (r, g, b, 0)
    return img


def crop_pose_from_sheet(sheet: Image.Image, index: int) -> Image.Image:
    cols, rows = 5, 3
    w, h = sheet.size
    col = index % cols
    row = index // cols
    pad_x = int(w * 0.02)
    pad_y = int(h * 0.03)
    left = int(col * w / cols) + pad_x
    top = int(row * h / rows) + pad_y
    right = int((col + 1) * w / cols) - pad_x
    bottom = int((row + 1) * h / rows) - pad_y
    crop = remove_background(sheet.crop((left, top, right, bottom)))
    side = min(crop.size)
    cx = (crop.width - side) // 2
    cy = (crop.height - side) // 2
    square = crop.crop((cx, cy, cx + side, cy + side))
    return square.resize((FRAME, FRAME), Image.Resampling.LANCZOS)


def load_base_portrait() -> Image.Image:
    if not DESIGN_SHEET.exists() and not BASE_PORTRAIT.exists():
        raise SystemExit(f"Missing source image: {BASE_PORTRAIT} or {DESIGN_SHEET}")
    if DESIGN_SHEET.exists():
        sheet = Image.open(DESIGN_SHEET).convert("RGBA")
        portrait = crop_pose_from_sheet(sheet, 0)
    else:
        portrait = remove_blue_disc(Image.open(BASE_PORTRAIT).convert("RGBA"))
    portrait = portrait.resize((FRAME, FRAME), Image.Resampling.LANCZOS)
    BASE_PORTRAIT.parent.mkdir(parents=True, exist_ok=True)
    portrait.save(BASE_PORTRAIT)
    return portrait


def transform_frame(base: Image.Image, frame_idx: int, total: int, anim: str) -> Image.Image:
    t = frame_idx / max(total - 1, 1)
    phase = math.sin(t * math.pi * 2)

    if anim == "idle":
        scale, dy, rot = 1.0 + 0.025 * phase, int(2 * phase), 0.8 * phase
    elif anim == "wave":
        scale, dy, rot = 1.0 + 0.03 * abs(phase), int(2 * phase), 2.5 * phase
    elif anim == "happy":
        scale, dy, rot = 1.0 + 0.04 * abs(phase), int(-3 * abs(phase)), 1.5 * phase
    elif anim == "exercising":
        scale, dy, rot = 1.0 + 0.035 * abs(phase), int(4 * abs(phase)), 2 * phase
    elif anim == "worried":
        scale, dy, rot = 1.0 + 0.01 * phase, int(1 * abs(phase)), -1 * abs(phase)
    elif anim == "think":
        scale, dy, rot = 1.0 + 0.015 * phase, int(1 * phase), 0.6 * phase
    elif anim == "point":
        scale, dy, rot = 1.0 + 0.02 * phase, 0, 1.2 * phase
    elif anim == "sleep":
        scale, dy, rot = 1.0 + 0.012 * abs(phase), int(2 * (1 - abs(phase))), 0.4 * phase
    else:
        scale, dy, rot = 1.0, 0, 0.0

    size = int(FRAME * scale)
    scaled = base.resize((size, size), Image.Resampling.LANCZOS)
    rotated = scaled.rotate(rot, resample=Image.Resampling.BICUBIC, expand=False)
    canvas = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    ox = (FRAME - rotated.width) // 2
    oy = (FRAME - rotated.height) // 2 + dy
    canvas.alpha_composite(rotated, (ox, oy))
    return canvas


def build_sheet(base: Image.Image, anim: str) -> Image.Image:
    frames = [transform_frame(base, i, FRAMES_PER_ANIM, anim) for i in range(FRAMES_PER_ANIM)]
    sheet = Image.new("RGBA", (FRAME * FRAMES_PER_ANIM, FRAME), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        sheet.paste(frame, (i * FRAME, 0), frame)
    return sheet


def main() -> None:
    base = load_base_portrait()
    OUT_DRAWABLE.mkdir(parents=True, exist_ok=True)
    OUT_WEB.mkdir(parents=True, exist_ok=True)
    OUT_WEB_STATIC.mkdir(parents=True, exist_ok=True)

    base.save(OUT_DRAWABLE / "xiaoou_base.png")

    manifest = {"frameWidth": FRAME, "frameHeight": FRAME, "animations": {}}
    for anim in ANIMATIONS:
        sheet = build_sheet(base, anim)
        name = f"xiaoou_{anim}.png"
        for out_dir in (OUT_DRAWABLE, OUT_WEB, OUT_WEB_STATIC):
            sheet.save(out_dir / name)
        manifest["animations"][anim] = {
            "drawable": f"xiaoou_{anim}",
            "frameCount": FRAMES_PER_ANIM,
            "fps": ANIMATION_FPS[anim],
        }
        print(f"built {name} from unified portrait")

    for out_dir in (OUT_ASSETS, OUT_WEB, OUT_WEB_STATIC):
        with open(out_dir / "animations.json", "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=2, ensure_ascii=False)

    for out_dir in (OUT_WEB, OUT_WEB_STATIC):
        base.save(out_dir / "xiaoou_base.png")
    print("done")


if __name__ == "__main__":
    main()
