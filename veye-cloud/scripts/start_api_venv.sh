#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -x ".venv/bin/python" ]]; then
  echo "[err] 未找到可执行的 venv：$(pwd)/.venv/bin/python" >&2
  echo "请先在 veye-cloud/ 下创建并安装依赖：" >&2
  echo "  python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt" >&2
  exit 1
fi

# 加载 .env（如果存在）
if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env"
  set +a
fi

# 由 start_all_venv.sh 拉齐多进程时设 UVICORN_EXEC=0，避免 exec 导致无法在同一脚本内回收子进程
if [[ "${UVICORN_EXEC:-1}" == "1" ]]; then
  exec .venv/bin/python -m uvicorn app.main:app --host "${API_HOST:-0.0.0.0}" --port "${API_PORT:-6006}"
fi
.venv/bin/python -m uvicorn app.main:app --host "${API_HOST:-0.0.0.0}" --port "${API_PORT:-6006}"

