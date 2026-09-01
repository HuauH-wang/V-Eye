#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Camera capture, local save, cloud upload."""

from __future__ import annotations

import os
import sys
import time


class PhotoService:
    def __init__(self, cfg: dict):
        self.cfg = cfg
        self._cam = None
        self._seq = 0

    def init_camera(self) -> None:
        from maix import camera

        w = int(self.cfg.get("camera_width", 1920))
        h = int(self.cfg.get("camera_height", 1080))
        self._cam = camera.Camera(w, h)
        print(f"[CAM] {w}x{h}")

    def _ensure_photo_dir(self) -> str:
        photo_dir = self.cfg.get("photo_dir", "/root/photos")
        os.makedirs(photo_dir, exist_ok=True)
        return photo_dir

    def capture_and_save(self) -> tuple[bool, str]:
        if self._cam is None:
            return False, "camera_not_init"

        try:
            img = self._cam.read()
            if img is None:
                return False, "read_failed"

            photo_dir = self._ensure_photo_dir()
            self._seq += 1
            ts = time.strftime("%Y%m%d_%H%M%S")
            filename = f"photo_{ts}_{self._seq:03d}.jpg"
            path = os.path.join(photo_dir, filename)
            img.save(path)
            print(f"[CAM] saved -> {path}")
            return True, path
        except Exception as e:
            print(f"[CAM] error: {e}", file=sys.stderr)
            return False, str(e)

    def is_online(self) -> bool:
        try:
            from maix import network

            wifi = network.wifi.Wifi()
            if not wifi.is_connected():
                return False
            ip = wifi.get_ip()
            return bool(ip and ip != "0.0.0.0")
        except Exception as e:
            print(f"[NET] check failed: {e}")
            return False

    def upload(self, path: str) -> tuple[bool, str]:
        url = (self.cfg.get("upload_url") or "").strip()
        if not url:
            return False, "upload_url_not_set"

        if not self.is_online():
            return False, "offline"

        if not os.path.isfile(path):
            return False, "file_not_found"

        timeout = int(self.cfg.get("upload_timeout_sec", 90))
        device_id = (self.cfg.get("device_id") or "").strip()
        device_secret = (self.cfg.get("device_secret") or "").strip()
        headers = {}
        if device_id and device_secret:
            headers = {"X-Device-Id": device_id, "X-Device-Secret": device_secret}
        scene = (self.cfg.get("scene") or "toxic_plant").strip()
        lang = (self.cfg.get("lang") or "zh-CN").strip()
        try:
            import requests

            with open(path, "rb") as fp:
                files = {"image": (os.path.basename(path), fp, "image/jpeg")}
                data = {"scene": scene, "lang": lang}
                resp = requests.post(url, files=files, data=data, headers=headers, timeout=timeout)

            if 200 <= resp.status_code < 300:
                print(f"[UPLOAD] OK {resp.status_code}")
                return True, ""
            print(f"[UPLOAD] HTTP {resp.status_code}: {resp.text[:200]}")
            return False, f"http_{resp.status_code}"
        except Exception as e:
            print(f"[UPLOAD] error: {e}", file=sys.stderr)
            return False, str(e)
