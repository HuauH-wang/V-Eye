#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BLE worker subprocess — run asyncio on main thread (fixes Maix RISC-V dbus_fast issue).

Stdout: one line per message to parent (commands from ESP32, or STATUS:*)
Stdin:  lines to write to ESP32 (responses from Maix app)
"""

from __future__ import annotations

import argparse
import asyncio
import os
import sys

# Maix RISC-V: bleak>=0.22 needs dbus-fast (connect often fails).
# Use: pip3 install 'bleak==0.21.1' dbus-python
os.environ.setdefault("BLEAK_DBUS_FAST", "0")

NUS_TX_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
NUS_RX_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"


def _emit(line: str) -> None:
    sys.stdout.write(line + "\n")
    sys.stdout.flush()


def _parse_lines(buf: bytearray, chunk: bytes) -> list[str]:
    buf.extend(chunk)
    out: list[str] = []
    while True:
        nl = buf.find(b"\n")
        if nl < 0:
            cr = buf.find(b"\r")
            if cr >= 0:
                out.append(bytes(buf[:cr]).decode("utf-8", errors="ignore").strip())
                del buf[: cr + 1]
            else:
                break
        else:
            out.append(bytes(buf[:nl]).decode("utf-8", errors="ignore").strip())
            del buf[: nl + 1]
    return [x for x in out if x]


async def _scan_name(name: str, timeout: float) -> str | None:
    from bleak import BleakScanner

    _emit(f"STATUS:scan name={name} timeout={timeout:.0f}")
    devices = await BleakScanner.discover(timeout=timeout)
    target = name.lower()
    for dev in devices:
        dev_name = (dev.name or "").strip()
        _emit(f"STATUS:seen {dev.address} {dev_name!r}")
        if dev_name.lower() == target:
            return dev.address.upper()
    return None


async def _bridge(address: str, scan_name: str, scan_timeout: float) -> None:
    from bleak import BleakClient

    addr = address.strip().upper()
    if not addr and scan_name:
        addr = await _scan_name(scan_name, scan_timeout) or ""
    if not addr:
        _emit("STATUS:error:device_not_found")
        return

    _emit(f"STATUS:connecting {addr}")
    rx_buf = bytearray()

    def on_notify(_handle: int, data: bytearray) -> None:
        for line in _parse_lines(rx_buf, bytes(data)):
            _emit(line)

    async with BleakClient(addr, timeout=20.0) as client:
        await client.start_notify(NUS_TX_UUID, on_notify)
        _emit(f"STATUS:connected {addr}")

        loop = asyncio.get_running_loop()
        while True:
            line = await loop.run_in_executor(None, sys.stdin.readline)
            if not line:
                break
            text = line.strip()
            if not text:
                continue
            payload = (text + "\n").encode("utf-8")
            await client.write_gatt_char(NUS_RX_UUID, payload, response=False)

    _emit("STATUS:disconnected")


def main() -> int:
    parser = argparse.ArgumentParser(description="Maix BLE worker for ESP32 NUS bridge")
    parser.add_argument("--address", default="", help="ESP32 BLE MAC, e.g. 44:BD:8D:27:96:A6")
    parser.add_argument("--name", default="U5-MAIX-BRIDGE", help="Scan target name if address empty")
    parser.add_argument("--scan-timeout", type=float, default=10.0)
    args = parser.parse_args()

    try:
        asyncio.run(
            _bridge(
                address=args.address,
                scan_name=args.name,
                scan_timeout=max(3.0, args.scan_timeout),
            )
        )
    except KeyboardInterrupt:
        _emit("STATUS:stopped")
        return 0
    except Exception as e:
        _emit(f"STATUS:error:{e}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
