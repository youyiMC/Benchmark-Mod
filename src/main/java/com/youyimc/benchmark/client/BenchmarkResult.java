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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of a completed benchmark test. Holds all statistics,
 * the raw sample series (for the report file) and the auto-generated
 * bottleneck analysis.
 */
public class BenchmarkResult {
    public final long timestampMillis;
    public final double targetDurationSeconds;
    public final double actualDurationSeconds;
    public final int totalFrames;
    public final double avgFps;
    public final double maxFps;
    public final double minFps;
    public final double low1Fps;
    public final double low01Fps;
    public final boolean hasServerData;
    public final double avgMspt;
    public final double maxMspt;
    public final double avgCpu;
    public final double maxCpu;
    public final double avgMemMB;
    public final double maxMemMB;
    public final double avgHeapMB;
    public final double maxHeapMB;
    public final boolean hasGpu;
    public final double avgGpu;
    public final double maxGpu;
    public final double avgVramMB;
    public final double maxVramMB;
    public final List<String> analysis;
    public final List<Double> frameTimesMs;
    public final List<Double> cpuSamples;
    public final List<Double> memSamples;
    public final List<Double> gpuSamples;
    public final List<Double> msptSamples;
    /** Version of the mod that produced the result, written into the report header. */
    public final String modVersion;
    public final HardwareInfo hardware;
    /** Report file(s) written for this result; filled in by {@code ReportWriter}. */
    public final List<Path> reportPaths = new ArrayList<>();

    public BenchmarkResult(long timestampMillis, double targetDurationSeconds, double actualDurationSeconds,
                           int totalFrames, double avgFps, double maxFps, double minFps, double low1Fps, double low01Fps,
                           boolean hasServerData, double avgMspt, double maxMspt,
                           double avgCpu, double maxCpu, double avgMemMB, double maxMemMB,
                           double avgHeapMB, double maxHeapMB, boolean hasGpu, double avgGpu, double maxGpu,
                           double avgVramMB, double maxVramMB, List<String> analysis,
                           List<Double> frameTimesMs, List<Double> cpuSamples, List<Double> memSamples,
                           List<Double> gpuSamples, List<Double> msptSamples, String modVersion, HardwareInfo hardware) {
        this.timestampMillis = timestampMillis;
        this.targetDurationSeconds = targetDurationSeconds;
        this.actualDurationSeconds = actualDurationSeconds;
        this.totalFrames = totalFrames;
        this.avgFps = avgFps;
        this.maxFps = maxFps;
        this.minFps = minFps;
        this.low1Fps = low1Fps;
        this.low01Fps = low01Fps;
        this.hasServerData = hasServerData;
        this.avgMspt = avgMspt;
        this.maxMspt = maxMspt;
        this.avgCpu = avgCpu;
        this.maxCpu = maxCpu;
        this.avgMemMB = avgMemMB;
        this.maxMemMB = maxMemMB;
        this.avgHeapMB = avgHeapMB;
        this.maxHeapMB = maxHeapMB;
        this.hasGpu = hasGpu;
        this.avgGpu = avgGpu;
        this.maxGpu = maxGpu;
        this.avgVramMB = avgVramMB;
        this.maxVramMB = maxVramMB;
        this.analysis = analysis;
        this.frameTimesMs = frameTimesMs;
        this.cpuSamples = cpuSamples;
        this.memSamples = memSamples;
        this.gpuSamples = gpuSamples;
        this.msptSamples = msptSamples;
        this.modVersion = modVersion;
        this.hardware = hardware;
    }
}
