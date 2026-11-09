/*
 * Copyright (c) 2026 youyiMC
 *
 * BenchMark - a Minecraft performance benchmark mod for NeoForge 1.21.1.
 * https://github.com/youyiMC/Benchmark-Mod
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.youyimc.benchmark.client.stutter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.youyimc.benchmark.Benchmark;
import com.youyimc.benchmark.client.report.MarkdownDoc;
import com.youyimc.benchmark.client.report.ReportStats;

/**
 * Writes the Markdown stutter analysis report next to the regular reports.
 */
public final class StutterReportRenderer {
    private StutterReportRenderer() {
    }

    /** Writes the report and returns its path. */
    public static Path write(StutterResult result, Path dir) throws IOException {
        String name = "stutter_report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(result.timestampMillis)) + ".md";
        Path file = dir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(file, render(result, file), StandardCharsets.UTF_8);
        Benchmark.LOGGER.info("Stutter analysis report written to {}", file.toAbsolutePath());
        return file;
    }

    /** Renders the report for the given target file (the path is printed in the footer). */
    public static String render(StutterResult r, Path targetFile) {
        StringBuilder sb = new StringBuilder(16 * 1024);

        MarkdownDoc.heading(sb, 1, "BenchMark 卡顿分析报告 / Stutter Analysis Report");
        sb.append("- **生成时间 / Generated at**：")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(r.timestampMillis))).append('\n');
        sb.append("- **模组版本 / Mod version**：").append(r.modVersion).append('\n');
        sb.append("- **模式 / Mode**：卡顿分析（JFR）/ Stutter analysis (JFR)\n");
        sb.append("- **采样率 / Sampling**：").append(r.samplePeriodMs).append(" ms\n");
        sb.append("- **渲染器 / Renderer**：").append(r.rendererSummary).append('\n');
        sb.append('\n');

        MarkdownDoc.heading(sb, 2, "AI 环境摘要 / Key-Value Summary");
        MarkdownDoc.fence(sb, "text", keyValueBlock(r));

        MarkdownDoc.heading(sb, 2, "测试条件与局限 / Conditions and Limitations");
        MarkdownDoc.bullets(sb, conditions(r));

        MarkdownDoc.heading(sb, 2, "帧率统计 / Frame Statistics");
        MarkdownDoc.tableHeader(sb, "指标", "数值");
        MarkdownDoc.row(sb, "渲染总帧数", r.totalFrames);
        MarkdownDoc.row(sb, "实际时长", ReportStats.format(r.actualDurationSeconds, "%.2f") + " 秒");
        MarkdownDoc.row(sb, "平均帧率", ReportStats.format(r.avgFps, "%.1f") + " FPS");
        MarkdownDoc.row(sb, "最高帧率", ReportStats.format(r.maxFps, "%.1f") + " FPS");
        MarkdownDoc.row(sb, "最低帧率", ReportStats.format(r.minFps, "%.1f") + " FPS");
        MarkdownDoc.row(sb, "1% Low", ReportStats.format(r.low1Fps, "%.1f") + " FPS");
        MarkdownDoc.row(sb, "0.1% Low", ReportStats.format(r.low01Fps, "%.1f") + " FPS");
        sb.append('\n');

        MarkdownDoc.heading(sb, 2, "卡顿时间线 / Stutter Timeline");
        if (r.stutters.isEmpty()) {
            MarkdownDoc.note(sb, "未检测到明显卡顿（判定阈值 = max(1.8 × 局部基线, 局部基线 + 16.7 ms)）。");
        } else {
            sb.append("共检测到 **").append(r.stutterCount).append("** 个卡顿事件（合计 ")
                    .append(r.stutterFrameCount).append(" 个慢帧），超出局部基线累计 ")
                    .append(ReportStats.format(r.stutterExcessMillis, "%.0f")).append(" ms；下表列出最严重的 ")
                    .append(r.stutters.size()).append(" 个。\n\n");
            sb.append("> 局部基线 = 前 ").append(BASELINE_HINT)
                    .append(" 帧的帧时间中位数；间隔小于 500 ms 的慢帧会合并成一个事件，持续低帧率阶段单独标注。\n\n");
            MarkdownDoc.tableHeader(sb, "起始时间 (s)", "持续 (ms)", "帧数", "最大帧间隔 (ms)", "超出基线合计 (ms)", "可能原因");
            for (StutterResult.StutterEvent event : r.stutters) {
                double relativeSeconds = (event.startMillis() - r.timestampMillis) / 1000.0;
                MarkdownDoc.row(sb,
                        ReportStats.format(Math.max(0, relativeSeconds), "%.2f"),
                        event.durationMillis(),
                        event.frames(),
                        ReportStats.format(event.maxIntervalMs(), "%.1f"),
                        ReportStats.format(event.excessMs(), "%.1f"),
                        event.cause());
            }
            sb.append('\n');
        }

        MarkdownDoc.heading(sb, 2, "帧时间分布 / Frame Time Distribution");
        appendFrameTimeTable(sb, r);

        MarkdownDoc.heading(sb, 2, "CPU 归因 / CPU Attribution");
        MarkdownDoc.note(sb, "基于 JFR 执行采样（每 " + r.samplePeriodMs
                + " ms 采样一次），属于统计归因：safepoint 采样存在偏差，原生/驱动代码的时间只能部分可见。");
        if (r.modCpu.isEmpty()) {
            MarkdownDoc.note(sb, "无 CPU 采样数据。");
        } else {
            MarkdownDoc.tableHeader(sb, "来源", "渲染线程占比", "服务端占比(单人)", "其他线程样本", "原生样本(全线程)");
            int rows = 0;
            for (StutterResult.ModCpuShare share : r.modCpu) {
                if (rows++ >= 15) {
                    break;
                }
                MarkdownDoc.row(sb, share.modId(),
                        ReportStats.format(share.renderSharePercent(), "%.1f") + "%",
                        ReportStats.format(share.serverSharePercent(), "%.1f") + "%",
                        share.otherSamples(),
                        share.nativeSamples());
            }
            sb.append('\n');
        }

        MarkdownDoc.heading(sb, 2, "热点方法 / Hot Methods");
        MarkdownDoc.note(sb, "渲染线程采样中出现次数最多的方法（不含包名）；行号与完整调用树将在后续版本提供。");
        if (r.hotMethods.isEmpty()) {
            MarkdownDoc.note(sb, "无采样数据。");
        } else {
            MarkdownDoc.tableHeader(sb, "来源", "方法", "样本", "渲染线程占比");
            int rows = 0;
            for (StutterResult.HotMethod method : r.hotMethods) {
                if (rows++ >= 12) {
                    break;
                }
                MarkdownDoc.row(sb, method.modId(), method.method(), method.samples(),
                        ReportStats.format(method.renderSharePercent(), "%.1f") + "%");
            }
            sb.append('\n');
        }
        MarkdownDoc.heading(sb, 2, "线程状态 / Thread States");
        MarkdownDoc.note(sb, "JFR 只对运行中的线程采样，等待时间来自 park / monitor / sleep 事件（阈值 1 ms）。");
        if (r.threadWaits.isEmpty()) {
            MarkdownDoc.note(sb, "未记录到阻塞事件。");
        } else {
            MarkdownDoc.tableHeader(sb, "线程", "CPU 样本", "Park", "锁竞争", "锁等待", "Sleep", "合计");
            int rows = 0;
            for (StutterResult.ThreadWaitStat wait : r.threadWaits) {
                if (rows++ >= 10) {
                    break;
                }
                MarkdownDoc.row(sb, wait.thread(),
                        wait.cpuSamples(),
                        wait.parkCount() + " 次 / " + wait.parkMillis() + " ms",
                        wait.monitorEnterCount() + " 次 / " + wait.monitorEnterMillis() + " ms",
                        wait.monitorWaitCount() + " 次 / " + wait.monitorWaitMillis() + " ms",
                        wait.sleepCount() + " 次 / " + wait.sleepMillis() + " ms",
                        wait.totalWaitMillis() + " ms");
            }
            sb.append('\n');
        }

        MarkdownDoc.heading(sb, 2, "内存与 GC / Memory and GC");
        MarkdownDoc.note(sb, "暂停时间取自每次 GC 的 sumOfPauses（ZGC 通常只有微秒级，G1 为毫秒级），"
                + "与整轮 GC 周期时长不同；只有 ≥1 ms 的暂停才会被算作卡顿原因。");
        MarkdownDoc.tableHeader(sb, "指标", "数值");
        MarkdownDoc.row(sb, "GC 周期数", r.gcCount);
        MarkdownDoc.row(sb, "累计暂停", ReportStats.format(r.gcTotalPauseMicros / 1000.0, "%.3f") + " ms");
        MarkdownDoc.row(sb, "最长单次暂停", ReportStats.format(r.gcLongestPauseMicros / 1000.0, "%.3f") + " ms");
        MarkdownDoc.row(sb, "GC 后堆占用最大值", ReportStats.format(r.heapUsedMaxMb, "%.0f") + " MB");
        MarkdownDoc.row(sb, "分配速率", ReportStats.format(r.allocationRateBytesPerSecond / (1024.0 * 1024.0), "%.2f") + " MB/s");
        sb.append('\n');

        if (!r.gcPauses.isEmpty()) {
            sb.append("### 暂停最长的 GC / Longest GC Pauses\n\n");
            MarkdownDoc.tableHeader(sb, "起始时间 (s)", "暂停合计 (ms)", "类型");
            for (StutterResult.GcPause pause : r.gcPauses) {
                double relativeSeconds = (pause.startMillis() - r.timestampMillis) / 1000.0;
                MarkdownDoc.row(sb, ReportStats.format(Math.max(0, relativeSeconds), "%.2f"),
                        ReportStats.format(pause.durationMicros() / 1000.0, "%.3f"), pause.name());
            }
            sb.append('\n');
        }

        if (!r.modAllocations.isEmpty()) {
            sb.append("### 按模组的分配速率 / Allocation per Mod\n\n");
            MarkdownDoc.tableHeader(sb, "来源", "分配速率", "占比");
            int rows = 0;
            for (StutterResult.ModAllocation allocation : r.modAllocations) {
                if (rows++ >= 10) {
                    break;
                }
                MarkdownDoc.row(sb, allocation.modId(),
                        ReportStats.format(allocation.bytesPerSecond() / (1024.0 * 1024.0), "%.2f") + " MB/s",
                        ReportStats.format(allocation.sharePercent(), "%.1f") + "%");
            }
            sb.append('\n');
        }

        MarkdownDoc.heading(sb, 2, "网络 / Network");
        if (r.packets.isEmpty()) {
            MarkdownDoc.note(sb, "未采集到数据包事件（单人游戏未经过网络层时可能出现）。");
        } else {
            MarkdownDoc.tableHeader(sb, "方向", "协议", "包", "次数", "字节");
            int rows = 0;
            for (StutterResult.PacketSummary packet : r.packets) {
                if (rows++ >= 12) {
                    break;
                }
                MarkdownDoc.row(sb, packet.direction(), packet.protocolId(), packet.packetId(),
                        packet.count(), bytes(packet.bytes()));
            }
            sb.append('\n');
            MarkdownDoc.note(sb, "数据包统计来自 Minecraft 自带的 minecraft.PacketSent / PacketReceived JFR 事件。");
        }

        MarkdownDoc.heading(sb, 2, "磁盘 / Disk");
        if (r.chunks.isEmpty()) {
            MarkdownDoc.note(sb, "未采集到区块读写事件：多人服务器下区块由服务端加载，客户端通常没有 region 文件读写。");
        } else {
            sb.append("### 区块读写 / Chunk Region IO\n\n");
            MarkdownDoc.tableHeader(sb, "类型", "维度", "X", "Z", "次数", "字节");
            int rows = 0;
            for (StutterResult.ChunkSummary chunk : r.chunks) {
                if (rows++ >= 12) {
                    break;
                }
                MarkdownDoc.row(sb, chunk.kind(), chunk.dimension(), chunk.x(), chunk.z(), chunk.count(), bytes(chunk.bytes()));
            }
            sb.append('\n');
        }
        sb.append("### 文件 IO / File IO\n\n");
        MarkdownDoc.tableHeader(sb, "方向", "总字节", "速率", "次数", "IO 耗时");
        MarkdownDoc.row(sb, "读取", bytes(r.fileReads.totalBytes()),
                ReportStats.format(r.fileReads.bytesPerSecond() / (1024.0 * 1024.0), "%.2f") + " MB/s",
                r.fileReads.count(), r.fileReads.timeSpentMillis() + " ms");
        MarkdownDoc.row(sb, "写入", bytes(r.fileWrites.totalBytes()),
                ReportStats.format(r.fileWrites.bytesPerSecond() / (1024.0 * 1024.0), "%.2f") + " MB/s",
                r.fileWrites.count(), r.fileWrites.timeSpentMillis() + " ms");
        sb.append('\n');
        if (!r.fileReads.topPaths().isEmpty() || !r.fileWrites.topPaths().isEmpty()) {
            sb.append("主要文件 / Top files:\n\n");
            for (String path : r.fileReads.topPaths()) {
                sb.append("- 读取：").append(path).append('\n');
            }
            for (String path : r.fileWrites.topPaths()) {
                sb.append("- 写入：").append(path).append('\n');
            }
            sb.append('\n');
        }

        MarkdownDoc.heading(sb, 2, "文件清单 / Files");
        sb.append("- 分析报告：").append(MarkdownDoc.inlineCode(targetFile.toAbsolutePath().toString())).append('\n');
        sb.append("- JFR 录制：").append(MarkdownDoc.inlineCode(r.jfrPath.toAbsolutePath().toString())).append('\n');
        sb.append("- 说明：可用 JDK 自带工具查看录制，例如 ")
                .append(MarkdownDoc.inlineCode("jfr print --events jdk.ExecutionSample <file.jfr>"))
                .append("，或用 JDK Mission Control 打开。\n");
        sb.append('\n');
        sb.append("---\n\n");
        sb.append("本报告由 BenchMark 模组自动生成；卡顿原因归因为启发式判断，仅供参考。\n");
        return sb.toString();
    }

    /** Formats a byte count with a human readable unit. */
    private static String bytes(long value) {
        if (value >= 1024L * 1024 * 1024) {
            return ReportStats.format(value / (1024.0 * 1024 * 1024), "%.2f") + " GB";
        }
        if (value >= 1024L * 1024) {
            return ReportStats.format(value / (1024.0 * 1024), "%.1f") + " MB";
        }
        if (value >= 1024L) {
            return ReportStats.format(value / 1024.0, "%.1f") + " KB";
        }
        return value + " B";
    }

    private static final int BASELINE_HINT = 120;

    private static String keyValueBlock(StutterResult r) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("MODE: STUTTER\n");
        sb.append("MOD_VERSION: ").append(r.modVersion).append('\n');
        sb.append("RENDERER: ").append(r.rendererSummary).append('\n');
        sb.append("SAMPLE_PERIOD_MS: ").append(r.samplePeriodMs).append('\n');
        sb.append("TEST_DURATION_SEC: ").append(ReportStats.format(r.targetDurationSeconds, "%.0f")).append('\n');
        sb.append("AVG_FPS: ").append(ReportStats.format(r.avgFps, "%.1f")).append('\n');
        sb.append("MIN_FPS: ").append(ReportStats.format(r.minFps, "%.1f")).append('\n');
        sb.append("P1_LOW_FPS: ").append(ReportStats.format(r.low1Fps, "%.1f")).append('\n');
        sb.append("STUTTER_EPISODES: ").append(r.stutterCount).append('\n');
        sb.append("STUTTER_FRAMES: ").append(r.stutterFrameCount).append('\n');
        sb.append("STUTTER_EXCESS_MS: ").append(ReportStats.format(r.stutterExcessMillis, "%.0f")).append('\n');
        sb.append("GC_COUNT: ").append(r.gcCount).append('\n');
        sb.append("GC_PAUSE_TOTAL_MS: ").append(ReportStats.format(r.gcTotalPauseMicros / 1000.0, "%.3f")).append('\n');
        sb.append("GC_LONGEST_PAUSE_MS: ").append(ReportStats.format(r.gcLongestPauseMicros / 1000.0, "%.3f")).append('\n');
        sb.append("TOP_CAUSE: ").append(r.stutters.isEmpty() ? "none" : r.stutters.get(0).cause()).append('\n');
        return sb.toString();
    }

    private static java.util.List<String> conditions(StutterResult r) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("运行环境：" + r.rendererSummary + "；JFR 采样间隔 " + r.samplePeriodMs + " ms。");
        lines.add("JFR 录制本身有约 1–4% 的额外开销，因此本报告的 FPS 数字不应与普通模式的报告直接比较。");
        lines.add("CPU 归因是抽样统计，等待/锁竞争只覆盖超过 1 ms 的事件。");
        for (String warning : r.warnings) {
            lines.add("注意：" + warning);
        }
        return lines;
    }

    private static void appendFrameTimeTable(StringBuilder sb, StutterResult r) {
        if (r.frameTimesMs.isEmpty()) {
            MarkdownDoc.note(sb, "无帧时间数据。");
            return;
        }
        MarkdownDoc.tableHeader(sb, "分位", "帧时间", "折算帧率");
        double min = ReportStats.minFrameTime(r.frameTimesMs);
        double max = ReportStats.maxFrameTime(r.frameTimesMs);
        MarkdownDoc.row(sb, "最小", ReportStats.format(min, "%.2f") + " ms",
                ReportStats.format(min > 0 ? 1000.0 / min : 0, "%.1f") + " FPS");
        for (ReportStats.Percentile percentile : ReportStats.percentiles(r.frameTimesMs)) {
            MarkdownDoc.row(sb, "P" + ReportStats.percentLabel(percentile.percent()),
                    ReportStats.format(percentile.frameTimeMs(), "%.2f") + " ms",
                    ReportStats.format(percentile.fps(), "%.1f") + " FPS");
        }
        MarkdownDoc.row(sb, "最大", ReportStats.format(max, "%.2f") + " ms",
                ReportStats.format(max > 0 ? 1000.0 / max : 0, "%.1f") + " FPS");
        sb.append('\n');
    }
}
