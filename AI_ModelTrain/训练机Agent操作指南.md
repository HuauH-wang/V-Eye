# V-Eye 语音模型 · 高性能训练机 Agent 操作指南

> **读者**：在训练用高性能 PC 上运行的 Cursor Agent / 人工操作者  
> **目标**：训练 **唤醒模型（wake）** 与 **命令模型（cmd）**，量化后部署到 STM32U575 固件 `AI/` 目录  
> **原则**：以 **验证集 / 独立测试集** 指标为准，**禁止** 以训练集 100% 作为停止条件

---

## 0. Agent 快速决策树

```
收到训练任务
    │
    ├─ 仅误唤醒多？ ──► 优先补 noise / 近音干扰，重训 wake
    │
    ├─ 唤醒 OK、命令错？ ──► 查混淆矩阵，补 hard examples，重训 cmd
    │
    ├─ train≈99% 且 val≈85%？ ──► 过拟合，停训 + 加数据/SpecAug，勿继续 epoch
    │
    └─ val/test recall ≥95% 且板端 OK？ ──► stedgeai 生成 C 代码 → 拷回 Keil 工程
```

**每次任务开始前**，Agent 必须先确认：

1. 本机路径是否与下表一致（不一致则全文替换路径变量）
2. GPU 可用（`nvidia-smi`）
3. 原始录音在 `D:\嵌赛\Audio\` 下按 **文件夹名=类别名** 组织
4. 训练完成后产物归档到 `D:\嵌赛\AI_ModelTrain\`

---

## 1. 系统架构（训练侧需对齐）

| 模型 | 类别 | 板端文件前缀 | 用途 |
|------|------|--------------|------|
| **wake** | `noise`, `xiaoou` | `network_wake*` | 唤醒「小欧小欧」 |
| **cmd** | 9 类（见下） | `network_cmd*` | 唤醒后 8s 内识别命令 |

**cmd 类别（顺序必须与 `classes_cmd.txt` 一致）**：

```
chaduiwu, chahuanjing, chayoujian, chayundong, noise,
paizhao, queren, quxiao, xiuxiba
```

**与 Maix 联动的 UART 命令**（仅 3 个，训练仍需全 9 类）：

| 识别类 | Maix 串口 |
|--------|-----------|
| paizhao | PHOTO |
| queren | CONFIRM |
| quxiao | CANCEL |

**特征参数（训练 yaml 必须与板端 `ai_config.h` 一致，勿改）**：

| 参数 | 值 |
|------|-----|
| 采样率 | 16000 Hz |
| 模型 | YAMNet E256 |
| 输入 | Log-Mel **64×96** |
| n_fft / hop / window | 512 / 160 / 400 (Hann) |
| fmin / fmax | 125 / 7500 Hz |
| PCM 长度 | 400 + 95×160 = **15600** 样本 |

---

## 2. 路径约定（D 盘）

| 用途 | 路径 |
|------|------|
| Python 3.12 | `D:\py\python.exe` |
| 嵌赛工程根 | `D:\嵌赛\嵌入式2026\` |
| **原始录音**（按类分子文件夹） | `D:\嵌赛\Audio\` |
| wake ESC 数据集（脚本生成） | `D:\嵌赛\Audio_esc\` |
| cmd ESC 数据集（脚本生成） | `D:\嵌赛\Audio_esc_cmds\` |
| 训练配置 / 脚本 | `D:\嵌赛\嵌入式2026\NUCLEO-U575ZI-Q\stedgeai_config\train_xiaoou_first\` |
| **本指南所在目录** | `D:\嵌赛\AI_ModelTrain\` |
| cmd 训练 yaml | `D:\嵌赛\AI_ModelTrain\config\user_config_train_cmds.yaml` |
| wake 训练 yaml | `...\train_xiaoou_first\user_config_train_xiaoou.yaml` |
| Model Zoo Services | `D:\STEdge\stm32ai-modelzoo-services\audio_event_detection\` |
| STEdgeAI CLI | `D:\STEdge\4.0\Utilities\windows\stedgeai.exe` |
| Keil 固件 AI 目录 | `D:\嵌赛\嵌入式2026\NUCLEO-U575ZI-Q\Firmware\STM32CubeU5\Projects\NUCLEO-U575ZI-Q\Examples\GPIO\GPIO_IOToggle\AI\` |
| 训练输出归档 | `D:\嵌赛\AI_ModelTrain\overall_model\` |
| 待部署 tflite | `D:\嵌赛\AI_ModelTrain\output_model\` |

> 若 STEdge 装在 `D:\ST\` 而非 `D:\STEdge\`，修改 yaml 中 `path_to_stedgeai` 即可。

---

## 3. 环境一次性安装

### 3.1 Python PATH

```cmd
D:\py\python.exe --version
```

应显示 `Python 3.12.x`。若无，安装 Python 3.12 到 `D:\py`，并将 `D:\py` 与 `D:\py\Scripts` 加入用户 PATH。

### 3.2 Model Zoo Services + venv

**双击或 cmd 执行**：

```cmd
D:\嵌赛\嵌入式2026\NUCLEO-U575ZI-Q\stedgeai_config\train_xiaoou_first\setup_python_env.bat
```

等价手动步骤：

```cmd
cd /d D:\STEdge\stm32ai-modelzoo-services\audio_event_detection
rmdir /s /q .venv
D:\py\python.exe -m venv .venv
.venv\Scripts\activate.bat
python -m pip install --upgrade pip -i https://pypi.tuna.tsinghua.edu.cn/simple
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
```

### 3.3 验证 GPU

```cmd
.venv\Scripts\python.exe -c "import tensorflow as tf; print(tf.config.list_physical_devices('GPU'))"
nvidia-smi
```

### 3.4 拷贝到训练机时的最小文件包

```
D:\嵌赛\Audio\                          # 全部原始 wav
D:\嵌赛\AI_ModelTrain\                  # 本指南 + config + eval 脚本
D:\嵌赛\嵌入式2026\NUCLEO-U575ZI-Q\stedgeai_config\train_xiaoou_first\
D:\STEdge\                              # stm32ai-modelzoo-services + stedgeai.exe
```

---

## 4. 录音数据规范

### 4.1 目录结构

```
D:\嵌赛\Audio\
├── xiaoou\          # 唤醒词「小欧小欧」
├── noise\           # 环境噪声、闲聊、近音词
├── paizhao\
├── queren\
├── quxiao\
├── chayoujian\
├── chaduiwu\
├── chahuanjing\
├── chayundong\
└── xiuxiba\
```

### 4.2 WAV 格式（`prepare_dataset.py` 会校验）

| 项 | 要求 |
|----|------|
| 格式 | PCM WAV，无压缩 |
| 声道 | 单声道 |
| 采样率 | **16000 Hz** |
| 位深 | 16 bit |

不符合的文件会被跳过并在控制台打印 `[FAIL]`。

### 4.3 每类建议样本量

| 模型 | 类别 | 建议最少 | 说明 |
|------|------|----------|------|
| wake | xiaoou | **≥80** | 多人、多距离、多语速 |
| wake | noise | **≥80** | 含「小X」近音、键盘、风噪 |
| cmd | 各命令 | **≥60/类** | 演示成员各录一批 |
| cmd | noise | **≥100** | 唤醒后窗口内的静音/杂音 |

### 4.4 划分原则（重要）

- **按说话人划分** train/val/test，不要把同一人录音同时出现在 train 和 val
- 推荐比例：**70% / 15% / 15%**
- `prepare_dataset.py` 默认只做 ESC 打包；**speaker 级划分**需在拷贝到 `Audio\` 前人工分文件夹，或扩展脚本（见 §8）

---

## 5. 流程 A：唤醒模型（wake）

### A1. 生成 ESC 数据集

```cmd
cd /d D:\嵌赛\嵌入式2026\NUCLEO-U575ZI-Q\stedgeai_config\train_xiaoou_first
D:\py\python.exe prepare_dataset.py --classes xiaoou noise --dst D:\嵌赛\Audio_esc
```

或双击 `1_prepare_dataset.bat`。

**检查输出**：

```
D:\嵌赛\Audio_esc\audio\*.wav
D:\嵌赛\Audio_esc\meta\labels.csv
```

### A2. 复制训练配置

```cmd
copy /Y "...\train_xiaoou_first\user_config_train_xiaoou.yaml" ^
     "D:\STEdge\stm32ai-modelzoo-services\audio_event_detection\tf\src\user_config.yaml"
