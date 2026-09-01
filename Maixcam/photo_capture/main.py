#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from __future__ import annotations

print(">>> main.py v3.10-cam-optional <<<")

import json
import os
import sys
import time

# MaixVision 可能只部署 main.py；优先本地模块，失败则用内联实现
try:
    from board_link import create_board_link
    from device_config import load_device_config, resolve_upload_url
    from protocol import (
        CMD_CANCEL,
        CMD_CONFIRM,
        CMD_PHOTO,
        CMD_PING,
        CMD_WAKE,
        RSP_ERR,
        RSP_OFFLINE,
        RSP_PHOTO_FAIL,
        RSP_PHOTO_SAVED,
        RSP_PHOTO_START,
        RSP_PONG,
        RSP_READY,
        RSP_UPLOAD_FAIL,
        RSP_UPLOAD_OK,
        RSP_UPLOAD_SKIP,
        RSP_UPLOAD_WAIT,
        format_rsp,
    )
    from ui_theme import Theme, rgb
except ImportError:
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
    RSP_PONG = "PONG"
    RSP_ERR = "ERR"

    def format_rsp(code: str, detail: str = "") -> str:
        if detail:
            return f"{code}:{detail}\n"
        return f"{code}\n"

    def _parse_uart_line(raw: bytes) -> str | None:
        try:
            line = raw.decode("utf-8", errors="ignore").strip()
        except Exception:
            return None
        if not line or line.startswith("#"):
            return None
        return line.upper()

    class BoardUart:
        def __init__(self, device: str, baudrate: int = 115200):
            self._device = device
            self._baudrate = baudrate
            self._uart = None
            self._rx_buf = bytearray()

        def open(self) -> None:
            from maix import pinmap, uart

            mapping = {
                "/dev/ttyS0": {"A16": "UART0_TX", "A17": "UART0_RX"},
                "/dev/ttyS1": {"A19": "UART1_TX", "A18": "UART1_RX"},
            }.get(self._device)
            if mapping:
                for pin, func in mapping.items():
                    pinmap.set_pin_function(pin, func)
                    print(f"[UART] pinmap {pin} -> {func}")
            self._uart = uart.UART(self._device, self._baudrate)
            print(f"[UART] opened {self._device} @ {self._baudrate}")

        def send(self, text: str) -> None:
            if self._uart is None:
                return
            if not text.endswith("\n"):
                text += "\n"
            self._uart.write_str(text)
            print(f"[UART->] {text.rstrip()}")

        def poll_command(self, timeout_ms: int = 50) -> str | None:
            if self._uart is None:
                return None
            chunk = self._uart.read(timeout=timeout_ms)
            if chunk:
                print(f"[UART] raw rx {len(chunk)}B: {chunk[:40]!r}")
                self._rx_buf.extend(chunk)
            while True:
                nl = self._rx_buf.find(b"\n")
                if nl < 0:
                    cr = self._rx_buf.find(b"\r")
                    if cr >= 0 and cr + 1 < len(self._rx_buf) and self._rx_buf[cr + 1] == ord("\n"):
                        line = bytes(self._rx_buf[:cr])
                        del self._rx_buf[: cr + 2]
                    elif cr >= 0:
                        line = bytes(self._rx_buf[:cr])
                        del self._rx_buf[: cr + 1]
                    else:
                        break
                else:
                    line = bytes(self._rx_buf[:nl])
                    del self._rx_buf[: nl + 1]
                cmd = _parse_uart_line(line)
                if cmd:
                    print(f"[UART<-] {cmd}")
                    return cmd
            return None

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
        device_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "device.json")
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

    class Theme:
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


VERSION = "v3.0-veye"

DEFAULT_CFG = {
    "photo_dir": "/root/photos",
    "camera_width": 640,
    "camera_height": 480,
    "upload_url": "",
    "device_id": "",
    "device_secret": "",
    "scene": "toxic_plant",
    "lang": "zh-CN",
    "upload_timeout_sec": 90,
    "upload_wait_timeout_sec": 300,
    "poll_interval_ms": 50,
    "status_msg_ms": 2500,
    "uart_enable": False,
    "uart_device": "/dev/ttyS0",
    "uart_baudrate": 115200,
    "link_mode": "ble",
    "ble_device_name": "U5-MAIX-BRIDGE",
    "ble_address": "44:BD:8D:27:96:A4",
    "ble_scan_timeout_sec": 10,
    "ble_reconnect_sec": 3,
    "tcp_port": 8765,
}

