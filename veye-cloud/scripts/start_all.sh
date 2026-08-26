#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

./scripts/start_vllm.sh &
VLLM_PID=$!

cleanup() {
  kill "${VLLM_PID}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

exec ./scripts/start_api.sh

