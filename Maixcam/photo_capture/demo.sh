#!/bin/sh
# MaixCAM ���� Demo һ�����У��������壩

export LANG=C.UTF-8
export LC_ALL=C.UTF-8

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "=========================================="
echo "  MaixCAM Photo Demo"
echo "  Dir: $DIR"
echo "=========================================="

python3 demo.py "$@"
