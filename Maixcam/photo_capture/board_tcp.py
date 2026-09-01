#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""TCP command link from ESP32 bridge (same line protocol as UART)."""

from __future__ import annotations

import queue
import socket
import threading

from protocol import parse_line


class BoardTcp:
    """Background TCP server; ESP32 connects as client."""

    def __init__(self, port: int = 8765, bind_host: str = "0.0.0.0"):
        self._port = port
        self._bind_host = bind_host
        self._cmd_q: queue.Queue[str] = queue.Queue()
        self._clients: list[socket.socket] = []
        self._clients_lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def open(self) -> None:
        self._stop.clear()
        self._thread = threading.Thread(target=self._serve, daemon=True, name="board-tcp")
        self._thread.start()
        print(f"[TCP] listening {self._bind_host}:{self._port}")

    def close(self) -> None:
        self._stop.set()
        with self._clients_lock:
            for sock in self._clients:
                try:
                    sock.close()
                except OSError:
                    pass
            self._clients.clear()

    def send(self, text: str) -> None:
        if not text.endswith("\n"):
            text += "\n"
        data = text.encode("utf-8")
        with self._clients_lock:
            dead: list[socket.socket] = []
            for sock in self._clients:
                try:
                    sock.sendall(data)
                except OSError:
                    dead.append(sock)
            for sock in dead:
                self._clients.remove(sock)
        print(f"[TCP->] {text.rstrip()}")

    def poll_command(self, timeout_ms: int = 50) -> str | None:
        try:
            return self._cmd_q.get(timeout=max(0.001, timeout_ms / 1000.0))
        except queue.Empty:
            return None

    def _serve(self) -> None:
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind((self._bind_host, self._port))
        srv.listen(2)
        srv.settimeout(0.5)
        try:
            while not self._stop.is_set():
                try:
                    conn, addr = srv.accept()
                except socket.timeout:
                    continue
                except OSError:
                    break
                conn.settimeout(0.05)
                print(f"[TCP] client {addr[0]}:{addr[1]}")
                with self._clients_lock:
                    self._clients.append(conn)
                threading.Thread(
                    target=self._client_loop,
                    args=(conn, addr),
                    daemon=True,
                    name=f"tcp-{addr[0]}",
                ).start()
        finally:
            try:
                srv.close()
            except OSError:
                pass

    def _client_loop(self, conn: socket.socket, addr) -> None:
        buf = bytearray()
        try:
            while not self._stop.is_set():
                try:
                    chunk = conn.recv(256)
                except socket.timeout:
                    continue
                except OSError:
                    break
                if not chunk:
                    break
                print(f"[TCP<-] {len(chunk)}B from {addr[0]}: {chunk[:48]!r}")
                buf.extend(chunk)
                while True:
                    nl = buf.find(b"\n")
                    if nl < 0:
                        cr = buf.find(b"\r")
                        if cr >= 0:
                            line = bytes(buf[:cr])
                            del buf[: cr + 1]
                        else:
                            break
                    else:
                        line = bytes(buf[:nl])
                        del buf[: nl + 1]
                    cmd = parse_line(line)
                    if cmd:
                        print(f"[TCP<-] {cmd}")
                        self._cmd_q.put(cmd)
        finally:
            with self._clients_lock:
                if conn in self._clients:
                    self._clients.remove(conn)
            try:
                conn.close()
            except OSError:
                pass
            print(f"[TCP] client gone {addr[0]}")
