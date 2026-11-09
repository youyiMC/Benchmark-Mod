# BenchMark

A client-side **Minecraft performance benchmarking mod** for **NeoForge 1.21.1**. It adds a button to the pause screen that opens a benchmark menu: it captures your hardware info, runs a configurable-duration FPS/TPS test, samples CPU/GPU/memory usage, analyses the results, and generates a timestamped **Markdown** report you can directly upload to an AI for deeper analysis.

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
- **AI-friendly report** — a timestamped **Markdown (`.md`)** report is saved to `./benchmark_reports/`: statistics as tables, an **AI key-value summary** in a fenced block, frame-time percentiles, raw sample series and analysis text. Plain text (`.txt`, legacy) or both formats can be selected with the `reportFormat` config option.

## 🚀 Getting Started

1. Install **Minecraft 1.21.1** with **NeoForge 21.1.241**.
2. Put the mod jar into your `mods` folder.
3. Launch the game, open the **Pause Menu**, and click **BenchMark** (top-left).
4. Pick a test duration with the slider, click **Start Test**, then play normally.
5. When the test finishes you'll get a chat message. Reopen the menu to view the results, or click "Open Report Folder" to jump straight to the files.

### Report location

```
<game dir>/benchmark_reports/benchmark_report_YYYYMMDD_HHmmss.md
```

Since **1.1.0** the report is Markdown by default — GitHub, editors and AI chat clients all render it. Set `reportFormat` to `PLAIN_TEXT` for the old `.txt` layout, or to `BOTH` to get one file of each. The default report folder can be changed in the mod config (`reportDirectory`).

The document starts like this:

```markdown
# BenchMark 性能测试报告 / Benchmark Report

- **生成时间 / Generated at**：2026-08-18 19:27:41
- **模组版本 / Mod version**：1.1.0

## 帧率统计 / FPS Statistics

| 指标 | 数值 |
| --- | --- |
| 平均帧率 | 78.1 FPS |
| 1% Low | 13.7 FPS |
```

## 📊 What gets measured

| Metric | Detail |
| --- | --- |
| FPS | Average, max, min, **1% Low**, **0.1% Low** |
| Server | **TPS / MSPT** (only when hosting single-player or LAN) |
| Hardware usage | CPU %, system RAM, JVM heap, GPU % + VRAM (best-effort) |
| Frame times | Full percentile table (P0.1 … P99.9) |

> Minecraft runs its server at **20 TPS = 50 ms per tick**. An average MSPT around 50 ms means the server is running at full speed and is healthy — it is **not** "under pressure". The analysis text in the report follows this logic correctly.

## 🔍 Stutter analysis mode (1.2.0)

The pause-screen menu has a mode switch: **Normal test** (the report above) or **Stutter analysis**. The stutter mode records a Java Flight Recorder session for the whole run and writes `stutter_report_*.md` plus the raw `stutter_*.jfr` next to your normal reports:

| Data | How |
| --- | --- |
| CPU attribution per mod | `jdk.ExecutionSample` (20 ms default, 10 ms optional) mapped to mods via their jar packages |
| Thread states | park / monitor-enter / monitor-wait / sleep events (≥ 1 ms), per thread |
| Hot methods | the most frequent render-thread methods (class#method), with sample counts |
| GC correlation | every GC pause with its duration, matched against frames that stuttered |
| Allocation per mod | `jdk.ObjectAllocationSample` (bytes/s), not retained size |
| Network | Minecraft's own `minecraft.PacketSent/Received` events: packet id, protocol, count, bytes |
| Disk | `minecraft.ChunkRegion*` events (dimension, chunk x/z, compression) + `jdk.FileRead/Write` timing |
| Heap dump | result-screen button (triggers a full GC and pauses the game for a few seconds) |

The stutter detection threshold is `max(50 ms, 2.5 × median frame time)`; each stutter is annotated with the GC pause, disk IO or hot mod that overlapped it.

**Limitations we are explicit about:** CPU attribution is statistical (safepoint sampling bias), JFR adds roughly 1–4% overhead, GPU timing is not part of 1.2.0 (coming in 1.3.0), and with Sodium/Iris the vanilla render-stage hooks are unavailable, so per-mod GPU numbers would be guesses — the report says so in its "conditions" section.

The `reportFormat` setting does not apply to the stutter report (always Markdown). Old `.jfr` files are pruned to the number in `retainedJfrFiles` (default: keep the latest one).

## 🤖 Report for AI analysis

Each report keeps a machine-friendly key-value block (inside a fenced code block, so the Markdown around it does not interfere):

```
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
MOD_VERSION: ...
OS: ...
```

You can paste the whole file (or just the key-value block) into any AI for bottleneck analysis.

## 🔧 Configuration

The mod registers a config screen (Mods → BenchMark → Config):

- `reportDirectory` — folder (relative to the game dir) for reports. Default: `benchmark_reports`.
- `reportFormat` — report format: `MARKDOWN` (default, `.md`), `PLAIN_TEXT` (legacy, `.txt`) or `BOTH`.
- `analysisMode` — `NORMAL` (default) or `STUTTER`; also switchable from the benchmark menu.
- `stutterSamplePeriodMs` — JFR sampling period for the stutter analysis: `20` (default) or `10` ms; also switchable from the menu.
- `retainedJfrFiles` — how many stutter `.jfr` recordings to keep. Default: `1`.
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

一个面向 **NeoForge 1.21.1** 的**客户端性能测试模组**。它在游戏暂停界面添加一个按钮，打开性能测试菜单：抓取硬件信息、按设定时长运行 FPS/TPS 测试、持续采样 CPU/GPU/内存占用、分析结果，并生成带时间戳的 **Markdown 报告**，方便你直接上传给 AI 做进一步分析。

## ✨ 功能特性

- **暂停界面按钮** —— 在游戏暂停菜单一键打开性能测试菜单。
- **硬件信息采集** —— CPU 型号、GPU（渲染器/厂商/OpenGL）、操作系统、Java、内存、JVM 堆。
- **早期瓶颈提示** —— 基于 CPU/GPU 代际的启发式对比，并明确标注“仅供参考”。
- **可配置测试时长** —— **5～600 秒**滑条。
- **全面指标** —— 平均/最高/最低帧率、**1% Low 与 0.1% Low**、**TPS 与 MSPT**（仅单人或局域网主机）、CPU 占用、系统内存、JVM 堆，以及 **GPU 占用与显存**（NVIDIA `nvidia-smi`、AMD `rocm-smi`/`amdsmi-cli`，或 Windows 性能计数器）。
- **轻量采样** —— 所有硬件采样都在后台线程并限频执行，不会干扰测试结果。
- **聊天通知** —— 测试开始与结束时都会在聊天框提示。
- **结果界面** —— 重新打开菜单即可查看最新结果与模组自动生成的瓶颈分析。
- **AI 友好报告** —— 自 **1.1.0** 起默认输出 **Markdown（`.md`）** 报告到 `./benchmark_reports/`：统计走表格、**AI 键值摘要**放进代码块、帧时间分位表、原始采样序列与分析文本一应俱全，可直接上传给 AI，也可在 GitHub/编辑器中正常渲染。`reportFormat` 配置项可切回旧版纯文本（`.txt`）或同时输出两种格式。

## 🚀 使用方法

1. 安装 **Minecraft 1.21.1** 与 **NeoForge 21.1.241**。
2. 将模组 jar 放入 `mods` 文件夹。
3. 启动游戏，打开**暂停菜单**，点击左上角**「性能测试」**按钮。
4. 用滑条设置测试时长，点击**「开始测试」**后正常游玩。
5. 测试结束会有聊天提示；重新打开菜单查看结果，或直接点击「打开报告文件夹」。

### 报告位置

```
<游戏目录>/benchmark_reports/benchmark_report_YYYYMMDD_HHmmss.md
```

自 **1.1.0** 起报告默认使用 Markdown 格式（GitHub、编辑器、AI 聊天客户端都能正常渲染）。把 `reportFormat` 设为 `PLAIN_TEXT` 可回到旧版 `.txt` 排版，设为 `BOTH` 则两种格式同时输出。默认报告文件夹可在模组配置中修改（`reportDirectory`）。

文档开头大致如下：

```markdown
# BenchMark 性能测试报告 / Benchmark Report

- **生成时间 / Generated at**：2026-08-18 19:27:41
- **模组版本 / Mod version**：1.1.0

## 帧率统计 / FPS Statistics

| 指标 | 数值 |
| --- | --- |
| 平均帧率 | 78.1 FPS |
| 1% Low | 13.7 FPS |
```

## 📊 采集指标

| 指标 | 说明 |
| --- | --- |
| 帧率 | 平均、最高、最低、**1% Low**、**0.1% Low** |
| 服务器 | **TPS / MSPT**（仅当单人游戏或作为局域网主机时） |
| 硬件占用 | CPU 占用、系统内存、JVM 堆、GPU 占用 + 显存（尽力而为） |
| 帧时间 | 完整分位表（P0.1 … P99.9） |

> Minecraft 服务器目标为 **20 TPS = 每刻 50ms**。平均 MSPT 约 50ms 表示服务器满速健康运行，而**并非**“压力较大”；报告中的分析文本已按此逻辑正确表述。

## 🔍 卡顿分析模式（1.2.0）

暂停菜菜单里可以切换模式：**普通测试**（上面的报告）或**卡顿分析**。卡顿分析会为整段测试录制一次 Java Flight Recorder，并在报告目录生成 `stutter_report_*.md` 与原始 `stutter_*.jfr`：

| 数据 | 来源 |
| --- | --- |
| 逐模组 CPU 归因 | `jdk.ExecutionSample`（默认 20 ms，可切 10 ms），按 mod jar 的包名映射到模组 |
| 线程状态 | park / 锁竞争 / 锁等待 / sleep 事件（≥ 1 ms），按线程统计 |
| 热点方法 | 渲染线程采样中出现最多的方法（类#方法名）及样本数 |
| GC 关联 | 每次 GC 停顿的时长，与发生卡顿的帧对齐 |
| 逐模组内存分配 | `jdk.ObjectAllocationSample`（字节/秒），只反映分配速率，不含常驻大小 |
| 网络 | Minecraft 自带的 `minecraft.PacketSent/Received` 事件：包 ID、协议、次数、字节 |
| 磁盘 | `minecraft.ChunkRegion*` 事件（维度、区块坐标、压缩算法）+ `jdk.FileRead/Write` 耗时 |
| 堆转储 | 结果页按钮（触发 Full GC 并暂停数秒，文件可达 GB 级） |

卡顿判定阈值为 `max(50 ms, 2.5 × 帧时间中位数)`，每次卡顿会标注与之重叠的 GC 停顿、磁盘 IO 或渲染线程热点模组。

**明确的局限**：CPU 归因是统计采样（存在 safepoint 偏差）；JFR 本身带来约 1–4% 开销；1.2.0 不含 GPU 计时（1.3.0 提供）；Sodium/Iris 下原版渲染阶段钩子失效，逐模组 GPU 数字只能算估算，报告会在“测试条件与局限”中说明。

`reportFormat` 不影响卡顿报告（它固定是 Markdown）。旧的 `.jfr` 会按 `retainedJfrFiles`（默认只保留最近 1 份）自动清理。

## 🤖 交给 AI 分析

每份报告都保留一段机器可读的键值块（放在代码块里，避免 Markdown 表格等语法干扰解析，`MOD_VERSION` 可用于区分不同版本的数据）：

```
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
MOD_VERSION: ...
OS: ...
```

你可以把整份文件或键值块直接粘贴给任意 AI 进行瓶颈分析。

## 🔧 配置

模组注册了配置界面（Mods → BenchMark → Config）：

- `reportDirectory` — 报告文件夹（相对游戏目录）。默认：`benchmark_reports`。
- `reportFormat` — 报告格式：`MARKDOWN`（默认，`.md`）、`PLAIN_TEXT`（旧版，`.txt`）或 `BOTH`（两种都输出）。
- `analysisMode` — `NORMAL`（默认）或 `STUTTER`；也可在测试菜单里切换。
- `stutterSamplePeriodMs` — 卡顿分析采样间隔：`20`（默认）或 `10` ms；也可在菜单里切换。
- `retainedJfrFiles` — 保留的 `.jfr` 录制数量。默认：`1`。
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
