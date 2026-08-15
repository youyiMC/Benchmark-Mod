# BenchMark

A client-side **Minecraft performance benchmarking mod** for **NeoForge 1.21.1**. It adds a button to the pause screen that opens a benchmark menu: it captures your hardware info, runs a configurable-duration FPS/TPS test, samples CPU/GPU/memory usage, analyses the results, and generates a timestamped plain-text report you can directly upload to an AI for deeper analysis.

> English introduction first, 中文介绍请见下方（Chinese version below).

---

## ✨ Features

- **Pause-screen button** — open the benchmark menu right from the game's pause screen.
- **Hardware detection** — CPU model, GPU (renderer / vendor / OpenGL), OS, Java, RAM and JVM heap.
- **Early bottleneck hints** — heuristic CPU/GPU generation comparison, clearly marked "for reference only".
- **Configurable test duration** — slider from **5 to 600 seconds**.
- **Comprehensive metrics** — average / max / min FPS, **1% Low & 0.1% Low**, **TPS & MSPT** (single-player or LAN host only), CPU usage, system memory, JVM heap, and **GPU usage + VRAM** (NVIDIA `nvidia-smi`, AMD `rocm-smi`/`amdsmi-cli`, or Windows performance counters).
- **Lightweight sampling** — hardware sampling runs on a background thread and is throttled so it never skews the test.
- **Chat notifications** — you are told when a test starts and when it finishes.
- **Results screen** — reopen the menu to see the latest results and the mod's auto-generated bottleneck analysis.
- **AI-friendly report** — a timestamped `.txt` report is saved to `./benchmark_reports/`, containing an **AI key-value summary**, full statistics, frame-time percentiles, raw sample series and analysis text.

## 🚀 Getting Started

1. Install **Minecraft 1.21.1** with **NeoForge 21.1.241**.
2. Put the mod jar into your `mods` folder.
3. Launch the game, open the **Pause Menu**, and click **BenchMark** (top-left).
4. Pick a test duration with the slider, click **Start Test**, then play normally.
5. When the test finishes you'll get a chat message. Reopen the menu to view the results, or click "Open Report Folder" to jump straight to the files.

### Report location

```
<game dir>/benchmark_reports/benchmark_report_YYYYMMDD_HHmmss.txt
```

The default report folder can be changed in the mod config (`reportDirectory`).

## 📊 What gets measured

| Metric | Detail |
| --- | --- |
| FPS | Average, max, min, **1% Low**, **0.1% Low** |
| Server | **TPS / MSPT** (only when hosting single-player or LAN) |
| Hardware usage | CPU %, system RAM, JVM heap, GPU % + VRAM (best-effort) |
| Frame times | Full percentile table (P0.1 … P99.9) |

> Minecraft runs its server at **20 TPS = 50 ms per tick**. An average MSPT around 50 ms means the server is running at full speed and is healthy — it is **not** "under pressure". The analysis text in the report follows this logic correctly.

## 🤖 Report for AI analysis

Each report starts with a machine-friendly key-value block:

```
==== AI 环境摘要（Key-Value）====
CPU_MODEL: ...
GPU_MODEL: ...
MEMORY_TOTAL_MB: ...
JVM_MAX_MB: ...
TEST_DURATION_SEC: ...
AVG_FPS: ...
MIN_FPS: ...
P1_LOW_FPS: ...
AVG_MSPT: ...
AVG_CPU_PERCENT: ...
AVG_GPU_PERCENT: ...
OS: ...
```

You can paste the whole file (or just the key-value block) into any AI for bottleneck analysis.

## 🔧 Configuration

The mod registers a config screen (Mods → BenchMark → Config):

- `reportDirectory` — folder (relative to the game dir) for reports. Default: `benchmark_reports`.
- `defaultTestDuration` — default slider value in seconds. Default: `30`.
- `enableGpuUsageDetection` — allow querying external GPU tools / performance counters. Default: `true`.

## 🛠 Build from source

```bash
gradlew.bat build        # Windows
./gradlew build          # Linux / macOS
```