```

或双击 `2_copy_config.bat`。

确认 yaml 内：

- `class_names: ['noise', 'xiaoou']`（**noise 在前**，与 `classes_wake.txt` 一致）
- `training_audio_path: D:/嵌赛/Audio_esc/audio`

### A3. 训练

```cmd
cd /d D:\STEdge\stm32ai-modelzoo-services\audio_event_detection\tf\src
..\..\..\.venv\Scripts\python.exe stm32ai_main.py
```

日志与模型默认在：

```
D:\STEdge\stm32ai-modelzoo-services\audio_event_detection\tf\src\experiments_outputs\<时间戳>\
```

**归档**（Agent 必做）：

```cmd
xcopy /E /I "<experiments_outputs>\<时间戳>" "D:\嵌赛\AI_ModelTrain\wake_model\<时间戳>\"
```

### A4. 选取模型

在 `saved_models\` 或 `quantized_models\` 中取 **`*_int8.tflite`**（优先 val_accuracy 最高 epoch，非 train）。

复制到：

```
D:\嵌赛\AI_ModelTrain\output_model\wake_int8.tflite
```

### A5. wake 停止 / 通过标准

| 指标 | 通过 | 不通过时的动作 |
|------|------|----------------|
| val_accuracy | **≥ 95%** | 加 noise / xiaoou 数据，勿加 epoch |
| train − val 差距 | **< 10%** | 开 SpecAug 或减 epoch |
| 板端误唤醒 | 可接受 | 加 hard noise，调 `AI_WAKE_*` 门限（固件侧） |

---

## 6. 流程 B：命令模型（cmd）

### B1. 生成 ESC 数据集

```cmd
cd /d D:\嵌赛\嵌入式2026\NUCLEO-U575ZI-Q\stedgeai_config\train_xiaoou_first
D:\py\python.exe prepare_dataset.py ^
  --classes chaduiwu chahuanjing chayoujian chayundong noise paizhao queren quxiao xiuxiba ^
  --dst D:\嵌赛\Audio_esc_cmds
