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

import com.youyimc.benchmark.client.BenchmarkResult;
import com.youyimc.benchmark.client.HardwareInfo;

import static com.youyimc.benchmark.client.report.MarkdownDoc.cell;
import static com.youyimc.benchmark.client.report.MarkdownDoc.fence;
import static com.youyimc.benchmark.client.report.MarkdownDoc.heading;
import static com.youyimc.benchmark.client.report.MarkdownDoc.inlineCode;
import static com.youyimc.benchmark.client.report.MarkdownDoc.row;
import static com.youyimc.benchmark.client.report.MarkdownDoc.tableHeader;

/**
 * The Markdown report ({@code .md}), the default output since 1.1.0.
 *
 * <p>The document is still plain UTF-8 text, so any AI can read it, but it also
 * renders as a normal Markdown page on GitHub, in editors and in chat clients:
 * the numbers become tables and the raw sampling series are fenced code blocks.
 * The machine readable {@code KEY: VALUE} summary is kept unchanged (fenced), so
 * existing AI prompts keep working.</p>
 */
public final class MarkdownReportRenderer implements ReportRenderer {
    private static final int SAMPLES_PER_LINE = 16;

    @Override
    public String extension() {
        return "md";
    }

    @Override
    public String render(BenchmarkResult r, Path targetFile) {
        StringBuilder sb = new StringBuilder(16 * 1024);
        HardwareInfo hw = r.hardware;

        heading(sb, 1, "BenchMark 性能测试报告 / Benchmark Report");
        sb.append("- **生成时间 / Generated at**：")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(r.timestampMillis))).append('\n');
        sb.append("- **模组版本 / Mod version**：").append(cell(r.modVersion)).append('\n');
        sb.append('\n');

        heading(sb, 2, "AI 环境摘要 / Key-Value Summary");
        fence(sb, "text", ReportStats.keyValueBlock(r));

        heading(sb, 2, "硬件信息 / Hardware");
        tableHeader(sb, "项目", "值");
        row(sb, "CPU", hw.cpuName);
        row(sb, "CPU 线程数", hw.cpuThreads);
        row(sb, "GPU 渲染器", hw.gpuRenderer);
        row(sb, "GPU 厂商", hw.gpuVendor);
        row(sb, "GPU 驱动 / OpenGL", hw.gpuDriverInfo);
        row(sb, "操作系统", hw.osName + " " + hw.osVersion + " (" + hw.osArch + ")");
        row(sb, "Java", hw.javaVersion + " / " + hw.javaVm);
        row(sb, "系统内存", ReportStats.format(hw.totalMemoryBytes / (1024.0 * 1024.0 * 1024.0), "%.1f") + " GB");
        row(sb, "JVM 最大堆", ReportStats.format(hw.maxHeapBytes / (1024.0 * 1024.0), "%.0f") + " MB");
        sb.append('\n');

        heading(sb, 2, "早期瓶颈提示 / Early Bottleneck Hints");
        for (String hint : hw.bottleneckHints) {
            sb.append("- ").append(cell(hint)).append('\n');
        }
        sb.append('\n');

        heading(sb, 2, "测试参数 / Test Parameters");
        tableHeader(sb, "项目", "值");
        row(sb, "目标时长", ReportStats.format(r.targetDurationSeconds, "%.0f") + " 秒");
        row(sb, "实际时长", ReportStats.format(r.actualDurationSeconds, "%.2f") + " 秒");
        row(sb, "渲染总帧数", String.valueOf(r.totalFrames));
        sb.append('\n');

        heading(sb, 2, "帧率统计 / FPS Statistics");
        tableHeader(sb, "指标", "数值");
        row(sb, "平均帧率", ReportStats.format(r.avgFps, "%.1f") + " FPS");
        row(sb, "最高帧率", ReportStats.format(r.maxFps, "%.1f") + " FPS");
        row(sb, "最低帧率", ReportStats.format(r.minFps, "%.1f") + " FPS");
        row(sb, "1% Low", ReportStats.format(r.low1Fps, "%.1f") + " FPS");
        row(sb, "0.1% Low", ReportStats.format(r.low01Fps, "%.1f") + " FPS");
        sb.append('\n');

        heading(sb, 2, "服务器统计 / Server Statistics");
        if (r.hasServerData) {
            double tps = r.avgMspt > 0 ? Math.min(20.0, 1000.0 / r.avgMspt) : 20.0;
            tableHeader(sb, "指标", "数值");
            row(sb, "平均 MSPT", ReportStats.format(r.avgMspt, "%.2f") + " ms");
            row(sb, "最高 MSPT", ReportStats.format(r.maxMspt, "%.2f") + " ms");
            row(sb, "平均 TPS", ReportStats.format(tps, "%.2f"));
            sb.append('\n');
        } else {
            sb.append("> 未采集（当前非单人/局域网主机模式）。\n\n");
        }

        heading(sb, 2, "硬件占用统计 / Hardware Usage");
        tableHeader(sb, "指标", "平均", "最高");
        row(sb, "CPU 占用率", ReportStats.format(r.avgCpu, "%.1f") + "%", ReportStats.format(r.maxCpu, "%.1f") + "%");
        row(sb, "系统内存占用", ReportStats.format(r.avgMemMB, "%.0f") + " MB", ReportStats.format(r.maxMemMB, "%.0f") + " MB");
        row(sb, "JVM 堆内存", ReportStats.format(r.avgHeapMB, "%.0f") + " MB", ReportStats.format(r.maxHeapMB, "%.0f") + " MB");
        if (r.hasGpu) {
            row(sb, "GPU 占用率", ReportStats.format(r.avgGpu, "%.1f") + "%", ReportStats.format(r.maxGpu, "%.1f") + "%");
            if (r.avgVramMB > 0) {
                row(sb, "显存占用", ReportStats.format(r.avgVramMB, "%.0f") + " MB", "-");
            }
        }
        sb.append('\n');
        if (!r.hasGpu) {
            sb.append("> GPU 占用率不可用（未找到 nvidia-smi / rocm-smi / amdsmi-cli / Windows 性能计数器，或不受支持）。\n\n");
        } else if (r.avgVramMB <= 0) {
            sb.append("> 显存占用未知（该工具未报告显存大小）。\n\n");
        }

        heading(sb, 2, "帧时间分布 / Frame Time Distribution");
        appendFrameTimeTable(sb, r);

        heading(sb, 2, "原始采样数据 / Raw Samples");
        appendRawSamples(sb, r);

        heading(sb, 2, "瓶颈分析 / Bottleneck Analysis");
        for (String text : r.analysis) {
            sb.append("- ").append(text).append('\n');
        }
        sb.append('\n');

        sb.append("---\n\n");
        sb.append("**报告文件 / Report file**：").append(inlineCode(targetFile.toAbsolutePath().toString())).append("\n\n");
        sb.append("本报告由 BenchMark 模组自动生成，可将本文件内容直接上传给 AI 进行进一步分析。\n");
        return sb.toString();
    }

    private static void appendFrameTimeTable(StringBuilder sb, BenchmarkResult r) {
        if (r.frameTimesMs.isEmpty()) {
            sb.append("> 无数据。\n\n");
            return;
        }
        double min = ReportStats.minFrameTime(r.frameTimesMs);
        double max = ReportStats.maxFrameTime(r.frameTimesMs);
        tableHeader(sb, "分位", "帧时间", "折算帧率");
        row(sb, "最小", ReportStats.format(min, "%.2f") + " ms", ReportStats.format(min > 0 ? 1000.0 / min : 0, "%.1f") + " FPS");
        for (ReportStats.Percentile row : ReportStats.percentiles(r.frameTimesMs)) {
            row(sb, "P" + ReportStats.percentLabel(row.percent()),
                    ReportStats.format(row.frameTimeMs(), "%.2f") + " ms",
                    ReportStats.format(row.fps(), "%.1f") + " FPS");
        }
        row(sb, "最大", ReportStats.format(max, "%.2f") + " ms", ReportStats.format(max > 0 ? 1000.0 / max : 0, "%.1f") + " FPS");
        sb.append('\n');
    }

    private static void appendRawSamples(StringBuilder sb, BenchmarkResult r) {
        boolean wroteAny = false;
        wroteAny |= appendSampleSeries(sb, "CPU 占用率 (%)", r.cpuSamples);
        wroteAny |= appendSampleSeries(sb, "系统内存占用 (MB)", r.memSamples);
        if (r.hasGpu) {
            wroteAny |= appendSampleSeries(sb, "GPU 占用率 (%)", r.gpuSamples);
        }
        if (r.hasServerData) {
            wroteAny |= appendSampleSeries(sb, "MSPT (ms)", r.msptSamples);
        }
        if (!wroteAny) {
            sb.append("> 无数据。\n\n");
        }
    }

    private static boolean appendSampleSeries(StringBuilder sb, String title, List<Double> samples) {
        if (samples.isEmpty()) {
            return false;
        }
        heading(sb, 3, title);
        fence(sb, "text", ReportStats.joinSamplesWrapped(samples, SAMPLES_PER_LINE));
        return true;
    }
}
