#!/usr/bin/env bash
# vLLM 模型切换：vision(8001) / report(8002)
# 同一 GPU 上互斥运行，切换时先停旧进程、等显存释放，再启新模型。
# 用法: ./scripts/model_switch.sh stop_all|start_vision|stop_vision|start_report|stop_report|status
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env"
  set +a
fi

VISION_PORT="${VLLM_PORT:-8001}"
REPORT_PORT="${REPORT_VLLM_PORT:-8002}"
VISION_BASE="${VLLM_BASE_URL:-http://127.0.0.1:8001}"
REPORT_BASE="${REPORT_VLLM_BASE_URL:-http://127.0.0.1:8002}"
PID_DIR="${PID_DIR:-logs}"
VISION_PID_FILE="${PID_DIR}/vllm-vision.pid"
REPORT_PID_FILE="${PID_DIR}/vllm-report.pid"
VISION_LOG="${PID_DIR}/vllm.log"
REPORT_LOG="${PID_DIR}/vllm-report.log"
# 切换后 GPU 占用需低于此值（MiB）才启动下一模型（0.75×32GB≈24GB，需腾出足够空间）
GPU_FREE_TARGET_MIB="${MODEL_SWITCH_GPU_FREE_MIB:-8000}"
SWITCH_TIMEOUT="${MODEL_SWITCH_TIMEOUT_S:-180}"

mkdir -p "${PID_DIR}"

gpu_used_mib() {
  if ! command -v nvidia-smi >/dev/null 2>&1; then
    echo 0
    return
  fi
  nvidia-smi --query-gpu=memory.used --format=csv,noheader,nounits 2>/dev/null | head -1 | tr -d ' '
}

wait_gpu_free() {
  local target="${1:-${GPU_FREE_TARGET_MIB}}"
  local timeout="${2:-${SWITCH_TIMEOUT}}"
  local deadline=$((SECONDS + timeout))
  echo "[model_switch] waiting for GPU used <= ${target} MiB (timeout ${timeout}s)..."
  while [[ "${SECONDS}" -lt "${deadline}" ]]; do
    local used
    used="$(gpu_used_mib)"
    if [[ -n "${used}" && "${used}" -le "${target}" ]]; then
      echo "[model_switch] GPU memory ok: ${used} MiB used"
      return 0
    fi
    echo "[model_switch] GPU still busy: ${used:-?} MiB used"
    sleep 3
  done
  echo "[model_switch] timeout waiting for GPU memory release" >&2
  return 1
}

kill_tree() {
  local pid="$1"
  [[ -z "${pid}" ]] && return 0
  if ! kill -0 "${pid}" >/dev/null 2>&1; then
    return 0
  fi
  local child
  for child in $(pgrep -P "${pid}" 2>/dev/null || true); do
    kill_tree "${child}"
  done
  kill "${pid}" >/dev/null 2>&1 || true
}

kill_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    local p
    for p in $(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true); do
      if [[ -n "${p}" ]]; then
        echo "[model_switch] stopping pid ${p} on port ${port}"
        kill_tree "${p}"
        sleep 1
        kill -9 "${p}" >/dev/null 2>&1 || true
      fi
    done
  fi
}

kill_vllm_by_port() {
  local port="$1"
  kill_pidfile_for_port "${port}"
  kill_port "${port}"
  # 兜底：按命令行匹配 vLLM 服务（含 EngineCore 父进程）
  if pgrep -f "vllm serve.*--port ${port}" >/dev/null 2>&1; then
    echo "[model_switch] pkill vllm serve on port ${port}"
    pkill -f "vllm serve.*--port ${port}" >/dev/null 2>&1 || true
    sleep 2
    pkill -9 -f "vllm serve.*--port ${port}" >/dev/null 2>&1 || true
  fi
}

kill_pidfile_for_port() {
  local port="$1"
  local f=""
  if [[ "${port}" == "${VISION_PORT}" ]]; then
    f="${VISION_PID_FILE}"
  elif [[ "${port}" == "${REPORT_PORT}" ]]; then
    f="${REPORT_PID_FILE}"
  fi
  [[ -z "${f}" || ! -f "${f}" ]] && return 0
  local pid
  pid="$(cat "${f}" 2>/dev/null || true)"
  if [[ -n "${pid}" ]]; then
    echo "[model_switch] stopping pid ${pid} from ${f}"
    kill_tree "${pid}"
    sleep 1
    kill -9 "${pid}" >/dev/null 2>&1 || true
  fi
  rm -f "${f}"
}

