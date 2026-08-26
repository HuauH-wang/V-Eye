#!/usr/bin/env bash
# 启动工作报告用纯文本 vLLM（port 8002）
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env"
  set +a
fi

export VLLM_USE_V1="${VLLM_USE_V1:-1}"

if [[ -z "${OMP_NUM_THREADS:-}" || "${OMP_NUM_THREADS}" -le 0 ]]; then
  unset OMP_NUM_THREADS
fi
if [[ -z "${MKL_NUM_THREADS:-}" || "${MKL_NUM_THREADS}" -le 0 ]]; then
  unset MKL_NUM_THREADS
fi

MODEL="${REPORT_VLLM_MODEL:-/root/autodl-tmp/.cache/modelscope/models/Qwen/Qwen2.5-7B-Instruct}"
GPU_UTIL="${REPORT_VLLM_GPU_MEMORY_UTILIZATION:-0.75}"
MAX_MODEL_LEN="${REPORT_VLLM_MAX_MODEL_LEN:-8192}"

export HF_HUB_OFFLINE="${HF_HUB_OFFLINE:-1}"
export TRANSFORMERS_OFFLINE="${TRANSFORMERS_OFFLINE:-1}"

VLLM_BIN="vllm"
if [[ -x ".venv/bin/vllm" ]]; then
  VLLM_BIN=".venv/bin/vllm"
fi

mkdir -p logs

exec "${VLLM_BIN}" serve "${MODEL}" \
  --host 127.0.0.1 \
  --port 8002 \
  --dtype bfloat16 \
  --enforce-eager \
  --gpu-memory-utilization "${GPU_UTIL}" \
  --max-model-len "${MAX_MODEL_LEN}" \
  --max-num-seqs 1
