#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BLE command link via system BlueZ (bluetoothctl + busctl, no pip bleak)."""

from __future__ import annotations

import json
import os
import queue
import shutil
import subprocess
import sys
import threading
import time

from protocol import parse_line

_APP_DIR = os.path.dirname(os.path.abspath(__file__))
_WORKER_SCRIPT = os.path.join(_APP_DIR, "ble_bluez_worker.py")
_MAC_CACHE_FILE = os.path.join(_APP_DIR, "ble_esp32_mac.txt")
_MAC_HINT_FILE = os.path.join(_APP_DIR, "ble_esp32_hint.json")

_KNOWN_CMD_PREFIXES = (
    "PING",
    "WAKE",
    "PHOTO",
    "CONFIRM",
    "CANCEL",
    "READY",
    "PONG",
    "ERR",
    "BLE",
    "PHOTO_",
    "UPLOAD_",
    "OFFLINE",
)


def _is_protocol_line(line: str) -> bool:
    up = line.strip().upper()
    if not up or up.startswith("STATUS:"):
        return False
    if "TRACEBACK" in up or "DBUS" in up or "ERROR" in up:
        return False
    return any(up.startswith(p) for p in _KNOWN_CMD_PREFIXES)


def check_bluez_tools() -> None:
    if shutil.which("bluetoothctl") is None:
        print("\n[BLE] ERROR: missing bluetoothctl\n[BLE] Run: bluetoothctl power on\n")
        raise RuntimeError("bluetoothctl missing")
    dbus_tools = ("busctl", "gdbus", "dbus-monitor", "dbus-send")
    found = [t for t in dbus_tools if shutil.which(t)]
    if not found:
        print(
            "\n[BLE] ERROR: no D-Bus CLI tool found.\n"
            "[BLE] Need one of: busctl, gdbus, dbus-monitor, dbus-send\n"
            "[BLE] Check on Maix: which gdbus; which dbus-send\n"
        )
        raise RuntimeError("dbus tools missing")
    print(f"[BLE] using D-Bus tool: {found[0]}")


def normalize_ble_address(raw: str) -> str:
    s = (raw or "").strip().upper().replace("-", ":")
    if len(s) == 12 and ":" not in s:
        s = ":".join(s[i : i + 2] for i in range(0, 12, 2))
    return s


def report_esp32_mac(address: str, device_name: str = "") -> None:
    mac = normalize_ble_address(address)
    if not mac:
        return
    banner = [
        "",
        "=" * 44,
        "  ESP32 BLE MAC (copy to config.json)",
        f"  ble_address: {mac}",
        f"  name: {device_name or '-'}",
        "=" * 44,
        "",
    ]
    for line in banner:
        print(line)
    try:
        with open(_MAC_CACHE_FILE, "w", encoding="utf-8") as fp:
            fp.write(mac + "\n")
    except OSError:
        pass


