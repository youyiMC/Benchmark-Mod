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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.datafixers.util.Pair;
import com.youyimc.benchmark.client.report.ReportStats;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.util.profiling.jfr.parse.JfrStatsParser;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
import net.minecraft.util.profiling.jfr.stats.FileIOStat;

/**
 * Turns a recording into the numbers the stutter report shows. Everything in
 * here runs on the analysis thread, never on the render thread.
 *
 * <p>Two data sources are combined: Minecraft's own {@code JfrStatsParser}
 * (ticks, packets, chunk IO, heap/GC summary) and a direct scan of the recording
 * for the events Minecraft does not summarise (execution samples, park/monitor
 * events, individual GC cycles, allocation samples).</p>
 */
public final class JfrAnalyzer {
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;
    private static final int MAX_STUTTER_ROWS = 50;
    private static final int MAX_GC_ROWS = 20;
    private static final int MAX_IO_ROWS = 10;
    private static final int MAX_HOT_METHOD_ROWS = 15;
    /** Frames used to estimate the local baseline frame time. */
    private static final int BASELINE_WINDOW = 120;
    /** A stutter must be at least this much slower than the local baseline. */
    private static final double STUTTER_FACTOR = 1.8;
    /** ... or at least this many milliseconds slower (one frame at 60 Hz). */
    private static final double STUTTER_MIN_EXCESS_MS = 16.7;
    /** A GC pause must be at least this long to be blamed for a stutter. */
    private static final long GC_ATTRIBUTION_MIN_MICROS = 1000;
    private static final int HOTSPOT_MIN_SAMPLES = 3;
    /** Mod ids that are code we cannot attribute to a specific mod. */
    private static final List<String> NON_MOD_SOURCES = List.of("minecraft", "jvm", "lwjgl", "unknown", "shared");

    /** Everything the analyzer needs that is not inside the recording. */
    public record Input(Path jfrPath, long timestampMillis, long startWallMillis,
                        double targetDurationSeconds, double actualDurationSeconds,
                        double[] frameIntervalsMs, int samplePeriodMs,
                        String modVersion, String rendererSummary, List<String> warnings) {
    }

    private record SamplePoint(long timeMillis, String modId, String method) {
    }

    private record IoPoint(long timeMillis, long durationMillis, String path) {
    }

    private record ChunkPoint(long timeMillis) {
    }

    private JfrAnalyzer() {
    }

