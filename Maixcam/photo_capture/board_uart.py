#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UART link to main board, non-blocking poll read."""

from __future__ import annotations

import sys

from protocol import parse_line

try:
    from uart_pins import open_uart
except ImportError:
    def open_uart(device: str, baudrate: int = 115200):
        from maix import pinmap, uart

        mapping = {
            "/dev/ttyS0": {"A16": "UART0_TX", "A17": "UART0_RX"},
            "/dev/ttyS1": {"A19": "UART1_TX", "A18": "UART1_RX"},
        }.get(device)
        if mapping:
            for pin, func in mapping.items():
                pinmap.set_pin_function(pin, func)
                print(f"[UART] pinmap {pin} -> {func}")
        dev = uart.UART(device, baudrate)
        print(f"[UART] opened {device} @ {baudrate}")
        return dev


class BoardUart:
    def __init__(self, device: str, baudrate: int = 115200):
        self._device = device
        self._baudrate = baudrate
        self._uart = None
        self._rx_buf = bytearray()
        self._rx_total = 0

    def open(self) -> None:
        try:
            from maix import uart  # noqa: F401 — runtime check
        except ImportError:
            print("[ERROR] Run on MaixCAM device", file=sys.stderr)
            raise SystemExit(1)

        self._uart = open_uart(self._device, self._baudrate)

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
            self._rx_total += len(chunk)

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

            cmd = parse_line(line)
            if cmd:
                print(f"[UART<-] {cmd} (rx_total={self._rx_total})")
                return cmd

        return None
