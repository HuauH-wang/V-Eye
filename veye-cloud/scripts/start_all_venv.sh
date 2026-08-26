#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

ANDROID_CLOUD_CONFIG="../veye-android/app/src/main/java/com/veye/mobile/cloud/CloudConfig.kt"
MAXICAM_DIR="${MAXICAM_DIR:-/root/autodl-tmp/photo_capture}"
BASE_URL_INPUT="${BASE_URL:-}"

usage() {
  cat <<'EOF'
Usage: ./scripts/start_all_venv.sh [--base-url URL]

Options:
  --base-url URL   启动前将 Android CloudConfig 默认地址更新为该值
  -h, --help       显示帮助

Also supported:
  BASE_URL 环境变量（等价于 --base-url）

AutoDL 容器 Pro（可选，自动拉取 6006 反代地址同步 Android + Maxicam）：
  在 .env 中配置 AUTODL_TOKEN（或 scripts/fetch_autodl_pro_6006_base_url.py 顶部 _EMBEDDED_*，后者需配合 AUTODL_FETCH_BASE_URL=1）；
  AUTODL_PRO_INSTANCE_UUID 为控制台实例 ID（原样）；可省略（仅当账号下恰好 1 个运行中实例时自动推断）。
  若未传 --base-url 且未设置 BASE_URL，将请求 https://api.autodl.com 获取 service_6006_domain 作为 BASE_URL。
  MAXICAM_DIR  Maxicam 工程目录（默认 /root/autodl-tmp/photo_capture），同步 upload_url/base_url。

Web 前端（Vite，默认与 API 一并启动，占用 6008，见 README）：
  START_VITE_FRONTEND=1（默认）在 vLLM 就绪后启动 frontend（npm run dev:autodl），日志 logs/vite.log。
  START_VITE_FRONTEND=0 关闭。需 Node>=18（可用 scripts/install_node20_portable.sh）。

地图瓦片加速（磁盘缓存 + 预热，见 scripts/warm_tile_cache.sh）：
  TILE_CACHE_DIR  瓦片磁盘缓存目录（默认 /data/veye/tiles）
  START_TILE_WARM=1（默认）FastAPI 就绪后阻塞预热 street（fast 模式），再启动 Vite。
  已配置 frontend/.env 中 VITE_AMAP_KEY 时不会预热 amap（Web 走官方 SDK）。
  TILE_WARM_MODE=fast|full  预热范围（默认 fast = z11-13）。
  TILE_WARM_PROVIDERS=street  可选，如 Leaflet 回退需 amap 可设 street,amap。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      if [[ $# -lt 2 ]]; then
        echo "[err] --base-url requires a value" >&2
        exit 2
      fi
      BASE_URL_INPUT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[err] unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -x ".venv/bin/python" ]]; then
  echo "[err] 未找到可执行的 venv：$(pwd)/.venv/bin/python" >&2
  echo "请先在 veye-cloud/ 下创建并安装依赖：" >&2
  echo "  python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt" >&2
  exit 1
fi

# 让脚本内所有子进程都能找到 venv 的可执行文件（uvicorn/vllm 等）
export PATH="$(pwd)/.venv/bin:${PATH}"

# 加载 .env（如果存在）
if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env"
  set +a
fi

# .env 中的 BASE_URL（若命令行未通过 --base-url / 环境传入）
if [[ -z "${BASE_URL_INPUT}" && -n "${BASE_URL:-}" ]]; then
  BASE_URL_INPUT="${BASE_URL}"
fi

# AutoDL Pro：每次启动拉取最新 6006 反代域名
if [[ -z "${BASE_URL_INPUT}" ]] && {
  [[ -n "${AUTODL_TOKEN:-}" ]] || [[ -n "${AUTODL_PRO_INSTANCE_UUID:-}" ]] || [[ "${AUTODL_FETCH_BASE_URL:-}" == "1" ]]
}; then
  if _adl_base="$(.venv/bin/python scripts/fetch_autodl_pro_6006_base_url.py)"; then
    if [[ -n "${_adl_base}" ]]; then
      BASE_URL_INPUT="${_adl_base}"
      echo "[info] AutoDL API 已获取 6006 反代 BASE_URL: ${BASE_URL_INPUT}"
    fi
  else
    echo "[warn] AutoDL BASE_URL 拉取失败（将忽略，可用 BASE_URL 手动指定）。stderr 见上；自检: .venv/bin/python scripts/fetch_autodl_pro_6006_base_url.py" >&2
  fi
fi

normalize_base_url() {
  local url="$1"
  if [[ -z "${url}" ]]; then
    echo ""
    return 0
  fi
  if [[ "${url}" != http://* && "${url}" != https://* ]]; then
    echo "[err] BASE_URL 必须以 http:// 或 https:// 开头：${url}" >&2
    exit 2
  fi
  if [[ "${url}" != */ ]]; then
    url="${url}/"
  fi
  echo "${url}"
}

sync_client_base_url() {
  local new_url="$1"
  MAXICAM_DIR="${MAXICAM_DIR}" .venv/bin/python scripts/sync_client_base_url.py "${new_url}"
}

CURRENT_ANDROID_BASE_URL="$(
  CLOUD_CFG_PATH="${ANDROID_CLOUD_CONFIG}" python - <<'PY' || true
import os, re, pathlib
path = pathlib.Path(os.environ["CLOUD_CFG_PATH"])
if not path.exists():
    print("")
else:
    text = path.read_text(encoding="utf-8")
    m = re.search(r'const val DEFAULT_BASE_URL:\s*String\s*=\s*"([^"]+)"', text)
    if not m:
        m = re.search(r'const val BASE_URL:\s*String\s*=\s*"([^"]+)"', text)  # backward-compat
    print(m.group(1) if m else "")
PY
)"

if [[ -t 0 ]]; then
  if [[ -n "${BASE_URL_INPUT}" ]]; then
    echo "[config] BASE_URL 已预设（命令行/.env/AutoDL）: ${BASE_URL_INPUT}"
  else
    echo "[config] CloudConfig 中当前 BASE_URL: ${CURRENT_ANDROID_BASE_URL:-<not found>}"
  fi
  read -r -p "[config] 输入新 BASE_URL（回车则沿用上方预设，不覆盖）: " interactive_url
  if [[ -n "${interactive_url}" ]]; then
    BASE_URL_INPUT="${interactive_url}"
  fi
fi

BASE_URL_INPUT="$(normalize_base_url "${BASE_URL_INPUT}")"
if [[ -n "${BASE_URL_INPUT}" ]]; then
  sync_client_base_url "${BASE_URL_INPUT}"
fi

API_HOST="${API_HOST:-0.0.0.0}"
API_PORT="${API_PORT:-6006}"
VLLM_BASE_URL="${VLLM_BASE_URL:-http://127.0.0.1:8001}"
TILE_CACHE_DIR="${TILE_CACHE_DIR:-/data/veye/tiles}"
export TILE_CACHE_DIR

mkdir -p logs "${TILE_CACHE_DIR}"

tcp_check() {
  # bash 内置 /dev/tcp 可能不可用，但在 Ubuntu 常见环境基本可用
  timeout 1 bash -lc "cat < /dev/null > /dev/tcp/$1/$2" >/dev/null 2>&1
}

ensure_local_postgres() {
  if tcp_check 127.0.0.1 5432; then
    echo "[info] postgres already reachable at 127.0.0.1:5432"
    return 0
  fi

  # 先尝试用 docker compose（如果容器/宿主允许）
  if ./scripts/start_postgres.sh >/dev/null 2>&1; then
    if tcp_check 127.0.0.1 5432; then
      echo "[info] postgres started via docker compose"
      return 0
    fi
  fi

  # 再尝试启动容器内的 system postgres（适用于 AutoDL 容器无 docker daemon 的情况）
  if command -v pg_ctlcluster >/dev/null 2>&1; then
    echo "[info] attempting to start local postgresql service/cluster..."
    service postgresql start >/dev/null 2>&1 || true
    pg_ctlcluster 14 main start >/dev/null 2>&1 || true
  fi

  if tcp_check 127.0.0.1 5432; then
    echo "[info] postgres is reachable at 127.0.0.1:5432"
    # 确保默认账号/数据库存在（幂等：已存在会忽略）
    if command -v su >/dev/null 2>&1; then
      su - postgres -c "psql -c \"CREATE ROLE veye LOGIN PASSWORD 'veye_password';\"" >/dev/null 2>&1 || true
      su - postgres -c "createdb -O veye veye" >/dev/null 2>&1 || true
    fi
    return 0
  fi

  echo "[warn] postgres not available; /vision/identify and /sos may return 503 database_unavailable" >&2
  return 0
}

ensure_local_postgres

kill_existing_api_on_port() {
  # 只清理本项目 FastAPI（uvicorn app.main:app）占用的端口，避免误杀其他服务
  local port="${1}"
  local pids
  # 注意：开启了 pipefail 时，grep 无匹配会返回 1，不能让脚本整体退出
  pids="$(
    ps -eo pid=,args= \
      | grep -F "uvicorn app.main:app" \
      | grep -E -- "(--port[ =]${port})([[:space:]]|$)" \
      | awk '{print $1}' \
      | tr '\n' ' ' \
      || true
  )"

  if [[ -z "${pids// }" ]]; then
    return 0
  fi

  echo "[warn] port ${port} already in use by existing uvicorn, stopping: ${pids}"
  # 先尝试优雅退出
  kill ${pids} >/dev/null 2>&1 || true
  sleep 1
  # 若仍存活则强杀
  for pid in ${pids}; do
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill -9 "${pid}" >/dev/null 2>&1 || true
    fi
  done
}

kill_existing_api_on_port "${API_PORT}"

prepend_portable_node_to_path() {
  local d
  shopt -s nullglob
  for d in "${HOME}/autodl-tmp"/node-v*-linux-x64/bin "${HOME}"/node-v*-linux-x64/bin; do
    if [[ -x "${d}/node" ]]; then
      export PATH="${d}:${PATH}"
      shopt -u nullglob
      return 0
    fi
  done
  shopt -u nullglob
  return 1
}

ensure_node18_for_frontend() {
  if command -v node >/dev/null 2>&1; then
    local ma
    ma="$(node -p "parseInt(process.versions.node.split('.')[0],10)" 2>/dev/null || echo 0)"
    if [[ "${ma}" -ge 18 ]]; then
      return 0
    fi
  fi
  if [[ -s "${HOME}/.nvm/nvm.sh" ]]; then
    # shellcheck disable=SC1090
    source "${HOME}/.nvm/nvm.sh"
    command -v nvm >/dev/null 2>&1 && nvm use >/dev/null 2>&1 || true
  fi
  if command -v node >/dev/null 2>&1; then
    local ma2
    ma2="$(node -p "parseInt(process.versions.node.split('.')[0],10)" 2>/dev/null || echo 0)"
    [[ "${ma2}" -ge 18 ]] && return 0
  fi
  prepend_portable_node_to_path || true
  if command -v node >/dev/null 2>&1; then
    local ma3
    ma3="$(node -p "parseInt(process.versions.node.split('.')[0],10)" 2>/dev/null || echo 0)"
    [[ "${ma3}" -ge 18 ]] && return 0
  fi
  return 1
}

kill_listeners_on_tcp_port() {
  local port="${1}"
  if command -v lsof >/dev/null 2>&1; then
    local p
    for p in $(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true); do
      if [[ -n "${p}" ]] && kill -0 "${p}" >/dev/null 2>&1; then
        echo "[warn] stopping process on port ${port}: ${p}"
        kill "${p}" >/dev/null 2>&1 || true
        sleep 1
        kill -9 "${p}" >/dev/null 2>&1 || true
      fi
    done
  fi
}

echo "[info] starting vLLM..."
./scripts/start_vllm.sh >logs/vllm.log 2>&1 &
VLLM_PID=$!
FRONTEND_PID=""
API_WRAP_PID=""

cleanup() {
  if [[ -n "${FRONTEND_PID:-}" ]] && kill -0 "${FRONTEND_PID}" >/dev/null 2>&1; then
    echo "[info] stopping Vite (pid=${FRONTEND_PID})..."
    kill "${FRONTEND_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${API_WRAP_PID:-}" ]] && kill -0 "${API_WRAP_PID}" >/dev/null 2>&1; then
    echo "[info] stopping API wrapper (pid=${API_WRAP_PID})..."
    kill "${API_WRAP_PID}" >/dev/null 2>&1 || true
  fi
  if kill -0 "${VLLM_PID}" >/dev/null 2>&1; then
    echo "[info] stopping vLLM (pid=${VLLM_PID})..."
    kill "${VLLM_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

