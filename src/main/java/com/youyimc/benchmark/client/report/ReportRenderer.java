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
package com.youyimc.benchmark.client.report;

import java.nio.file.Path;

import com.youyimc.benchmark.client.BenchmarkResult;

/**
 * Turns a {@link BenchmarkResult} into the text of one report file.
 * Implementations are stateless and must be safe to call from the render thread.
 */
public interface ReportRenderer {
    /** {@return the file extension, without the leading dot} */
    String extension();

    /**
     * Renders the report.
     *
     * @param result     the completed benchmark
     * @param targetFile the file the report will be written to (used in the footer)
     */
    String render(BenchmarkResult result, Path targetFile);
}
