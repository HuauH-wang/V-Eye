# V-Eye

面向户外/野外场景的智能识别与团队协作系统：**云端推理服务** + **Android 客户端**，通过 BLE 手表/相机采集图像并调用多模态大模型完成识别、SOS 上报与队伍协作。

本仓库为 **veye-cloud**（云端）与 **veye-android**（Android App）的 monorepo 整理版，源代码保持原样，仅新增根目录说明与 `.gitignore`。

## 仓库结构

```
veye/
├── veye-cloud/          # 云端：FastAPI + vLLM + PostgreSQL + Web 控制台
├── veye-android/        # Android：Jetpack Compose + BLE + Room
├── README.md            # 本文件
└── .gitignore
```

| 子项目 | 说明 | 详细文档 |
|--------|------|----------|
| [veye-cloud](./veye-cloud/) | AutoDL 部署的云端 API、VLM 识图、Web 管理台 | [veye-cloud/README.md](./veye-cloud/README.md) |
| [veye-android](./veye-android/) | 手机端 App：BLE 联机、云端识别、图鉴、组队、地图等 | [veye-android/README.md](./veye-android/README.md) |

## 系统架构

```
┌─────────────────┐     BLE GATT      ┌──────────────────┐
│  ESP32 手表/相机  │ ◄──────────────► │  veye-android    │
└─────────────────┘                   │  (Android App)   │
                                      └────────┬─────────┘
                                               │ HTTPS
                                               ▼
┌──────────────────────────────────────────────────────────┐
│                    veye-cloud (AutoDL)                    │
│  FastAPI :6006  ──►  vLLM :8001 (识图) / :8002 (报告)    │
│       │                                                   │
│       └──►  PostgreSQL :5432                              │
│  frontend/ ──► Web 控制台（可选构建进 API 静态目录）        │
└──────────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 云端（veye-cloud）

```bash
cd veye-cloud
cp .env.example .env          # 编辑 API_KEY、DATABASE_URL 等
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
./scripts/start_postgres.sh   # PostgreSQL
./scripts/start_vllm.sh       # vLLM 识图模型（需 GPU）
./scripts/start_api.sh        # FastAPI :6006
```

Web 控制台（需 Node.js ≥ 18）：

```bash
cd veye-cloud/frontend
npm install && npm run dev    # 开发 :5173；AutoDL 可用 npm run dev:autodl
# 或 npm run build 后由 FastAPI 挂载 app/static/web/
```

完整步骤见 [veye-cloud/README.md](./veye-cloud/README.md)。

### 2. Android（veye-android）

1. 用 Android Studio 打开 `veye-android/`
2. 编辑 `app/src/main/java/com/veye/mobile/cloud/CloudConfig.kt` 中的 `BASE_URL` 为云端公网地址
3. 复制 `local.properties.example` 为 `local.properties`（如需签名或高德地图 Key）
4. 真机运行（BLE 需真机，最低 API 26）

详细说明见 [veye-android/README.md](./veye-android/README.md)。

## 核心 API（Android / Web 共用）

| 端点 | 方法 | 说明 |
|------|------|------|
| `/health` | GET | 健康检查 |
| `/vision/identify` | POST | 图像识别（`scene`: `toxic_plant` / `medicine` / `generic`） |
| `/sos` | POST | SOS 事件上报 |
| `/auth/*` | — | 用户注册、登录、JWT |
| `/teams/*` | — | 队伍、群聊、邀请 |
| `/map/*` | — | 地图瓦片、轨迹、位置 |
| `/reports/*` | — | AI 工作报告 |

可选鉴权：设置 `.env` 中 `API_KEY` 后，请求需带 Header `X-API-Key`。

## 技术栈概览

| 层级 | 技术 |
|------|------|
| 云端 API | FastAPI, SQLAlchemy, PostgreSQL |
| 视觉模型 | vLLM + Qwen2.5-VL-7B-Instruct |
| Web UI | Vite 5, React 18, TypeScript |
| Android | Kotlin, Jetpack Compose, Room, Retrofit, BLE GATT |
| 固件 | ESP32-C3（`veye-android/firmware/`） |

## 环境要求

- **云端**：Linux + CUDA GPU（AutoDL 等）、Docker、Python 3.10+、Node.js ≥ 18（前端）
- **Android**：Android Studio、真机（BLE）、minSdk 26

## 安全说明

- 勿将 `.env`、`local.properties`、keystore 提交到 Git（已在根 `.gitignore` 排除）
- 生产环境请修改 `API_KEY`、`JWT_SECRET` 为强随机字符串
- vLLM 端口 8001/8002 仅内网暴露，公网只反代 FastAPI 6006

## 许可证

各子项目若未单独声明许可证，使用前请与项目维护者确认。
