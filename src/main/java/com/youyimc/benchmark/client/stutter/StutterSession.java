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
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.youyimc.benchmark.Benchmark;
import com.youyimc.benchmark.Config;
import com.youyimc.benchmark.client.BenchmarkSession;
import com.youyimc.benchmark.client.HardwareInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Runs one stutter analysis: records frame intervals on the render thread while
 * a JFR recording is active, then hands the {@code .jfr} file to a background
 * thread for parsing, aggregation and report rendering.
 *
 * <p>The render thread only does two nanosecond reads and an array write per
 * frame; everything else (JFR writing, parsing, attribution, Markdown) happens
 * off-thread so the measurement itself is not skewed.</p>
 */
public final class StutterSession {
    public enum State {
        IDLE,
        RUNNING,
        ANALYZING,
        DONE,
        FAILED
    }

    private static final StutterSession INSTANCE = new StutterSession();

    public static StutterSession getInstance() {
        return INSTANCE;
    }

    private volatile State state = State.IDLE;

    private long startNanos;
    private long lastFrameNanos;
    private long startWallMillis;
    private double targetDurationSeconds = 60;
    private int samplePeriodMs = 20;
    private String modVersion = "unknown";
    private String rendererSummary = "vanilla renderer";
    private int frameCount;

    private double[] intervals = new double[8192];
    private int intervalCount;

    private Path jfrPath;
    private Path reportPath;
    private StutterResult result;
    private String error = "";
    private JfrRecorder recorder;
    private boolean completionNotified;

    private StutterSession() {
    }

    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isBusy() {
        return state == State.RUNNING || state == State.ANALYZING;
    }

    public StutterResult getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public Path getJfrPath() {
        return jfrPath;
    }

    public Path getReportPath() {
        return reportPath;
    }

    public int getSamplePeriodMs() {
        return samplePeriodMs;
    }

    public double getTargetDurationSeconds() {
        return targetDurationSeconds;
    }

    public double getElapsedSeconds() {
        return state == State.RUNNING ? (System.nanoTime() - startNanos) / 1e9 : 0;
    }

    /** Starts a stutter analysis. Returns false (and sets {@link #getError()}) on failure. */
    public boolean start(double seconds) {
        if (isBusy()) {
            return false;
        }
        if (BenchmarkSession.getInstance().isRunning()) {
            fail("普通性能测试正在进行，请先等待它结束");
            return false;
        }
        if (!JfrRecorder.isSupported()) {
            fail("当前 JVM 不支持 Java Flight Recorder（缺少 jdk.jfr 模块）");
            return false;
        }
        if (JfrRecorder.isAnotherRecordingActive()) {
            fail("已有其他 JFR 录制在运行（例如 spark），请先停止它");
            return false;
        }

        Path dir = HardwareInfo.getReportDirectory();
        samplePeriodMs = Math.max(10, Math.min(20, Config.STUTTER_SAMPLE_PERIOD_MS.get()));
        targetDurationSeconds = Math.max(10.0, Math.min(600.0, seconds));
        startNanos = System.nanoTime();
        lastFrameNanos = startNanos;
        startWallMillis = System.currentTimeMillis();
        frameCount = 0;
        intervalCount = 0;
        result = null;
        reportPath = null;
        error = "";
        completionNotified = false;
        modVersion = Benchmark.modVersion();
        rendererSummary = RendererCapabilities.detect().summary();

        String baseName = "stutter_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(startWallMillis));
        JfrRecorder created = new JfrRecorder();
        try {
            jfrPath = created.start(dir, baseName, samplePeriodMs);
        } catch (Throwable t) {
            created.stop();
            fail("无法启动 JFR 录制：" + t.getMessage());
            return false;
        }
        recorder = created;
        state = State.RUNNING;
        sendChat("benchmark.chat.stutter_started", format("%.0f", targetDurationSeconds));
        return true;
    }

    /** Called once per rendered frame from the render frame event. */
    public void onFrame() {
        if (state != State.RUNNING) {
            return;
        }
        long now = System.nanoTime();
        long deltaNanos = now - lastFrameNanos;
        lastFrameNanos = now;
        if (deltaNanos <= 0) {
            return;
        }
        frameCount++;
        if (frameCount == 1) {
            // the first frame's delta includes the time before the first render
            return;
        }
        if (intervalCount == intervals.length) {
            if (intervals.length >= 2_000_000) {
                return;
            }
            intervals = Arrays.copyOf(intervals, intervals.length * 2);
        }
        intervals[intervalCount++] = deltaNanos / 1_000_000.0;
        checkCompletion();
    }

    /** Called from the client tick, guaranteeing completion when frames stall. */
    public void tick() {
        if (state == State.RUNNING) {
            checkCompletion();
        }
        notifyCompletion();
    }

    private void checkCompletion() {
        if (state != State.RUNNING) {
            return;
        }
        if ((System.nanoTime() - startNanos) / 1e9 >= targetDurationSeconds) {
            finish();
        }
    }

    private void finish() {
        state = State.ANALYZING;
        double actualSeconds = (System.nanoTime() - startNanos) / 1e9;
        double[] captured = Arrays.copyOf(intervals, intervalCount);
        Path dir = HardwareInfo.getReportDirectory();
        JfrRecorder active = recorder;
        recorder = null;
        List<String> warnings = new ArrayList<>();

        // Stop the recording on the render thread: it only flushes the file once,
        // and it keeps the recording aligned with the measured interval.
        Path file = null;
        try {
            if (active != null) {
                file = active.stop();
            }
        } catch (Throwable t) {
            warnings.add("停止 JFR 录制失败：" + t);
        }

        Path recording = file;
        Thread worker = new Thread(() -> analyze(recording, dir, captured, startWallMillis,
                targetDurationSeconds, actualSeconds, samplePeriodMs, modVersion, rendererSummary, warnings),
                "BenchMark-Stutter-Analysis");
        worker.setDaemon(true);
        worker.start();
    }

    private void analyze(Path jfr, Path dir, double[] captured, long timestamp, double target, double actual,
                         int period, String version, String renderer, List<String> warnings) {
        try {
            if (jfr == null) {
                throw new IOException("JFR 录制未生成");
            }
            JfrRecorder.cleanupOldRecordings(dir, Math.max(1, Config.RETAINED_JFR_FILES.get()));
            StutterResult analyzed = JfrAnalyzer.analyze(new JfrAnalyzer.Input(
                    jfr, timestamp, timestamp, target, actual, captured, period, version, renderer, warnings));
            Path report = StutterReportRenderer.write(analyzed, dir);
            analyzed.reportPath = report;
            this.result = analyzed;
            this.reportPath = report;
            this.jfrPath = jfr;
            state = State.DONE;
        } catch (Throwable t) {
            Benchmark.LOGGER.error("Stutter analysis failed", t);
            this.error = String.valueOf(t);
            state = State.FAILED;
        }
    }

    private void notifyCompletion() {
        if (completionNotified) {
            return;
        }
        if (state == State.DONE) {
            completionNotified = true;
            sendChat("benchmark.chat.stutter_completed");
        } else if (state == State.FAILED) {
            completionNotified = true;
            sendChat("benchmark.chat.stutter_failed", error);
        }
    }

    private void fail(String reason) {
        error = reason;
        state = State.FAILED;
        sendChat("benchmark.chat.stutter_failed", reason);
    }

    private static String format(String pattern, double value) {
        return String.format(java.util.Locale.ROOT, pattern, value);
    }

    private void sendChat(String key, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable(key, args));
        }
    }
}
