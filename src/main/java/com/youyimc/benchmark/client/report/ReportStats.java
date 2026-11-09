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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.youyimc.benchmark.client.BenchmarkResult;
import com.youyimc.benchmark.client.HardwareInfo;

/**
 * Shared numeric helpers used by both report renderers, so the plain text and
 * the Markdown report always print the same values.
 */
public final class ReportStats {
    /** Percentiles printed in the frame time distribution; min / max are listed separately. */
    private static final double[] FRAME_TIME_PERCENTILES = {0.1, 1, 5, 10, 25, 50, 75, 90, 95, 99, 99.9};

    /** One row of the frame time distribution table. */
    public record Percentile(double percent, double frameTimeMs, double fps) {
    }

    private ReportStats() {
    }

    /**
     * {@return the frame time percentile rows} Uses the same nearest-rank
     * rounding the plain text report has always used, so both formats agree.
     */
    public static List<Percentile> percentiles(List<Double> frameTimesMs) {
        List<Percentile> rows = new ArrayList<>();
        if (frameTimesMs.isEmpty()) {
            return rows;
        }
        List<Double> sorted = new ArrayList<>(frameTimesMs);
        Collections.sort(sorted);
        for (double percent : FRAME_TIME_PERCENTILES) {
            int idx = (int) Math.floor(sorted.size() * percent / 100.0);
            idx = Math.min(sorted.size() - 1, Math.max(0, idx));
            double frameTimeMs = sorted.get(idx);
            rows.add(new Percentile(percent, frameTimeMs, frameTimeMs > 0 ? 1000.0 / frameTimeMs : 0));
        }
        return rows;
    }

    public static double minFrameTime(List<Double> frameTimesMs) {
        double min = Double.MAX_VALUE;
        for (double v : frameTimesMs) {
            if (v < min) {
                min = v;
            }
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    public static double maxFrameTime(List<Double> frameTimesMs) {
        double max = 0;
        for (double v : frameTimesMs) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    /** Formats a raw sample series as a single comma separated line. */
    public static String joinSamples(List<Double> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(format(values.get(i), "%.1f"));
        }
        return sb.toString();
    }

    /**
     * Formats a raw sample series, starting a new line every {@code perLine}
     * values so a Markdown reader does not have to scroll horizontally.
     */
    public static String joinSamplesWrapped(List<Double> values, int perLine) {
        int perLineClamped = Math.max(1, perLine);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(i % perLineClamped == 0 ? ",\n" : ", ");
            }
            sb.append(format(values.get(i), "%.1f"));
        }
        return sb.toString();
    }

    /**
     * {@return the machine readable {@code KEY: VALUE} summary block} Shared by
     * all report formats so the block never drifts between them.
     */
    public static String keyValueBlock(BenchmarkResult r) {
        HardwareInfo hw = r.hardware;
        StringBuilder sb = new StringBuilder();
        sb.append("CPU_MODEL: ").append(hw.cpuName).append('\n');
        sb.append("GPU_VENDOR: ").append(hw.gpuVendor).append('\n');
        sb.append("GPU_MODEL: ").append(hw.gpuRenderer).append('\n');
        sb.append("MEMORY_TOTAL_MB: ").append(format(hw.totalMemoryBytes / (1024.0 * 1024.0), "%.0f")).append('\n');
        sb.append("JVM_MAX_MB: ").append(format(hw.maxHeapBytes / (1024.0 * 1024.0), "%.0f")).append('\n');
        sb.append("TEST_DURATION_SEC: ").append(format(r.targetDurationSeconds, "%.0f")).append('\n');
        sb.append("AVG_FPS: ").append(format(r.avgFps, "%.1f")).append('\n');
        sb.append("MIN_FPS: ").append(format(r.minFps, "%.1f")).append('\n');
        sb.append("P1_LOW_FPS: ").append(format(r.low1Fps, "%.1f")).append('\n');
        sb.append("AVG_MSPT: ").append(format(r.avgMspt, "%.2f")).append('\n');
        sb.append("AVG_CPU_PERCENT: ").append(format(r.avgCpu, "%.1f")).append('\n');
        sb.append("AVG_GPU_PERCENT: ").append(format(r.avgGpu, "%.1f")).append('\n');
        sb.append("MOD_VERSION: ").append(r.modVersion).append('\n');
        sb.append("OS: ").append(hw.osName).append(' ').append(hw.osVersion).append('\n');
        return sb.toString();
    }

    /** Locale independent {@link String#format(String, Object...)}. */
    public static String format(double value, String pattern) {
        return String.format(Locale.ROOT, pattern, value);
    }

    /** Renders a percentile label like {@code "0.1"}, {@code "1"} or {@code "99.9"}. */
    public static String percentLabel(double percent) {
        if (percent == Math.floor(percent)) {
            return String.valueOf((long) percent);
        }
        return String.valueOf(percent);
    }
}