```

### B2. 复制 cmd 训练配置

```cmd
copy /Y "D:\嵌赛\AI_ModelTrain\config\user_config_train_cmds.yaml" ^
     "D:\STEdge\stm32ai-modelzoo-services\audio_event_detection\tf\src\user_config.yaml"
```

**class_names 顺序必须与 `AI\classes_cmd.txt` 完全一致**（见 §1）。

### B3. 训练

同 §A3，`stm32ai_main.py` 一次只训一个 yaml。

**推荐超参**（已在 cmd yaml 中）：

- `fine_tune: true`
- `batch_size: 8`
- `epochs: 100`（EarlyStopping patience=40）
- `SpecAug: enable`（cmd 比 wake 更需要）

### B4. 归档与选取

```cmd
xcopy /E /I "<experiments_outputs>\<时间戳>" "D:\嵌赛\AI_ModelTrain\overall_model\<时间戳>\"
copy "<best_int8.tflite>" "D:\嵌赛\AI_ModelTrain\output_model\cmd_int8.tflite"
```

### B5. cmd 停止 / 通过标准

| 指标 | 通过 | 说明 |
|------|------|------|
| val_accuracy | **≥ 95%** | 历史 run 约 98% train / 85% val → **过拟合** |
| paizhao / queren / quxiao recall | **≥ 95%** | 联调 Maix 的三类必须优先 |
| 易混对（如 chayoujian↔noise） | 混淆 < 5% | 见 §7 评估 |

**禁止**：train accuracy 已到 99% 仍继续训练指望 val 上升。

---

## 7. 离线评估（Agent 必跑）

### 7.1 批量评估脚本

```cmd
cd /d D:\嵌赛\AI_ModelTrain
D:\STEdge\stm32ai-modelzoo-services\audio_event_detection\.venv\Scripts\python.exe eval_dataset.py ^
  --model D:\嵌赛\AI_ModelTrain\output_model\cmd_int8.tflite ^
  --dataset D:\嵌赛\Audio_esc_cmds ^
  --class-names chaduiwu chahuanjing chayoujian chayundong noise paizhao queren quxiao xiuxiba