class BoardBle:
    """Spawn ble_bluez_worker.py (system BlueZ, no bleak)."""

    def __init__(
        self,
        device_name: str = "U5-MAIX-BRIDGE",
        device_address: str = "",
        scan_timeout_sec: float = 10.0,
        reconnect_sec: float = 3.0,
    ):
        self._device_name = device_name.strip()
        self._device_address = normalize_ble_address(device_address)
        self._scan_timeout_sec = max(3.0, float(scan_timeout_sec))
        self._reconnect_sec = max(1.0, float(reconnect_sec))
        self._cmd_q: queue.Queue[str] = queue.Queue()
        self._send_q: queue.Queue[str] = queue.Queue()
        self._stop = threading.Event()
        self._connected = threading.Event()
        self._notify_ready = threading.Event()
        self._ready = False
        self._proc: subprocess.Popen | None = None
        self._thread: threading.Thread | None = None
        self._writer_thread: threading.Thread | None = None

    @property
    def is_connected(self) -> bool:
        return self._connected.is_set()

    @property
    def is_command_ready(self) -> bool:
        return self._connected.is_set() and self._notify_ready.is_set()

    def open(self) -> None:
        check_bluez_tools()
        if not self._device_address:
            raise RuntimeError("ble_address required (set ESP32 MAC in config.json)")
        if not os.path.isfile(_WORKER_SCRIPT):
            raise RuntimeError(f"ble_bluez_worker.py missing: {_WORKER_SCRIPT}")

        self._ready = True
        self._stop.clear()
        self._thread = threading.Thread(target=self._supervisor_loop, daemon=True, name="board-ble")
        self._thread.start()
        print(f"[BLE] BlueZ connect MAC={self._device_address} (no bleak)")

    def close(self) -> None:
        self._stop.set()
        self._terminate_worker()
        if self._thread is not None:
            self._thread.join(timeout=2.0)

    def send(self, text: str, quiet: bool = False) -> None:
        if not self._ready:
            return
        if not text.endswith("\n"):
            text += "\n"
        self._send_q.put(text.rstrip("\n"))
        if self._connected.is_set() and not quiet:
            print(f"[BLE->] {text.rstrip()}")

    def poll_command(self, timeout_ms: int = 50) -> str | None:
        try:
            return self._cmd_q.get(timeout=max(0.001, timeout_ms / 1000.0))
        except queue.Empty:
            return None

    def _worker_cmd(self) -> list[str]:
        py = sys.executable or "python3"
        cmd = [
            py,
            _WORKER_SCRIPT,
            "--address",
            self._device_address,
            "--name",
            self._device_name,
            "--scan-timeout",
            str(self._scan_timeout_sec),
        ]
        return cmd

    def _terminate_worker(self) -> None:
        proc = self._proc
        self._proc = None
        self._connected.clear()
        self._notify_ready.clear()
        if proc is None:
            return
        try:
            if proc.stdin:
                proc.stdin.close()
        except OSError:
            pass
        try:
            proc.terminate()
            proc.wait(timeout=2.0)
        except Exception:
            try:
                proc.kill()
            except Exception:
                pass

    def _supervisor_loop(self) -> None:
        while not self._stop.is_set():
            if not self._start_worker():
                time.sleep(self._reconnect_sec)
                continue

            proc = self._proc
            if proc is None:
                time.sleep(self._reconnect_sec)
                continue

            assert proc.stdout is not None
            for raw in proc.stdout:
                if self._stop.is_set():
                    break
                line = raw.decode("utf-8", errors="ignore").strip()
                if not line:
                    continue
                if line.startswith("STATUS:"):
                    self._handle_status(line[7:])
                    continue
                if not _is_protocol_line(line):
                    continue
                cmd = parse_line(line.encode("utf-8"))
                if cmd:
                    print(f"[BLE<-] {cmd}")
                    self._cmd_q.put(cmd)

            self._terminate_worker()
            if self._stop.is_set():
                break
            print(f"[BLE] reconnect in {self._reconnect_sec:.0f}s ...")
            time.sleep(self._reconnect_sec)

    def _handle_status(self, status: str) -> None:
        print(f"[BLE] {status}")
        if status.startswith("connected "):
            self._connected.set()
        elif status.startswith("worker_v"):
            pass
        elif status.startswith("notify_btctl"):
            pass
        elif status.startswith("notify_ready"):
            self._notify_ready.set()
            print("[BLE] notify listener ready")
        elif status.startswith("poll_on"):
            print("[BLE] POLL pull active (250ms)")
        elif status.startswith("write_ok"):
            if "READY" not in status:
                print(f"[BLE] {status}")
        elif status.startswith("pull_rx") or status.startswith("pull "):
            if "PHOTO" in status or "WAKE" in status:
                print(f"[BLE] {status}")
        elif status.startswith("btctl_raw:"):
            print(f"[BLE] {status[10:120]}")
        elif status.startswith("disconnected") or status.startswith("error:"):
            self._connected.clear()
            self._notify_ready.clear()

    def _start_worker(self) -> bool:
        self._terminate_worker()
        try:
            proc = subprocess.Popen(
                self._worker_cmd(),
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                cwd=_APP_DIR,
                bufsize=0,
            )
        except OSError as e:
            print(f"[BLE] worker start fail: {e}")
            return False

        self._proc = proc
        threading.Thread(
            target=self._stderr_loop,
            args=(proc,),
            daemon=True,
            name="board-ble-err",
        ).start()
        self._writer_thread = threading.Thread(target=self._writer_loop, daemon=True, name="board-ble-wr")
        self._writer_thread.start()
        return True

    def _stderr_loop(self, proc: subprocess.Popen) -> None:
        if proc.stderr is None:
            return
        for raw in proc.stderr:
            line = raw.decode("utf-8", errors="ignore").strip()
            if line:
                print(f"[BLE-ERR] {line[:160]}")

    def _writer_loop(self) -> None:
        while not self._stop.is_set():
            proc = self._proc
            if proc is None or proc.stdin is None:
                time.sleep(0.05)
                continue
            try:
                line = self._send_q.get(timeout=0.2)
            except queue.Empty:
                continue
            if proc.poll() is not None:
                return
            try:
                proc.stdin.write((line + "\n").encode("utf-8"))
                proc.stdin.flush()
            except OSError as e:
                print(f"[BLE] worker write fail: {e}")
                return
