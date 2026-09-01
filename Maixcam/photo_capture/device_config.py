#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Load device credentials and upload settings."""

from __future__ import annotations

import json
import os


def _project_dir() -> str:
    return os.path.dirname(os.path.abspath(__file__))


def load_device_config(cfg: dict) -> dict:
    out = {
        "device_id": (cfg.get("device_id") or "").strip(),
        "device_secret": (cfg.get("device_secret") or "").strip(),
        "upload_url": (cfg.get("upload_url") or "").strip(),
        "bind_uri": "",
        "base_url": (cfg.get("base_url") or "").strip(),
        "scene": (cfg.get("scene") or "toxic_plant").strip(),
        "lang": (cfg.get("lang") or "zh-CN").strip(),
    }
    device_path = os.path.join(_project_dir(), "device.json")
    if os.path.isfile(device_path):
        try:
            with open(device_path, "r", encoding="utf-8") as fp:
                dev = json.load(fp)
            for key in out:
                if dev.get(key):
                    out[key] = str(dev[key]).strip()
        except Exception as e:
            print(f"[CFG] device.json error: {e}")

    if not out["upload_url"] and out["base_url"]:
        out["upload_url"] = out["base_url"].rstrip("/") + "/camera/upload"
    return out


def resolve_upload_url(cfg: dict) -> str:
    url = (cfg.get("upload_url") or "").strip()
    if url:
        return url
    base = (cfg.get("base_url") or "").strip()
    if base:
        return base.rstrip("/") + "/camera/upload"
    return ""
