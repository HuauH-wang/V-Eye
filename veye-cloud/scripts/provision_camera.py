#!/usr/bin/env python3
"""Provision a Maxicam device: DB record + device.json + static bind QR (no URL)."""

from __future__ import annotations

import argparse
import json
import os
import secrets
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.auth import hash_password  # noqa: E402
from app.config import Settings  # noqa: E402
from app.db import create_engine_from_settings, create_session_factory  # noqa: E402
from app.models import Base, CameraDevice  # noqa: E402
from scripts.sync_client_base_url import (  # noqa: E402
    build_bind_uri,
    normalize_base_url,
    sync_maxicam,
    upload_url_from_base,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Provision Maxicam camera device")
    parser.add_argument(
        "--base-url",
        default=os.environ.get("VEYE_PUBLIC_BASE_URL", ""),
        help="Public API base URL (optional; synced separately via sync_client_base_url.py)",
    )
    parser.add_argument(
        "--output-dir",
        default=os.environ.get("MAXICAM_DIR", "/root/autodl-tmp/photo_capture"),
        help="Maxicam project directory",
    )
    parser.add_argument("--device-id", default="", help="Optional fixed device id")
    parser.add_argument("--name", default="Maxicam Pro", help="Device display name")
    args = parser.parse_args()

    device_id = args.device_id.strip() or f"MAXICAM-{uuid.uuid4().hex[:8].upper()}"
    device_secret = secrets.token_urlsafe(24)
    bind_uri = build_bind_uri(device_id=device_id, device_secret=device_secret)

    base_url = normalize_base_url(args.base_url) if args.base_url.strip() else ""
    upload_url = upload_url_from_base(base_url) if base_url else ""

    settings = Settings()
    engine = create_engine_from_settings(settings)
    Base.metadata.create_all(bind=engine)
    session_factory = create_session_factory(engine)

    with session_factory() as db:
        existing = db.query(CameraDevice).filter(CameraDevice.device_id == device_id).one_or_none()
        if existing:
            print(f"Device {device_id} already exists in DB — skip insert")
        else:
            dev = CameraDevice(
                id=uuid.uuid4(),
                device_id=device_id,
                secret_hash=hash_password(device_secret),
                name=args.name[:64],
                device_type="maxicam",
            )
            db.add(dev)
            db.commit()
            print(f"Created device in DB: {device_id}")

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    device_json = {
        "device_id": device_id,
        "device_secret": device_secret,
        "bind_uri": bind_uri,
        "upload_url": upload_url,
        "base_url": base_url,
        "scene": "toxic_plant",
        "lang": "zh-CN",
    }
    device_path = out_dir / "device.json"
    device_path.write_text(json.dumps(device_json, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {device_path}")

    config_path = out_dir / "config.json"
    cfg = {}
    if config_path.is_file():
        cfg = json.loads(config_path.read_text(encoding="utf-8"))
    cfg.update(
        {
            "device_id": device_id,
            "device_secret": device_secret,
            "scene": "toxic_plant",
            "lang": "zh-CN",
        }
    )
    if base_url:
        cfg["base_url"] = base_url
        cfg["upload_url"] = upload_url
    config_path.write_text(json.dumps(cfg, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Updated {config_path}")

    qr_path = out_dir / "bind_qr.png"
    try:
        import qrcode

        qr = qrcode.QRCode(box_size=8, border=2)
        qr.add_data(bind_uri)
        qr.make(fit=True)
        img = qr.make_image(fill_color="#1b4332", back_color="#fafaf7")
        img.save(qr_path)
        print(f"Wrote {qr_path} (static v2, no URL)")
    except ImportError:
        print("Install qrcode[pil] to generate bind_qr.png: pip install qrcode[pil]")
        (out_dir / "bind_uri.txt").write_text(bind_uri + "\n", encoding="utf-8")

    if base_url:
        sync_maxicam(base_url, out_dir, regen_qr=False)

    print("\n--- Static Bind URI (QR content, proxy-independent) ---")
    print(bind_uri)
    print("\n--- After AutoDL proxy changes, run ---")
    print("  python scripts/sync_client_base_url.py '<new-base-url>' --regen-qr")
    print("\n--- Upload headers ---")
    print(f"X-Device-Id: {device_id}")
    print(f"X-Device-Secret: {device_secret}")


if __name__ == "__main__":
    main()
