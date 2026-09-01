"""V-Eye web UI tokens for MaixCAM (veye-cloud/frontend/src/styles/tokens.css)."""

from __future__ import annotations


class Theme:
    # RGB tuples
    BG = (250, 250, 247)
    SURFACE = (255, 255, 255)
    SURFACE_MUTED = (240, 244, 241)
    PRIMARY = (27, 67, 50)
    PRIMARY_LIGHT = (64, 145, 108)
    ACCENT_SOFT = (216, 243, 220)
    ACCENT_WARM = (244, 162, 97)
    INK = (26, 46, 26)
    MUTED = (92, 107, 92)
    SAFE = (82, 183, 136)
    DANGER = (231, 111, 81)
    HEADER_TEXT = (236, 253, 245)
    BORDER = (27, 67, 50)


def rgb(color):
    from maix import image

    return image.Color.from_rgb(color[0], color[1], color[2])
