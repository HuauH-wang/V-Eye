#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f ".env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source ".env"
  set +a
fi

export VLLM_USE_V1="${VLLM_USE_V1:-1}"

# AutoDL / IDE agent 等环境可能注入 OMP_NUM_THREADS=0，会导致 vLLM 在
# torch.set_num_threads() 处崩溃：RuntimeError: set_num_threads expects a positive integer
if [[ -z "${OMP_NUM_THREADS:-}" || "${OMP_NUM_THREADS}" -le 0 ]]; then
  unset OMP_NUM_THREADS
fi
if [[ -z "${MKL_NUM_THREADS:-}" || "${MKL_NUM_THREADS}" -le 0 ]]; then
  unset MKL_NUM_THREADS
fi

MODEL="${VLLM_MODEL:-Qwen/Qwen2.5-VL-7B-Instruct}"
GPU_UTIL="${VLLM_GPU_MEMORY_UTILIZATION:-0.75}"
MAX_MODEL_LEN="${VLLM_MAX_MODEL_LEN:-2048}"

# 在部分环境无法访问 huggingface.co 时，强制离线从本地目录加载。
export HF_HUB_OFFLINE="${HF_HUB_OFFLINE:-1}"
export TRANSFORMERS_OFFLINE="${TRANSFORMERS_OFFLINE:-1}"

VLLM_BIN="vllm"
if [[ -x ".venv/bin/vllm" ]]; then
  VLLM_BIN=".venv/bin/vllm"
fi

exec "${VLLM_BIN}" serve "${MODEL}" \
  --host 127.0.0.1 \
  --port 8001 \
  --dtype bfloat16 \
  --enforce-eager \
  --gpu-memory-utilization "${GPU_UTIL}" \
  --max-model-len "${MAX_MODEL_LEN}" \
  --max-num-seqs 1 \
  --limit-mm-per-prompt '{"image": 1}'