```

wake 模型把 `--model` 换为 `wake_int8.tflite`，`--dataset` 换为 `Audio_esc`，`--class-names noise xiaoou`。

### 7.2 输出解读

脚本打印：

- Overall accuracy
- **Per-class recall**（每类召回，优先看 paizhao/queren/quxiao/xiaoou）
- 混淆矩阵（找 top 混淆对）

### 7.3 混淆后的数据补强策略

| 现象 | 动作 |
|------|------|
| A 类常被认成 noise | 补 A 类 + 补「唤醒后静音段」noise |
| chayoujian ↔ chayundong | 补对立样本，语速分开 |
| val 低、train 高 | **加数据**，不是加 epoch |
| 板端 score 低但离线准确 | 固件调 `AI_*_SCORE_MIN`（`ai_config.h`） |

---

## 8. 量化 → C 代码 → 合入 Keil

STEdgeAI **`--name` 必须与板端前缀一致**，wake / cmd **各生成一次**。

### 8.1 唤醒模型

```cmd
set STEDGEAI=D:\STEdge\4.0\Utilities\windows\stedgeai.exe
set OUT=D:\嵌赛\AI_ModelTrain\deploy_temp\wake

"%STEDGEAI%" generate --target stm32u5 --type tflite ^
  --model "D:\嵌赛\AI_ModelTrain\output_model\wake_int8.tflite" ^
  --name network_wake ^
  --output "%OUT%" ^
  --allocate-inputs --allocate-outputs --verbosity 1
```

复制到 Keil：

```cmd
set AI=D:\嵌赛\嵌入式2026\NUCLEO-U575ZI-Q\Firmware\STM32CubeU5\Projects\NUCLEO-U575ZI-Q\Examples\GPIO\GPIO_IOToggle\AI

copy /Y "%OUT%\network_wake.c" "%OUT%\network_wake.h" ^
        "%OUT%\network_wake_data.c" "%OUT%\network_wake_data.h" ^
        "%OUT%\network_wake_details.h" "%AI%\"
```

### 8.2 命令模型

```cmd
set OUT=D:\嵌赛\AI_ModelTrain\deploy_temp\cmd

"%STEDGEAI%" generate --target stm32u5 --type tflite ^
  --model "D:\嵌赛\AI_ModelTrain\output_model\cmd_int8.tflite" ^
  --name network_cmd ^
  --output "%OUT%" ^
  --allocate-inputs --allocate-outputs --verbosity 1

copy /Y "%OUT%\network_cmd.c" "%OUT%\network_cmd.h" ^
        "%OUT%\network_cmd_data.c" "%OUT%\network_cmd_data.h" ^
        "%OUT%\network_cmd_details.h" "%AI%\"
