#!/usr/bin/env bash
# Package V-Eye monorepo for distribution (excludes build artifacts & secrets).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="veye"
VERSION="${1:-$(date +%Y%m%d)}"
OUT_DIR="${2:-/root}"
ARCHIVE="${OUT_DIR}/${NAME}-${VERSION}.tar.gz"

cd "$ROOT"

tar -czf "$ARCHIVE" \
  --exclude='.git' \
  --exclude='.venv' \
  --exclude='venv' \
  --exclude='node_modules' \
  --exclude='__pycache__' \
  --exclude='.gradle' \
  --exclude='build' \
  --exclude='dist' \
  --exclude='logs' \
  --exclude='*.log' \
  --exclude='.env' \
  --exclude='local.properties' \
  --exclude='keystore' \
  --exclude='*.apk' \
  --exclude='*.aab' \
  --exclude='*.keystore' \
  --exclude='*.jks' \
  --exclude='.ipynb_checkpoints' \
  --exclude='app/static/web' \
  --exclude='frontend/dist' \
  --exclude='*.xcodeproj' \
  --exclude='*.xcworkspace' \
  --exclude='DerivedData' \
  --exclude='.DS_Store' \
  --exclude='.idea' \
  -C "$(dirname "$ROOT")" "$(basename "$ROOT")"

echo "Created: $ARCHIVE"
ls -lh "$ARCHIVE"
