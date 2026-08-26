#!/usr/bin/env bash
# 预热常用区域瓦片到磁盘（默认仅 street/Esri，稳定且足够 Leaflet 回退）。
# 用法：./scripts/warm_tile_cache.sh [mode]
#   fast   — z11-13（默认，启动阻塞用）
#   full   — z11-14
#   custom — 传 provider zmin zmax（兼容旧用法）
# 环境变量：
#   TILE_WARM_PROVIDERS  默认 street；已配置 VITE_AMAP_KEY 时不会预热 amap
set -euo pipefail

cd "$(dirname "$0")/.."

LAT="${TILE_WARM_LAT:-30.5928}"
LNG="${TILE_WARM_LNG:-114.3055}"
SPAN="${TILE_WARM_SPAN:-0.22}"
API="http://127.0.0.1:${API_PORT:-6006}"

if [[ -f frontend/.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source frontend/.env
  set +a
fi

DEFAULT_PROVIDERS="street"
if [[ -n "${VITE_AMAP_KEY:-}" && -n "${VITE_AMAP_SECURITY_CODE:-}" ]]; then
  echo "[warm] 已配置高德 JS API Key → 跳过 amap 瓦片预热（Web 底图走官方 SDK）"
elif [[ -z "${TILE_WARM_PROVIDERS:-}" ]]; then
  echo "[warm] 未配置高德 SDK，仅预热 street（Leaflet 默认底图）"
fi
TILE_WARM_PROVIDERS="${TILE_WARM_PROVIDERS:-${DEFAULT_PROVIDERS}}"

if ! curl -sf --max-time 5 "${API}/health" >/dev/null; then
  echo "[err] FastAPI 未运行：${API}/health" >&2
  exit 1
fi

if [[ ! -x ".venv/bin/python" ]]; then
  echo "[err] 未找到 .venv" >&2
  exit 1
fi

run_warm() {
  local providers="$1"
  local zmin="$2"
  local zmax="$3"
  echo "[warm] providers=${providers} zoom=${zmin}-${zmax} center=(${LAT},${LNG}) span=${SPAN}"
  .venv/bin/python scripts/warm_tile_cache.py \
    --api-base "${API}" \
    --providers "${providers}" \
    --lat "${LAT}" --lng "${LNG}" --span "${SPAN}" \
    --zoom-min "${zmin}" --zoom-max "${zmax}" \
    --concurrency "${TILE_WARM_CONCURRENCY:-4}" \
    --retries "${TILE_WARM_RETRIES:-1}"
}

MODE="${1:-fast}"
case "${MODE}" in
  fast)
    run_warm "${TILE_WARM_PROVIDERS}" 11 13
    ;;
  full)
    run_warm "${TILE_WARM_PROVIDERS}" 11 14
    ;;
  custom)
    shift
    run_warm "${1:-amap}" "${2:-11}" "${3:-14}"
    ;;
  *)
    echo "[err] unknown mode: ${MODE} (use fast|full|custom)" >&2
    exit 2
    ;;
esac