```

### 8.3 合入检查清单

- [ ] `classes_wake.txt` = `noise` / `xiaoou`（行序同 yaml `class_names`）
- [ ] `classes_cmd.txt` 9 行与 yaml 一致
- [ ] `ai_config.h` 中 `AI_WAKE_NUM_CLASSES=2`、`AI_CMD_NUM_CLASSES=9` 未改错
- [ ] Keil 工程已包含上述 10 个 network_*.c/h
- [ ] **不要**覆盖 `ai_aed.c` / `ai_preproc.c` / `user_mel_tables.*`（除非有意修改）

### 8.4 拷回开发机

将以下目录同步回 U 盘 / 网盘 / git：

```
AI\network_wake*
AI\network_cmd*
AI\classes_wake.txt
AI\classes_cmd.txt
D:\嵌赛\AI_ModelTrain\output_model\*.tflite
D:\嵌赛\AI_ModelTrain\overall_model\<最新时间戳>\   # 含 metrics CSV
```

---

## 9. 板端验证（训练机无法完成时需交回开发机）

1. Keil：`MDK-ARM\GPIO_IOToggle.uvprojx` → Rebuild → Download  
2. `board_pins.h`：`APP_RUN_MODE=2`（MIC+AED），纯语音调试可 `APP_MAIXCAM_ENABLE=0`  
3. 串口 115200，预期：

```
*** WAKE + CMD dual model (voice only) ***
>>> WAKE OK, say command ...
>>> CMD OK paizhao (done)
```

4. 联调 Maix 时：`APP_MAIXCAM_ENABLE=1`，上电顺序 **ESP32 → Maix main.py → U5 复位**

---

## 10. 常见问题

| 问题 | 处理 |
|------|------|
| `No Python at ...Python311` | 删 `.venv`，重跑 `setup_python_env.bat` |
| PowerShell 禁止 Activate | 用 **cmd** + `activate.bat` |
| `prepare_dataset.py` 报 missing folders | 检查 `D:\嵌赛\Audio\<类名>\` 是否存在 |
| train 99% / val 85% | **过拟合**：加数据、SpecAug，用 EarlyStopping 最佳权重 |
| stedgeai generate 失败 | 确认 tflite 为 int8；路径无中文空格问题时可拷到 `deploy_temp\` |
| 板端全 noise | 查 class 顺序是否与训练不一致 |
| 唤醒太灵敏 | 固件增大 `AI_WAKE_XIAOOU_SCORE_MIN` / `AI_WAKE_MARGIN_MIN` |

---

## 11. Agent 任务模板（复制填写）

```markdown
### 任务
- [ ] wake / cmd / both
- 原因：误唤醒 / 某命令识别差 / 例行重训

### 数据
- 新增录音：`<路径>`，说话人：`<name>`，类：`<class>`

### 执行记录
- prepare_dataset 输出样本数：
- 训练输出目录：
- val_accuracy / 离线 eval：
- 部署 tflite 路径：
- 是否已拷回 Keil AI/：

### 结论
- 通过 / 需补数据 / 需调固件门限
```

---

## 12. 相关文件索引

| 文件 | 说明 |
|------|------|
| `train_xiaoou_first/prepare_dataset.py` | ESC 数据集打包 |
| `train_xiaoou_first/user_config_train_xiaoou.yaml` | wake 训练配置 |
| `AI_ModelTrain/config/user_config_train_cmds.yaml` | cmd 训练配置 |
| `AI_ModelTrain/eval_dataset.py` | 离线批量评估 |
| `train_xiaoou_first/高性能电脑训练方案.md` | 简版 wake 流程 |
| `GPIO_IOToggle/AI/ai_config.h` | 板端特征与门限 |
| `GPIO_IOToggle/VOICE_DEBUG_README.txt` | 纯语音调试说明 |

---

*文档版本：2026-07-13 · V-Eye STM32U575 双模型（YAMNet E256 64×96）*
