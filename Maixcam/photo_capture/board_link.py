#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Create board command link: BLE (ESP32), TCP (legacy WiFi), or UART."""

from __future__ import annotations


def create_board_link(cfg: dict):
    mode = (cfg.get("link_mode") or "ble").strip().lower()
    if mode == "uart":
        from board_uart import BoardUart

        device = (cfg.get("uart_device") or "/dev/ttyS0").strip()
        baud = int(cfg.get("uart_baudrate", 115200))
        link = BoardUart(device, baud)
        link.open()
        return link

    if mode == "tcp":
        from board_tcp import BoardTcp

        port = int(cfg.get("tcp_port", 8765))
        link = BoardTcp(port=port)
        link.open()
        return link

    from board_ble import BoardBle, normalize_ble_address

    ble_addr = normalize_ble_address((cfg.get("ble_address") or "").strip())
    link = BoardBle(
        device_name=(cfg.get("ble_device_name") or "U5-MAIX-BRIDGE").strip(),
        device_address=ble_addr,
        scan_timeout_sec=float(cfg.get("ble_scan_timeout_sec", 10)),
        reconnect_sec=float(cfg.get("ble_reconnect_sec", 3)),
    )
    if ble_addr:
        print(f"[LINK] BLE fast connect MAC={ble_addr}")
    link.open()
    return link
