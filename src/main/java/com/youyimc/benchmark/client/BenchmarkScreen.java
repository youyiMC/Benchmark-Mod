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

import com.youyimc.benchmark.Benchmark;
import com.youyimc.benchmark.Config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The benchmark menu: shows hardware info + early bottleneck hints, lets the
 * player pick a test duration, starts the test and displays the latest result
 * with the auto-generated bottleneck analysis.
 */
public class BenchmarkScreen extends Screen {
    private HardwareInfo hardware = HardwareInfo.detect();
    private DurationSlider durationSlider;
    private int scrollOffset = 0;

    public BenchmarkScreen() {
        super(Component.translatable("benchmark.gui.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height - 44;
        this.durationSlider = new DurationSlider(cx - 150, y, 140, 20, 5, 600, Config.DEFAULT_TEST_DURATION.get());
        this.addRenderableWidget(this.durationSlider);

        this.addRenderableWidget(Button.builder(Component.translatable("benchmark.gui.start"), b -> startTest())
                .bounds(cx - 4, y, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("benchmark.gui.refresh"), b -> this.hardware = HardwareInfo.detect())
                .bounds(cx + 82, y, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("benchmark.gui.open_report_folder"), b -> openReportFolder())
                .bounds(cx - 150, y + 24, 140, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("benchmark.gui.close"), b -> this.onClose())
                .bounds(cx - 4, y + 24, 80, 20).build());
    }

    private void startTest() {
        BenchmarkSession session = BenchmarkSession.getInstance();
        double seconds = this.durationSlider.getValue();
        session.start(seconds);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("benchmark.chat.started", String.format("%.0f", seconds)));
        }
        mc.setScreen(null);
    }

    private void openReportFolder() {
        try {
            Path dir = HardwareInfo.getReportDirectory();
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (IOException e) {
            Benchmark.LOGGER.error("Failed to open report folder", e);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawContent(guiGraphics);
    }

    private void drawContent(GuiGraphics guiGraphics) {
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        List<Component> lines = buildLines();
        int x = 20;
        int y = 30;
        int lineHeight = 10;
        int areaHeight = this.height - 100;
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
                    this.width / 2, this.height - 66, 0x808080);
        }
    }

    private List<Component> buildLines() {
        List<Component> lines = new ArrayList<>();
        BenchmarkSession session = BenchmarkSession.getInstance();

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

        lines.add(Component.empty());
        lines.add(Component.translatable("benchmark.gui.analysis_note").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
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
        if (r.reportPath != null) {
            lines.add(Component.empty());
            lines.add(Component.translatable("benchmark.gui.report_hint", r.reportPath).withStyle(ChatFormatting.GREEN));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollOffset -= (int) Math.signum(verticalAmount) * 3;
        return true;
    }
}
