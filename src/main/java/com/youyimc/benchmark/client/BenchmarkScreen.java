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

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.youyimc.benchmark.AnalysisMode;
import com.youyimc.benchmark.Benchmark;
import com.youyimc.benchmark.Config;
import com.youyimc.benchmark.client.stutter.HeapDumpService;
import com.youyimc.benchmark.client.stutter.StutterResult;
import com.youyimc.benchmark.client.stutter.StutterSession;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The benchmark menu. Shows hardware info + early bottleneck hints, lets the
 * player pick the mode (normal test or stutter analysis) and a duration, starts
 * the run and displays the latest result.
 */
public class BenchmarkScreen extends Screen {
    private HardwareInfo hardware = HardwareInfo.detect();
    private DurationSlider durationSlider;
    private Button modeButton;
    private Button samplePeriodButton;
    private Button heapDumpButton;
    private int scrollOffset = 0;

    public BenchmarkScreen() {
        super(Component.translatable("benchmark.gui.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int rowMode = this.height - 92;
        int rowMain = this.height - 44;
        int rowBottom = this.height - 20;

        this.modeButton = this.addRenderableWidget(Button.builder(modeLabel(), b -> toggleMode())
                .bounds(cx - 150, rowMode, 148, 20).build());
        this.samplePeriodButton = this.addRenderableWidget(Button.builder(samplePeriodLabel(), b -> toggleSamplePeriod())
                .bounds(cx - 2, rowMode, 70, 20).build());
        this.heapDumpButton = this.addRenderableWidget(Button.builder(Component.translatable("benchmark.gui.heap_dump"), b -> confirmHeapDump())
                .bounds(cx + 72, rowMode, 78, 20).build());

        this.durationSlider = new DurationSlider(cx - 150, rowMain, 140, 20, 5, 600, Config.DEFAULT_TEST_DURATION.get());
        this.addRenderableWidget(this.durationSlider);

        this.addRenderableWidget(Button.builder(Component.translatable("benchmark.gui.start"), b -> startTest())
                .bounds(cx - 4, rowMain, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("benchmark.gui.refresh"), b -> this.hardware = HardwareInfo.detect())
                .bounds(cx + 82, rowMain, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("benchmark.gui.open_report_folder"), b -> openReportFolder())
                .bounds(cx - 150, rowBottom, 140, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("benchmark.gui.close"), b -> this.onClose())
                .bounds(cx - 4, rowBottom, 80, 20).build());

        updateButtons();
    }

    private AnalysisMode mode() {
        try {
            return Config.ANALYSIS_MODE.get();
        } catch (Throwable t) {
            return AnalysisMode.NORMAL;
        }
    }

    private Component modeLabel() {
        return Component.translatable(mode() == AnalysisMode.STUTTER
                ? "benchmark.gui.mode_stutter" : "benchmark.gui.mode_normal");
    }

    private Component samplePeriodLabel() {
        int period;
        try {
            period = Config.STUTTER_SAMPLE_PERIOD_MS.get();
        } catch (Throwable t) {
            period = 20;
        }
        return Component.translatable("benchmark.gui.sample_period", period);
    }

    private void toggleMode() {
        AnalysisMode next = mode() == AnalysisMode.STUTTER ? AnalysisMode.NORMAL : AnalysisMode.STUTTER;
        Config.ANALYSIS_MODE.set(next);
        Config.ANALYSIS_MODE.save();
        this.rebuildWidgets();
    }

    private void toggleSamplePeriod() {
        int current = Config.STUTTER_SAMPLE_PERIOD_MS.get();
        int next = current == 10 ? 20 : 10;
        Config.STUTTER_SAMPLE_PERIOD_MS.set(next);
        Config.STUTTER_SAMPLE_PERIOD_MS.save();
        this.rebuildWidgets();
    }

    private void startTest() {
        double seconds = this.durationSlider.getValue();
        if (mode() == AnalysisMode.STUTTER) {
            if (StutterSession.getInstance().start(seconds)) {
                Minecraft.getInstance().setScreen(null);
            }
            return;
        }

        BenchmarkSession session = BenchmarkSession.getInstance();
        if (session.isRunning()) {
            return;
        }
        session.start(seconds);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("benchmark.chat.started", String.format("%.0f", seconds)));
        }
        mc.setScreen(null);
    }

