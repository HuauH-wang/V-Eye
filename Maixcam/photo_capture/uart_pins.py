#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MaixCAM UART pinmap + open helpers."""

from __future__ import annotations

# MaixCAM-Pro 排针 TX/RX 常用映射（须 pinmap 后 open）
UART_PROFILES: list[dict] = [
    {
        "device": "/dev/ttyS0",
        "label": "A16(TX)/A17(RX) UART0",
        "pins": {"A16": "UART0_TX", "A17": "UART0_RX"},
    },
    {
        "device": "/dev/ttyS1",
        "label": "A19(TX)/A18(RX) UART1",
        "pins": {"A19": "UART1_TX", "A18": "UART1_RX"},
    },
]


def apply_pinmap(pins: dict) -> None:
    from maix import pinmap

    for pin, func in pins.items():
        pinmap.set_pin_function(pin, func)
        print(f"[UART] pinmap {pin} -> {func}")


def open_uart(device: str, baudrate: int = 115200):
    from maix import uart

    profile = next((p for p in UART_PROFILES if p["device"] == device), None)
    if profile is not None:
        apply_pinmap(profile["pins"])
    else:
        print(f"[UART] no pinmap profile for {device}, try anyway")

    dev = uart.UART(device, baudrate)
    print(f"[UART] opened {device} @ {baudrate}")
    return dev


def iter_profiles(preferred_device: str = "") -> list[dict]:
    out: list[dict] = []
    if preferred_device:
        for p in UART_PROFILES:
            if p["device"] == preferred_device:
                out.append(p)
    for p in UART_PROFILES:
        if p not in out:
            out.append(p)
    return out
