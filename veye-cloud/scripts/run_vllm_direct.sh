#!/usr/bin/env bash
set -euo pipefail
MODEL_DIR="/root/autodl-tmp/.cache/modelscope/models/Qwen/Qwen2.5-VL-7B-Instruct"
source /root/veye-cloud/.venv/bin/activate
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1
export PYTORCH_ALLOC_CONF=expandable_segments:True
exec vllm serve "$MODEL_DIR" \
  --host 127.0.0.1 \
  --port 8001 \
  --dtype bfloat16 \
  --enforce-eager \
  --gpu-memory-utilization 0.80 \
  --max-model-len 1024 \
  --max-num-seqs 1 \
  --limit-mm-per-prompt "{\"image\": 1}"
