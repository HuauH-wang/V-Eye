#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from __future__ import annotations

print(">>> photo_key.py v2.1-key <<<")

import json
import os
import sys
import time

VERSION = "v2.1-key"

DEFAULT_CFG = {
    "photo_dir": "/root/photos",
    "camera_width": 640,
    "camera_height": 480,
    "upload_url": "",
    "upload_timeout_sec": 30,
    "upload_wait_timeout_sec": 300,
    "poll_interval_ms": 50,
    "status_msg_ms": 2000,
}

STATE_PREVIEW = "preview"
STATE_UPLOAD_WAIT = "upload_wait"
STATE_SHOW_MSG = "show_msg"

KEY_RELEASED = 0
KEY_PRESSED = 1
KEY_LONG_PRESSED = 2


def load_config() -> dict:
    cfg = dict(DEFAULT_CFG)
    try:
        cfg_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.json")
        if os.path.isfile(cfg_path):
            with open(cfg_path, "r", encoding="utf-8") as fp:
                cfg.update(json.load(fp))
    except Exception:
        pass
    return cfg


def sleep_ms(ms: int) -> None:
    try:
        from maix import time as maix_time

        maix_time.sleep_ms(ms)
    except ImportError:
        time.sleep(ms / 1000.0)


def should_exit() -> bool:
    try:
        from maix import app

        return app.need_exit()
    except ImportError:
        return False


def is_online() -> tuple[bool, str]:
    try:
        from maix import network

        wifi = network.wifi.Wifi()
        if not wifi.is_connected():
            return False, ""
        ip = wifi.get_ip()
        if ip and ip != "0.0.0.0":
            return True, ip
    except Exception as e:
        print(f"[NET] {e}")
    return False, ""


def upload_file(cfg: dict, path: str) -> tuple[bool, str]:
    url = (cfg.get("upload_url") or "").strip()
    if not url:
        return False, "no upload_url"
    online, _ = is_online()
    if not online:
        return False, "offline"
    if not os.path.isfile(path):
        return False, "file missing"
    try:
        import requests

        timeout = int(cfg.get("upload_timeout_sec", 30))
        with open(path, "rb") as fp:
            files = {"file": (os.path.basename(path), fp, "image/jpeg")}
            resp = requests.post(url, files=files, timeout=timeout)
        if 200 <= resp.status_code < 300:
            return True, ""
        return False, f"http_{resp.status_code}"
    except Exception as e:
        return False, str(e)


def save_photo(cam, photo_dir: str, seq: int, prefix: str = "photo") -> tuple[bool, str, object | None]:
    try:
        img = cam.read()
        if img is None:
            return False, "read_failed", None
        os.makedirs(photo_dir, exist_ok=True)
        ts = time.strftime("%Y%m%d_%H%M%S")
        path = os.path.join(photo_dir, f"{prefix}_{ts}_{seq:03d}.jpg")
        img.save(path)
        print(f"[CAM] saved -> {path}")
        return True, path, img
    except Exception as e:
        print(f"[CAM] error: {e}")
        return False, str(e), None


def draw_hint(img, lines: list[str]) -> None:
    from maix import image

    y = 8
    for line in lines:
        img.draw_string(8, y, line, image.Color.from_rgb(255, 255, 0), 1.2)
        y += 22