sleep 1
if ! kill -0 "${VLLM_PID}" >/dev/null 2>&1; then
  echo "[err] vLLM exited immediately. Last logs:" >&2
  tail -n 120 logs/vllm.log >&2 || true
  exit 1
fi

echo "[info] waiting for vLLM to be ready at ${VLLM_BASE_URL} ..."
VLLM_PID="${VLLM_PID}" python - <<'PY'
import os, time, sys
import urllib.request

base = os.environ.get("VLLM_BASE_URL", "http://127.0.0.1:8001").rstrip("/")
url = base + "/v1/models"
vllm_pid = int(os.environ.get("VLLM_PID", "0") or "0")
deadline = time.time() + 180
last_err = None
while time.time() < deadline:
  if vllm_pid > 0:
    try:
      os.kill(vllm_pid, 0)
    except OSError:
      print("[err] vLLM process exited before becoming ready; see logs/vllm.log", file=sys.stderr)
      sys.exit(1)
  try:
    with urllib.request.urlopen(url, timeout=2) as r:
      if 200 <= r.status < 300:
        print("[info] vLLM ready")
        sys.exit(0)
  except Exception as e:
    last_err = e
  time.sleep(2)
print(f"[err] vLLM not ready after 180s: {last_err}", file=sys.stderr)
sys.exit(1)
PY

