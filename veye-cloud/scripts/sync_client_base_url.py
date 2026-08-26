#!/usr/bin/env python3
"""Sync public BASE_URL to Android CloudConfig and Maxicam config files."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]

DEFAULT_ANDROID_CONFIG = ROOT.parent / "veye-android/app/src/main/java/com/veye/mobile/cloud/CloudConfig.kt"
DEFAULT_MAXICAM_DIR = Path(os.environ.get("MAXICAM_DIR", "/root/autodl-tmp/photo_capture"))


def normalize_base_url(url: str) -> str:
    u = url.strip()
    if not u:
        return ""
    if not u.startswith(("http://", "https://")):
        raise ValueError(f"BASE_URL must start with http:// or https://: {u}")
    return u if u.endswith("/") else f"{u}/"


def upload_url_from_base(base_url: str) -> str:
    return base_url.rstrip("/") + "/camera/upload"


def build_bind_uri(*, device_id: str, device_secret: str) -> str:
    """Static bind QR — no public URL (proxy may change)."""
    return (
        f"veye://bind?v=2&did={quote(device_id, safe='')}"
        f"&sec={quote(device_secret, safe='')}"
    )


def sync_android(base_url: str, config_path: Path) -> bool:
    if not config_path.is_file():
        print(f"[warn] Android CloudConfig not found: {config_path}", file=sys.stderr)
        return False
    text = config_path.read_text(encoding="utf-8")
    patterns = [
        r'(const val DEFAULT_BASE_URL:\s*String\s*=\s*")[^"]*(")',
        r'(const val BASE_URL:\s*String\s*=\s*")[^"]*(")',
    ]
    new_text = text
    count = 0
    for pattern in patterns:
        new_text, count = re.subn(pattern, r"\1" + base_url + r"\2", new_text, count=1)
        if count == 1:
            break
    if count != 1:
        print(f"[err] DEFAULT_BASE_URL not found in {config_path}", file=sys.stderr)
        return False
    config_path.write_text(new_text, encoding="utf-8")
    print(f"[info] Android BASE_URL -> {base_url}")
    return True


def sync_maxicam(base_url: str, maxicam_dir: Path, regen_qr: bool = False) -> bool:
    if not maxicam_dir.is_dir():
        print(f"[warn] Maxicam dir not found: {maxicam_dir}", file=sys.stderr)
        return False

    upload = upload_url_from_base(base_url)
    updated = False

    config_path = maxicam_dir / "config.json"
    if config_path.is_file():
        cfg = json.loads(config_path.read_text(encoding="utf-8"))
        if cfg.get("base_url") != base_url or cfg.get("upload_url") != upload:
            cfg["base_url"] = base_url
            cfg["upload_url"] = upload
            config_path.write_text(json.dumps(cfg, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            print(f"[info] updated {config_path}")
            updated = True

    device_path = maxicam_dir / "device.json"
    device_id = ""
    device_secret = ""
    if device_path.is_file():
        dev = json.loads(device_path.read_text(encoding="utf-8"))
        device_id = (dev.get("device_id") or "").strip()
        device_secret = (dev.get("device_secret") or "").strip()
        bind_uri = build_bind_uri(device_id=device_id, device_secret=device_secret) if device_id and device_secret else dev.get("bind_uri", "")
        changed = (
            dev.get("base_url") != base_url
            or dev.get("upload_url") != upload
            or dev.get("bind_uri") != bind_uri
        )
        if changed:
            dev["base_url"] = base_url
            dev["upload_url"] = upload
            if bind_uri:
                dev["bind_uri"] = bind_uri
            device_path.write_text(json.dumps(dev, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            print(f"[info] updated {device_path}")
            updated = True

    if regen_qr and device_id and device_secret:
        qr_path = maxicam_dir / "bind_qr.png"
        bind_uri = build_bind_uri(device_id=device_id, device_secret=device_secret)
        try:
            import qrcode

            qr = qrcode.QRCode(box_size=8, border=2)
            qr.add_data(bind_uri)
            qr.make(fit=True)
            img = qr.make_image(fill_color="#1b4332", back_color="#fafaf7")
            img.save(qr_path)
            print(f"[info] regenerated {qr_path} (static, no URL)")
            updated = True
        except ImportError:
            print("[warn] qrcode not installed, skip bind_qr.png", file=sys.stderr)

    if not updated:
        print(f"[info] Maxicam already aligned: {upload}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync public BASE_URL to Android + Maxicam")
    parser.add_argument("base_url", help="Public API base URL, e.g. https://xxx:8443/")
    parser.add_argument("--android-config", type=Path, default=DEFAULT_ANDROID_CONFIG)
    parser.add_argument("--maxicam-dir", type=Path, default=DEFAULT_MAXICAM_DIR)
    parser.add_argument("--skip-android", action="store_true")
    parser.add_argument("--skip-maxicam", action="store_true")
    parser.add_argument("--regen-qr", action="store_true", help="Regenerate bind_qr.png if credentials exist")
    args = parser.parse_args()

    base_url = normalize_base_url(args.base_url)
    ok = True
    if not args.skip_android:
        ok = sync_android(base_url, args.android_config) and ok
    if not args.skip_maxicam:
        ok = sync_maxicam(base_url, args.maxicam_dir, regen_qr=args.regen_qr) and ok
    print(f"[done] upload_url = {upload_url_from_base(base_url)}")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