kill_pidfile() {
  kill_pidfile_for_port "${VISION_PORT}"
  kill_pidfile_for_port "${REPORT_PORT}"
}

wait_health() {
  local base="$1"
  local timeout="${2:-${SWITCH_TIMEOUT}}"
  local url="${base%/}/v1/models"
  local deadline=$((SECONDS + timeout))
  while [[ "${SECONDS}" -lt "${deadline}" ]]; do
    if curl -sf "${url}" >/dev/null 2>&1; then
      echo "[model_switch] ready: ${url}"
      return 0
    fi
    sleep 2
  done
  echo "[model_switch] timeout waiting for ${url}" >&2
  return 1
}

wait_service_down() {
  local base="$1"
  local timeout="${2:-60}"
  local url="${base%/}/v1/models"
  local deadline=$((SECONDS + timeout))
  while [[ "${SECONDS}" -lt "${deadline}" ]]; do
    if ! curl -sf "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "[model_switch] timeout waiting for ${url} to go down" >&2
  return 1
}

stop_vision() {
  echo "[model_switch] stop vision (${VISION_PORT})"
  kill_vllm_by_port "${VISION_PORT}"
  wait_service_down "${VISION_BASE}" 60 || true
  wait_gpu_free "${GPU_FREE_TARGET_MIB}" "${SWITCH_TIMEOUT}"
}

stop_report() {
  echo "[model_switch] stop report (${REPORT_PORT})"
  kill_vllm_by_port "${REPORT_PORT}"
  wait_service_down "${REPORT_BASE}" 60 || true
  wait_gpu_free "${GPU_FREE_TARGET_MIB}" "${SWITCH_TIMEOUT}"
}

stop_all() {
  echo "[model_switch] stop all"
  kill_vllm_by_port "${VISION_PORT}"
  kill_vllm_by_port "${REPORT_PORT}"
  wait_service_down "${VISION_BASE}" 30 || true
  wait_service_down "${REPORT_BASE}" 30 || true
  wait_gpu_free "${GPU_FREE_TARGET_MIB}" "${SWITCH_TIMEOUT}"
}

start_vision() {
  stop_report
  if curl -sf "${VISION_BASE%/}/v1/models" >/dev/null 2>&1; then
    echo "[model_switch] vision already running"
    return 0
  fi
  echo "[model_switch] starting vision on port ${VISION_PORT}..."
  nohup ./scripts/start_vllm.sh >"${VISION_LOG}" 2>&1 &
  echo $! >"${VISION_PID_FILE}"
  wait_health "${VISION_BASE}" "${SWITCH_TIMEOUT}"
}

start_report() {
  stop_vision
  if curl -sf "${REPORT_BASE%/}/v1/models" >/dev/null 2>&1; then
    echo "[model_switch] report already running"
    return 0
  fi
  echo "[model_switch] starting report on port ${REPORT_PORT}..."
  nohup ./scripts/start_vllm_report.sh >"${REPORT_LOG}" 2>&1 &
  echo $! >"${REPORT_PID_FILE}"
  wait_health "${REPORT_BASE}" "${SWITCH_TIMEOUT}"
}

status_cmd() {
  echo "vision(${VISION_PORT}): $(curl -sf "${VISION_BASE%/}/v1/models" >/dev/null 2>&1 && echo up || echo down)"
  echo "report(${REPORT_PORT}): $(curl -sf "${REPORT_BASE%/}/v1/models" >/dev/null 2>&1 && echo up || echo down)"
  if command -v nvidia-smi >/dev/null 2>&1; then
    echo "gpu_used_mib: $(gpu_used_mib)"
  fi
}

cmd="${1:-status}"
case "${cmd}" in
  stop_all) stop_all ;;
  start_vision) start_vision ;;
  stop_vision) stop_vision ;;
  start_report) start_report ;;
  stop_report) stop_report ;;
  status) status_cmd ;;
  *)
    echo "Usage: $0 stop_all|start_vision|stop_vision|start_report|stop_report|status" >&2
    exit 2
    ;;
esac
