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

import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * File format(s) the benchmark report is written in.
 *
 * <p>{@link #MARKDOWN} is the default since 1.1.0: the report is a regular
 * Markdown document that renders nicely on GitHub, in editors and in AI chat
 * clients, while remaining plain text that any AI can parse.</p>
 */
public enum ReportFormat implements TranslatableEnum {
    /** Markdown document ({@code .md}) - the default. */
    MARKDOWN,
    /** Legacy plain text report ({@code .txt}). */
    PLAIN_TEXT,
    /** Write both the Markdown and the plain text report. */
    BOTH;

    /**
     * {@return the concrete formats to write, in output order}
     * {@link #BOTH} expands to Markdown followed by plain text.
     */
    public List<ReportFormat> expand() {
        return this == BOTH ? List.of(MARKDOWN, PLAIN_TEXT) : List.of(this);
    }

    @Override
    public Component getTranslatedName() {
        return Component.translatable("benchmark.configuration.reportFormat." + name().toLowerCase(Locale.ROOT));
    }
}
