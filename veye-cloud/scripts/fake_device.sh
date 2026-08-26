#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-}"
API_KEY="${API_KEY:-}"
IMAGE_PATH="${IMAGE_PATH:-}"
DEVICE_ID="${DEVICE_ID:-fake-watch-001}"

if [[ -z "${BASE_URL}" ]]; then
  echo "ERROR: BASE_URL is required, e.g. BASE_URL=\"https://your-domain.example/\""
  exit 2
fi

if [[ "${BASE_URL}" != */ ]]; then
  BASE_URL="${BASE_URL}/"
fi

hdr_api_key=()
if [[ -n "${API_KEY}" ]]; then
  hdr_api_key=(-H "X-API-Key: ${API_KEY}")
fi

echo "BASE_URL=${BASE_URL}"
echo "DEVICE_ID=${DEVICE_ID}"
if [[ -n "${API_KEY}" ]]; then
  echo "API_KEY=(set)"
else
  echo "API_KEY=(empty)"
fi
echo

echo "==> 1) GET /health"
curl -sS -i "${hdr_api_key[@]}" "${BASE_URL}health"
echo
echo

echo "==> 2) POST /sos (fake payload)"
payload="$(cat <<JSON
{
  "device_id": "${DEVICE_ID}",
  "event_type": "fall_detected",
  "client_ts": "2026-04-09T09:40:00Z",
  "gps_lat": 31.2304,
  "gps_lng": 121.4737,
  "accuracy_m": 12.5
}
JSON
)"
curl -sS -i "${hdr_api_key[@]}" \
  -H "Content-Type: application/json" \
  -d "${payload}" \
  "${BASE_URL}sos"
echo
echo

if [[ -n "${IMAGE_PATH}" ]]; then
  echo "==> 3) POST /vision/identify (image=${IMAGE_PATH})"
  curl -sS -i "${hdr_api_key[@]}" \
    -X POST "${BASE_URL}vision/identify" \
    -F "image=@${IMAGE_PATH}" \
    -F "scene=generic" \
    -F "lang=zh-CN" \
    -F "device_id=${DEVICE_ID}" \
    -F "client_ts=2026-04-09T09:40:00Z" \
    -F "gps_lat=31.2304" \
    -F "gps_lng=121.4737"
  echo
else
  echo "==> 3) POST /vision/identify skipped (set IMAGE_PATH=/path/to/test.jpg to enable)"
fi