    private void confirmHeapDump() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(
                accepted -> {
                    if (accepted) {
                        HeapDumpService.request(HardwareInfo.getReportDirectory());
                    }
                    minecraft.setScreen(this);
                },
                Component.translatable("benchmark.gui.heap_dump_confirm_title"),
                Component.translatable("benchmark.gui.heap_dump_confirm_message"),
                Component.translatable("benchmark.gui.heap_dump_confirm_yes"),
                Component.translatable("benchmark.gui.close")));
    }

    private void openReportFolder() {
        try {
            Path dir = HardwareInfo.getReportDirectory();
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Throwable t) {
            Benchmark.LOGGER.error("Failed to open report folder", t);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawContent(guiGraphics);
    }

    /** Keeps the mode / sample / heap dump buttons in sync with the current state. */
    private void updateButtons() {
        boolean stutterMode = mode() == AnalysisMode.STUTTER;
        StutterSession session = StutterSession.getInstance();
        if (this.modeButton != null) {
            this.modeButton.setMessage(modeLabel());
            this.modeButton.active = !session.isBusy();
        }
        if (this.samplePeriodButton != null) {
            this.samplePeriodButton.visible = stutterMode;
            this.samplePeriodButton.setMessage(samplePeriodLabel());
            this.samplePeriodButton.active = !session.isBusy();
        }
        if (this.heapDumpButton != null) {
            this.heapDumpButton.visible = stutterMode && session.getResult() != null;
            this.heapDumpButton.active = HeapDumpService.getState() != HeapDumpService.State.RUNNING;
            this.heapDumpButton.setMessage(HeapDumpService.getState() == HeapDumpService.State.RUNNING
                    ? Component.translatable("benchmark.gui.heap_dump_running")
                    : Component.translatable("benchmark.gui.heap_dump"));
        }
    }

    private void drawContent(GuiGraphics guiGraphics) {
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        List<Component> lines = buildLines();
        int x = 20;
        int y = 30;
        int lineHeight = 10;
        int areaHeight = this.height - 120;
        int visible = Math.max(1, areaHeight / lineHeight);
        int maxScroll = Math.max(0, lines.size() - visible);
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));
        int end = Math.min(lines.size(), this.scrollOffset + visible);
        for (int i = this.scrollOffset; i < end; i++) {
            guiGraphics.drawString(this.font, lines.get(i), x, y, 0xFFFFFF);
            y += lineHeight;
        }
        if (maxScroll > 0) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("↑↓ / 滚轮滚动 (" + (this.scrollOffset + 1) + " / " + lines.size() + ")"),
                    this.width / 2, this.height - 106, 0x808080);
        }
    }

    private List<Component> buildLines() {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable("benchmark.gui.hardware_title").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("benchmark.gui.cpu", this.hardware.cpuName));
        lines.add(Component.translatable("benchmark.gui.gpu", this.hardware.gpuRenderer));
        lines.add(Component.translatable("benchmark.gui.os", this.hardware.osName + " " + this.hardware.osVersion + " (" + this.hardware.osArch + ")"));
        lines.add(Component.translatable("benchmark.gui.java", this.hardware.javaVersion + " / " + this.hardware.javaVm));
        lines.add(Component.translatable("benchmark.gui.memory", String.format("%.1f", this.hardware.totalMemoryBytes / (1024.0 * 1024.0 * 1024.0))));
        lines.add(Component.empty());
        lines.add(Component.translatable("benchmark.gui.bottleneck_title").withStyle(ChatFormatting.GOLD));
        for (String hint : this.hardware.bottleneckHints) {
            lines.add(Component.literal("- " + hint).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.empty());

        if (mode() == AnalysisMode.STUTTER) {
            addStutterLines(lines);
        } else {
            addNormalLines(lines);
        }

        lines.add(Component.empty());
        lines.add(Component.translatable("benchmark.gui.analysis_note").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private void addNormalLines(List<Component> lines) {
        BenchmarkSession session = BenchmarkSession.getInstance();
        if (session.isRunning()) {
            double elapsed = session.getElapsedSeconds();
            double target = session.getTargetDurationSeconds();
            int pct = (int) (elapsed / target * 100);
            lines.add(Component.translatable("benchmark.gui.running", Math.min(100, pct)).withStyle(ChatFormatting.YELLOW));
            lines.add(Component.empty());
            lines.add(Component.translatable("benchmark.gui.running_hint").withStyle(ChatFormatting.GRAY));
        } else if (session.getResult() != null) {
            lines.add(Component.translatable("benchmark.gui.result_title").withStyle(ChatFormatting.GREEN));
            addResultLines(lines, session.getResult());
        } else {
            lines.add(Component.translatable("benchmark.gui.no_result").withStyle(ChatFormatting.GRAY));
        }
    }

    private void addStutterLines(List<Component> lines) {
        StutterSession session = StutterSession.getInstance();
        StutterSession.State state = session.getState();

        if (state == StutterSession.State.RUNNING) {
            double elapsed = session.getElapsedSeconds();
            double target = Math.max(1, session.getTargetDurationSeconds());
            int pct = (int) (elapsed / target * 100);
            lines.add(Component.translatable("benchmark.gui.stutter_running", Math.min(100, pct)).withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable("benchmark.gui.stutter_hint").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (state == StutterSession.State.ANALYZING) {
            lines.add(Component.translatable("benchmark.gui.stutter_analyzing").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (state == StutterSession.State.FAILED) {
            lines.add(Component.translatable("benchmark.gui.stutter_failed", session.getError()).withStyle(ChatFormatting.RED));
            return;
        }
        StutterResult result = session.getResult();
        if (result == null) {
            lines.add(Component.translatable("benchmark.gui.stutter_hint").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("benchmark.gui.stutter_no_result").withStyle(ChatFormatting.GRAY));
            return;
        }
        addStutterResultLines(lines, result);
    }

    private void addStutterResultLines(List<Component> lines, StutterResult r) {
        lines.add(Component.translatable("benchmark.gui.stutter_result_title").withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("benchmark.gui.stutter_summary_stutters",
                String.valueOf(r.stutterCount), String.valueOf(r.stutterFrameCount),
                String.format("%.0f", r.stutterExcessMillis)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("benchmark.gui.stutter_summary_gc",
                String.valueOf(r.gcCount), String.format("%.3f", r.gcTotalPauseMicros / 1000.0)).withStyle(ChatFormatting.GRAY));
        if (!r.stutters.isEmpty()) {
            lines.add(Component.translatable("benchmark.gui.stutter_summary_top_cause", r.stutters.get(0).cause()).withStyle(ChatFormatting.GRAY));
        }
        if (!r.modCpu.isEmpty()) {
            StutterResult.ModCpuShare top = r.modCpu.get(0);
            lines.add(Component.translatable("benchmark.gui.stutter_summary_top_mod",
                    top.modId(), String.format("%.1f", top.renderSharePercent())).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("benchmark.gui.result_avg_fps", String.format("%.1f", r.avgFps)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("benchmark.gui.result_1pct_low", String.format("%.1f", r.low1Fps)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());
        if (r.reportPath != null) {
            lines.add(Component.translatable("benchmark.gui.stutter_report_hint", r.reportPath.toString()).withStyle(ChatFormatting.GREEN));
        }
        if (r.jfrPath != null) {
            lines.add(Component.translatable("benchmark.gui.stutter_jfr_hint", r.jfrPath.toString()).withStyle(ChatFormatting.GRAY));
        }
        HeapDumpService.State dumpState = HeapDumpService.getState();
        if (dumpState == HeapDumpService.State.DONE && HeapDumpService.getFile() != null) {
            lines.add(Component.translatable("benchmark.gui.heap_dump_done",
                    HeapDumpService.getFile().toString(), HeapDumpService.getDetail()).withStyle(ChatFormatting.GREEN));
        } else if (dumpState == HeapDumpService.State.FAILED) {
            lines.add(Component.translatable("benchmark.gui.heap_dump_failed", HeapDumpService.getDetail()).withStyle(ChatFormatting.RED));
        }
    }

    private void addResultLines(List<Component> lines, BenchmarkResult r) {
        lines.add(Component.translatable("benchmark.gui.result_avg_fps", String.format("%.1f", r.avgFps)));
        lines.add(Component.translatable("benchmark.gui.result_max_fps", String.format("%.1f", r.maxFps)));
        lines.add(Component.translatable("benchmark.gui.result_min_fps", String.format("%.1f", r.minFps)));
        lines.add(Component.translatable("benchmark.gui.result_1pct_low", String.format("%.1f", r.low1Fps)));
        lines.add(Component.translatable("benchmark.gui.result_01pct_low", String.format("%.1f", r.low01Fps)));
        if (r.hasServerData) {
            double tps = r.avgMspt > 0 ? Math.min(20.0, 1000.0 / r.avgMspt) : 20.0;
            lines.add(Component.translatable("benchmark.gui.result_mspt", String.format("%.2f", r.avgMspt)));
            lines.add(Component.translatable("benchmark.gui.result_tps", String.format("%.2f", tps)));
        } else {
            lines.add(Component.translatable("benchmark.gui.result_tps_na"));
        }
        lines.add(Component.translatable("benchmark.gui.result_cpu_usage", String.format("%.1f", r.avgCpu), String.format("%.1f", r.maxCpu)));
        lines.add(Component.translatable("benchmark.gui.result_mem_usage", String.format("%.0f", r.avgMemMB), String.format("%.0f", r.maxMemMB)));
        lines.add(Component.translatable("benchmark.gui.result_heap_usage", String.format("%.0f", r.avgHeapMB), String.format("%.0f", r.maxHeapMB)));
        if (r.hasGpu) {
            lines.add(Component.translatable("benchmark.gui.result_gpu_usage", String.format("%.1f", r.avgGpu), String.format("%.1f", r.maxGpu)));
            if (r.avgVramMB > 0) {
                lines.add(Component.translatable("benchmark.gui.result_vram", String.format("%.0f", r.avgVramMB)));
            }
        } else {
            lines.add(Component.translatable("benchmark.gui.result_gpu_na"));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("benchmark.gui.analysis_title").withStyle(ChatFormatting.AQUA));
        for (String text : r.analysis) {
            lines.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
        }
        if (!r.reportPaths.isEmpty()) {
            lines.add(Component.empty());
            for (Path path : r.reportPaths) {
                lines.add(Component.translatable("benchmark.gui.report_hint", path.toString()).withStyle(ChatFormatting.GREEN));
            }
        } else {
            lines.add(Component.empty());
            lines.add(Component.translatable("benchmark.gui.report_failed").withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollOffset -= (int) Math.signum(verticalAmount) * 3;
        return true;
    }
}
