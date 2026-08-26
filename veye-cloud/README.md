# V-Eye 云端推理服务（FastAPI:6006 + vLLM:8001 + PostgreSQL:5432）

目标：在 AutoDL 部署一套“手机端 App 可直接对接”的云端服务：

- **公网（AutoDL 反向代理端口 6006）**：FastAPI 对外提供业务 API
- **内网 8001**：vLLM 承载一体化多模态 VLM（OpenAI 兼容 `/v1/chat/completions`）
- **内网 5432**：PostgreSQL 落库识别记录与 SOS 事件

## 1) 目录结构

- `app/`：FastAPI 服务代码
- `frontend/`：Web 控制台（Vite + React + TS，`npm run build` 产物输出到 `app/static/web`）
- `scripts/`：启动脚本（vLLM / API / 一键）
- `requirements.txt`：Python 依赖
- `.env.example`：环境变量示例（不要提交 `.env`）

## 2) 环境变量

复制并编辑：

```bash
cp .env.example .env
```

- **可选**
  - `API_KEY`：如果设置了它，则会启用鉴权（Android 每次请求 Header 带 `X-API-Key`）；如果不设置或置空，则不鉴权。
  - `CORS_ALLOW_ORIGINS`：逗号分隔的浏览器 Origin（如 `http://localhost:5173`）。仅当前端与 API **不同源**且直连 API 时需要；**同源**（见下节「构建并挂载 Web UI」）可不设。

## 3) 安装依赖

在 `veye-cloud/`：

```bash
python -m venv .venv
source .venv/bin/activate
pip install -U pip wheel
pip install -r requirements.txt
```

## 4) 安装 Docker（用于 PostgreSQL）

如果你的 AutoDL 机器还没装 Docker（`docker: command not found`），按 Ubuntu 常见方式安装：

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo \"$VERSION_CODENAME\") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
docker --version
docker compose version
```

> 如果你的 AutoDL 系统不是 Ubuntu，告诉我发行版信息（如 Debian/CentOS），我会把这段改成对应命令。

## 5) 启动 PostgreSQL（Docker Compose）

使用 compose（推荐）：

```bash
./scripts/start_postgres.sh
```

或直接 docker run：

```bash
docker run -d --name veye-postgres \
  -e POSTGRES_USER=veye \
  -e POSTGRES_PASSWORD=veye_password \
  -e POSTGRES_DB=veye \
  -p 5432:5432 \
  -v /data/veye/postgres:/var/lib/postgresql/data \
  postgres:16
```

> 如果 PostgreSQL 暂时不可用：服务仍可启动，但 `/vision/identify` 与 `/sos` 会返回 `503 database_unavailable`（便于你先联调网络与反代）。

## 6) 启动 vLLM（内网 8001）

先安装依赖（如果你把 vLLM 和 API 放同一 venv）：

```bash
pip install -r requirements.txt
```

```bash
./scripts/start_vllm.sh
```

自检（可选）：

```bash
curl http://127.0.0.1:8001/v1/models
```

> 说明：`vllm` 会依赖 CUDA/驱动环境；在 AutoDL 的 GPU 机上通常可用。如果安装 `vllm` 时遇到编译/依赖问题，把报错贴我，我会按你的 CUDA/驱动版本给出可用的安装方式（例如换 pip 源、指定版本或改用容器化）。

## 7) 启动 FastAPI（公网 6006）

```bash
./scripts/start_api.sh
```

健康检查：

```bash
curl http://127.0.0.1:6006/health
```

## 8) API 说明（Android 直连）

鉴权头（可选）：

- 如果你设置了 `API_KEY`：每次请求带 `X-API-Key: <API_KEY>`
- 如果你没设置 `API_KEY`：无需任何鉴权头

### 7.1 `GET /health`

返回示例：

```json
{
  "status": "ok",
  "model": "Qwen/Qwen2.5-VL-7B-Instruct",
  "vllm": { "base_url": "http://127.0.0.1:8001" }
}
```

### 7.2 `POST /vision/identify`

`multipart/form-data`：

- `image`: JPEG/PNG
- `scene`: `toxic_plant | medicine | generic`
- `lang`: 可选（默认 `zh-CN`）
- `device_id/gps_lat/gps_lng/client_ts`: 可选

返回字段固定（便于 App 展示与 TTS）：

- `label_main/confidence/candidates/risk_level/risk_tags/summary/advice/details/latency_ms`

### 7.3 `POST /sos`

JSON body：

- `device_id/event_type/client_ts/gps_lat/gps_lng/accuracy_m`

## 9) 反向代理（AutoDL）

把公网域名反代到 FastAPI 的 `6006` 即可；vLLM `8001` 不要暴露公网。

## 10) Web 控制台（`frontend/`）

前端代码在 **`veye-cloud` 仓库**下的 `frontend/`，与 **`veye-android`（手机 App）仓库是两套目录**。

请先进入本仓库根目录（下面以 `~/veye-cloud` 为例，请改成你机器上的实际路径）：

```bash
cd ~/veye-cloud          # 必须是包含 app/、frontend/、scripts/ 的这一层
ls frontend/package.json # 应能列出文件；若报错说明目录不对
```

### 10.0 Node.js 版本（必读）

本前端使用 **Vite 5**，需要 **Node.js ≥ 18**（推荐 **20 LTS**）。系统自带的 `node v12` 会出现 `SyntaxError: Unexpected reserved word`（不支持顶层 `await`），**必须先升级 Node** 再执行 `npm run dev`。

**方式 C：官方二进制包（AutoDL 上最稳，推荐）** — 不依赖 apt 换源、也不依赖 `git clone` 装 nvm；默认解压到 **`~/autodl-tmp`**（数据盘，空间较大）：

```bash
cd ~/veye-cloud
chmod +x scripts/install_node20_portable.sh
./scripts/install_node20_portable.sh
source ~/.bashrc
node -v    # 应 v20.x
cd ~/veye-cloud/frontend
rm -rf node_modules package-lock.json
npm install
npm run dev
```

可选环境变量：`NODE_VERSION=20.18.0`、`NODE_INSTALL_DIR=/root/autodl-tmp`。

**方式 B：NodeSource（Ubuntu/Debian，需 sudo）**

整行管道执行（**不要**拆成 `sudo -E` 单独一行，否则易报错 `bash: -E: command not found`）：

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo bash
sudo apt-get update
sudo apt-get install -y nodejs
node -v   # 须 >= 18；若仍是 12，请改用方式 C
```

