# V-Eye

**户外智能识别与团队协作系统 · Outdoor Smart Recognition and Team Collaboration System**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

面向户外/野外场景的**智能识别与团队协作**系统，包含 ESP32 设备、Android 客户端和云端 Web 控制台。设备通过 BLE 向手机传输图像与事件，手机上传图像至云端多模态模型识别；FastAPI 与 PostgreSQL 提供记录保存、SOS 上报、队伍协作和位置共享，文本模型用于生成工作报告。

## 功能概览

- **智能识图** — 有毒植物、药用植物、通用场景识别（Qwen2.5-VL）
- **BLE 联机** — ESP32-C3 手表/相机，GATT 分片传图协议
- **团队协作** — 组队、群聊、地图轨迹、位置共享
- **SOS 上报** — 紧急情况一键上报
- **AI 报告** — 基于 LLM 的工作报告生成
- **多端支持** — Android 客户端 + Web 控制台

## 系统架构

![V-Eye 系统架构](docs/images/veye-architecture.png)

主要数据流：

1. **图像识别**：ESP32 图像分片 → Android BLE 组包 → `/vision/identify` → vLLM 视觉模型 → 结构化结果、数据库记录与客户端展示。
2. **团队协作**：Android / Web → FastAPI → 用户、队伍、群聊、位置与 SOS 记录。
3. **工作报告**：报告任务 → 模型调度 → 文本模型生成报告。视觉服务默认使用 `8001`，报告服务使用 `8002`；当前代码包含两种模式的切换与互斥调度。

## 仓库结构

```
V-Eye/
├── veye-cloud/          # 云端：FastAPI + vLLM + PostgreSQL + Web 控制台
├── veye-android/        # Android：Jetpack Compose + BLE + Room
├── docs/images/         # 系统架构图
├── scripts/             # 仓库工具脚本（打包等）
├── LICENSE
└── README.md
```

| 子项目 | 技术栈 | 职责 | 文档 |
|--------|--------|------|------|
| [veye-cloud](./veye-cloud/) | FastAPI, vLLM, PostgreSQL, React | 业务 API、模型推理、记录存储与 Web 控制台 | [云端说明](./veye-cloud/README.md) |
| [veye-android](./veye-android/) | Kotlin, Compose, BLE, Room | BLE 设备接入、识别结果、记录缓存与团队交互 | [Android 说明](./veye-android/README.md) |

## 快速开始

先克隆完整仓库：

```bash
git clone https://github.com/HuauH-wang/V-Eye.git
cd V-Eye
```

下面的云端初始化命令从仓库根目录执行；各服务启动命令从 `veye-cloud/` 执行。

### 前置要求

| 组件 | 要求 |
|------|------|
| 云端 | Linux + CUDA GPU、Docker、Python 3.10+、Node.js ≥ 18 |
| Android | Android Studio、真机（BLE）、minSdk 26 |

### 1. 云端（veye-cloud）

初始化 Python 环境与配置：

```bash
cd veye-cloud
cp .env.example .env
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

编辑 `.env` 中的 `API_KEY`、`JWT_SECRET`、`DATABASE_URL`、`VLLM_MODEL` 等配置。示例值需要按部署环境填写；启动脚本默认离线加载模型，请先准备模型权重，并将 `VLLM_MODEL` 指向可用模型目录。报告功能还需要配置 `REPORT_VLLM_MODEL`。

启动数据库：

```bash
bash scripts/start_postgres.sh
```

分别在两个终端中进入 `veye-cloud/`，激活虚拟环境并启动服务。两个命令都会持续占用各自终端：

```bash
# 终端 A：视觉模型，127.0.0.1:8001
source .venv/bin/activate
bash scripts/start_vllm.sh
```

```bash
# 终端 B：FastAPI，默认端口 6006
source .venv/bin/activate
bash scripts/start_api.sh
```

检查 API 是否可访问：

```bash
curl http://127.0.0.1:6006/health
```

接口文档位于 `http://127.0.0.1:6006/docs`。健康检查不代表模型推理、数据库写入或 BLE 联调均已通过。

Web 控制台在另一个终端启动，从仓库根目录执行：

```bash
cd veye-cloud/frontend
npm install
npm run dev                  # 默认开发端口 5173
# AutoDL 环境可改用：npm run dev:autodl  # 端口 6008
```

也可执行 `npm run build`，将控制台构建到 `veye-cloud/app/static/web/`，由 FastAPI 提供页面。

详见 [veye-cloud/README.md](./veye-cloud/README.md)。

### 2. Android（veye-android）

1. 用 Android Studio 打开 `veye-android/`
2. 在 `app/src/main/java/com/veye/mobile/cloud/CloudConfig.kt` 中设置 `DEFAULT_BASE_URL`，或在 App 内保存地址覆盖；地址需以 `/` 结尾，并可从手机访问。
3. 在 `veye-android/` 内复制 `local.properties.example` 为 `local.properties`，填写本机 SDK 路径及所需的签名 / 高德地图配置。
4. 使用真机运行，开启蓝牙并授予 BLE、定位等所需权限。若云端启用了 `API_KEY`，客户端也需配置对应 Key。

Android 当前版本号以 [`version.properties`](./veye-android/version.properties) 为准。

详见 [veye-android/README.md](./veye-android/README.md)。

## API 参考

Android / Web 共用以下 REST 端点：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/health` | GET | 健康检查 |
| `/vision/identify` | POST | 图像识别（`scene`: `toxic_plant` / `medicine` / `generic`） |
| `/sos` | POST | SOS 事件上报 |
| `/auth/*` | — | 用户注册、登录、JWT |
| `/teams/*` | — | 队伍、群聊、邀请 |
| `/map/*` | — | 地图瓦片、轨迹、位置 |
| `/reports/*` | — | AI 工作报告 |

鉴权按接口区分：`/vision/identify`、`/sos` 等接口在配置 `API_KEY` 后需要 `X-API-Key`；用户、队伍和报告等业务接口按路由要求使用登录返回的 Bearer Token。完整参数及响应以 FastAPI 的 `/docs` 和对应路由代码为准。

## 技术栈

| 层级 | 技术 |
|------|------|
| 云端 API | FastAPI, SQLAlchemy, Alembic, PostgreSQL |
| 视觉模型 | vLLM + Qwen2.5-VL-7B-Instruct |
| 报告模型 | vLLM + Qwen2.5-7B-Instruct |
| Web UI | Vite 5, React 18, TypeScript, Leaflet |
| Android | Kotlin, Jetpack Compose, Room, Retrofit, BLE GATT |
| 固件 | ESP32-C3（`veye-android/firmware/`） |

## 参与贡献

欢迎通过 Issue 提交复现步骤，或通过 Pull Request 改进代码与文档。提交时说明影响的子项目，以及已执行的构建或联调检查；涉及 BLE 的改动请注明设备与固件环境。

请使用与本人 GitHub 账号关联的提交邮箱（也可使用 GitHub 提供的 `noreply` 邮箱），并将贡献合入默认分支 `main`，便于 GitHub 正确关联贡献记录。

## 许可证

本项目采用 [MIT License](LICENSE) 开源。
