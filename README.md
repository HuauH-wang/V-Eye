# V-Eye

**户外智能识别与团队协作系统**

**简体中文** | [English](README.en.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

V-Eye 将 Android 客户端、ESP32 BLE 设备和云端服务连接起来，为户外探索与野外作业提供图像识别、位置共享、团队沟通和工作报告。图像由云端多模态模型分析；SOS 事件和队伍协作由业务 API 处理。

## 功能概览

| 功能 | 说明 |
| --- | --- |
| 智能识图 | 有毒植物、药用植物与通用场景识别，返回候选名称、置信度、风险等级和建议 |
| BLE 联机 | Android 提供 GATT 通信与图像分片接收协议；ESP32-C3 示例固件用于 BLE 文字收发联调 |
| 图鉴与历史 | Room 本地记录、图片缓存与云端识别记录查询 |
| 团队协作 | 创建队伍、邀请成员、群聊、地图轨迹与位置共享 |
| SOS 上报 | 上传设备、时间和位置等事件信息，并在客户端提示 |
| AI 工作报告 | 汇总作业记录，通过文本模型生成报告，支持查看与编辑 |
| 多端访问 | Android 客户端与 React Web 控制台共用云端 API |

## 系统架构

![V-Eye 中英双语系统架构：ESP32 BLE 设备、Android、FastAPI、Web 控制台、vLLM 与 PostgreSQL](docs/images/veye-architecture-bilingual.png)

识别流程：设备图像 → Android 分片组装 → 云端 API → 视觉模型 → 结构化结果与记录。完整的设备拍照与传图链路需要实现对应协议的硬件和固件；仓库中的 ESP32-C3 示例尚不包含相机采集与图像发送。

报告流程通过调度脚本切换视觉模型和文本模型。报告生成期间，识图接口返回 `503 report_generation_in_progress`；流程结束后会尝试恢复视觉模型。

## 仓库结构

```text
V-Eye/
├── veye-cloud/          # FastAPI、模型服务、PostgreSQL 与 Web 控制台
├── veye-android/        # Kotlin、Jetpack Compose、BLE 与 Room
│   └── firmware/        # ESP32-C3 BLE 示例固件
├── docs/images/         # README 架构图
├── scripts/             # 仓库工具脚本
├── LICENSE
├── README.md            # 中文说明
└── README.en.md         # English documentation
```

| 子项目 | 职责 | 文档 |
| --- | --- | --- |
| [veye-cloud](veye-cloud/) | 云端业务 API、模型推理与 Web 控制台 | [部署与配置](veye-cloud/README.md) |
| [veye-android](veye-android/) | 移动端、BLE 接入与本地数据；当前版本 `0.6.4` | [运行与 BLE 协议](veye-android/README.md) |

Android 版本以 [version.properties](veye-android/version.properties) 为准。

## 快速开始

### 前置要求

| 组件 | 要求 |
| --- | --- |
| 云端推理 | Linux、兼容的 NVIDIA GPU / CUDA 环境、Python 3.10+；显存需求取决于模型和 vLLM 配置 |
| 数据库 | PostgreSQL；示例脚本使用 Docker Compose 启动 PostgreSQL 16 |
| Web 控制台 | Node.js ≥ 18（仓库声明的最低版本） |
| Android | Android Studio、JDK 17、Android SDK 35；最低 Android 8.0（API 26），BLE 与定位联调使用真机 |

### 1. 获取代码

```bash
git clone https://github.com/HuauH-wang/V-Eye.git
cd V-Eye
```

后续云端命令在 Linux / GPU 主机上执行，Android 工程可在开发电脑上打开。

### 2. 配置并启动云端

```bash
cd veye-cloud
cp .env.example .env
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

启动前编辑 `.env`，重点检查：

| 配置 | 用途 |
| --- | --- |
| `DATABASE_URL` | 数据库连接；凭据须与 PostgreSQL 配置一致 |
| `API_KEY` | 识图、识别记录查询及 SOS 上报等接口使用的可选共享密钥 |
| `JWT_SECRET` | 用户登录令牌的签名密钥；部署时替换示例值 |
| `VLLM_MODEL` | 视觉模型 ID 或本地目录，默认 Qwen2.5-VL-7B-Instruct |
| `REPORT_VLLM_MODEL` | 报告模型 ID 或本地目录；示例值是 AutoDL 路径，需要按实际环境修改 |
| `IMAGE_DIR` / `TILE_CACHE_DIR` | 图片与地图瓦片目录；确保 API 进程有写入权限 |
| `CORS_ALLOW_ORIGINS` | 浏览器直接跨域访问 API 时使用；同源部署或 Vite 代理通常不需要 |

模型启动脚本默认启用离线加载。先准备模型权重并设置正确路径；若首次通过模型 ID 在线下载，在 `.env` 中设置 `HF_HUB_OFFLINE=0` 和 `TRANSFORMERS_OFFLINE=0`，并确保主机可以访问模型源。

示例 Compose 使用 `veye_password` 和 `/data/veye/postgres`。按实际部署修改 [docker-compose.yml](veye-cloud/docker-compose.yml)，并同步 `DATABASE_URL`。

```bash
./scripts/start_postgres.sh
./scripts/model_switch.sh start_vision  # 后台启动视觉模型，等待服务就绪
./scripts/start_api.sh                 # FastAPI :6006，前台运行
```

API 运行时保持该终端打开。视觉模型默认监听 `127.0.0.1:8001`，报告模型监听 `127.0.0.1:8002`。报告任务由调度器切换模型，无需同时启动两个服务；模型日志位于 `veye-cloud/logs/`。

在另一终端检查：

```bash
curl http://127.0.0.1:6006/health
curl http://127.0.0.1:8001/v1/models
```

`/health` 表示 API 可以响应，并返回模型配置；它不检查数据库连接或模型推理是否成功。可用下方图像识别示例验证完整请求链路。

### 3. 启动 Web 控制台

在仓库根目录打开另一终端：

```bash
cd veye-cloud/frontend
npm ci
npm run dev
```

本地访问 `http://localhost:5173`，页面中的 **API Base** 留空即可通过 Vite 的 `/api` 代理访问 FastAPI。默认代理目标为 `http://127.0.0.1:6006`；API 在其他主机时设置 `VITE_API_PROXY_TARGET`。

AutoDL 开发环境使用 `npm run dev:autodl`（端口 `6008`）。同源部署执行 `npm run build`，将 Web 构建到 `veye-cloud/app/static/web/`，然后重启 API，通过 API 地址访问控制台。

### 4. 运行 Android 客户端

1. 用 Android Studio 打开 `veye-android/`，使用 JDK 17 并同步 Gradle。
2. 复制 `local.properties.example` 为 `local.properties`，设置 `sdk.dir`；使用高德地图时配置对应包名和签名的 Android Key。Release 签名配置在打包发布时填写。
3. 真机运行，在 App 的「云端连接」设置中填写 Base URL 和 API Key，例如 `https://your-domain.example/`。
4. 如需修改内置默认地址，编辑 [CloudConfig.kt](veye-android/app/src/main/java/com/veye/mobile/cloud/CloudConfig.kt) 中的 `DEFAULT_BASE_URL`，地址以 `/` 结尾。
5. 授予蓝牙、定位及通知等所需权限，连接与 App 协议匹配的 BLE 设备。

手机上的 `127.0.0.1` 指向手机自身。云端联调应使用可达的主机地址或 HTTPS 反向代理地址。详见 [Android 文档](veye-android/README.md)。

## API 参考

服务启动后，在 `http://127.0.0.1:6006/docs` 查看交互式接口文档；完整参数与响应结构以运行服务的 OpenAPI 文档为准。

| 端点 | 方法 | 说明 |
| --- | --- | --- |
| `/health` | GET | API 响应与模型配置信息 |
| `/vision/identify` | POST | `multipart/form-data` 图像识别 |
| `/vision/records` | GET | 查询识别记录 |
| `/sos` | POST | SOS 事件上报 |
| `/auth/*` | 多种 | 注册、登录与用户信息 |
| `/teams/*` | 多种 | 队伍、成员、邀请与群聊 |
| `/map/*` | 多种 | 地图瓦片、轨迹与位置上报 |
| `/reports/*` | 多种 | 报告预览、生成任务与报告编辑 |

鉴权分为两类：配置 `API_KEY` 后，受共享密钥保护的接口需携带 `X-API-Key`；组队、群聊、位置与报告等用户接口需要登录取得的 `Authorization: Bearer <token>`。`/health` 无需共享密钥。共享密钥不能代替用户登录。

### 图像识别示例

将密钥替换为 `.env` 中的值，将图片路径替换为实际文件。未配置 `API_KEY` 时可移除请求头：

```bash
curl -X POST http://127.0.0.1:6006/vision/identify \
  -H 'X-API-Key: your-api-key' \
  -F 'image=@/path/to/plant.jpg' \
  -F 'scene=toxic_plant' \
  -F 'lang=zh-CN'
```

`scene` 支持 `toxic_plant`、`medicine` 和 `generic`；`lang` 默认 `zh-CN`，可设为 `en` 请求英文识别文本。主要响应字段包括 `label_main`、`confidence`、`candidates`、`risk_level`、`risk_tags`、`summary`、`advice`、`details` 与 `latency_ms`。

## 常见问题

| 现象 | 检查方向 |
| --- | --- |
| `401 unauthorized` | 识图或 SOS 请求的 `X-API-Key` 是否与服务端一致 |
| `401 not_authenticated` | 用户接口是否携带有效的 Bearer 登录令牌 |
| `503 database_unavailable` | PostgreSQL 是否启动，`DATABASE_URL` 是否正确 |
| `503 report_generation_in_progress` | 报告任务正在占用模型；任务完成并恢复视觉模型后重试 |
| 模型启动失败 | 权重目录、离线加载设置、CUDA / vLLM 环境与可用显存 |
| Web 无法连接 API | API 是否启动，Vite 代理目标或跨域配置是否正确 |
| BLE 没有收到图像 | 固件是否实现图像分片协议；仓库示例仅提供文字收发 |

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 云端 API 与数据 | FastAPI、SQLAlchemy、PostgreSQL |
| 视觉推理 | vLLM + Qwen2.5-VL-7B-Instruct |
| 报告生成 | vLLM + Qwen2.5-7B-Instruct |
| Web UI | Vite 5、React 18、TypeScript、Leaflet / 高德地图 |
| Android | Kotlin、Jetpack Compose、Room、Retrofit、BLE GATT |
| 示例固件 | ESP32-C3，见 [firmware](veye-android/firmware/) |

## 使用范围

云端识别、同步和 SOS 上报依赖网络及服务可用性。植物识别与风险提示不能用于判断植物可食用或可用于治疗；SOS 上报是系统内的事件记录，不保证送达公共救援机构。

## 参与贡献

欢迎通过 [Issues](https://github.com/HuauH-wang/V-Eye/issues) 反馈问题或提出功能建议，也欢迎提交 Pull Request。反馈时请提供组件、版本、复现步骤和相关日志，并说明已执行的构建或联调检查；涉及 BLE 的改动请注明设备与固件环境。提交前移除 `.env`、令牌、签名文件及私人服务地址。

请使用与本人 GitHub 账号关联的提交邮箱（也可使用 GitHub 提供的 `noreply` 邮箱），并将贡献合入默认分支 `main`，便于 GitHub 正确关联贡献记录。

## 许可证

本项目采用 [MIT License](LICENSE)。

