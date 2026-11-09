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
package com.youyimc.benchmark.client.report;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.youyimc.benchmark.client.BenchmarkResult;
import com.youyimc.benchmark.client.HardwareInfo;

/**
 * The legacy plain text report ({@code .txt}), kept so people who prefer the
 * old layout (or scripts that look for {@code .txt}) keep working.
 */
public final class TextReportRenderer implements ReportRenderer {
    @Override
    public String extension() {
        return "txt";
    }

    @Override
    public String render(BenchmarkResult r, Path targetFile) {
        HardwareInfo hw = r.hardware;
        StringBuilder sb = new StringBuilder(8 * 1024);
        String line = "=".repeat(72);
        sb.append(line).append("\n");
        sb.append("            BenchMark 性能测试报告 / Benchmark Report\n");
        sb.append(line).append("\n\n");

        sb.append("【生成时间 / Generated at】: ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(r.timestampMillis))).append("\n");

        sb.append("\n==== AI 环境摘要（Key-Value，方便 AI 直接解析）====\n");
        sb.append(ReportStats.keyValueBlock(r));

        sb.append("\n==== 硬件信息 / Hardware ====\n");
        sb.append("CPU: ").append(hw.cpuName).append("\n");
        sb.append("CPU 线程数: ").append(hw.cpuThreads).append("\n");
        sb.append("GPU 渲染器: ").append(hw.gpuRenderer).append("\n");
        sb.append("GPU 厂商: ").append(hw.gpuVendor).append("\n");
        sb.append("GPU 驱动/OpenGL: ").append(hw.gpuDriverInfo).append("\n");
        sb.append("操作系统: ").append(hw.osName).append(" ").append(hw.osVersion)
                .append(" (").append(hw.osArch).append(")\n");
        sb.append("Java: ").append(hw.javaVersion).append(" / ").append(hw.javaVm).append("\n");
        sb.append("系统内存: ").append(ReportStats.format(hw.totalMemoryBytes / (1024.0 * 1024.0 * 1024.0), "%.1f GB")).append("\n");
        sb.append("JVM 最大堆: ").append(ReportStats.format(hw.maxHeapBytes / (1024.0 * 1024.0), "%.0f MB")).append("\n");

        sb.append("\n==== 早期瓶颈提示（仅供参考）/ Early Bottleneck Hints ====\n");
        for (String hint : hw.bottleneckHints) {
            sb.append("- ").append(hint).append("\n");
        }

        sb.append("\n==== 测试参数 / Test Parameters ====\n");
        sb.append("目标时长: ").append(ReportStats.format(r.targetDurationSeconds, "%.0f")).append(" 秒\n");
        sb.append("实际时长: ").append(ReportStats.format(r.actualDurationSeconds, "%.2f")).append(" 秒\n");
        sb.append("渲染总帧数: ").append(r.totalFrames).append("\n");

        sb.append("\n==== 帧率统计 / FPS Statistics ====\n");
        sb.append("平均帧率: ").append(ReportStats.format(r.avgFps, "%.1f")).append(" FPS\n");
        sb.append("最高帧率: ").append(ReportStats.format(r.maxFps, "%.1f")).append(" FPS\n");
        sb.append("最低帧率: ").append(ReportStats.format(r.minFps, "%.1f")).append(" FPS\n");
        sb.append("1% Low: ").append(ReportStats.format(r.low1Fps, "%.1f")).append(" FPS\n");
        sb.append("0.1% Low: ").append(ReportStats.format(r.low01Fps, "%.1f")).append(" FPS\n");

        sb.append("\n==== 服务器统计 / Server Statistics ====\n");
        if (r.hasServerData) {
            double tps = r.avgMspt > 0 ? Math.min(20.0, 1000.0 / r.avgMspt) : 20.0;
            sb.append("平均 MSPT: ").append(ReportStats.format(r.avgMspt, "%.2f")).append(" ms\n");
            sb.append("最高 MSPT: ").append(ReportStats.format(r.maxMspt, "%.2f")).append(" ms\n");
            sb.append("平均 TPS: ").append(ReportStats.format(tps, "%.2f")).append("\n");
        } else {
            sb.append("未采集（当前非单人/局域网主机模式）\n");
        }

        sb.append("\n==== 硬件占用统计 / Hardware Usage ====\n");
        sb.append("CPU 占用率: 平均 ").append(ReportStats.format(r.avgCpu, "%.1f")).append("% / 最高 ")
                .append(ReportStats.format(r.maxCpu, "%.1f")).append("%\n");
        sb.append("系统内存占用: 平均 ").append(ReportStats.format(r.avgMemMB, "%.0f")).append(" MB / 最高 ")
                .append(ReportStats.format(r.maxMemMB, "%.0f")).append(" MB\n");
        sb.append("JVM 堆内存: 平均 ").append(ReportStats.format(r.avgHeapMB, "%.0f")).append(" MB / 最高 ")
                .append(ReportStats.format(r.maxHeapMB, "%.0f")).append(" MB\n");
        if (r.hasGpu) {
            sb.append("GPU 占用率: 平均 ").append(ReportStats.format(r.avgGpu, "%.1f")).append("% / 最高 ")
                    .append(ReportStats.format(r.maxGpu, "%.1f")).append("%\n");
            if (r.avgVramMB > 0) {
                sb.append("显存占用: 平均 ").append(ReportStats.format(r.avgVramMB, "%.0f")).append(" MB\n");
            } else {
                sb.append("显存占用: 未知（该工具未报告显存大小）\n");
            }
        } else {
            sb.append("GPU 占用率: 不可用（未找到 nvidia-smi / rocm-smi / amdsmi-cli / Windows 性能计数器，或不受支持）\n");
        }

        sb.append("\n==== 帧时间分布 / Frame Time Distribution (ms) ====\n");
        sb.append(buildPercentileTable(r));

        sb.append("\n==== 原始采样数据 / Raw Samples ====\n");
        sb.append("CPU 占用率(%): ").append(ReportStats.joinSamples(r.cpuSamples)).append("\n");
        sb.append("系统内存占用(MB): ").append(ReportStats.joinSamples(r.memSamples)).append("\n");
        if (r.hasGpu) {
            sb.append("GPU 占用率(%): ").append(ReportStats.joinSamples(r.gpuSamples)).append("\n");
        }
        if (r.hasServerData) {
            sb.append("MSPT(ms): ").append(ReportStats.joinSamples(r.msptSamples)).append("\n");
        }

        sb.append("\n==== 瓶颈分析（仅供参考）/ Bottleneck Analysis ====\n");
        for (String text : r.analysis) {
            sb.append("- ").append(text).append("\n");
        }

        sb.append("\n").append(line).append("\n");
        sb.append("报告文件: ").append(targetFile.toAbsolutePath()).append("\n");
        sb.append("本报告由 BenchMark 模组自动生成，可将本文件内容直接上传给 AI 进行进一步分析。\n");
        sb.append(line).append("\n");
        return sb.toString();
    }

    private static String buildPercentileTable(BenchmarkResult r) {
        if (r.frameTimesMs.isEmpty()) {
            return "无数据\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("最小帧时间: ").append(ReportStats.format(ReportStats.minFrameTime(r.frameTimesMs), "%.2f")).append(" ms\n");
        sb.append("最大帧时间: ").append(ReportStats.format(ReportStats.maxFrameTime(r.frameTimesMs), "%.2f")).append(" ms\n");
        List<ReportStats.Percentile> rows = ReportStats.percentiles(r.frameTimesMs);
        for (ReportStats.Percentile row : rows) {
            sb.append(String.format(Locale.ROOT, "P%s: %.2f ms (≈%.1f FPS)\n",
                    ReportStats.percentLabel(row.percent()), row.frameTimeMs(), row.fps()));
        }
        return sb.toString();
    }
}