STATE_PREVIEW = "preview"
STATE_UPLOAD_WAIT = "upload_wait"
STATE_SHOW_MSG = "show_msg"
STATE_GALLERY = "gallery"
STATE_QR = "qr_bind"

KEY_RELEASED = 0
KEY_PRESSED = 1
KEY_LONG_PRESSED = 2

TAP_MOVE_MAX = 12
SWIPE_MIN = 28
HEADER_H = 44
FOOTER_H = 56


class Rect:
    __slots__ = ("x", "y", "w", "h")

    def __init__(self, x: int, y: int, w: int, h: int):
        self.x = x
        self.y = y
        self.w = w
        self.h = h

    def contains(self, px: int, py: int) -> bool:
        return self.x <= px < self.x + self.w and self.y <= py < self.y + self.h


def load_config() -> dict:
    cfg = dict(DEFAULT_CFG)
    try:
        cfg_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.json")
        if os.path.isfile(cfg_path):
            with open(cfg_path, "r", encoding="utf-8") as fp:
                cfg.update(json.load(fp))
    except Exception:
        pass
    cfg.update(load_device_config(cfg))
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
    url = resolve_upload_url(cfg)
    if not url:
        return False, "no upload_url"
    device_id = (cfg.get("device_id") or "").strip()
    device_secret = (cfg.get("device_secret") or "").strip()
    if not device_id or not device_secret:
        return False, "device_not_configured"
    online, _ = is_online()
    if not online:
        return False, "offline"
    if not os.path.isfile(path):
        return False, "file missing"
    try:
        import requests

        timeout = int(cfg.get("upload_timeout_sec", 90))
        headers = {
            "X-Device-Id": device_id,
            "X-Device-Secret": device_secret,
        }
        scene = (cfg.get("scene") or "toxic_plant").strip()
        lang = (cfg.get("lang") or "zh-CN").strip()
        with open(path, "rb") as fp:
            files = {"image": (os.path.basename(path), fp, "image/jpeg")}
            data = {"scene": scene, "lang": lang}
            resp = requests.post(url, files=files, data=data, headers=headers, timeout=timeout)
        if 200 <= resp.status_code < 300:
            try:
                body = resp.json()
                label = body.get("label_main") or body.get("summary") or "OK"
                return True, str(label)[:40]
            except Exception:
                return True, ""
        if resp.status_code == 403:
            return False, "not_bound_scan_qr"
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


def list_photo_files(photo_dir: str) -> list[str]:
    if not os.path.isdir(photo_dir):
        return []
    out: list[str] = []
    for name in os.listdir(photo_dir):
        low = name.lower()
        if low.endswith((".jpg", ".jpeg", ".png")):
            out.append(os.path.join(photo_dir, name))
    out.sort(key=lambda p: os.path.getmtime(p), reverse=True)
    return out


def draw_hint(img, lines: list[str], y0: int = 8, color=None) -> None:
    if color is None:
        color = rgb(Theme.ACCENT_SOFT)
    y = y0
    for line in lines:
        img.draw_string(8, y, line, color, 1.0)
        y += 18


