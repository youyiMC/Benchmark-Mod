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
package com.youyimc.benchmark;

import net.neoforged.neoforge.common.ModConfigSpec;

// Config for the BenchMark mod.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Folder (relative to the game directory) where benchmark reports are saved.
    public static final ModConfigSpec.ConfigValue<String> REPORT_DIRECTORY = BUILDER
            .comment("The folder (relative to the game directory) where benchmark reports are saved.")
            .define("reportDirectory", "benchmark_reports");

    // File format of the generated report: Markdown (default), plain text or both.
    public static final ModConfigSpec.EnumValue<ReportFormat> REPORT_FORMAT = BUILDER
            .comment("Report file format: MARKDOWN (default, .md), PLAIN_TEXT (legacy, .txt) or BOTH.")
            .defineEnum("reportFormat", ReportFormat.MARKDOWN);

    // Which benchmark the pause screen starts: NORMAL or STUTTER analysis.
    public static final ModConfigSpec.EnumValue<AnalysisMode> ANALYSIS_MODE = BUILDER
            .comment("Benchmark mode: NORMAL (FPS/TPS report) or STUTTER (JFR based stutter analysis).")
            .defineEnum("analysisMode", AnalysisMode.NORMAL);

    // JFR execution sampling period used by the stutter analysis (10 or 20 ms).
    public static final ModConfigSpec.IntValue STUTTER_SAMPLE_PERIOD_MS = BUILDER
            .comment("Stutter analysis sampling period in milliseconds: 10 (detailed) or 20 (lighter).")
            .defineInRange("stutterSamplePeriodMs", 20, 10, 20);

    // How many stutter .jfr recordings are kept in the report folder.
    public static final ModConfigSpec.IntValue RETAINED_JFR_FILES = BUILDER
            .comment("How many stutter analysis .jfr recordings to keep; the oldest ones are deleted.")
            .defineInRange("retainedJfrFiles", 1, 1, 10);

    // Default test duration in seconds for the duration slider.
    public static final ModConfigSpec.IntValue DEFAULT_TEST_DURATION = BUILDER
            .comment("Default benchmark test duration in seconds.")
            .defineInRange("defaultTestDuration", 30, 5, 600);

    // Whether to try querying GPU usage via external tools (nvidia-smi / rocm-smi / amdsmi-cli).
    public static final ModConfigSpec.BooleanValue ENABLE_GPU_USAGE = BUILDER
            .comment("Try to query GPU usage via external tools (nvidia-smi / rocm-smi / amdsmi-cli). May not be available on all systems.")
            .define("enableGpuUsageDetection", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}