    public static StutterResult analyze(Input input) {
        List<String> warnings = new ArrayList<>(input.warnings());

        Map<String, long[]> modCounters = new HashMap<>(); // render, server, other, native
        Map<String, long[]> waitByThread = new LinkedHashMap<>(); // park c/ms, monitor enter c/ms, monitor wait c/ms, sleep c/ms
        Map<String, Long> samplesByThread = new HashMap<>();
        List<StutterResult.GcPause> gcPauses = new ArrayList<>();
        List<SamplePoint> renderSamples = new ArrayList<>();
        Map<String, Long> allocBytesByMod = new HashMap<>();
        Map<String, Long> hotMethodCounts = new HashMap<>();
        List<IoPoint> ioPoints = new ArrayList<>();
        List<ChunkPoint> chunkPoints = new ArrayList<>();
        Map<String, long[]> packetAgg = new HashMap<>();
        Map<String, long[]> chunkAgg = new HashMap<>();
        long[] fileAgg = new long[6]; // readBytes, readCount, readNanos, writeBytes, writeCount, writeNanos
        Map<String, Long> readPathBytes = new HashMap<>();
        Map<String, Long> writePathBytes = new HashMap<>();
        long[] heapUsedMax = new long[1];
        long[] gcStats = new long[3]; // cycles, totalPauseNanos, longestPauseNanos
        boolean[] sawGcHeapSummary = new boolean[1];

        try (RecordingFile file = new RecordingFile(input.jfrPath())) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                switch (event.getEventType().getName()) {
                    case "jdk.ExecutionSample" -> recordSample(event, modCounters, renderSamples, samplesByThread, hotMethodCounts);
                    case "jdk.NativeMethodSample" -> recordNative(event, modCounters, samplesByThread);
                    case "jdk.ThreadPark" -> recordWait(event, waitByThread, 0);
                    case "jdk.JavaMonitorEnter" -> recordWait(event, waitByThread, 2);
                    case "jdk.JavaMonitorWait" -> recordWait(event, waitByThread, 4);
                    case "jdk.ThreadSleep" -> recordWait(event, waitByThread, 6);
                    case "jdk.GarbageCollection" -> recordGcCycle(event, gcPauses, gcStats);
                    case "jdk.GCHeapSummary" -> {
                        sawGcHeapSummary[0] = true;
                        heapUsedMax[0] = Math.max(heapUsedMax[0], longField(event, "heapUsed"));
                    }
                    case "jdk.ObjectAllocationSample" -> recordAllocation(event, allocBytesByMod);
                    case "jdk.FileRead", "jdk.FileWrite" ->
                            recordFileIo(event, ioPoints, fileAgg, readPathBytes, writePathBytes);
                    case "minecraft.PacketReceived", "minecraft.PacketSent" -> recordPacket(event, packetAgg);
                    case "minecraft.ChunkRegionRead", "minecraft.ChunkRegionWrite" -> {
                        chunkPoints.add(new ChunkPoint(timeOf(event)));
                        recordChunk(event, chunkAgg);
                    }
                    default -> {
                        // events we do not use
                    }
                }
            }
        } catch (Throwable t) {
            warnings.add("JFR 事件扫描失败：" + t);
        }

        // Minecraft's summary parser assumes at least one GC heap summary event, so
        // only use it when the recording actually contains one. The per-event
        // aggregation above is the fallback for everything else.
        JfrStatsResult stats = null;
        if (sawGcHeapSummary[0]) {
            try {
                stats = JfrStatsParser.parse(input.jfrPath());
            } catch (Throwable t) {
                warnings.add("Minecraft JFR 汇总不可用，已改用逐事件统计。");
            }
        }

        if (renderSamples.isEmpty()) {
            warnings.add("未采集到渲染线程的执行采样，CPU 归因不可用（JFR 可能被其他工具占用或采样被禁用）。");
        }

        double durationSeconds = Math.max(0.001, input.actualDurationSeconds());
        List<Double> frameTimesMs = toList(input.frameIntervalsMs());
        double avgFps = frameTimesMs.isEmpty() ? 0 : frameTimesMs.size() / durationSeconds;
        double minInterval = ReportStats.minFrameTime(frameTimesMs);
        double maxInterval = ReportStats.maxFrameTime(frameTimesMs);
        double maxFps = minInterval > 0 ? 1000.0 / minInterval : 0;
        double minFps = maxInterval > 0 ? 1000.0 / maxInterval : 0;
        double low1Fps = ReportStats.lowFps(frameTimesMs, 1.0);
        double low01Fps = ReportStats.lowFps(frameTimesMs, 0.1);

        long totalRender = total(modCounters, 0);
        long totalServer = total(modCounters, 1);
        if (totalServer == 0) {
            warnings.add("未采集到服务端线程样本：多人服务器下属正常（TPS/MSPT 不适用）。");
        }
        int slowFrames = 0;
        for (double interval : frameTimesMs) {
            if (interval >= 33.4) {
                slowFrames++;
            }
        }
        if (!frameTimesMs.isEmpty() && slowFrames > frameTimesMs.size() * 0.2) {
            warnings.add("超过 20% 的帧慢于 33 ms：本次运行可能存在长时间低帧率阶段（窗口失焦、帧率上限或重负载），卡顿判定已按局部基线计算。");
        }

        StutterDetection detection = detectStutters(input, frameTimesMs, gcPauses, ioPoints, chunkPoints, renderSamples);

        List<StutterResult.ModCpuShare> modCpu = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : modCounters.entrySet()) {
            long[] counters = entry.getValue();
            modCpu.add(new StutterResult.ModCpuShare(entry.getKey(),
                    counters[0] + counters[1] + counters[2] + counters[3],
                    counters[0], counters[1], counters[2], counters[3],
                    totalRender == 0 ? 0 : counters[0] * 100.0 / totalRender,
                    totalServer == 0 ? 0 : counters[1] * 100.0 / totalServer));
        }
        modCpu.sort(Comparator.comparingLong(StutterResult.ModCpuShare::renderSamples).reversed()
                .thenComparing(Comparator.comparingLong(StutterResult.ModCpuShare::samples).reversed()));

        List<StutterResult.HotMethod> hotMethods = new ArrayList<>();
        for (Map.Entry<String, Long> entry : hotMethodCounts.entrySet()) {
            String[] parts = entry.getKey().split("\u0000", 2);
            hotMethods.add(new StutterResult.HotMethod(parts[0], parts.length > 1 ? parts[1] : "?",
                    entry.getValue(), totalRender == 0 ? 0 : entry.getValue() * 100.0 / totalRender));
        }
        hotMethods.sort(Comparator.comparingLong(StutterResult.HotMethod::samples).reversed());
        if (hotMethods.size() > MAX_HOT_METHOD_ROWS) {
            hotMethods = new ArrayList<>(hotMethods.subList(0, MAX_HOT_METHOD_ROWS));
        }

        List<StutterResult.ThreadWaitStat> threadWaits = buildThreadWaits(waitByThread, samplesByThread, durationSeconds);

        long gcTotalPauseMicros = gcStats[1] / 1000L;
        long gcLongestPauseMicros = gcStats[2] / 1000L;
        long gcCount = gcStats[0] > 0 ? gcStats[0] : gcPauses.size();
        List<StutterResult.GcPause> topGcPauses = new ArrayList<>(gcPauses);
        topGcPauses.sort(Comparator.comparingLong(StutterResult.GcPause::durationMicros).reversed());
        if (topGcPauses.size() > MAX_GC_ROWS) {
            topGcPauses = new ArrayList<>(topGcPauses.subList(0, MAX_GC_ROWS));
        }

        long totalAlloc = 0;
        for (long value : allocBytesByMod.values()) {
            totalAlloc += value;
        }
        double allocationRate = totalAlloc / durationSeconds;
        if (stats != null && stats.heapSummary().allocationRateBytesPerSecond() > 0) {
            allocationRate = stats.heapSummary().allocationRateBytesPerSecond();
        }

        List<StutterResult.ModAllocation> modAllocations = new ArrayList<>();
        for (Map.Entry<String, Long> entry : allocBytesByMod.entrySet()) {
            modAllocations.add(new StutterResult.ModAllocation(entry.getKey(),
                    entry.getValue() / durationSeconds,
                    totalAlloc == 0 ? 0 : entry.getValue() * 100.0 / totalAlloc));
        }
        modAllocations.sort(Comparator.comparingDouble(StutterResult.ModAllocation::bytesPerSecond).reversed());

        List<StutterResult.PacketSummary> packets = buildPackets(packetAgg);
        List<StutterResult.ChunkSummary> chunks = buildChunks(chunkAgg);
        StutterResult.FileSummary fileReads = stats != null && stats.fileReads().counts() > 0
                ? toFileSummary(stats.fileReads())
                : fileSummary(fileAgg[0], fileAgg[1], fileAgg[2], readPathBytes, durationSeconds);
        StutterResult.FileSummary fileWrites = stats != null && stats.fileWrites().counts() > 0
                ? toFileSummary(stats.fileWrites())
                : fileSummary(fileAgg[3], fileAgg[4], fileAgg[5], writePathBytes, durationSeconds);

        if (input.rendererSummary() != null && !input.rendererSummary().contains("vanilla")) {
            warnings.add("检测到非原版渲染管线（" + input.rendererSummary() + "）：原版渲染阶段钩子不可用，逐模组 GPU 归因不可靠。");
        }
        warnings.add("1.2.0 未采集 GPU 计时，GPU 侧耗时请等待 1.3.0。");

        return new StutterResult(input.timestampMillis(), input.targetDurationSeconds(), input.actualDurationSeconds(),
                frameTimesMs.size(), avgFps, maxFps, minFps, low1Fps, low01Fps, frameTimesMs,
                input.samplePeriodMs(), input.modVersion(), input.rendererSummary(),
                detection.episodes, detection.frames, detection.excessMillis, detection.events,
                modCpu, hotMethods, threadWaits, topGcPauses, gcCount, gcTotalPauseMicros, gcLongestPauseMicros,
                heapUsedMax[0] / BYTES_PER_MB, allocationRate, modAllocations,
                packets, chunks, fileReads, fileWrites,
                input.jfrPath(), warnings);
    }

    private record StutterDetection(int episodes, int frames, double excessMillis,
                                    List<StutterResult.StutterEvent> events) {
    }

    /** One slow frame. */
    private record FrameHit(long startMillis, long endMillis, double intervalMs, double baselineMs, double excessMs) {
    }

    /** Frames closer together than this are merged into one stutter episode. */
    private static final long EPISODE_GAP_MILLIS = 500;
    /** Episodes longer than this are reported as a sustained low-FPS phase. */
    private static final long SUSTAINED_PHASE_MILLIS = 750;

    private static StutterDetection detectStutters(Input input, List<Double> frameTimes,
                                                   List<StutterResult.GcPause> gcPauses,
                                                   List<IoPoint> ioPoints, List<ChunkPoint> chunkPoints,
                                                   List<SamplePoint> renderSamples) {
        if (frameTimes.isEmpty()) {
            return new StutterDetection(0, 0, 0, List.of());
        }
        double[] baselines = baselineMedians(frameTimes);
        List<FrameHit> hits = new ArrayList<>();
        double cumulativeMillis = 0;

        for (int i = 0; i < frameTimes.size(); i++) {
            double interval = frameTimes.get(i);
            double baseline = baselines[i];
            double threshold = Math.max(baseline * STUTTER_FACTOR, baseline + STUTTER_MIN_EXCESS_MS);
            if (interval >= threshold) {
                long start = input.startWallMillis() + Math.round(cumulativeMillis);
                hits.add(new FrameHit(start, start + Math.round(interval), interval, baseline,
                        Math.max(0, interval - baseline)));
            }
            cumulativeMillis += interval;
        }

        List<StutterResult.StutterEvent> episodes = new ArrayList<>();
        int index = 0;
        while (index < hits.size()) {
            FrameHit first = hits.get(index);
            long episodeStart = first.startMillis();
            long episodeEnd = first.endMillis();
            int frames = 1;
            double maxInterval = first.intervalMs();
            double baselineSum = first.baselineMs();
            double excess = first.excessMs();
            index++;
            while (index < hits.size() && hits.get(index).startMillis() - episodeEnd <= EPISODE_GAP_MILLIS) {
                FrameHit next = hits.get(index);
                episodeEnd = Math.max(episodeEnd, next.endMillis());
                frames++;
                maxInterval = Math.max(maxInterval, next.intervalMs());
                baselineSum += next.baselineMs();
                excess += next.excessMs();
                index++;
            }
            double baseline = baselineSum / frames;
            long duration = episodeEnd - episodeStart;
            String cause = duration >= SUSTAINED_PHASE_MILLIS
                    ? "持续低帧率阶段（" + frames + " 帧）"
                    : describeCause(episodeStart, episodeEnd, gcPauses, ioPoints, chunkPoints, renderSamples);
            episodes.add(new StutterResult.StutterEvent(episodeStart, episodeEnd, frames, maxInterval,
                    baseline, excess, cause));
        }

        episodes.sort(Comparator.comparingDouble(StutterResult.StutterEvent::excessMs).reversed());
        if (episodes.size() > MAX_STUTTER_ROWS) {
            episodes = new ArrayList<>(episodes.subList(0, MAX_STUTTER_ROWS));
        }
        return new StutterDetection(episodes.size(), hits.size(),
                episodes.stream().mapToDouble(StutterResult.StutterEvent::excessMs).sum(), episodes);
    }

    /**
     * {@return the local baseline (median of the previous frames) for every frame}
     * A rolling median keeps a slow-but-stable phase from being reported as one
     * giant stutter, while still catching spikes inside a fast phase.
     */
    private static double[] baselineMedians(List<Double> frameTimes) {
        int n = frameTimes.size();
        double[] result = new double[n];
        double globalMedian = median(frameTimes);
        double[] window = new double[BASELINE_WINDOW];
        for (int i = 0; i < n; i++) {
            int from = Math.max(0, i - BASELINE_WINDOW);
            int length = i - from;
            if (length < 10) {
                result[i] = globalMedian;
                continue;
            }
            for (int j = 0; j < length; j++) {
                window[j] = frameTimes.get(from + j);
            }
            Arrays.sort(window, 0, length);
            result[i] = length % 2 == 0
                    ? (window[length / 2 - 1] + window[length / 2]) / 2.0
                    : window[length / 2];
        }
        return result;
    }

    private static String describeCause(long start, long end,
                                        List<StutterResult.GcPause> gcPauses,
                                        List<IoPoint> ioPoints,
                                        List<ChunkPoint> chunkPoints,
                                        List<SamplePoint> renderSamples) {
        StutterResult.GcPause gc = null;
        for (StutterResult.GcPause pause : gcPauses) {
            if (pause.durationMicros() < GC_ATTRIBUTION_MIN_MICROS) {
                continue;
            }
            if (pause.startMillis() <= end && pause.startMillis() + (pause.durationMicros() / 1000L) >= start
                    && (gc == null || pause.durationMicros() > gc.durationMicros())) {
                gc = pause;
            }
        }
        if (gc != null) {
            return "GC（" + gc.name() + "，暂停 " + ReportStats.format(gc.durationMicros() / 1000.0, "%.2f") + " ms）";
        }

        IoPoint io = null;
        for (IoPoint point : ioPoints) {
            if (point.durationMillis >= 2 && point.timeMillis <= end
                    && point.timeMillis + point.durationMillis >= start
                    && (io == null || point.durationMillis > io.durationMillis)) {
                io = point;
            }
        }
        if (io != null) {
            return "磁盘 IO（" + shortPath(io.path) + "，" + io.durationMillis + " ms）";
        }

        int chunkHits = 0;
        for (ChunkPoint point : chunkPoints) {
            if (point.timeMillis >= start - 20 && point.timeMillis <= end + 20) {
                chunkHits++;
            }
        }
        if (chunkHits > 0) {
            return "区块 IO（窗口内 " + chunkHits + " 次）";
        }

        Map<String, Integer> hot = new HashMap<>();
        Map<String, Integer> hotMethod = new HashMap<>();
        for (SamplePoint point : renderSamples) {
            if (point.timeMillis >= start && point.timeMillis <= end) {
                hot.merge(point.modId, 1, Integer::sum);
                if (point.method != null) {
                    hotMethod.merge(point.method, 1, Integer::sum);
                }
            }
        }
        String bestMod = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : hot.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestMod = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        String bestMethod = null;
        int bestMethodCount = 0;
        for (Map.Entry<String, Integer> entry : hotMethod.entrySet()) {
            if (entry.getValue() > bestMethodCount) {
                bestMethod = entry.getKey();
                bestMethodCount = entry.getValue();
            }
        }
        if (bestCount >= HOTSPOT_MIN_SAMPLES && bestMod != null) {
            String suffix = bestMethod == null ? "" : " · " + bestMethod;
            if (NON_MOD_SOURCES.contains(bestMod)) {
                return "原版渲染管线热点" + suffix + "（" + bestCount + " 次采样）";
            }
            return "渲染线程热点：" + bestMod + suffix + "（" + bestCount + " 次采样）";
        }
        return "未归因（采样不足，可能是 GPU 等待或帧调度）";
    }

    private static void recordSample(RecordedEvent event, Map<String, long[]> modCounters,
                                     List<SamplePoint> renderSamples, Map<String, Long> samplesByThread,
                                     Map<String, Long> hotMethodCounts) {
        String thread = threadName(event);
        samplesByThread.merge(thread, 1L, Long::sum);
        String mod = ModAttribution.get().modForClass(topClass(event));
        long[] counters = modCounters.computeIfAbsent(mod, key -> new long[4]);
        if (isRenderThread(thread)) {
            counters[0]++;
            String method = topMethod(event);
            renderSamples.add(new SamplePoint(timeOf(event), mod, method));
            if (method != null) {
                hotMethodCounts.merge(mod + "\u0000" + method, 1L, Long::sum);
            }
        } else if (isServerThread(thread)) {
            counters[1]++;
        } else {
            counters[2]++;
        }
    }

    private static void recordNative(RecordedEvent event, Map<String, long[]> modCounters,
                                     Map<String, Long> samplesByThread) {
        samplesByThread.merge(threadName(event), 1L, Long::sum);
        String mod = ModAttribution.get().modForClass(topClass(event));
        long[] counters = modCounters.computeIfAbsent(mod, key -> new long[4]);
        counters[3]++;
    }

    private static void recordWait(RecordedEvent event, Map<String, long[]> waitByThread, int index) {
        long[] counters = waitByThread.computeIfAbsent(threadName(event), key -> new long[8]);
        counters[index]++;
        counters[index + 1] += event.getDuration().toMillis();
    }

    private static void recordGcCycle(RecordedEvent event, List<StutterResult.GcPause> gcPauses, long[] gcStats) {
        String name;
        try {
            name = event.getString("name");
        } catch (Throwable t) {
            name = "GC";
        }
        long pauseNanos;
        long longestNanos;
        try {
            pauseNanos = event.getDuration("sumOfPauses").toNanos();
            longestNanos = event.getDuration("longestPause").toNanos();
        } catch (Throwable t) {
            pauseNanos = 0;
            longestNanos = 0;
        }
        gcStats[0]++;
        gcStats[1] += pauseNanos;
        gcStats[2] = Math.max(gcStats[2], longestNanos);
        gcPauses.add(new StutterResult.GcPause(timeOf(event), pauseNanos / 1000L, name == null ? "GC" : name));
    }

    private static void recordAllocation(RecordedEvent event, Map<String, Long> allocBytesByMod) {
        long weight = longField(event, "weight");
        if (weight <= 0) {
            return;
        }
        String mod = ModAttribution.get().modForClass(topClass(event));
        allocBytesByMod.merge(mod, weight, Long::sum);
    }

    private static void recordFileIo(RecordedEvent event, List<IoPoint> ioPoints, long[] fileAgg,
                                     Map<String, Long> readPathBytes, Map<String, Long> writePathBytes) {
        boolean write = event.getEventType().getName().endsWith("Write");
        String path;
        try {
            path = event.getString("path");
        } catch (Throwable t) {
            path = "";
        }
        String resolvedPath = path == null ? "" : path;
        long bytes = longField(event, write ? "bytesWritten" : "bytesRead");
        int offset = write ? 3 : 0;
        fileAgg[offset] += bytes;
        fileAgg[offset + 1]++;
        fileAgg[offset + 2] += event.getDuration().toNanos();
        (write ? writePathBytes : readPathBytes).merge(resolvedPath, bytes, Long::sum);

        long durationMillis = event.getDuration().toMillis();
        if (durationMillis >= 2 && ioPoints.size() < 5000) {
            ioPoints.add(new IoPoint(timeOf(event), durationMillis, resolvedPath));
        }
    }

    private static void recordPacket(RecordedEvent event, Map<String, long[]> packetAgg) {
        try {
            String direction = event.getString("packetDirection");
            String protocol = event.getString("protocolId");
            String packetId = event.getString("packetId");
            int bytes = event.getInt("bytes");
            String key = direction + "\u0000" + protocol + "\u0000" + packetId;
            long[] aggregate = packetAgg.computeIfAbsent(key, ignored -> new long[2]);
            aggregate[0]++;
            aggregate[1] += bytes;
        } catch (Throwable ignored) {
            // event is missing a field on this JVM version
        }
    }

    private static void recordChunk(RecordedEvent event, Map<String, long[]> chunkAgg) {
        try {
            String kind = event.getEventType().getName().endsWith("Write") ? "写入" : "读取";
            String dimension = event.getString("dimension");
            int x = event.getInt("chunkPosX");
            int z = event.getInt("chunkPosZ");
            int bytes = event.getInt("bytes");
            String key = kind + "\u0000" + dimension + "\u0000" + x + "\u0000" + z;
            long[] aggregate = chunkAgg.computeIfAbsent(key, ignored -> new long[2]);
            aggregate[0]++;
            aggregate[1] += bytes;
        } catch (Throwable ignored) {
            // event is missing a field on this JVM version
        }
    }

    private static List<StutterResult.ThreadWaitStat> buildThreadWaits(Map<String, long[]> waitByThread,
                                                                       Map<String, Long> samplesByThread,
                                                                       double durationSeconds) {
        Map<String, long[]> merged = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> entry : waitByThread.entrySet()) {
            merged.put(entry.getKey(), entry.getValue());
        }
        for (String thread : samplesByThread.keySet()) {
            merged.computeIfAbsent(thread, key -> new long[8]);
        }
        List<StutterResult.ThreadWaitStat> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : merged.entrySet()) {
            long[] counters = entry.getValue();
            long samples = samplesByThread.getOrDefault(entry.getKey(), 0L);
            long totalWait = counters[1] + counters[3] + counters[5] + counters[7];
            // Pure idle loops (watchers, parked pools) wait for the whole run and
            // never get a CPU sample; hide them unless they actually ran code.
            boolean idleOnly = samples == 0 && totalWait >= durationSeconds * 1000.0 * 0.8;
            if (idleOnly && !isRenderThread(entry.getKey()) && !isServerThread(entry.getKey())) {
                continue;
            }
            result.add(new StutterResult.ThreadWaitStat(entry.getKey(), samples,
                    counters[0], counters[1], counters[2], counters[3], counters[4], counters[5], counters[6], counters[7]));
        }
        result.sort(Comparator.comparingLong(StutterResult.ThreadWaitStat::cpuSamples).reversed()
                .thenComparing(Comparator.comparingLong(StutterResult.ThreadWaitStat::totalWaitMillis).reversed()));
        return result;
    }

    private static List<StutterResult.PacketSummary> buildPackets(Map<String, long[]> packetAgg) {
        List<StutterResult.PacketSummary> packets = new ArrayList<>(packetAgg.size());
        for (Map.Entry<String, long[]> entry : packetAgg.entrySet()) {
            String[] parts = entry.getKey().split("\u0000", 3);
            long[] aggregate = entry.getValue();
            packets.add(new StutterResult.PacketSummary(parts[0], parts[1], parts[2], aggregate[0], aggregate[1]));
        }
        packets.sort(Comparator.comparingLong(StutterResult.PacketSummary::bytes).reversed());
        return packets;
    }

    private static List<StutterResult.ChunkSummary> buildChunks(Map<String, long[]> chunkAgg) {
        List<StutterResult.ChunkSummary> chunks = new ArrayList<>(chunkAgg.size());
        for (Map.Entry<String, long[]> entry : chunkAgg.entrySet()) {
            String[] parts = entry.getKey().split("\u0000", 4);
            long[] aggregate = entry.getValue();
            chunks.add(new StutterResult.ChunkSummary(parts[0], parts[1],
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), aggregate[0], aggregate[1]));
        }
        chunks.sort(Comparator.comparingLong(StutterResult.ChunkSummary::bytes).reversed());
        return chunks;
    }

    private static StutterResult.FileSummary fileSummary(long bytes, long count, long nanos,
                                                         Map<String, Long> pathBytes, double durationSeconds) {
        if (count == 0) {
            return StutterResult.FileSummary.empty();
        }
        List<String> paths = new ArrayList<>();
        pathBytes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_IO_ROWS)
                .forEach(entry -> paths.add(shortPath(entry.getKey()) + "（"
                        + ReportStats.format(entry.getValue() / BYTES_PER_MB, "%.1f") + " MB）"));
        return new StutterResult.FileSummary(bytes, bytes / Math.max(0.001, durationSeconds), count,
                nanos / 1_000_000.0, paths);
    }

    private static StutterResult.FileSummary toFileSummary(FileIOStat.Summary summary) {
        if (summary == null) {
            return StutterResult.FileSummary.empty();
        }
        List<String> paths = new ArrayList<>();
        for (Pair<String, Long> pair : summary.topTenContributorsByTotalBytes()) {
            paths.add(shortPath(pair.getFirst()) + "（"
                    + ReportStats.format(pair.getSecond() / BYTES_PER_MB, "%.1f") + " MB）");
            if (paths.size() >= MAX_IO_ROWS) {
                break;
            }
        }
        return new StutterResult.FileSummary(summary.totalBytes(), summary.bytesPerSecond(), summary.counts(),
                summary.timeSpentInIO().toMillis(), paths);
    }

    private static String shortPath(String path) {
        if (path == null || path.isEmpty()) {
            return "未知文件";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static boolean isRenderThread(String thread) {
        return thread != null && (thread.startsWith("Render thread") || thread.equals("main"));
    }

    private static boolean isServerThread(String thread) {
        return thread != null && thread.startsWith("Server thread");
    }

    private static String threadName(RecordedEvent event) {
        try {
            RecordedThread thread = event.getThread();
            if (thread == null) {
                thread = event.getThread("sampledThread");
            }
            String name = thread == null ? null : thread.getJavaName();
            return name == null ? "unknown" : name;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static String topClass(RecordedEvent event) {
        try {
            RecordedStackTrace trace = event.getStackTrace();
            if (trace == null) {
                return null;
            }
            for (RecordedFrame frame : trace.getFrames()) {
                if (!frame.isJavaFrame()) {
                    continue;
                }
                RecordedMethod method = frame.getMethod();
                if (method == null || method.getType() == null) {
                    continue;
                }
                return method.getType().getName();
            }
        } catch (Throwable ignored) {
            // no stack trace on this event
        }
        return null;
    }

    /** {@return "SimpleClass#method" of the innermost Java frame, or null} */
    private static String topMethod(RecordedEvent event) {
        try {
            RecordedStackTrace trace = event.getStackTrace();
            if (trace == null) {
                return null;
            }
            for (RecordedFrame frame : trace.getFrames()) {
                if (!frame.isJavaFrame()) {
                    continue;
                }
                RecordedMethod method = frame.getMethod();
                if (method == null || method.getType() == null) {
                    continue;
                }
                String type = method.getType().getName();
                int dot = type.lastIndexOf('.');
                String simple = dot >= 0 ? type.substring(dot + 1) : type;
                return simple + "#" + method.getName();
            }
        } catch (Throwable ignored) {
            // no stack trace on this event
        }
        return null;
    }
    private static long timeOf(RecordedEvent event) {
        return event.getStartTime().toEpochMilli();
    }

    private static long longField(RecordedEvent event, String field) {
        try {
            return event.getLong(field);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long total(Map<String, long[]> counters, int index) {
        long sum = 0;
        for (long[] values : counters.values()) {
            sum += values[index];
        }
        return sum;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double[] sorted = new double[values.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = values.get(i);
        }
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0 : sorted[middle];
    }

    private static List<Double> toList(double[] values) {
        List<Double> list = new ArrayList<>(values.length);
        for (double value : values) {
            list.add(value);
        }
        return list;
    }
}
