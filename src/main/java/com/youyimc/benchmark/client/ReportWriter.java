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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.youyimc.benchmark.Benchmark;
import com.youyimc.benchmark.Config;
import com.youyimc.benchmark.ReportFormat;
import com.youyimc.benchmark.client.report.MarkdownReportRenderer;
import com.youyimc.benchmark.client.report.ReportRenderer;
import com.youyimc.benchmark.client.report.TextReportRenderer;

/**
 * Writes the completed benchmark into one or more timestamped report files in
 * the configured report folder. The format(s) come from the {@code reportFormat}
 * config option; Markdown is the default since 1.1.0.
 */
public final class ReportWriter {
    private static final ReportRenderer MARKDOWN = new MarkdownReportRenderer();
    private static final ReportRenderer PLAIN_TEXT = new TextReportRenderer();

    private ReportWriter() {
    }

    /**
     * Writes the report file(s) for the given result.
     *
     * @return the files that were written; empty if writing failed
     */
    public static List<Path> writeReport(BenchmarkResult result) {
        result.reportPaths.clear();
        Path dir = HardwareInfo.getReportDirectory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Benchmark.LOGGER.error("Failed to create benchmark report directory {}", dir, e);
            return result.reportPaths;
        }

        String baseName = "benchmark_report_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(result.timestampMillis));
        for (ReportFormat format : Config.REPORT_FORMAT.get().expand()) {
            ReportRenderer renderer = rendererFor(format);
            Path file = dir.resolve(baseName + "." + renderer.extension());
            try {
                Files.writeString(file, renderer.render(result, file), StandardCharsets.UTF_8);
                result.reportPaths.add(file.toAbsolutePath());
                Benchmark.LOGGER.info("Benchmark report ({}) written to {}", format, file.toAbsolutePath());
            } catch (IOException | RuntimeException e) {
                Benchmark.LOGGER.error("Failed to write benchmark report to {}", file.toAbsolutePath(), e);
            }
        }
        return result.reportPaths;
    }

    private static ReportRenderer rendererFor(ReportFormat format) {
        return switch (format) {
            case PLAIN_TEXT -> PLAIN_TEXT;
            case MARKDOWN, BOTH -> MARKDOWN;
        };
    }
}
