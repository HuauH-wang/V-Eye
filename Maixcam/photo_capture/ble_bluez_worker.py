#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BLE worker using system BlueZ (no pip bleak).
Uses bluetoothctl + one of: busctl / gdbus / dbus-monitor / dbus-send
"""

from __future__ import annotations

import argparse
import os
import re
import select
import shutil
import subprocess
import sys
import threading
import time

from protocol import is_valid_board_line

NUS_RX_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
NUS_TX_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

_DBUS_BACKEND = ""


def emit(line: str) -> None:
    sys.stdout.write(line + "\n")
    sys.stdout.flush()


def mac_to_dev_path(mac: str) -> str:
    return "/org/bluez/hci0/dev_" + mac.replace(":", "_").upper()


def run_cmd(args: list[str], timeout: float = 25.0) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout)


def detect_dbus_backend() -> str:
    global _DBUS_BACKEND
    if _DBUS_BACKEND:
        return _DBUS_BACKEND
    if shutil.which("bluetoothctl") is None:
        raise RuntimeError("missing bluetoothctl")
    for name in ("busctl", "gdbus", "dbus-monitor", "dbus-send"):
        if shutil.which(name):
            _DBUS_BACKEND = name
            emit(f"STATUS:dbus_backend {name}")
            return name
    raise RuntimeError("need busctl, gdbus, dbus-monitor, or dbus-send")


def bt_power_on() -> None:
    run_cmd(["bluetoothctl", "power", "on"], timeout=8)


def bt_devices_text() -> str:
    proc = run_cmd(["bluetoothctl", "devices"], timeout=8)
    return f"{proc.stdout}\n{proc.stderr}"


def device_in_cache(mac: str, name: str = "") -> bool:
    text = bt_devices_text()
    if mac.upper() in text.upper():
        return True
    if name:
        for line in text.splitlines():
            if name.lower() in line.lower():
                return True
    return False


def bt_scan(mac: str, name: str, timeout: float = 12.0) -> bool:
    bt_power_on()
    if device_in_cache(mac, name):
        emit("STATUS:device_cached")
        return True

    emit(f"STATUS:scan_start timeout={timeout:.0f}s name={name!r}")
    run_cmd(["bluetoothctl", "--timeout", str(max(5, int(timeout))), "scan", "on"], timeout=timeout + 8)

    if device_in_cache(mac, name):
        emit(f"STATUS:scan_found {mac}")
        run_cmd(["bluetoothctl", "scan", "off"], timeout=5)
        return True

    proc = subprocess.Popen(
        ["bluetoothctl"],
        stdin=subprocess.PIPE,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        text=True,
    )
    assert proc.stdin is not None
    proc.stdin.write("power on\nscan on\n")
    proc.stdin.flush()

    t0 = time.time()
    found = False
    while time.time() - t0 < timeout:
        if device_in_cache(mac, name):
            found = True
            break
        time.sleep(0.8)

    try:
        proc.stdin.write("scan off\nquit\n")
        proc.stdin.flush()
        proc.stdin.close()
        proc.wait(timeout=4)
    except Exception:
        proc.kill()

    if found:
        emit(f"STATUS:scan_found {mac}")
        return True

    emit(f"STATUS:error:scan_not_found (is ESP32 powered & advertising?)")
    emit(f"STATUS:debug_devices:{bt_devices_text()[-300:]}")
    return False


def bt_connect(mac: str, name: str = "", scan_timeout: float = 12.0) -> bool:
    if not bt_scan(mac, name, scan_timeout):
        return False

    run_cmd(["bluetoothctl", "trust", mac], timeout=8)
    emit(f"STATUS:connecting {mac}")
    proc = run_cmd(["bluetoothctl", "connect", mac], timeout=25)
    text = f"{proc.stdout}\n{proc.stderr}"
    if proc.returncode == 0 or "Connection successful" in text or "Already connected" in text:
        time.sleep(1.0)
        return True
    emit(f"STATUS:error:connect_failed:{text.strip()[:160]}")
    return False


def bt_list_attributes(mac: str) -> str:
    script = f"connect {mac}\nmenu gatt\nlist-attributes\nquit\n"
    proc = subprocess.run(
        ["bluetoothctl"],
        input=script,
        capture_output=True,
        text=True,
        timeout=30,
    )
    return f"{proc.stdout}\n{proc.stderr}"


def find_char_paths_gdbus(mac: str) -> tuple[str, str] | None:
    if shutil.which("gdbus") is None:
        return None
    dev = mac_to_dev_path(mac)
    proc = run_cmd(
        [
            "gdbus",
            "call",
            "--system",
            "-d",
            "org.bluez",
            "-o",
            "/",
            "-m",
            "org.freedesktop.DBus.ObjectManager.GetManagedObjects",
        ],
        timeout=20,
    )
    if proc.returncode != 0:
        return None
    blob = proc.stdout
    rx_path = tx_path = None
    for uuid, kind in ((NUS_RX_UUID, "rx"), (NUS_TX_UUID, "tx")):
        m = re.search(rf"('{re.escape(dev)}/service[^']+/char[^']+')[^']*'{uuid}'", blob, re.I)
        if not m:
            m = re.search(rf"({re.escape(dev)}/service[^/]+/char[^,\s]+)[^']*'{uuid}'", blob, re.I)
        if m:
            path = m.group(1).strip("'")
            if kind == "rx":
                rx_path = path
            else:
                tx_path = path
    if rx_path and tx_path:
        return rx_path, tx_path
    return None


def find_char_paths(mac: str) -> tuple[str, str] | None:
    text = bt_list_attributes(mac)
    rx_path = tx_path = None

    uuid_re = re.compile(
        r"(/org/bluez/hci\d+/dev_[0-9A-F_]+/service[0-9a-f]+/char[0-9a-f]+)\s+"
        r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
        re.I,
    )
    for path, uuid in uuid_re.findall(text):
        u = uuid.lower()
        if u == NUS_RX_UUID:
            rx_path = path
        elif u == NUS_TX_UUID:
            tx_path = path

    if not (rx_path and tx_path):
        alt = find_char_paths_gdbus(mac)
        if alt:
            rx_path, tx_path = alt

    if not (rx_path and tx_path):
        for line in text.splitlines():
            low = line.lower()
            m = re.search(r"(/org/bluez/hci\d+/dev_[0-9a-f_]+/service[0-9a-f]+/char[0-9a-f]+)", line, re.I)
            if not m:
                continue
            path = m.group(1)
            if NUS_RX_UUID in low:
                rx_path = path
            if NUS_TX_UUID in low:
                tx_path = path

    if rx_path and tx_path:
        emit(f"STATUS:chars rx={rx_path} tx={tx_path}")
        return rx_path, tx_path

    emit("STATUS:error:chars_not_found")
    emit(f"STATUS:debug:{text[-500:]}")
    return None


def find_char_paths_with_retry(mac: str, retries: int = 6, delay: float = 1.5) -> tuple[str, str] | None:
    for attempt in range(max(1, retries)):
        if attempt > 0:
            emit(f"STATUS:gatt_wait retry={attempt}")
            time.sleep(delay)
        rx_path = tx_path = None
        text = bt_list_attributes(mac)
        uuid_re = re.compile(
            r"(/org/bluez/hci\d+/dev_[0-9A-F_]+/service[0-9a-f]+/char[0-9a-f]+)\s+"
            r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
            re.I,
        )
        for path, uuid in uuid_re.findall(text):
            u = uuid.lower()
            if u == NUS_RX_UUID:
                rx_path = path
            elif u == NUS_TX_UUID:
                tx_path = path
        if not (rx_path and tx_path):
            alt = find_char_paths_gdbus(mac)
            if alt:
                rx_path, tx_path = alt
        if rx_path and tx_path:
            emit(f"STATUS:chars rx={rx_path} uuid={NUS_RX_UUID}")
            emit(f"STATUS:chars tx={tx_path} uuid={NUS_TX_UUID}")
            return rx_path, tx_path
    emit("STATUS:error:chars_not_found")
    return None


def gatt_write(path: str, data: bytes, quiet: bool = False, retries: int = 4) -> bool:
    backend = detect_dbus_backend()
    last_err = ""
    for attempt in range(max(1, retries)):
        if backend == "busctl":
            args = [
                "busctl", "call", "org.bluez", path,
                "org.bluez.GattCharacteristic1", "WriteValue",
                "aya{sv}", str(len(data)), *[str(b) for b in data], "0",
            ]
            proc = run_cmd(args, timeout=10)
        elif backend == "gdbus":
            arr = "[" + ", ".join(f"0x{b:02x}" for b in data) + "]" if data else "[]"
            proc = run_cmd(
                [
                    "gdbus", "call", "--system", "-d", "org.bluez", "-o", path,
                    "-m", "org.bluez.GattCharacteristic1.WriteValue", arr, "{}",
                ],
                timeout=10,
            )
        else:
            byte_list = ",".join(str(b) for b in data)
            proc = run_cmd(
                [
                    "dbus-send", "--system", "--print-reply",
                    "--dest=org.bluez", path,
                    "org.bluez.GattCharacteristic1.WriteValue",
                    f"array:byte:{byte_list}", "dict:string:string:",
                ],
                timeout=10,
            )
        if proc.returncode == 0:
            preview = data.decode("utf-8", errors="ignore").strip().replace("\n", "\\n")
            if not quiet:
                emit(f"STATUS:write_ok {preview[:32]}")
            return True
        last_err = (proc.stderr or proc.stdout or "").strip()[:120]
        if "InProgress" in last_err or "In Progress" in last_err:
            time.sleep(0.06 * (attempt + 1))
            continue
        break
    if not quiet:
        emit(f"STATUS:error:write:{last_err}")
    return False


def try_acquire_notify_fd(tx_path: str) -> tuple[int, int] | None:
    try:
        import dbus  # type: ignore
    except ImportError:
        emit("STATUS:dbus_py_missing")
        return None
    try:
        bus = dbus.SystemBus()
        obj = bus.get_object("org.bluez", tx_path)
        iface = dbus.Interface(obj, "org.bluez.GattCharacteristic1")
        fd_obj, mtu = iface.AcquireNotify({}, dbus_interface="org.bluez.GattCharacteristic1")
        fd = int(fd_obj)
        emit(f"STATUS:acquire_notify fd={fd} mtu={int(mtu)}")
        return fd, int(mtu)
    except Exception as exc:
        emit(f"STATUS:acquire_notify_fail:{str(exc)[:120]}")
        return None


class FdNotifyReader:
    def __init__(self, fd: int, mtu: int, on_bytes):
        self._fd = fd
        self._mtu = max(20, mtu)
        self._on_bytes = on_bytes
        self._stop = threading.Event()
        self._ready = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self._thread = threading.Thread(target=self._run, daemon=True, name="ble-fd-notify")
        self._thread.start()

    def wait_ready(self, timeout: float = 5.0) -> bool:
        return self._ready.wait(timeout)

    def stop(self) -> None:
        self._stop.set()
        try:
            os.close(self._fd)
        except OSError:
            pass

    def _run(self) -> None:
        self._ready.set()
        emit("STATUS:notify_ready")
        while not self._stop.is_set():
            try:
                ready, _, _ = select.select([self._fd], [], [], 0.3)
            except (OSError, ValueError):
                break
            if not ready:
                continue
            try:
                data = os.read(self._fd, self._mtu)
            except OSError:
                break
            if data:
                emit(f"STATUS:notify_rx {len(data)}B")
                self._on_bytes(data)


def gatt_start_notify(path: str) -> bool:
    backend = detect_dbus_backend()
    if backend == "busctl":
        proc = run_cmd(
            ["busctl", "call", "org.bluez", path, "org.bluez.GattCharacteristic1", "StartNotify"],
            timeout=10,
        )
    elif backend == "gdbus":
        proc = run_cmd(
            [
                "gdbus", "call", "--system", "-d", "org.bluez", "-o", path,
                "-m", "org.bluez.GattCharacteristic1.StartNotify",
            ],
            timeout=10,
        )
    else:
        proc = run_cmd(
            [
                "dbus-send", "--system", "--print-reply",
                "--dest=org.bluez", path,
                "org.bluez.GattCharacteristic1.StartNotify",
            ],
            timeout=10,
        )
    return proc.returncode == 0


def parse_notify_bytes(chunk: str) -> bytes | None:
    if not chunk:
        return None

    m = re.search(r"\(\[byte\s(.*?)\],?\)", chunk, re.I | re.S)
    if m:
        nums = re.findall(r"0x[0-9a-fA-F]+|\d+", m.group(1))
        if nums:
            return bytes(int(n, 0) & 0xFF for n in nums)

    nums = re.findall(r"byte\s+(0x[0-9a-fA-F]+|\d+)", chunk, re.I)
    if nums:
        return bytes(int(n, 0) & 0xFF for n in nums)

    m = re.search(r"@ay\s*\[([^\]]*)\]", chunk, re.I)
    if m:
        nums = re.findall(r"0x[0-9a-fA-F]+|\d+", m.group(1))
        if nums:
            return bytes(int(n, 0) & 0xFF for n in nums)
    m = re.search(r"'Value'\s*:\s*<?@ay\s*\[([^\]]*)\]>", chunk, re.I)
    if m:
        nums = re.findall(r"0x[0-9a-fA-F]+|\d+", m.group(1))
        if nums:
            return bytes(int(n, 0) & 0xFF for n in nums)
    m = re.search(r"array\s*\[(\d+)\]\s*\[(.*?)\]", chunk, re.S | re.I)
    if m:
        nums = re.findall(r"0x[0-9a-fA-F]+|\d+", m.group(2))
        if nums:
            return bytes(int(n, 0) & 0xFF for n in nums)
    m = re.search(r"Value\s*=\s*ay\s*(\d+)\s*(.*)", chunk, re.I)
    if m:
        nums = re.findall(r"0x[0-9a-fA-F]+|\d+", m.group(2))
        if nums:
            return bytes(int(n, 0) & 0xFF for n in nums[: int(m.group(1))])
    m = re.search(r"\[((?:0x[0-9a-fA-F]+(?:,\s*)?)+)\]", chunk, re.I)
    if m:
        nums = re.findall(r"0x[0-9a-fA-F]+|\d+", m.group(1))
        if nums:
            return bytes(int(n, 0) & 0xFF for n in nums)
    if re.search(r"\(\[\],\)", chunk) or re.search(r"@ay\s*\[\s*\]", chunk, re.I):
        return b""
    return None


def gatt_get_cached_value(path: str) -> bytes | None:
    backend = detect_dbus_backend()
    if backend == "gdbus":
        proc = run_cmd(
            [
                "gdbus", "call", "--system", "-d", "org.bluez", "-o", path,
                "-m", "org.freedesktop.DBus.Properties.Get",
                "org.bluez.GattCharacteristic1", "Value",
            ],
            timeout=8,
        )
    elif backend == "busctl":
        proc = run_cmd(
            [
                "busctl", "get-property", "org.bluez", path,
                "org.bluez.GattCharacteristic1", "Value",
            ],
            timeout=8,
        )
    else:
        proc = run_cmd(
            [
                "dbus-send", "--system", "--print-reply",
                "--dest=org.bluez", path,
                "org.freedesktop.DBus.Properties.Get",
                "string:org.bluez.GattCharacteristic1", "string:Value",
            ],
            timeout=8,
        )
    if proc.returncode != 0:
        return None
    return parse_notify_bytes(proc.stdout)


def gatt_read(path: str) -> bytes | None:
    backend = detect_dbus_backend()
    if backend == "busctl":
        proc = run_cmd(
            [
                "busctl", "call", "org.bluez", path,
                "org.bluez.GattCharacteristic1", "ReadValue", "a{sv}", "0",
            ],
            timeout=8,
        )
    elif backend == "gdbus":
        proc = run_cmd(
            [
                "gdbus", "call", "--system", "-d", "org.bluez", "-o", path,
                "-m", "org.bluez.GattCharacteristic1.ReadValue", "{}",
            ],
            timeout=8,
        )
    else:
        proc = run_cmd(
            [
                "dbus-send", "--system", "--print-reply",
                "--dest=org.bluez", path,
                "org.bluez.GattCharacteristic1.ReadValue",
                "dict:string:string:",
            ],
            timeout=8,
        )
    if proc.returncode != 0:
        return None
    val = parse_notify_bytes(proc.stdout)
    if val is not None and len(val) > 0:
        return val
    return None


def bridge(mac: str, name: str = "U5-MAIX-BRIDGE", scan_timeout: float = 12.0) -> None:
    emit("STATUS:worker_v10_gdbus_read")
    detect_dbus_backend()
    if not bt_connect(mac, name, scan_timeout):
        return

    chars = find_char_paths_with_retry(mac)
    if not chars:
        return
    rx_path, tx_path = chars

    last_seen: dict[str, float] = {}
    rx_buf = bytearray()
    ready_evt = threading.Event()
    stop_poll = threading.Event()

    def emit_command(line: str, source: str) -> None:
        up = line.strip().upper()
        if not up or up == "POLL":
            return
        if not is_valid_board_line(up):
            return
        now = time.monotonic()
        prev = last_seen.get(up, 0.0)
        dedup_sec = 8.0 if up in ("PHOTO", "WAKE") else 3.0
        if (now - prev) < dedup_sec:
            return
        last_seen[up] = now
        emit(f"STATUS:{source} {up}")
        emit(up)

    def feed_rx(data: bytes, source: str) -> None:
        if not data:
            return
        rx_buf.extend(data)
        while True:
            nl = rx_buf.find(b"\n")
            if nl < 0:
                cr = rx_buf.find(b"\r")
                if cr >= 0:
                    line = bytes(rx_buf[:cr]).decode("utf-8", errors="ignore").strip()
                    del rx_buf[: cr + 1]
                else:
                    if rx_buf and source == "pull":
                        line = bytes(rx_buf).decode("utf-8", errors="ignore").strip()
                        rx_buf.clear()
                        if line:
                            emit_command(line, source)
                    break
                if not line:
                    continue
            else:
                line = bytes(rx_buf[:nl]).decode("utf-8", errors="ignore").strip()
                del rx_buf[: nl + 1]
            if line:
                emit_command(line, source)

    def poll_loop() -> None:
        cycles = 0
        empty_streak = 0
        debug_reads = 0
        while not stop_poll.is_set():
            gatt_write(rx_path, b"POLL\n", quiet=True, retries=2)
            time.sleep(0.12)
            val = gatt_read(tx_path)
            if val:
                empty_streak = 0
                emit(f"STATUS:pull_rx {len(val)}B {val[:32]!r}")
                feed_rx(val, "pull")
            else:
                empty_streak += 1
                if empty_streak == 20:
                    emit("STATUS:pull_empty (re-flash ESP32 v4 if persists)")
                    empty_streak = 0
                elif debug_reads < 2 and empty_streak in (1, 5):
                    backend = detect_dbus_backend()
                    if backend == "gdbus":
                        proc = run_cmd(
                            [
                                "gdbus", "call", "--system", "-d", "org.bluez", "-o", tx_path,
                                "-m", "org.bluez.GattCharacteristic1.ReadValue", "{}",
                            ],
                            timeout=8,
                        )
                        raw = (proc.stdout or proc.stderr or "").strip().replace("\n", " ")[:120]
                        emit(f"STATUS:read_debug rc={proc.returncode} {raw}")
                        debug_reads += 1
            cycles += 1
            if cycles >= 6 and not ready_evt.is_set():
                ready_evt.set()
                emit("STATUS:notify_ready")
                emit(f"STATUS:connected {mac}")
                emit("STATUS:poll_on")
            stop_poll.wait(0.35)

    threading.Thread(target=poll_loop, daemon=True, name="ble-poll").start()
    if not ready_evt.wait(timeout=25.0):
        emit("STATUS:error:poll_warmup_timeout")
        stop_poll.set()
        return

    try:
        while True:
            line = sys.stdin.readline()
            if not line:
                break
            text = line.strip()
            if not text:
                continue
            ok = False
            for _ in range(6):
                if gatt_write(rx_path, (text + "\n").encode("utf-8")):
                    ok = True
                    break
                time.sleep(0.08)
            if not ok:
                emit(f"STATUS:error:write_drop {text[:24]}")
    finally:
        stop_poll.set()
        run_cmd(["bluetoothctl", "disconnect", mac], timeout=8)
        emit("STATUS:disconnected")


def main() -> int:
    parser = argparse.ArgumentParser(description="Maix BLE worker (BlueZ, no bleak)")
    parser.add_argument("--address", required=True, help="ESP32 MAC")
    parser.add_argument("--name", default="U5-MAIX-BRIDGE", help="ESP32 BLE name for scan")
    parser.add_argument("--scan-timeout", type=float, default=12.0)
    args = parser.parse_args()
    mac = args.address.strip().upper()
    try:
        bridge(mac, args.name.strip(), max(5.0, float(args.scan_timeout)))
    except Exception as e:
        emit(f"STATUS:error:{e}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
