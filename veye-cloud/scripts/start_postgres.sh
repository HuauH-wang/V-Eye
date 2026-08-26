#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if command -v docker >/dev/null 2>&1; then
  if command -v docker-compose >/dev/null 2>&1; then
    exec docker-compose up -d postgres
  fi
  if docker compose version >/dev/null 2>&1; then
    exec docker compose up -d postgres
  fi
fi

echo "未检测到 docker/docker compose，无法自动启动 PostgreSQL。" >&2
echo "请先安装 Docker，或手动安装并启动 PostgreSQL（监听 127.0.0.1:5432）。" >&2
exit 1

