#!/usr/bin/env python3
"""Dwara serial bridge (DEMO ONLY).

The ESP32 gate controller can be booted into a serial networking mode by
grounding GPIO26. In that mode it emits one newline-terminated frame per
backend request on its USB serial port:

    DWRAREQ {"id":1,"method":"POST","path":"/gate/validate",
             "authorization":"Bearer ...","body":"{...}"}

This script reads those frames, performs the equivalent HTTP call against the
locally running Spring Boot app, and writes the answer back:

    DWARERES {"id":1,"status":200,"body":"{\"decision\":\"open\",...}"}

WiFi is the production transport; this bridge exists only for demonstrating
the backend flow without a WiFi network.

Usage:
    python3 bridge.py --port /dev/tty.usbserial-0001
    python3 bridge.py --backend http://localhost:8080
    python3 bridge.py --help

Dependencies: pyserial, requests (see requirements.txt).
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from typing import Optional

import requests
import serial
from serial.tools import list_ports

REQUEST_PREFIX = "DWRAREQ "
RESPONSE_PREFIX = "DWARERES "

DEFAULT_BAUD = 115200
DEFAULT_BACKEND = "http://localhost:8080"
DEFAULT_TIMEOUT = 3.0


def _looks_like_esp32(port: list_ports.ListPortInfo) -> bool:
    """Best-effort match for the USB bridges found on ESP32 dev boards."""
    if port.vid == 0x303A:  # Espressif USB-JTAG/serial
        return True
    description = f"{port.description} {port.manufacturer or ''}".lower()
    return any(
        token in description
        for token in ("esp32", "espressif", "cp210", "ch340", "ch910", "usb jtag")
    )


def find_port(explicit: Optional[str]) -> str:
    if explicit:
        return explicit

    ports = list(list_ports.comports())
    candidates = [port for port in ports if _looks_like_esp32(port)]
    if len(candidates) == 1:
        return candidates[0].device
    if len(ports) == 1:
        return ports[0].device

    listing = "\n".join(
        f"  {port.device}\t{port.description}" for port in ports
    ) or "  (no serial ports found)"
    raise RuntimeError(
        "could not pick a serial port automatically; pass --port.\n"
        f"Available ports:\n{listing}"
    )


def forward(
    payload: dict,
    session: requests.Session,
    backend: str,
    timeout: float,
) -> dict:
    """Turn one DWRAREQ frame into an HTTP call and a DWARERES-ready reply."""
    request_id = payload.get("id")
    method = str(payload.get("method", "GET")).upper()
    path = payload.get("path")

    if not isinstance(path, str) or not path.startswith("/"):
        raise ValueError("frame path must be a string starting with '/'")

    headers = {"Accept": "application/json"}
    authorization = payload.get("authorization")
    if authorization:
        headers["Authorization"] = str(authorization)

    body = payload.get("body")
    data = None
    if body is not None:
        if not isinstance(body, str):
            raise ValueError("frame body must be a JSON string")
        data = body.encode("utf-8")
        headers["Content-Type"] = "application/json"

    try:
        response = session.request(
            method,
            backend + path,
            data=data,
            headers=headers,
            timeout=timeout,
        )
    except requests.RequestException as exc:
        print(f"[bridge] backend unreachable: {exc}", file=sys.stderr, flush=True)
        return {"id": request_id, "status": 0, "body": ""}

    return {"id": request_id, "status": response.status_code, "body": response.text}


def serve(port: str, baud: int, backend: str, timeout: float, verbose: bool) -> int:
    try:
        connection = serial.Serial(port, baud, timeout=0.2)
    except (serial.SerialException, OSError) as exc:
        print(f"[bridge] cannot open {port}: {exc}", file=sys.stderr, flush=True)
        return 2

    session = requests.Session()
    print(
        f"[bridge] {port} @ {baud} -> {backend}\n"
        f"[bridge] waiting for {REQUEST_PREFIX.strip()} frames "
        "(Ctrl-C to stop)",
        flush=True,
    )

    try:
        with connection:
            while True:
                raw = connection.readline()
                if not raw:
                    continue

                line = raw.decode("utf-8", errors="replace").strip()
                if not line.startswith(REQUEST_PREFIX):
                    continue  # boot noise, firmware logs, partial writes

                started = time.monotonic()
                try:
                    payload = json.loads(line[len(REQUEST_PREFIX):])
                    if not isinstance(payload, dict):
                        raise ValueError("frame must be a JSON object")
                    reply = forward(payload, session, backend, timeout)
                except (json.JSONDecodeError, ValueError) as exc:
                    print(
                        f"[bridge] bad frame ({exc}): {line[:160]}",
                        file=sys.stderr,
                        flush=True,
                    )
                    continue

                frame = RESPONSE_PREFIX + json.dumps(
                    reply, separators=(",", ":"), ensure_ascii=False
                )
                connection.write(frame.encode("utf-8") + b"\n")
                connection.flush()

                elapsed_ms = (time.monotonic() - started) * 1000
                body = reply["body"]
                shown = body if verbose else body[:160] + ("..." if len(body) > 160 else "")
                print(
                    f"[bridge] {payload.get('method', 'GET')} {payload.get('path')}"
                    f" -> {reply['status']} ({elapsed_ms:.0f} ms) {shown}",
                    flush=True,
                )
    except KeyboardInterrupt:
        print("\n[bridge] stopped", flush=True)
        return 0
    except (serial.SerialException, OSError) as exc:
        print(f"[bridge] serial error: {exc}", file=sys.stderr, flush=True)
        return 2
    finally:
        session.close()


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Relay DWARA serial-bridge frames from an ESP32 gate controller "
            "to a locally running Spring Boot app."
        )
    )
    parser.add_argument(
        "--port",
        default=os.environ.get("DWARA_SERIAL_PORT"),
        help="serial device (default: auto-detect, or $DWARA_SERIAL_PORT)",
    )
    parser.add_argument(
        "--baud",
        type=int,
        default=DEFAULT_BAUD,
        help=f"serial baud rate (default: {DEFAULT_BAUD})",
    )
    parser.add_argument(
        "--backend",
        default=os.environ.get("DWARA_BACKEND_URL", DEFAULT_BACKEND),
        help=f"Spring Boot base URL (default: {DEFAULT_BACKEND})",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=DEFAULT_TIMEOUT,
        help=f"HTTP timeout in seconds (default: {DEFAULT_TIMEOUT})",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="print full response bodies instead of truncating them",
    )
    return parser.parse_args(argv)


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    backend = args.backend.rstrip("/")

    try:
        port = find_port(args.port)
    except RuntimeError as exc:
        print(f"[bridge] {exc}", file=sys.stderr, flush=True)
        return 2

    return serve(port, args.baud, backend, args.timeout, args.verbose)


if __name__ == "__main__":
    raise SystemExit(main())
