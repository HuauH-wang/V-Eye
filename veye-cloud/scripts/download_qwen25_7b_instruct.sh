#!/usr/bin/env bash
# 下载 Qwen2.5-7B-Instruct（纯文本，用于工作记录等文本生成）
# 用法:
#   ./scripts/download_qwen25_7b_instruct.sh
#   MODELSCOPE_MODEL=Qwen/Qwen2.5-3B-Instruct ./scripts/download_qwen25_7b_instruct.sh
set -euo pipefail

cd "$(dirname "$0")/.."

MODELSCOPE_MODEL="${MODELSCOPE_MODEL:-Qwen/Qwen2.5-7B-Instruct}"
# 与现有 Qwen2.5-VL-7B 同一缓存根目录
CACHE_ROOT="${MODEL_CACHE_ROOT:-/root/autodl-tmp/.cache/modelscope/models}"
MODEL_SHORT="${MODELSCOPE_MODEL##*/}"
LOCAL_DIR="${LOCAL_DIR:-${CACHE_ROOT}/${MODELSCOPE_MODEL}}"

usage() {
  cat <<EOF
Usage: ./scripts/download_qwen25_7b_instruct.sh

下载 Qwen 纯文本 Instruct 模型（默认 Qwen2.5-7B-Instruct，约 15GB BF16）。

环境变量:
  MODELSCOPE_MODEL   ModelScope 模型 ID（默认 Qwen/Qwen2.5-7B-Instruct）
  LOCAL_DIR          本地保存目录（默认 \${MODEL_CACHE_ROOT}/\${MODELSCOPE_MODEL}）
  MODEL_CACHE_ROOT   缓存根目录（默认 /root/autodl-tmp/.cache/modelscope/models）
  USE_HF=1           改用 Hugging Face CLI 下载（需能访问 huggingface.co）
  SKIP_PIP=1         跳过 pip install modelscope

示例:
  ./scripts/download_qwen25_7b_instruct.sh
  MODELSCOPE_MODEL=Qwen/Qwen2.5-3B-Instruct ./scripts/download_qwen25_7b_instruct.sh

下载完成后 vLLM 启动示例:
  vllm serve "${LOCAL_DIR}" --host 127.0.0.1 --port 8002 --dtype bfloat16 --max-model-len 8192
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -x ".venv/bin/python" ]]; then
  PYTHON=".venv/bin/python"
  PIP=".venv/bin/pip"
else
  PYTHON="python3"
  PIP="pip3"
fi

echo "[info] model:     ${MODELSCOPE_MODEL}"
echo "[info] local_dir: ${LOCAL_DIR}"
echo "[info] disk_need: ~18GB free recommended (BF16 ~15GB + buffer)"

mkdir -p "${LOCAL_DIR}"

if [[ -f "${LOCAL_DIR}/config.json" && -f "${LOCAL_DIR}/model.safetensors.index.json" ]]; then
  echo "[info] config.json and index already exist — will resume/verify download"
fi

if [[ "${USE_HF:-0}" == "1" ]]; then
  echo "[info] using Hugging Face Hub..."
  if [[ "${SKIP_PIP:-0}" != "1" ]]; then
    "${PIP}" install -U huggingface_hub
  fi
  HF_ID="${HF_MODEL:-${MODELSCOPE_MODEL}}"
  "${PYTHON}" - <<PY
from huggingface_hub import snapshot_download
snapshot_download(
    repo_id="${HF_ID}",
    local_dir="${LOCAL_DIR}",
    local_dir_use_symlinks=False,
)
print("[done] huggingface download -> ${LOCAL_DIR}")
PY
else
  echo "[info] using ModelScope (国内推荐)..."
  if [[ "${SKIP_PIP:-0}" != "1" ]]; then
    "${PIP}" install -U modelscope
  fi
  "${PYTHON}" - <<PY
from modelscope import snapshot_download
path = snapshot_download("${MODELSCOPE_MODEL}", local_dir="${LOCAL_DIR}")
print("[done] modelscope download ->", path)
PY
fi

echo ""
echo "[verify] key files:"
for f in config.json tokenizer.json model.safetensors.index.json; do
  if [[ -f "${LOCAL_DIR}/${f}" ]]; then
    echo "  OK  ${f}"
  else
    echo "  MISS ${f}" >&2
  fi
done

SHARD_COUNT="$(find "${LOCAL_DIR}" -maxdepth 1 -name 'model-*.safetensors' 2>/dev/null | wc -l | tr -d ' ')"
if [[ "${SHARD_COUNT}" -gt 0 ]]; then
  echo "  OK  ${SHARD_COUNT} weight shard(s)"
else
  echo "  MISS weight shards (model-*.safetensors)" >&2
  exit 1
fi

TOTAL_MB="$(du -sm "${LOCAL_DIR}" | awk '{print $1}')"
echo "[info] total size: ${TOTAL_MB} MB"

cat <<EOF

[done] Download complete.

Add to .env (work report / text LLM, port 8002):
  REPORT_VLLM_MODEL=${LOCAL_DIR}
  REPORT_VLLM_BASE_URL=http://127.0.0.1:8002

Start vLLM (separate from vision on 8001):
  source .venv/bin/activate
  vllm serve "${LOCAL_DIR}" \\
    --host 127.0.0.1 --port 8002 \\
    --dtype bfloat16 \\
    --max-model-len 8192 \\
    --gpu-memory-utilization 0.75

Test:
  curl -s http://127.0.0.1:8002/v1/models | head
EOF
