#!/bin/sh
# MaixCAM photo_capture + U5 board UART control

export LANG=C.UTF-8
export LC_ALL=C.UTF-8

set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "=========================================="
echo "  MaixCAM Photo Capture + Board UART"
echo "  Dir: $DIR"
echo "  Wiring: Maix RX<-U5 TX, Maix TX->U5 RX"
echo "=========================================="

mkdir -p "$(python3 -c "import json; print(json.load(open('config.json')).get('photo_dir','/root/photos'))")"

echo "[START] UART commands: PING PHOTO CONFIRM CANCEL"
python3 main.py
