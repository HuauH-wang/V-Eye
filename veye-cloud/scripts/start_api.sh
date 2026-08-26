#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env"
  set +a
fi

exec uvicorn app.main:app --host "${API_HOST:-0.0.0.0}" --port "${API_PORT:-6006}"