def draw_button(img, rect: Rect, label: str, pressed: bool = False, primary: bool = True) -> None:
    if primary:
        fill = rgb(Theme.PRIMARY_LIGHT if pressed else Theme.PRIMARY)
    else:
        fill = rgb(Theme.SURFACE_MUTED if not pressed else Theme.MUTED)
    border = rgb(Theme.PRIMARY_LIGHT)
    text = rgb(Theme.HEADER_TEXT if primary else Theme.INK)
    img.draw_rect(rect.x, rect.y, rect.w, rect.h, fill, thickness=-1)
    img.draw_rect(rect.x, rect.y, rect.w, rect.h, border, thickness=1)
    tx = rect.x + max(6, (rect.w - len(label) * 7) // 2)
    img.draw_string(tx, rect.y + 14, label, text, 1.0)


def fit_draw_photo(canvas, photo, x: int, y: int, cw: int, ch) -> None:
    sw, sh = photo.width(), photo.height()
    if sw <= 0 or sh <= 0:
        return
    scale = min(cw / sw, ch / sh)
    nw = max(1, int(sw * scale))
    nh = max(1, int(sh * scale))
    try:
        scaled = photo.resize(nw, nh)
    except Exception:
        scaled = photo
    ox = x + (cw - nw) // 2
    oy = y + (ch - nh) // 2
    canvas.draw_image(ox, oy, scaled)


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
        self._ts = None
        self._dw = 0
        self._dh = 0
        self._btn_gallery = Rect(0, 0, 0, 0)
        self._btn_qr = Rect(0, 0, 0, 0)
        self._btn_prev = Rect(0, 0, 0, 0)
        self._btn_next = Rect(0, 0, 0, 0)
        self._btn_back = Rect(0, 0, 0, 0)
        self._gallery_paths: list[str] = []
        self._gallery_index = 0
        self._gallery_cached_path = ""
        self._gallery_cached_img = None
        self._gallery_return_state = STATE_UPLOAD_WAIT
        self._touch_active = False
        self._was_pressed = False
        self._touch_x0 = 0
        self._touch_y0 = 0
        self._touch_x1 = 0
        self._touch_y1 = 0
        self._touch_moved = False
        self._pressed_btn = ""
        self._bind_qr_img = None
        self._project_dir = os.path.dirname(os.path.abspath(__file__))
        self._board_link = None
        self._link_name = "off"
        self._link_hb_last = 0.0
        self._link_ble_announced = False

    def _layout_upload_buttons(self) -> None:
        pad = 8
        y = self._dh - FOOTER_H + 6
        half = (self._dw - pad * 3) // 2
        self._btn_qr = Rect(pad, y, half, FOOTER_H - 12)
        self._btn_gallery = Rect(pad * 2 + half, y, half, FOOTER_H - 12)

    def _layout_gallery_buttons(self) -> None:
        pad = 6
        y = self._dh - FOOTER_H + 6
        bw = (self._dw - pad * 4) // 3
        self._btn_prev = Rect(pad, y, bw, FOOTER_H - 12)
        self._btn_next = Rect(pad * 2 + bw, y, bw, FOOTER_H - 12)
        self._btn_back = Rect(pad * 3 + bw * 2, y, bw, FOOTER_H - 12)

    def _content_rect(self) -> Rect:
        return Rect(0, HEADER_H, self._dw, self._dh - HEADER_H - FOOTER_H)

    def _footer_rect(self) -> Rect:
        return Rect(0, self._dh - FOOTER_H, self._dw, FOOTER_H)

    def _new_screen(self):
        from maix import image

        canvas = image.Image(self._dw, self._dh, image.Format.FMT_RGB888)
        canvas.draw_rect(0, 0, self._dw, self._dh, rgb(Theme.BG), thickness=-1)
        return canvas

    def _draw_header_bar(self, canvas, title: str, subtitle: str = "") -> None:
        canvas.draw_rect(0, 0, self._dw, HEADER_H, rgb(Theme.PRIMARY), thickness=-1)
        canvas.draw_rect(0, HEADER_H - 2, self._dw, 2, rgb(Theme.PRIMARY_LIGHT), thickness=-1)
        canvas.draw_string(10, 8, title, rgb(Theme.HEADER_TEXT), 1.15)
        if subtitle:
            canvas.draw_string(10, 26, subtitle[:36], rgb(Theme.ACCENT_SOFT), 0.95)

    def _draw_footer_bar(self, canvas) -> None:
        fr = self._footer_rect()
        canvas.draw_rect(fr.x, fr.y, fr.w, fr.h, rgb(Theme.SURFACE_MUTED), thickness=-1)
        canvas.draw_rect(fr.x, fr.y, 1, fr.h, rgb(Theme.PRIMARY_LIGHT), thickness=-1)
        draw_button(canvas, self._btn_qr, "Bind QR", pressed=self._pressed_btn == "qr", primary=True)
        draw_button(canvas, self._btn_gallery, "Gallery", pressed=self._pressed_btn == "gallery", primary=False)

    def _draw_gallery_footer(self, canvas) -> None:
        fr = self._footer_rect()
        canvas.draw_rect(fr.x, fr.y, fr.w, fr.h, rgb(Theme.SURFACE_MUTED), thickness=-1)
        draw_button(canvas, self._btn_prev, "<", pressed=self._pressed_btn == "prev", primary=False)
        draw_button(canvas, self._btn_next, ">", pressed=self._pressed_btn == "next", primary=False)
        draw_button(canvas, self._btn_back, "Back", pressed=self._pressed_btn == "back", primary=True)

    def _draw_photo_screen(self, photo, title: str, subtitle: str, hints: list[str]) -> None:
        canvas = self._new_screen()
        self._draw_header_bar(canvas, title, subtitle)
        area = self._content_rect()
        fit_draw_photo(canvas, photo, area.x, area.y, area.w, area.h)
        overlay_y = area.y + area.h - len(hints) * 18 - 8
        draw_hint(canvas, hints, y0=max(area.y + 6, overlay_y), color=rgb(Theme.ACCENT_SOFT))
        self._draw_footer_bar(canvas)
        self._disp.show(canvas)

    def _draw_preview_screen(self, frame) -> None:
        canvas = self._new_screen()
        online, ip = is_online()
        net = f"WiFi {ip}" if online else "Offline"
        did = (self.cfg.get("device_id") or "no-id")[:18]
        uart_tag = self._link_name[:12]
        self._draw_header_bar(canvas, "V-Eye Maxicam", f"{net}  Link:{uart_tag}")
        area = Rect(0, HEADER_H, self._dw, self._dh - HEADER_H - FOOTER_H)
        fit_draw_photo(canvas, frame, area.x, area.y, area.w, area.h)
        draw_hint(
            canvas,
            ["OK short: capture", "OK long: exit", "Touch Bind QR to pair App"],
            y0=area.y + 6,
            color=rgb(Theme.ACCENT_SOFT),
        )
        self._draw_footer_bar(canvas)
        self._disp.show(canvas)

    def _load_bind_qr_image(self):
        if self._bind_qr_img is not None:
            return self._bind_qr_img
        qr_path = os.path.join(self._project_dir, "bind_qr.png")
        if os.path.isfile(qr_path):
            try:
                from maix import image

                self._bind_qr_img = image.load(qr_path)
                return self._bind_qr_img
            except Exception as e:
                print(f"[QR] load fail: {e}")
        return None

    def _draw_qr_screen(self) -> None:
        canvas = self._new_screen()
        self._draw_header_bar(canvas, "Scan to Bind", "Open V-Eye App > Device > Scan")
        area = self._content_rect()
        canvas.draw_rect(area.x + 8, area.y + 8, area.w - 16, area.h - 16, rgb(Theme.SURFACE), thickness=-1)
        canvas.draw_rect(area.x + 8, area.y + 8, area.w - 16, area.h - 16, rgb(Theme.PRIMARY_LIGHT), thickness=1)

        qr_img = self._load_bind_qr_image()
        qr_size = min(area.w - 48, area.h - 100, 220)
        if qr_img is not None:
            try:
                scaled = qr_img.resize(qr_size, qr_size)
                ox = area.x + (area.w - qr_size) // 2
                oy = area.y + 16
                canvas.draw_image(ox, oy, scaled)
            except Exception:
                qr_img = None

        bind_uri = (self.cfg.get("bind_uri") or "").strip()
        did = (self.cfg.get("device_id") or "-")[:24]
        lines = [f"ID: {did}"]
        if not qr_img:
            lines.append("QR PNG missing")
            if bind_uri:
                lines.append(bind_uri[:32])
        lines.append("Touch Back or OK to return")
        draw_hint(canvas, lines, y0=area.y + area.h - 72, color=rgb(Theme.INK))

        back_rect = Rect(area.x + 16, self._dh - FOOTER_H + 8, area.w - 32, FOOTER_H - 16)
        draw_button(canvas, back_rect, "Back", pressed=self._pressed_btn == "back", primary=True)
        self._disp.show(canvas)

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
            self._cancel_upload()
        elif self.state in (STATE_GALLERY, STATE_QR):
            self._leave_overlay()

    def _handle_short(self) -> None:
        if self.state == STATE_PREVIEW:
            print("[KEY] short OK -> capture")
            self._do_capture()
        elif self.state == STATE_UPLOAD_WAIT:
            print("[KEY] short OK -> confirm upload")
            self._do_confirm_upload()
        elif self.state == STATE_GALLERY:
            print("[KEY] short OK -> select photo")
            self._select_gallery_photo()
        elif self.state == STATE_QR:
            self._leave_overlay()

    def _show_msg(self, text: str) -> None:
        self.msg = text
        self.msg_until = time.monotonic() + self.cfg.get("status_msg_ms", 2500) / 1000.0
        self.state = STATE_SHOW_MSG
        print(f"[APP] {text}")

    def _goto_preview(self) -> None:
        self.state = STATE_PREVIEW
        self.last_path = ""
        self.last_img = None
        self.upload_wait_start = 0.0

    def _leave_overlay(self) -> None:
        if self.state == STATE_GALLERY:
            self._leave_gallery()
        elif self.state == STATE_QR:
            self.state = STATE_PREVIEW

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

    def _do_confirm_upload(self) -> tuple[bool, str]:
        online, _ip = is_online()
        if not online:
            self._show_msg("Offline, saved locally")
            return False, "offline"
        if not self.last_path:
            self._show_msg("No photo to upload")
            return False, "no_photo"
        ok, reason = upload_file(self.cfg, self.last_path)
        if ok:
            label = f": {reason}" if reason else ""
            self._show_msg(f"Upload OK{label}")
            return True, reason
        if reason == "not_bound_scan_qr":
            self._show_msg("Scan QR in App first")
        else:
            self._show_msg(f"Upload fail: {reason}")
        return False, reason

    def _cancel_upload(self) -> None:
        self._show_msg("Upload cancelled")

    def _open_camera(self):
        import gc
        from maix import camera, image

        gc.collect()
        print("[CAM] wait 3s for mmf release (reboot board if ION fail)")
        time.sleep(3.0)

        w = int(self.cfg.get("camera_width", 640))
        h = int(self.cfg.get("camera_height", 480))
        buff_num = max(1, int(self.cfg.get("camera_buff_num", 1)))
        fmt = image.Format.FMT_RGB888

        print(f"[CAM] single try {w}x{h} rgb888 buff={buff_num}")
        try:
            cam = camera.Camera(w, h, fmt, buff_num=buff_num)
            print("[CAM] OK")
            return cam
        except Exception as exc:
            print(f"[CAM] FAIL: {exc}")
            if self.cfg.get("camera_optional", True):
                print("[CAM] BLE-only mode (full power-off 10s then restart for camera)")
                return None
            raise RuntimeError(f"camera open failed: {exc}") from exc

    def _init_board_link(self) -> None:
        if self._board_link is not None:
            return
        mode = (self.cfg.get("link_mode") or "ble").strip().lower()
        if mode == "uart" and not self.cfg.get("uart_enable", False):
            print("[LINK] uart disabled")
            return
        try:
            link = create_board_link(self.cfg)
            self._board_link = link
            self._link_name = mode
            print(f"[LINK] ready mode={mode}")
            self._show_msg(f"Link OK {mode}")
        except Exception as e:
            print(f"[LINK] init failed: {e}")
            self._board_link = None
            self._link_name = "fail"

    def _handle_board_command(self, cmd: str) -> None:
        link = self._board_link
        if link is None:
            print(f"[LINK] drop cmd (no link): {cmd}")
            return

        base = cmd.split(":", 1)[0].strip().upper()
        cmd = base
        if cmd == CMD_PING:
            link.send(format_rsp(RSP_PONG))
            return

        if cmd == CMD_WAKE:
            print("[LINK] WAKE — voice session started")
            self._show_msg("Voice wake — say command")
            link.send(format_rsp(RSP_PONG, "wake_ok"))
            return

        if cmd == CMD_PHOTO:
            print("[LINK] PHOTO command received")
            if self._cam is None:
                link.send(format_rsp(RSP_PHOTO_FAIL, "cam_unavailable"))
                self._show_msg("Camera offline - reboot Maix")
                return
            self._show_msg("Taking photo...")
            link.send(format_rsp(RSP_PHOTO_START))
            ok, result, img = save_photo(
                self._cam,
                self.cfg.get("photo_dir", "/root/photos"),
                self.seq,
            )
            if not ok:
                link.send(format_rsp(RSP_PHOTO_FAIL, result))
                self._show_msg(f"Capture fail: {result}")
                return
            self.seq += 1
            self.last_path = result
            self.last_img = img
            self.state = STATE_UPLOAD_WAIT
            self.upload_wait_start = time.monotonic()
            link.send(format_rsp(RSP_PHOTO_SAVED, self.last_path))
            link.send(format_rsp(RSP_UPLOAD_WAIT))
            return

        if cmd == CMD_CONFIRM:
            if self.state != STATE_UPLOAD_WAIT or not self.last_path:
                link.send(format_rsp(RSP_ERR, "not_in_upload_wait"))
                return
            online, _ip = is_online()
            if not online:
                link.send(format_rsp(RSP_OFFLINE))
                self._show_msg("Offline, saved locally")
                return
            ok, reason = upload_file(self.cfg, self.last_path)
            if ok:
                label = f": {reason}" if reason else ""
                self._show_msg(f"Upload OK{label}")
                link.send(format_rsp(RSP_UPLOAD_OK, reason))
                self._goto_preview()
            else:
                self._show_msg(f"Upload fail: {reason}")
                link.send(format_rsp(RSP_UPLOAD_FAIL, reason))
            return

        if cmd == CMD_CANCEL:
            if self.state == STATE_UPLOAD_WAIT:
                self._cancel_upload()
            link.send(format_rsp(RSP_UPLOAD_SKIP))
            return

        link.send(format_rsp(RSP_ERR, f"unknown:{cmd}"))

    def _enter_gallery(self, return_state: str = STATE_UPLOAD_WAIT) -> None:
        photo_dir = self.cfg.get("photo_dir", "/root/photos")
        self._gallery_paths = list_photo_files(photo_dir)
        self._gallery_return_state = return_state
        if not self._gallery_paths:
            self._show_msg("No photos in folder")
            return
        start = 0
        if self.last_path and self.last_path in self._gallery_paths:
            start = self._gallery_paths.index(self.last_path)
        self._gallery_index = start
        self._gallery_cached_path = ""
        self._gallery_cached_img = None
        self._layout_gallery_buttons()
        self.state = STATE_GALLERY

    def _enter_qr(self) -> None:
        self.state = STATE_QR
        self._pressed_btn = ""

    def _leave_gallery(self) -> None:
        self.state = self._gallery_return_state
        if self.state == STATE_UPLOAD_WAIT:
            self.upload_wait_start = time.monotonic()

    def _select_gallery_photo(self) -> None:
        if not self._gallery_paths:
            self._leave_gallery()
            return
        path = self._gallery_paths[self._gallery_index]
        try:
            from maix import image

            self.last_path = path
            self.last_img = image.load(path)
            self._leave_gallery()
        except Exception as e:
            self._show_msg(f"Load fail: {e}")

    def _gallery_view_rect(self) -> Rect:
        return self._content_rect()

    def _gallery_prev(self) -> None:
        if self._gallery_index > 0:
            self._gallery_index -= 1
            self._gallery_cached_path = ""

    def _gallery_next(self) -> None:
        if self._gallery_index < len(self._gallery_paths) - 1:
            self._gallery_index += 1
            self._gallery_cached_path = ""

    def _hit_gallery_btn(self, x: int, y: int) -> str:
        if self._btn_prev.contains(x, y):
            return "prev"
        if self._btn_next.contains(x, y):
            return "next"
        if self._btn_back.contains(x, y):
            return "back"
        return ""

    def _on_touch_start(self, x: int, y: int) -> None:
        self._touch_active = True
        self._touch_x0 = x
        self._touch_y0 = y
        self._touch_x1 = x
        self._touch_y1 = y
        self._touch_moved = False
        if self.state == STATE_PREVIEW and self._btn_qr.contains(x, y):
            self._pressed_btn = "qr"
        elif self.state == STATE_PREVIEW and self._btn_gallery.contains(x, y):
            self._pressed_btn = "gallery"
        elif self.state == STATE_UPLOAD_WAIT and self._btn_gallery.contains(x, y):
            self._pressed_btn = "gallery"
        elif self.state == STATE_QR:
            back_rect = Rect(16, self._dh - FOOTER_H + 8, self._dw - 32, FOOTER_H - 16)
            self._pressed_btn = "back" if back_rect.contains(x, y) else ""
        elif self.state == STATE_GALLERY:
            self._pressed_btn = self._hit_gallery_btn(x, y)
        else:
            self._pressed_btn = ""

    def _on_touch_move(self, x: int, y: int) -> None:
        self._touch_x1 = x
        self._touch_y1 = y
        if abs(x - self._touch_x0) > TAP_MOVE_MAX or abs(y - self._touch_y0) > TAP_MOVE_MAX:
            self._touch_moved = True

    def _on_touch_end(self, x: int, y: int) -> None:
        self._touch_x1 = x
        self._touch_y1 = y
        btn = self._pressed_btn
        self._pressed_btn = ""
        self._touch_active = False

        if self.state == STATE_PREVIEW and btn == "qr" and not self._touch_moved:
            self._enter_qr()
            return
        if self.state in (STATE_PREVIEW, STATE_UPLOAD_WAIT) and btn == "gallery" and not self._touch_moved:
            self._enter_gallery(self.state)
            return
        if self.state == STATE_QR and btn == "back" and not self._touch_moved:
            self.state = STATE_PREVIEW
            return

        if self.state != STATE_GALLERY:
            return

        if btn == "prev" and not self._touch_moved:
            self._gallery_prev()
            return
        if btn == "next" and not self._touch_moved:
            self._gallery_next()
            return
        if btn == "back" and not self._touch_moved:
            self._leave_gallery()
            return

        if self._footer_rect().contains(self._touch_x0, self._touch_y0):
            return

        dy = y - self._touch_y0
        dx = x - self._touch_x0
        if abs(dy) >= SWIPE_MIN and abs(dy) >= abs(dx):
            if dy <= -SWIPE_MIN:
                self._gallery_next()
            else:
                self._gallery_prev()
            return

        view = self._gallery_view_rect()
        if view.contains(self._touch_x0, self._touch_y0) and not self._touch_moved:
            mid = view.x + view.w // 2
            if x < mid:
                self._gallery_prev()
            else:
                self._gallery_next()

    def _poll_touch(self) -> None:
        if self._ts is None:
            return
        try:
            while self._ts.available():
                x, y, pressed = self._ts.read0()
                self._handle_touch_event(int(x), int(y), bool(pressed))
        except Exception:
            x, y, pressed = self._ts.read()
            self._handle_touch_event(int(x), int(y), bool(pressed))

    def _handle_touch_event(self, x: int, y: int, pressed: bool) -> None:
        if pressed:
            if not self._was_pressed:
                self._on_touch_start(x, y)
            else:
                self._on_touch_move(x, y)
            self._was_pressed = True
            return
        if self._was_pressed:
            self._on_touch_end(x, y)
            self._was_pressed = False

    def _draw_gallery(self) -> None:
        from maix import image

        canvas = image.Image(self._dw, self._dh, image.Format.FMT_RGB888)
        canvas.draw_rect(0, 0, self._dw, self._dh, rgb(Theme.BG), thickness=-1)

        total = len(self._gallery_paths)
        idx = self._gallery_index
        name = os.path.basename(self._gallery_paths[idx]) if total else "-"
        title = f"Library {idx + 1}/{total}" if total else "Library"
        self._draw_header_bar(canvas, title, name[:28])

        view = self._gallery_view_rect()
        path = self._gallery_paths[idx]
        if self._gallery_cached_path != path or self._gallery_cached_img is None:
            try:
                self._gallery_cached_img = image.load(path)
                self._gallery_cached_path = path
            except Exception as e:
                draw_hint(canvas, [f"Load error: {e}"], y0=view.y + 20, color=rgb(Theme.DANGER))
                self._gallery_cached_img = None
        if self._gallery_cached_img is not None:
            fit_draw_photo(canvas, self._gallery_cached_img, view.x, view.y, view.w, view.h)

        self._draw_gallery_footer(canvas)
        self._disp.show(canvas)

    def _draw_msg_screen(self) -> None:
        from maix import image

        canvas = image.Image(self._dw, self._dh, image.Format.FMT_RGB888)
        canvas.draw_rect(0, 0, self._dw, self._dh, rgb(Theme.BG), thickness=-1)
        self._draw_header_bar(canvas, "V-Eye", "Status")
        draw_hint(canvas, [self.msg], y0=self._dh // 2 - 10, color=rgb(Theme.PRIMARY))
        self._disp.show(canvas)

    def run(self) -> None:
        from maix import camera, display, key, touchscreen

        print("=" * 42)
        print(f"  V-Eye Maxicam {VERSION}")
        print("=" * 42)

        w = int(self.cfg.get("camera_width", 640))
        h = int(self.cfg.get("camera_height", 480))
        print(f"[CAM] config {w}x{h}")
        self._cam = self._open_camera()
        if self._cam is None:
            self._show_msg("No camera - BLE voice OK")
        self._disp = display.Display()
        self._ts = touchscreen.TouchScreen()
        self._dw = self._disp.width()
        self._dh = self._disp.height()
        self._layout_upload_buttons()
        self._key_obj = key.Key(self._on_key)
        self._init_board_link()

        online, ip = is_online()
        print(f"[NET] {'online' if online else 'offline'}" + (f" {ip}" if ip else ""))
        print(f"[DEV] id={(self.cfg.get('device_id') or 'unset')}")
        print(f"[UP]  url={resolve_upload_url(self.cfg) or 'unset'}")

        self._link_hb_last = time.monotonic()

        while not should_exit():
            if self._board_link is not None:
                link = self._board_link
                if getattr(link, "is_command_ready", False) and not self._link_ble_announced:
                    link.send(format_rsp(RSP_READY))
                    print("[LINK] BLE up -> READY sent to U5")
                    self._show_msg("BLE ready - voice OK")
                    self._link_ble_announced = True
                elif getattr(link, "is_command_ready", False):
                    if time.monotonic() - self._link_hb_last >= 45.0:
                        link.send(format_rsp(RSP_READY), quiet=True)
                        self._link_hb_last = time.monotonic()

                cmd = link.poll_command(timeout_ms=20)
                while cmd:
                    self._handle_board_command(cmd)
                    cmd = link.poll_command(timeout_ms=0)

            self._poll_touch()

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
                if self._cam is not None:
                    frame = self._cam.read()
                    if frame:
                        self._draw_preview_screen(frame)
                else:
                    self._draw_msg_screen()
            elif self.state == STATE_UPLOAD_WAIT and self.last_img is not None:
                self._draw_photo_screen(
                    self.last_img,
                    "Photo Saved",
                    os.path.basename(self.last_path),
                    ["OK short: upload to cloud", "OK long: cancel"],
                )
            elif self.state == STATE_GALLERY:
                self._draw_gallery()
                sleep_ms(16)
                continue
            elif self.state == STATE_QR:
                self._draw_qr_screen()
                sleep_ms(16)
                continue
            elif self.state == STATE_SHOW_MSG:
                self._draw_msg_screen()

            sleep_ms(int(self.cfg.get("poll_interval_ms", 50)))

        print("[EXIT] done")


if __name__ == "__main__":
    try:
        KeyPhotoApp(load_config()).run()
    except KeyboardInterrupt:
        print("\n[EXIT] interrupted")
