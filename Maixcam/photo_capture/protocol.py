#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Board UART protocol: text lines, newline terminated, 115200 8N1."""

from __future__ import annotations

CMD_PHOTO = "PHOTO"
CMD_CONFIRM = "CONFIRM"
CMD_CANCEL = "CANCEL"
CMD_PING = "PING"
CMD_WAKE = "WAKE"

RSP_READY = "READY"
RSP_PHOTO_START = "PHOTO_START"
RSP_PHOTO_SAVED = "PHOTO_SAVED"
RSP_PHOTO_FAIL = "PHOTO_FAIL"
RSP_UPLOAD_WAIT = "UPLOAD_WAIT"
RSP_UPLOAD_OK = "UPLOAD_OK"
RSP_UPLOAD_FAIL = "UPLOAD_FAIL"
RSP_UPLOAD_SKIP = "UPLOAD_SKIP"
RSP_OFFLINE = "OFFLINE"
RSP_UPLOAD_TIMEOUT = "UPLOAD_TIMEOUT"
RSP_PONG = "PONG"
RSP_ERR = "ERR"


def parse_line(raw: bytes) -> str | None:
    try:
        line = raw.decode("utf-8", errors="ignore").strip()
    except Exception:
        return None
    if not line or line.startswith("#"):
        return None
    return line.upper()


def format_rsp(code: str, detail: str = "") -> str:
    if detail:
        return f"{code}:{detail}\n"
    return f"{code}\n"


def is_valid_board_line(line: str) -> bool:
    """ASCII protocol line only (drop BLE/GATT parse garbage)."""
    s = line.strip()
    if not s or len(s) > 80:
        return False
    for ch in s:
        if ord(ch) < 0x20 or ord(ch) > 0x7E:
            return False
    up = s.upper()
    prefixes = (
        "PING",
        "WAKE",
        "PHOTO",
        "CONFIRM",
        "CANCEL",
        "READY",
        "PONG",
        "ERR",
        "BLE:",
        "TCP:",
        "PHOTO_",
        "UPLOAD_",
        "OFFLINE",
    )
    return any(up.startswith(p) for p in prefixes)
