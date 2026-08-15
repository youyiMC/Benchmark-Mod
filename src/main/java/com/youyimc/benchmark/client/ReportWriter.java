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
package com.youyimc.benchmark.client;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.youyimc.benchmark.Benchmark;

/**
 * Writes a timestamped, human-readable report (plain text) into the game
 * directory under the configured report folder, so players can upload it to
 * an AI for further analysis.
 */
public class ReportWriter {
    public static Path writeReport(BenchmarkResult r) {
        Path dir = HardwareInfo.getReportDirectory();
        try {
            Files.createDirectories(dir);
            String fileName = "benchmark_report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(r.timestampMillis)) + ".txt";
            Path file = dir.resolve(fileName);
            // Set the path on the result BEFORE building the text so the footer
            // shows the real location instead of "(写入失败)".
            r.reportPath = file.toAbsolutePath().toString();
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(buildText(r));
                writer.flush();
            }
            Benchmark.LOGGER.info("Benchmark report written to {}", file.toAbsolutePath());
            return file;
        } catch (IOException e) {
            Benchmark.LOGGER.error("Failed to write benchmark report", e);
            return null;
        }
    }

    private static String buildText(BenchmarkResult r) {
        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(72);
        sb.append(line).append("\n");
        sb.append("            BenchMark 性能测试报告 / Benchmark Report\n");
        sb.append(line).append("\n\n");

        sb.append("【生成时间 / Generated at】: ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(r.timestampMillis))).append("\n");

        HardwareInfo hw = r.hardware;
        sb.append("\n==== AI 环境摘要（Key-Value，方便 AI 直接解析）====\n");
        sb.append("CPU_MODEL: ").append(hw.cpuName).append("\n");
        sb.append("GPU_VENDOR: ").append(hw.gpuVendor).append("\n");
        sb.append("GPU_MODEL: ").append(hw.gpuRenderer).append("\n");
        sb.append(String.format("MEMORY_TOTAL_MB: %.0f\n", hw.totalMemoryBytes / (1024.0 * 1024.0)));
        sb.append(String.format("JVM_MAX_MB: %.0f\n", hw.maxHeapBytes / (1024.0 * 1024.0)));
        sb.append(String.format("TEST_DURATION_SEC: %.0f\n", r.targetDurationSeconds));
        sb.append(String.format("AVG_FPS: %.1f\n", r.avgFps));
        sb.append(String.format("MIN_FPS: %.1f\n", r.minFps));
        sb.append(String.format("P1_LOW_FPS: %.1f\n", r.low1Fps));
        sb.append(String.format("AVG_MSPT: %.2f\n", r.avgMspt));
        sb.append(String.format("AVG_CPU_PERCENT: %.1f\n", r.avgCpu));
        sb.append(String.format("AVG_GPU_PERCENT: %.1f\n", r.avgGpu));
        sb.append("OS: ").append(hw.osName).append(" ").append(hw.osVersion).append("\n");
        sb.append("\n==== 硬件信息 / Hardware ====\n");
        sb.append("CPU: ").append(hw.cpuName).append("\n");
        sb.append("CPU 线程数: ").append(hw.cpuThreads).append("\n");
        sb.append("GPU 渲染器: ").append(hw.gpuRenderer).append("\n");
        sb.append("GPU 厂商: ").append(hw.gpuVendor).append("\n");
        sb.append("GPU 驱动/OpenGL: ").append(hw.gpuDriverInfo).append("\n");
        sb.append("操作系统: ").append(hw.osName).append(" ").append(hw.osVersion).append(" (").append(hw.osArch).append(")\n");
        sb.append("Java: ").append(hw.javaVersion).append(" / ").append(hw.javaVm).append("\n");
        sb.append(String.format("系统内存: %.1f GB\n", hw.totalMemoryBytes / (1024.0 * 1024.0 * 1024.0)));
        sb.append(String.format("JVM 最大堆: %.0f MB\n", hw.maxHeapBytes / (1024.0 * 1024.0)));

        sb.append("\n==== 早期瓶颈提示（仅供参考）/ Early Bottleneck Hints ====\n");
        for (String hint : hw.bottleneckHints) {
            sb.append("- ").append(hint).append("\n");
        }

        sb.append("\n==== 测试参数 / Test Parameters ====\n");
        sb.append(String.format("目标时长: %.0f 秒\n", r.targetDurationSeconds));
        sb.append(String.format("实际时长: %.2f 秒\n", r.actualDurationSeconds));
        sb.append("渲染总帧数: ").append(r.totalFrames).append("\n");

        sb.append("\n==== 帧率统计 / FPS Statistics ====\n");
        sb.append(String.format("平均帧率: %.1f FPS\n", r.avgFps));
        sb.append(String.format("最高帧率: %.1f FPS\n", r.maxFps));
        sb.append(String.format("最低帧率: %.1f FPS\n", r.minFps));
        sb.append(String.format("1%% Low: %.1f FPS\n", r.low1Fps));
        sb.append(String.format("0.1%% Low: %.1f FPS\n", r.low01Fps));

        sb.append("\n==== 服务器统计 / Server Statistics ====\n");
        if (r.hasServerData) {
            double tps = r.avgMspt > 0 ? Math.min(20.0, 1000.0 / r.avgMspt) : 20.0;
            sb.append(String.format("平均 MSPT: %.2f ms\n", r.avgMspt));
            sb.append(String.format("最高 MSPT: %.2f ms\n", r.maxMspt));
            sb.append(String.format("平均 TPS: %.2f\n", tps));
        } else {
            sb.append("未采集（当前非单人/局域网主机模式）\n");
        }

        sb.append("\n==== 硬件占用统计 / Hardware Usage ====\n");
        sb.append(String.format("CPU 占用率: 平均 %.1f%% / 最高 %.1f%%\n", r.avgCpu, r.maxCpu));
        sb.append(String.format("系统内存占用: 平均 %.0f MB / 最高 %.0f MB\n", r.avgMemMB, r.maxMemMB));
        sb.append(String.format("JVM 堆内存: 平均 %.0f MB / 最高 %.0f MB\n", r.avgHeapMB, r.maxHeapMB));
        if (r.hasGpu) {
            sb.append(String.format("GPU 占用率: 平均 %.1f%% / 最高 %.1f%%\n", r.avgGpu, r.maxGpu));
            if (r.avgVramMB > 0) {
                sb.append(String.format("显存占用: 平均 %.0f MB\n", r.avgVramMB));
            } else {
                sb.append("显存占用: 未知（该工具未报告显存大小）\n");
            }
        } else {
            sb.append("GPU 占用率: 不可用（未找到 nvidia-smi / rocm-smi / amdsmi-cli / Windows 性能计数器，或不受支持）\n");
        }

        sb.append("\n==== 帧时间分布 / Frame Time Distribution (ms) ====\n");
        sb.append(buildPercentileTable(r));

        sb.append("\n==== 原始采样数据 / Raw Samples ====\n");
        sb.append("CPU 占用率(%): ").append(join(r.cpuSamples)).append("\n");
        sb.append("系统内存占用(MB): ").append(join(r.memSamples)).append("\n");
        if (r.hasGpu) {
            sb.append("GPU 占用率(%): ").append(join(r.gpuSamples)).append("\n");
        }
        if (r.hasServerData) {
            sb.append("MSPT(ms): ").append(join(r.msptSamples)).append("\n");
        }

        sb.append("\n==== 瓶颈分析（仅供参考）/ Bottleneck Analysis ====\n");
        for (String text : r.analysis) {
            sb.append("- ").append(text).append("\n");
        }

        sb.append("\n").append(line).append("\n");
        sb.append("报告文件: ").append(r.reportPath == null ? "(写入失败)" : r.reportPath).append("\n");
        sb.append("本报告由 BenchMark 模组自动生成，可将本文件内容直接上传给 AI 进行进一步分析。\n");
        sb.append(line).append("\n");
        return sb.toString();
    }

    private static String buildPercentileTable(BenchmarkResult r) {
        if (r.frameTimesMs.isEmpty()) {
            return "无数据\n";
        }
        List<Double> sorted = new ArrayList<>(r.frameTimesMs);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        sb.append("最小帧时间: ").append(String.format("%.2f ms\n", sorted.get(0)));
        sb.append("最大帧时间: ").append(String.format("%.2f ms\n", sorted.get(sorted.size() - 1)));
        double[] pcts = {0.1, 1, 5, 10, 25, 50, 75, 90, 95, 99, 99.9};
        for (double p : pcts) {
            int idx = (int) Math.floor(sorted.size() * p / 100.0);
            idx = Math.min(sorted.size() - 1, Math.max(0, idx));
            double v = sorted.get(idx);
            double fps = v > 0 ? 1000.0 / v : 0;
            sb.append(String.format("P%s: %.2f ms (≈%.1f FPS)\n", fmtPct(p), v, fps));
        }
        return sb.toString();
    }

    private static String fmtPct(double p) {
        if (p == Math.floor(p)) {
            return String.valueOf((long) p);
        }
        return String.valueOf(p);
    }

    private static String join(List<Double> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("%.1f", list.get(i)));
        }
        return sb.toString();
    }
}
