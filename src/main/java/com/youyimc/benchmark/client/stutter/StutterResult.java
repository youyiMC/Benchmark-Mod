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

import java.nio.file.Path;
import java.util.List;

/**
 * Everything a stutter analysis produced. Deliberately free of Minecraft types
 * so the report renderer (and tests) only deal with plain data.
 */
public final class StutterResult {
    public final long timestampMillis;
    public final double targetDurationSeconds;
    public final double actualDurationSeconds;
    public final int totalFrames;

    public final double avgFps;
    public final double maxFps;
    public final double minFps;
    public final double low1Fps;
    public final double low01Fps;
    public final List<Double> frameTimesMs;

    public final int samplePeriodMs;
    public final String modVersion;
    public final String rendererSummary;

    public final int stutterCount;
    public final int stutterFrameCount;
    /** Sum of (frame interval - local baseline) over all stutters. */
    public final double stutterExcessMillis;
    public final List<StutterEvent> stutters;

    public final List<ModCpuShare> modCpu;
    public final List<HotMethod> hotMethods;
    public final List<ThreadWaitStat> threadWaits;

    public final List<GcPause> gcPauses;
    public final long gcCount;
    public final long gcTotalPauseMicros;
    public final long gcLongestPauseMicros;
    public final double heapUsedMaxMb;
    public final double allocationRateBytesPerSecond;
    public final List<ModAllocation> modAllocations;

    public final List<PacketSummary> packets;
    public final List<ChunkSummary> chunks;
    public final FileSummary fileReads;
    public final FileSummary fileWrites;

    public final Path jfrPath;
    public final List<String> warnings;
    public Path reportPath;

    public StutterResult(long timestampMillis, double targetDurationSeconds, double actualDurationSeconds,
                         int totalFrames, double avgFps, double maxFps, double minFps, double low1Fps, double low01Fps,
                         List<Double> frameTimesMs, int samplePeriodMs, String modVersion, String rendererSummary,
                         int stutterCount, int stutterFrameCount, double stutterExcessMillis, List<StutterEvent> stutters,
                         List<ModCpuShare> modCpu, List<HotMethod> hotMethods, List<ThreadWaitStat> threadWaits,
                         List<GcPause> gcPauses, long gcCount, long gcTotalPauseMicros, long gcLongestPauseMicros,
                         double heapUsedMaxMb, double allocationRateBytesPerSecond, List<ModAllocation> modAllocations,
                         List<PacketSummary> packets, List<ChunkSummary> chunks,
                         FileSummary fileReads, FileSummary fileWrites,
                         Path jfrPath, List<String> warnings) {
        this.timestampMillis = timestampMillis;
        this.targetDurationSeconds = targetDurationSeconds;
        this.actualDurationSeconds = actualDurationSeconds;
        this.totalFrames = totalFrames;
        this.avgFps = avgFps;
        this.maxFps = maxFps;
        this.minFps = minFps;
        this.low1Fps = low1Fps;
        this.low01Fps = low01Fps;
        this.frameTimesMs = frameTimesMs;
        this.samplePeriodMs = samplePeriodMs;
        this.modVersion = modVersion;
        this.rendererSummary = rendererSummary;
        this.stutterCount = stutterCount;
        this.stutterFrameCount = stutterFrameCount;
        this.stutterExcessMillis = stutterExcessMillis;
        this.stutters = stutters;
        this.modCpu = modCpu;
        this.hotMethods = hotMethods;
        this.threadWaits = threadWaits;
        this.gcPauses = gcPauses;
        this.gcCount = gcCount;
        this.gcTotalPauseMicros = gcTotalPauseMicros;
        this.gcLongestPauseMicros = gcLongestPauseMicros;
        this.heapUsedMaxMb = heapUsedMaxMb;
        this.allocationRateBytesPerSecond = allocationRateBytesPerSecond;
        this.modAllocations = modAllocations;
        this.packets = packets;
        this.chunks = chunks;
        this.fileReads = fileReads;
        this.fileWrites = fileWrites;
        this.jfrPath = jfrPath;
        this.warnings = warnings;
    }

    /**
     * One stutter episode: consecutive frames that were clearly longer than the
     * local baseline. Grouping keeps a multi-second FPS drop from being reported
     * as hundreds of separate stutters.
     *
     * @param startMillis  wall clock of the first slow frame
     * @param endMillis    wall clock of the last slow frame
     * @param frames       number of slow frames in the episode
     * @param maxIntervalMs worst frame interval in the episode
     * @param baselineMs   median local baseline around the episode
     * @param excessMs     sum of (interval - baseline) over the episode
     */
    public record StutterEvent(long startMillis, long endMillis, int frames, double maxIntervalMs,
                               double baselineMs, double excessMs, String cause) {
        public long durationMillis() {
            return endMillis - startMillis;
        }
    }

    /** CPU samples attributed to one mod, split by thread group. */
    public record ModCpuShare(String modId, long samples, long renderSamples, long serverSamples, long otherSamples,
                              long nativeSamples, double renderSharePercent, double serverSharePercent) {
    }

    /** A single Java method that showed up in the render-thread samples. */
    public record HotMethod(String modId, String method, long samples, double renderSharePercent) {
    }

    /** Park / monitor / sleep events plus the CPU samples of one thread. */
    public record ThreadWaitStat(String thread, long cpuSamples, long parkCount, long parkMillis,
                                 long monitorEnterCount, long monitorEnterMillis,
                                 long monitorWaitCount, long monitorWaitMillis,
                                 long sleepCount, long sleepMillis) {
        public long totalWaitMillis() {
            return parkMillis + monitorEnterMillis + monitorWaitMillis + sleepMillis;
        }
    }

    /** One GC cycle with the pause time it actually caused (ZGC/G1 differ a lot). */
    public record GcPause(long startMillis, long durationMicros, String name) {
    }

    /** Allocation rate (bytes/s) attributed to one mod, sampled by JFR. */
    public record ModAllocation(String modId, double bytesPerSecond, double sharePercent) {
    }

    /** One packet type seen during the run. */
    public record PacketSummary(String direction, String protocolId, String packetId, long count, long bytes) {
    }

    /** One chunk region file target (dimension + chunk position). */
    public record ChunkSummary(String kind, String dimension, int x, int z, long count, long bytes) {
    }

    /** Aggregated file IO, mirroring Minecraft's JFR summary. */
    public record FileSummary(long totalBytes, double bytesPerSecond, long count, double timeSpentMillis,
                              List<String> topPaths) {
        public static FileSummary empty() {
            return new FileSummary(0, 0, 0, 0, List.of());
        }
    }
}
