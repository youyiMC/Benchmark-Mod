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

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.management.OperatingSystemMXBean;
import com.youyimc.benchmark.Benchmark;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Runs a single benchmark test. Frames are fed in via {@link #onFrame()} from
 * the render frame event; {@link #tick()} from the client tick guarantees the
 * test finishes on time even when frames stall. Sampling is lightweight and
 * hardware queries are throttled so the test itself is not skewed.
 */
public class BenchmarkSession {
    private static final BenchmarkSession INSTANCE = new BenchmarkSession();

    public static BenchmarkSession getInstance() {
        return INSTANCE;
    }

    private static final double SAMPLE_INTERVAL_SECONDS = 0.5;

    private boolean running = false;
    private double targetDurationSeconds = 30;
    private long startNanos = 0;
    private long lastFrameNanos = 0;
    private long lastSampleNanos = 0;
    private int frames = 0;

    private final List<Double> frameTimesMs = new ArrayList<>();
    private final List<Double> cpuSamples = new ArrayList<>();
    private final List<Double> memSamples = new ArrayList<>();
    private final List<Double> heapSamples = new ArrayList<>();
    private final List<Double> gpuSamples = new ArrayList<>();
    private final List<Double> vramSamples = new ArrayList<>();
    private final List<Double> msptSamples = new ArrayList<>();

    private boolean hasServerData = false;
    private double instMinFps = Double.MAX_VALUE;
    private double instMaxFps = 0;

    private long lastServerTickCount = -1;
    private long lastServerSampleNanos = 0;

    private BenchmarkResult result = null;
    private final List<Path> reportPaths = new ArrayList<>();

    private BenchmarkSession() {
    }

    public boolean isRunning() {
        return running;
    }

    public BenchmarkResult getResult() {
        return result;
    }

    public List<Path> getReportPaths() {
        return reportPaths;
    }

    public double getTargetDurationSeconds() {
        return targetDurationSeconds;
    }

    public double getElapsedSeconds() {
        if (!running) {
            return 0;
        }
        return (System.nanoTime() - startNanos) / 1e9;
    }

    public void start(double seconds) {
        this.targetDurationSeconds = Math.max(1.0, seconds);
        this.running = true;
        this.frames = 0;
        this.frameTimesMs.clear();
        this.cpuSamples.clear();
        this.memSamples.clear();
        this.heapSamples.clear();
        this.gpuSamples.clear();
        this.vramSamples.clear();
        this.msptSamples.clear();
        this.hasServerData = false;
        this.instMinFps = Double.MAX_VALUE;
        this.instMaxFps = 0;
        this.lastServerTickCount = -1;
        this.lastServerSampleNanos = 0;
        this.result = null;
        this.reportPaths.clear();
        long now = System.nanoTime();
        this.startNanos = now;
        this.lastFrameNanos = now;
        this.lastSampleNanos = now;
    }

    /** Called once per rendered frame while a test is running. */
    public void onFrame() {
        if (!running) {
            return;
        }
        long now = System.nanoTime();
        long deltaNanos = now - lastFrameNanos;
        lastFrameNanos = now;

        if (frames > 0 && deltaNanos > 0) {
            double deltaMs = deltaNanos / 1_000_000.0;
            frameTimesMs.add(deltaMs);
            double fps = 1000.0 / deltaMs;
            if (fps < instMinFps) {
                instMinFps = fps;
            }
            if (fps > instMaxFps) {
                instMaxFps = fps;
            }
        }
        frames++;

        if (now - lastSampleNanos >= (long) (SAMPLE_INTERVAL_SECONDS * 1e9)) {
            sampleHardware();
            lastSampleNanos = now;
        }

        checkCompletion();
    }

    /** Called from the client tick to guarantee on-time completion. */
    public void tick() {
        if (!running) {
            return;
        }
        checkCompletion();
    }

    private void checkCompletion() {
        if (!running) {
            return;
        }
        double elapsed = (System.nanoTime() - startNanos) / 1e9;
        if (elapsed >= targetDurationSeconds) {
            finish();
        }
    }

    private void sampleHardware() {
        try {
            OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double cpu = os.getCpuLoad();
            if (cpu >= 0) {
                cpuSamples.add(cpu * 100.0);
            }
            long total = os.getTotalMemorySize();
            long free = os.getFreeMemorySize();
            memSamples.add((total - free) / (1024.0 * 1024.0));
            heapSamples.add((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024.0 * 1024.0));
        } catch (Throwable t) {
            // ignore sampling errors
        }

        HardwareInfo.GpuUsage gpu = HardwareInfo.getGpuUsage();
        if (gpu != null) {
            gpuSamples.add(gpu.usagePercent);
            vramSamples.add(gpu.vramUsedMB);
        }

        // TPS / MSPT: only sampled when we are the singleplayer or LAN host.
        // Measured from the server tick counter delta (the per-tick timing array
        // is not exposed by the vanilla API in 1.21.1).
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            try {
                long nowNanos = System.nanoTime();
                int tick = mc.getSingleplayerServer().getTickCount();
                if (lastServerTickCount >= 0 && lastServerSampleNanos > 0) {
                    double dt = (nowNanos - lastServerSampleNanos) / 1e9;
                    long dtick = (long) tick - lastServerTickCount;
                    if (dt > 0 && dtick >= 0) {
                        double tps = dtick / dt;
                        if (tps > 0) {
                            msptSamples.add(1000.0 / tps);
                            hasServerData = true;
                        }
                    }
                }
                lastServerTickCount = tick;
                lastServerSampleNanos = nowNanos;
            } catch (Throwable t) {
                // ignore
            }
        } else {
            lastServerTickCount = -1;
            lastServerSampleNanos = 0;
        }
    }

    private void finish() {
        running = false;
        long now = System.nanoTime();
        double actualSeconds = (now - startNanos) / 1e9;
        double avgFps = actualSeconds > 0 ? frames / actualSeconds : 0;
        double minFps = instMinFps == Double.MAX_VALUE ? 0 : instMinFps;
        double maxFps = instMaxFps;
        double low1 = percentileFps(frameTimesMs, 1.0);
        double low01 = percentileFps(frameTimesMs, 0.1);

        double avgMspt = 0;
        double maxMspt = 0;
        if (!msptSamples.isEmpty()) {
            double sum = 0;
            double max = 0;
            for (double v : msptSamples) {
                sum += v;
                if (v > max) {
                    max = v;
                }
            }
            avgMspt = sum / msptSamples.size();
            maxMspt = max;
        }

        double avgCpu = average(cpuSamples);
        double maxCpu = maxValue(cpuSamples);
        double avgMem = average(memSamples);
        double maxMem = maxValue(memSamples);
        double avgHeap = average(heapSamples);
        double maxHeap = maxValue(heapSamples);
        boolean hasGpu = !gpuSamples.isEmpty();
        double avgGpu = average(gpuSamples);
        double maxGpu = maxValue(gpuSamples);
        double avgVram = average(vramSamples);
        double maxVram = maxValue(vramSamples);

        List<String> analysis = analyze(avgFps, low1, avgMspt, hasServerData, avgCpu, avgGpu, hasGpu);

        HardwareInfo hardware = HardwareInfo.detect();

        result = new BenchmarkResult(
                System.currentTimeMillis(), targetDurationSeconds, actualSeconds, frames,
                avgFps, maxFps, minFps, low1, low01,
                hasServerData, avgMspt, maxMspt,
                avgCpu, maxCpu, avgMem, maxMem, avgHeap, maxHeap,
                hasGpu, avgGpu, maxGpu, avgVram, maxVram,
                analysis,
                new ArrayList<>(frameTimesMs),
                new ArrayList<>(cpuSamples),
                new ArrayList<>(memSamples),
                new ArrayList<>(gpuSamples),
                new ArrayList<>(msptSamples),
                Benchmark.modVersion(),
                hardware);

        ReportWriter.writeReport(result);
        this.reportPaths.addAll(result.reportPaths);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("benchmark.chat.completed"));
        }
    }

    private static double percentileFps(List<Double> frameTimesMs, double percent) {
        if (frameTimesMs.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(frameTimesMs);
        Collections.sort(sorted);
        int n = Math.max(1, (int) Math.floor(sorted.size() * percent / 100.0));
        double sum = 0;
        for (int i = sorted.size() - n; i < sorted.size(); i++) {
            sum += sorted.get(i);
        }
        double avgMs = sum / n;
        return avgMs > 0 ? 1000.0 / avgMs : 0;
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private static double maxValue(List<Double> values) {
        double max = 0;
        for (double v : values) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    private static List<String> analyze(double avgFps, double low1, double avgMspt,
                                        boolean hasServer, double avgCpu, double avgGpu, boolean hasGpu) {
        List<String> lines = new ArrayList<>();
        lines.add("以下分析由模组自动生成，仅供参考，不构成任何性能优化保证。");

        if (avgFps >= 120) {
            lines.add(String.format("整体帧率表现优秀（平均 %.1f FPS）。", avgFps));
        } else if (avgFps >= 60) {
            lines.add(String.format("整体帧率表现良好（平均 %.1f FPS），接近流畅标准。", avgFps));
        } else if (avgFps >= 30) {
            lines.add(String.format("整体帧率一般（平均 %.1f FPS），建议适当降低画质设置。", avgFps));
        } else {
            lines.add(String.format("整体帧率偏低（平均 %.1f FPS），建议大幅降低画质/分辨率并关闭后台程序。", avgFps));
        }

        if (low1 > 0 && avgFps > 0 && (low1 / avgFps) < 0.6) {
            lines.add(String.format("1%% Low（%.1f FPS）明显低于平均帧率，帧生成时间波动较大，可能存在卡顿感。", low1));
        } else if (low1 > 0) {
            lines.add(String.format("1%% Low 为 %.1f FPS，帧生成较为稳定。", low1));
        }

        if (hasServer) {
            double tps = avgMspt > 0 ? Math.min(20.0, 1000.0 / avgMspt) : 20.0;
            // Minecraft targets 20 TPS = 50ms per tick budget. MSPT is derived from
            // the measured TPS (capped at 20), so it stays ~50ms while the server
            // keeps up, and only climbs above 50ms when TPS actually drops.
            if (tps >= 19.5) {
                lines.add(String.format("服务器以满 20 TPS 运行（平均 MSPT %.2f ms，处于每刻 50ms 预算内），服务器侧流畅无滞后。", avgMspt));
            } else if (avgMspt >= 60) {
                lines.add(String.format("平均 MSPT 达 %.2f ms（约 %.1f TPS），已超出 50ms/刻 预算，服务器压力很大，可能造成明显卡顿/方块延迟。", avgMspt, tps));
            } else {
                lines.add(String.format("平均 MSPT 为 %.2f ms（约 %.1f TPS），服务器接近满负荷，可能偶发卡顿。", avgMspt, tps));
            }
        } else {
            lines.add("当前未处于单人/局域网主机模式，未采集服务器 TPS/MSPT 数据。");
        }

        if (hasGpu && avgGpu > 95 && avgCpu < 60) {
            lines.add("GPU 占用率接近满载而 CPU 占用较低，瓶颈很可能在 GPU（渲染/分辨率压力）。");
        }
        if (hasGpu && avgCpu > 95 && avgGpu < 70) {
            lines.add("CPU 占用率接近满载而 GPU 占用不高，瓶颈很可能在 CPU（实体/逻辑运算压力）。");
        }

        lines.add("建议：优先调整渲染距离、实体渲染距离与光影设置，并关注 1% Low 帧率变化。");
        return lines;
    }
}