echo "[info] starting FastAPI at ${API_HOST}:${API_PORT} ..."
# 再清理一次端口占用（vLLM 等待期间可能有人手动启动过 API）
kill_existing_api_on_port "${API_PORT}"

export UVICORN_EXEC=0
./scripts/start_api_venv.sh &
API_WRAP_PID=$!

wait_for_api_health() {
  local deadline=$((SECONDS + 90))
  while (( SECONDS < deadline )); do
    if curl -sf --max-time 2 "http://127.0.0.1:${API_PORT}/health" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  return 1
}

START_TILE_WARM="${START_TILE_WARM:-1}"
if [[ "${START_TILE_WARM}" == "1" ]]; then
  if wait_for_api_health; then
    TILE_WARM_MODE="${TILE_WARM_MODE:-fast}"
    if [[ -f frontend/.env ]]; then
      set -a
      # shellcheck disable=SC1091
      source frontend/.env
      set +a
      if [[ -n "${VITE_AMAP_KEY:-}" && -n "${VITE_AMAP_SECURITY_CODE:-}" ]]; then
        echo "[info] 高德 JS API 已配置：Web 地图请访问 6006 同源；底图不走 /map/tiles/amap"
      fi
    fi
    echo "[info] blocking tile warm (${TILE_WARM_MODE}) → ${TILE_CACHE_DIR} (logs/tile_warm.log)"
    if ./scripts/warm_tile_cache.sh "${TILE_WARM_MODE}" >>logs/tile_warm.log 2>&1; then
      echo "[info] tile warm finished"
    else
      echo "[warn] tile warm incomplete（未命中瓦片会走 Esri 快速回退）" >&2
    fi
  else
    echo "[warn] FastAPI 未在 90s 内就绪，跳过瓦片预热" >&2
  fi
