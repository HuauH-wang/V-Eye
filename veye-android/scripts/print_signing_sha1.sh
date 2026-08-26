#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f local.properties ]]; then
  echo "[err] 未找到 local.properties，请先运行: ./scripts/setup_release_signing.sh" >&2
  exit 1
fi

get_prop() {
  grep "^$1=" local.properties | head -n1 | cut -d= -f2- | tr -d '\r'
}

STORE="$(get_prop RELEASE_STORE_FILE)"
STORE="${STORE:-keystore/veye-release.keystore}"
ALIAS="$(get_prop RELEASE_KEY_ALIAS)"
ALIAS="${ALIAS:-veye}"
PASS="$(get_prop RELEASE_STORE_PASSWORD)"

if [[ ! -f "${STORE}" ]]; then
  echo "[err] keystore 不存在: ${STORE}" >&2
  exit 1
fi
if [[ -z "${PASS}" ]]; then
  echo "[err] local.properties 缺少 RELEASE_STORE_PASSWORD" >&2
  exit 1
fi

echo "Release PackageName: com.veye.mobile"
echo -n "Release SHA1: "
keytool -list -v -keystore "${STORE}" -alias "${ALIAS}" -storepass "${PASS}" 2>/dev/null \
  | awk -F': ' '/SHA1:/ {print $2}'

echo -n "Debug SHA1:   "
keytool -list -v -keystore "${HOME}/.android/debug.keystore" -alias androiddebugkey \
  -storepass android -keypass android 2>/dev/null \
  | awk -F': ' '/SHA1:/ {print $2}' || echo "(debug keystore not found)"
