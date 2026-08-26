#!/usr/bin/env bash
# 在无法升级系统 apt node、nvm 克隆失败等环境下，下载官方 Node 20 二进制到数据盘（默认可写、空间较大）。
# 用法：
#   chmod +x scripts/install_node20_portable.sh
#   ./scripts/install_node20_portable.sh
#   source ~/.bashrc   # 若已按脚本提示追加了 PATH；或新开终端
#   node -v && cd frontend && rm -rf node_modules package-lock.json && npm install && npm run dev

set -euo pipefail

VERSION="${NODE_VERSION:-20.18.0}"
ARCH="linux-x64"
TARBALL="node-v${VERSION}-${ARCH}.tar.xz"
# 默认装到数据盘，避免占满系统盘；可通过 NODE_INSTALL_DIR 覆盖
DEST="${NODE_INSTALL_DIR:-${HOME}/autodl-tmp}"
URL="https://nodejs.org/dist/v${VERSION}/${TARBALL}"
EXTRACT_DIR="node-v${VERSION}-${ARCH}"

mkdir -p "${DEST}"
cd "${DEST}"

if [[ -d "${EXTRACT_DIR}" && -x "${EXTRACT_DIR}/bin/node" ]]; then
  echo "[info] 已存在 ${DEST}/${EXTRACT_DIR}，跳过下载。"
else
  echo "[info] 下载 ${URL} ..."
  if command -v wget >/dev/null 2>&1; then
    wget -O "${TARBALL}" "${URL}"
  else
    curl -fL "${URL}" -o "${TARBALL}"
  fi
  echo "[info] 解压 ..."
  tar -xf "${TARBALL}"
  rm -f "${TARBALL}"
fi

NODE_HOME="${DEST}/${EXTRACT_DIR}"
export PATH="${NODE_HOME}/bin:${PATH}"

MARK_BEGIN="# >>> veye-cloud portable node ${VERSION}"
MARK_END="# <<< veye-cloud portable node"
BASHRC="${HOME}/.bashrc"

if ! grep -qF "${MARK_BEGIN}" "${BASHRC}" 2>/dev/null; then
  {
    echo ""
    echo "${MARK_BEGIN}"
    echo "export PATH=\"${NODE_HOME}/bin:\${PATH}\""
    echo "${MARK_END}"
  } >>"${BASHRC}"
  echo "[info] 已追加 PATH 到 ${BASHRC}，请执行: source ${BASHRC}"
else
  echo "[info] ${BASHRC} 中已有 portable node 配置，跳过写入。"
fi

echo "[info] 当前 shell 已临时生效 PATH，node 版本："
"${NODE_HOME}/bin/node" -v
"${NODE_HOME}/bin/npm" -v

echo ""
echo "接下来在 frontend 目录执行："
echo "  cd ~/veye-cloud/frontend   # 按你的仓库路径"
echo "  rm -rf node_modules package-lock.json"
echo "  npm install && npm run dev"
