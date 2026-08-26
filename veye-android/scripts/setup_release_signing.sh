#!/usr/bin/env bash
# 生成 release 签名 keystore，写入 local.properties，并打印高德控制台所需的 SHA1。
set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE_DIR="${KEYSTORE_DIR:-keystore}"
KEYSTORE_FILE="${KEYSTORE_FILE:-${KEYSTORE_DIR}/veye-release.keystore}"
KEY_ALIAS="${RELEASE_KEY_ALIAS:-veye}"
VALIDITY_DAYS="${RELEASE_KEY_VALIDITY_DAYS:-10000}"
LOCAL_PROPS="local.properties"

if ! command -v keytool >/dev/null 2>&1; then
  echo "[err] 未找到 keytool，请安装 JDK 并确保 keytool 在 PATH 中" >&2
  exit 1
fi

mkdir -p "${KEYSTORE_DIR}"

if [[ -z "${RELEASE_STORE_PASSWORD:-}" ]]; then
  if [[ -f "${KEYSTORE_FILE}" ]]; then
    echo "[err] keystore 已存在但未设置 RELEASE_STORE_PASSWORD 环境变量" >&2
    echo "      示例: RELEASE_STORE_PASSWORD='你的密码' $0" >&2
    exit 2
  fi
  RELEASE_STORE_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
  echo "[info] 已自动生成 RELEASE_STORE_PASSWORD（仅显示一次，请妥善保存）："
  echo "       ${RELEASE_STORE_PASSWORD}"
fi

RELEASE_KEY_PASSWORD="${RELEASE_KEY_PASSWORD:-${RELEASE_STORE_PASSWORD}}"

if [[ ! -f "${KEYSTORE_FILE}" ]]; then
  echo "[info] 生成 release keystore → ${KEYSTORE_FILE}"
  keytool -genkeypair -v \
    -keystore "${KEYSTORE_FILE}" \
    -alias "${KEY_ALIAS}" \
    -keyalg RSA -keysize 2048 -validity "${VALIDITY_DAYS}" \
    -storepass "${RELEASE_STORE_PASSWORD}" \
    -keypass "${RELEASE_KEY_PASSWORD}" \
    -dname "CN=V-Eye, OU=Mobile, O=V-Eye, L=Wuhan, ST=Hubei, C=CN"
else
  echo "[info] 使用已有 keystore: ${KEYSTORE_FILE}"
fi

touch "${LOCAL_PROPS}"

upsert_prop() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "${LOCAL_PROPS}" 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${value}|" "${LOCAL_PROPS}"
  else
    printf '%s=%s\n' "${key}" "${value}" >>"${LOCAL_PROPS}"
  fi
}

# 相对项目根目录，便于不同机器路径一致
REL_KEYSTORE="${KEYSTORE_FILE}"
upsert_prop "RELEASE_STORE_FILE" "${REL_KEYSTORE}"
upsert_prop "RELEASE_STORE_PASSWORD" "${RELEASE_STORE_PASSWORD}"
upsert_prop "RELEASE_KEY_ALIAS" "${KEY_ALIAS}"
upsert_prop "RELEASE_KEY_PASSWORD" "${RELEASE_KEY_PASSWORD}"

echo
echo "==> 高德 Android Key（Release）填写参考"
echo "PackageName:     com.veye.mobile"
echo "发布版 SHA1:"
keytool -list -v \
  -keystore "${KEYSTORE_FILE}" \
  -alias "${KEY_ALIAS}" \
  -storepass "${RELEASE_STORE_PASSWORD}" 2>/dev/null | awk -F': ' '/SHA1:/ {print "                 " $2}'
echo
echo "调试版 SHA1（Debug Key 另建，PackageName=com.veye.mobile.debug）:"
keytool -list -v \
  -keystore "${HOME}/.android/debug.keystore" \
  -alias androiddebugkey \
  -storepass android -keypass android 2>/dev/null | awk -F': ' '/SHA1:/ {print "                 " $2}' || echo "                 （未找到 ~/.android/debug.keystore）"
echo
echo "[done] local.properties 已更新；请备份 ${KEYSTORE_FILE} 与密码。"