fi

START_VITE_FRONTEND="${START_VITE_FRONTEND:-1}"
if [[ "${START_VITE_FRONTEND}" == "1" ]] && [[ -f frontend/package.json ]]; then
  if ensure_node18_for_frontend; then
    mkdir -p logs
    kill_listeners_on_tcp_port 6008
    echo "[info] starting Vite (npm run dev:autodl, port 6008) → logs/vite.log"
    (
      cd frontend
      if [[ -f .env ]]; then
        set -a
        # shellcheck disable=SC1091
        source .env
        set +a
      fi
      if [[ ! -d node_modules ]]; then
        echo "[info] frontend: 首次安装依赖 npm install …"
        npm install
      fi
      if [[ "${BUILD_WEB_UI:-1}" == "1" ]]; then
        echo "[info] frontend: npm run build → app/static/web（6006 同源访问更快）"
        npm run build
      fi
      npm run dev:autodl
    ) >>logs/vite.log 2>&1 &
    FRONTEND_PID=$!
    sleep 1
    if ! kill -0 "${FRONTEND_PID}" >/dev/null 2>&1; then
      echo "[warn] Vite 似乎立即退出，请查看 logs/vite.log" >&2
    else
      echo "[info] Vite pid=${FRONTEND_PID}（地图建议优先用 6006 同源；6008 仅开发）"
    fi
  else
    echo "[warn] 未找到 Node>=18，跳过 Vite。请运行: ./scripts/install_node20_portable.sh 或在 .env 设 START_VITE_FRONTEND=0" >&2
  fi
elif [[ "${START_VITE_FRONTEND}" == "1" ]]; then
  echo "[warn] 未找到 frontend/package.json，跳过 Vite" >&2
fi

wait "${API_WRAP_PID}"