**方式 A：nvm（若 git 克隆失败，可先强制 HTTP/1.1）**

```bash
git config --global http.version HTTP/1.1
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
cd ~/veye-cloud/frontend
nvm install
nvm use
node -v
rm -rf node_modules package-lock.json
npm install
npm run dev
```

### 10.1 本地开发

Vite 将 `/api` 代理到 FastAPI，避免浏览器 CORS：

```bash
cd ~/veye-cloud/frontend
npm install
npm run dev
```

默认监听 **`0.0.0.0:5173`**（可通过环境变量 `VITE_DEV_HOST` / `VITE_DEV_PORT` 覆盖，见 `vite.config.ts`）。

**AutoDL 反向代理仅开放 6006 与 6008 时**：一般 **6006** 已给 FastAPI，前端开发服请绑 **6008**，并用控制台给出的 **6008 反代域名** 访问：

```bash
cd ~/veye-cloud/frontend
npm run dev:autodl
# 等价：VITE_DEV_PORT=6008 VITE_DEV_HOST=0.0.0.0 npm run dev
```

浏览器使用实例详情里的 **`service_6008_domain`（https + 8443）** 打开页面；「API Base」仍留空，由 Vite 把 `/api` 转到本机 `http://127.0.0.1:6006` 的 FastAPI。

若页面提示 **Blocked request … not allowed**，说明 Vite 校验了 `Host`：本仓库已在 `vite.config.ts` 默认 **`allowedHosts: true`**（允许经反代域名访问）。若你改过 `VITE_ALLOWED_HOSTS`，可设为 `all` 或逗号分隔的主机名，例如：`VITE_ALLOWED_HOSTS=uu885493-990d-93896148.westc.seetacloud.com`。

默认代理目标为 `http://127.0.0.1:6006`，可通过环境变量覆盖：

```bash
cd ~/veye-cloud/frontend
VITE_API_PROXY_TARGET=http://127.0.0.1:6006 npm run dev
```

非 AutoDL 时本地一般为 `http://localhost:5173`。在页面「API Base」留空即可走 `/api` 代理；若填写绝对地址（如 `https://你的域名:6006`），请求将直连该地址（跨域时需在 `.env` 配置 `CORS_ALLOW_ORIGINS`）。

### 10.2 与 API 同端口部署（构建进 FastAPI）

```bash
cd ~/veye-cloud/frontend
npm install
npm run build
# 然后照常启动 FastAPI；浏览器访问 http://<host>:6006/ 打开控制台
```

构建产物目录为 `app/static/web/`（已在仓库 `.gitignore` 忽略，勿提交构建文件）。

### 10.3 常见错误

- **`cd: veye-cloud/frontend: No such file or directory`**：当前不在 `veye-cloud` 仓库里。若在 `veye-android/.../cloud` 等路径下，请先 `cd` 到 **`veye-cloud` 根目录**再进入 `frontend/`。
- **`npm ERR! enoent ... package.json`**：在错误目录执行了 `npm install`。请确认 `pwd` 下能看到 `frontend/package.json`（即先 `cd .../veye-cloud/frontend`）。
- **机器上还没有 `veye-cloud` 仓库**：需要先 `git clone`（或从本机拷贝）包含 `frontend/` 的 **veye-cloud** 项目，不能只在 `veye-android` 里找前端。
- **`SyntaxError: Unexpected reserved word`（运行 `vite` 时）**：当前 Node 过旧（常见为 v12）。见上文 **§10.0**，升级到 Node 18+ 后删除 `node_modules` 与 `package-lock.json` 再 `npm install`。
- **`sh: 1: vite: not found`**：在旧 Node 下装的依赖未正确生成 `node_modules/.bin/vite`，或 PATH 未指向新 Node。升级 Node 后务必 **`rm -rf node_modules package-lock.json && npm install`**，并用 `npx vite` 验证：`npx vite --version`。
- **`bash: -E: command not found`**：NodeSource 管道被错误拆开。请用 **`curl ... | sudo bash`**（见 §10.0 方式 B 整行）。
- **nvm 安装报 `RPC failed` / HTTP2**：先执行 `git config --global http.version HTTP/1.1` 再装 nvm；或直接改用 **方式 C**。

