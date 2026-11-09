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
