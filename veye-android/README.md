# V-Eye Mobile（Android / Jetpack Compose）

本工程是 `app.txt` 与 `cursor_app_txt_file_content.md` 中“手机端 App”部分的落地实现骨架，目标是：

- **BLE 联机**：扫描/连接手表（GATT Client），接收事件（含 SOS）与图像分片
- **云端推理**：调用 AutoDL 云端 FastAPI（公网端口 6006）接口 `/health`、`/vision/identify`、`/sos`
- **图鉴与历史**：Room 本地数据库 + 图片文件缓存
- **高级 UI**：暗色高级风格、动效、卡片化结果、功耗展示板、队伍态势（示意）
- **TTS/通知**：识别结果播报、SOS 强提醒（本地通知）

## 运行要求

- Android Studio（建议最新版稳定版）
- Android 设备（**BLE 与定位必须真机**）
- 最低 Android 版本：Android 8.0（可按需调整）

## 配置云端 Base URL

编辑：

- `app/src/main/java/com/veye/mobile/cloud/CloudConfig.kt`

将 `BASE_URL` 改为你的 AutoDL 反代域名，例如：

```kotlin
const val BASE_URL = "https://your-domain.example/"
```

> 注意：Base URL 需要以 `/` 结尾（Retrofit 要求）。

## 云端联调（与你现有 veye-cloud 对齐）

你的云端应满足：

- `GET /health`
- `POST /vision/identify`（multipart：`image` + `scene` + 可选字段）
- `POST /sos`

其中 `scene` 只能为：

- `toxic_plant`
- `medicine`
- `generic`

## 真机 BLE 联调步骤（简版）

1. 手机打开蓝牙，授予 App 权限
2. 进入 **设备** 页，开始扫描（后续将把扫描按钮接入 `BleClient`）
3. 连接设备后订阅 notify：
   - `CTRL_TX`
   - `DATA_META`
   - `DATA_TX`
4. 收到 `IMG_META` 后创建 `ImageReceiver`（输出为本地文件）
5. 持续收 `IMG_CHUNK` 组包，缺片则写 `RETX_REQ` 到 `DATA_RX`
6. 组包完成后将图片文件上传到云端 `/vision/identify` 并展示结果 + TTS

## “3 秒软超时提示”

`cursor_app_txt_file_content.md` 要求的体验已在代码层提供机制：

- `com.veye.mobile.domain.SoftTimeout`
- UI 会在 3 秒后提示“仍在分析”，但不会取消真实请求

## 功能入口（页面）

- **设备**：扫描/连接、MTU、模式切换（LPBAM）、事件日志
- **识别**：触发拍照/收图（BLE）→ 上传云端 `/vision/identify` → 结构化结果 + TTS
- **图鉴**：本地记录列表/详情（图片 + 风险级别 + 建议）
- **组队**：示意版队友列表/距离（可后续接地图 SDK）
- **功耗板**：传统 vs LPBAM 对比图（示意 + 可接真实数据）

## 权限说明

Android 12+ 扫描 BLE 需要：

- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`

Android 11- 还需要位置权限（扫描 BLE 被系统归类到位置）：

- `ACCESS_FINE_LOCATION`

此外：

- `POST_NOTIFICATIONS`（Android 13+）

## 备注

BLE 协议与帧格式按 `cursor_app_txt_file_content.md` 的 “V-Eye BLE GATT v0.1” 设计实现（含 Frame header、IMG_META/IMG_CHUNK、RETX_REQ）。