class KeyPhotoApp:
    def __init__(self, cfg: dict):
        self.cfg = cfg
        self.state = STATE_PREVIEW
        self.seq = 0
        self.last_path = ""
        self.last_img = None
        self.msg = ""
        self.msg_until = 0.0
        self.upload_wait_start = 0.0
        self._pending_short = False
        self._long_this_press = False
        self._key_obj = None
        self._cam = None
        self._disp = None

    def _on_key(self, key_id, state) -> None:
        try:
            from maix import key

            if key_id != key.Keys.KEY_OK:
                return
        except Exception:
            pass

        if state == KEY_PRESSED:
            self._long_this_press = False
            return
        if state == KEY_LONG_PRESSED:
            self._long_this_press = True
            self._pending_short = False
            return
        if state == KEY_RELEASED:
            if self._long_this_press:
                self._long_this_press = False
                self._handle_long()
            else:
                self._pending_short = True

    def _handle_long(self) -> None:
        if self.state == STATE_PREVIEW:
            print("[KEY] long OK -> exit")
            try:
                from maix import app

                app.set_exit_flag(True)
            except ImportError:
                sys.exit(0)
        elif self.state == STATE_UPLOAD_WAIT:
            print("[KEY] long OK -> cancel upload")
            self._show_msg("Upload cancelled")

    def _handle_short(self) -> None:
        if self.state == STATE_PREVIEW:
            print("[KEY] short OK -> capture")
            self._do_capture()
        elif self.state == STATE_UPLOAD_WAIT:
            print("[KEY] short OK -> confirm upload")
            self._do_confirm_upload()

    def _show_msg(self, text: str) -> None:
        self.msg = text
        self.msg_until = time.monotonic() + self.cfg.get("status_msg_ms", 2000) / 1000.0
        self.state = STATE_SHOW_MSG
        print(f"[APP] {text}")

    def _goto_preview(self) -> None:
        self.state = STATE_PREVIEW
        self.last_path = ""
        self.last_img = None
        self.upload_wait_start = 0.0

    def _do_capture(self) -> None:
        ok, result, img = save_photo(
            self._cam,
            self.cfg.get("photo_dir", "/root/photos"),
            self.seq,
        )
        if not ok:
            self._show_msg(f"Capture fail: {result}")
            return
        self.seq += 1
        self.last_path = result
        self.last_img = img
        self.state = STATE_UPLOAD_WAIT
        self.upload_wait_start = time.monotonic()
        print("[APP] upload wait: short=confirm long=cancel")

    def _do_confirm_upload(self) -> None:
        online, ip = is_online()
        if not online:
            self._show_msg("Offline, saved locally")
            return
        ok, reason = upload_file(self.cfg, self.last_path)
        if ok:
            self._show_msg(f"Upload OK ({ip})")
        else:
            self._show_msg(f"Upload fail: {reason}")

    def _overlay(self, base_img, hint_lines: list[str]):
        from maix import image

        overlay = image.Image(base_img.width(), base_img.height(), image.Format.FMT_RGB888)
        overlay.draw_image(0, 0, base_img)
        draw_hint(overlay, hint_lines)
        return overlay

    def run(self) -> None:
        from maix import camera, display, key

        print("=" * 42)
        print(f"  MaixCAM Photo {VERSION}")
        print("  Mode: key control (no board)")
        print("=" * 42)

        w = int(self.cfg.get("camera_width", 640))
        h = int(self.cfg.get("camera_height", 480))
        self._cam = camera.Camera(w, h)
        self._disp = display.Display()
        self._key_obj = key.Key(self._on_key)

        online, ip = is_online()
        print(f"[NET] {'online' if online else 'offline'}" + (f" {ip}" if ip else ""))
        print("[HELP] preview: short OK=capture  long OK=exit")
        print("[HELP] upload : short OK=confirm  long OK=cancel")

        while not should_exit():
            if self._pending_short:
                self._pending_short = False
                self._handle_short()

            if self.state == STATE_UPLOAD_WAIT:
                timeout = int(self.cfg.get("upload_wait_timeout_sec", 300))
                if time.monotonic() - self.upload_wait_start >= timeout:
                    self._show_msg("Upload wait timeout")

            if self.state == STATE_SHOW_MSG:
                if time.monotonic() >= self.msg_until:
                    self._goto_preview()

            if self.state == STATE_PREVIEW:
                frame = self._cam.read()
                if frame:
                    shown = self._overlay(frame, [
                        "Preview",
                        "Short OK: capture",
                        "Long OK: exit",
                    ])
                    self._disp.show(shown)
            elif self.state == STATE_UPLOAD_WAIT and self.last_img is not None:
                shown = self._overlay(self.last_img, [
                    "Saved locally",
                    self.last_path.split("/")[-1],
                    "Short OK: upload",
                    "Long OK: cancel",
                ])
                self._disp.show(shown)
            elif self.state == STATE_SHOW_MSG:
                from maix import image

                blank = image.Image(self._disp.width(), self._disp.height(), image.Format.FMT_RGB888)
                draw_hint(blank, [self.msg])
                self._disp.show(blank)

            sleep_ms(int(self.cfg.get("poll_interval_ms", 50)))

        print("[EXIT] done")


if __name__ == "__main__":
    try:
        KeyPhotoApp(load_config()).run()
    except KeyboardInterrupt:
        print("\n[EXIT] interrupted")