The jar is produced at `build/libs/benchmark-<version>.jar`. Requires **JDK 21**.

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)** — see [LICENSE](LICENSE).
The project scaffold is based on the [NeoForged MDK](https://github.com/NeoForged/MDK) template, which is provided under the MIT License (see `TEMPLATE_LICENSE.txt`).

---

# BenchMark（模组中文介绍）

一个面向 **NeoForge 1.21.1** 的**客户端性能测试模组**。它在游戏暂停界面添加一个按钮，打开性能测试菜单：抓取硬件信息、按设定时长运行 FPS/TPS 测试、持续采样 CPU/GPU/内存占用、分析结果，并生成带时间戳的纯文本报告，方便你直接上传给 AI 做进一步分析。

## ✨ 功能特性

- **暂停界面按钮** —— 在游戏暂停菜单一键打开性能测试菜单。
- **硬件信息采集** —— CPU 型号、GPU（渲染器/厂商/OpenGL）、操作系统、Java、内存、JVM 堆。
- **早期瓶颈提示** —— 基于 CPU/GPU 代际的启发式对比，并明确标注“仅供参考”。
- **可配置测试时长** —— **5～600 秒**滑条。
- **全面指标** —— 平均/最高/最低帧率、**1% Low 与 0.1% Low**、**TPS 与 MSPT**（仅单人或局域网主机）、CPU 占用、系统内存、JVM 堆，以及 **GPU 占用与显存**（NVIDIA `nvidia-smi`、AMD `rocm-smi`/`amdsmi-cli`，或 Windows 性能计数器）。
- **轻量采样** —— 所有硬件采样都在后台线程并限频执行，不会干扰测试结果。
- **聊天通知** —— 测试开始与结束时都会在聊天框提示。
- **结果界面** —— 重新打开菜单即可查看最新结果与模组自动生成的瓶颈分析。
- **AI 友好报告** —— 报告保存到 `./benchmark_reports/`，包含 **AI 键值摘要**、完整统计、帧时间分位数、原始采样序列与分析文本。

## 🚀 使用方法

1. 安装 **Minecraft 1.21.1** 与 **NeoForge 21.1.241**。
2. 将模组 jar 放入 `mods` 文件夹。
3. 启动游戏，打开**暂停菜单**，点击左上角**「性能测试」**按钮。
4. 用滑条设置测试时长，点击**「开始测试」**后正常游玩。
5. 测试结束会有聊天提示；重新打开菜单查看结果，或直接点击「打开报告文件夹」。

### 报告位置

```
<游戏目录>/benchmark_reports/benchmark_report_YYYYMMDD_HHmmss.txt
```

默认报告文件夹可在模组配置中修改（`reportDirectory`）。

## 📊 采集指标

| 指标 | 说明 |
| --- | --- |
| 帧率 | 平均、最高、最低、**1% Low**、**0.1% Low** |
| 服务器 | **TPS / MSPT**（仅当单人游戏或作为局域网主机时） |
| 硬件占用 | CPU 占用、系统内存、JVM 堆、GPU 占用 + 显存（尽力而为） |
| 帧时间 | 完整分位表（P0.1 … P99.9） |

> Minecraft 服务器目标为 **20 TPS = 每刻 50ms**。平均 MSPT 约 50ms 表示服务器满速健康运行，而**并非**“压力较大”；报告中的分析文本已按此逻辑正确表述。

## 🤖 交给 AI 分析

每份报告开头都有一段机器可读的键值块（`CPU_MODEL`、`GPU_MODEL`、`AVG_FPS`、`P1_LOW_FPS`、`AVG_MSPT` 等）。你可以把整份文件或键值块直接粘贴给任意 AI 进行瓶颈分析。

## 🔧 配置

模组注册了配置界面（Mods → BenchMark → Config）：

- `reportDirectory` — 报告文件夹（相对游戏目录）。默认：`benchmark_reports`。
- `defaultTestDuration` — 滑条默认时长（秒）。默认：`30`。
- `enableGpuUsageDetection` — 是否允许调用外部 GPU 工具/性能计数器。默认：`true`。

## 🛠 从源码构建

```bash
gradlew.bat build        # Windows
./gradlew build          # Linux / macOS
```

产物位于 `build/libs/benchmark-<版本>.jar`，需要 **JDK 21**。

## 📄 许可证

本项目以 **GNU 通用公共许可证 v3.0（GPL-3.0）** 开源，详见 [LICENSE](LICENSE)。
项目脚手架基于 [NeoForged MDK](https://github.com/NeoForged/MDK) 模板，该模板以 MIT 许可证提供（见 `TEMPLATE_LICENSE.txt`）。
