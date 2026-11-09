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

/**
 * Small helpers for writing Markdown documents: headings, tables (with cell
 * escaping), fenced code blocks and inline code. Shared by the regular report
 * and the stutter analysis report.
 */
public final class MarkdownDoc {
    private MarkdownDoc() {
    }

    /** Appends a heading of the given level (1 = {@code #}, 2 = {@code ##}, ...). */
    public static void heading(StringBuilder sb, int level, String title) {
        sb.append("#".repeat(Math.max(1, Math.min(6, level)))).append(' ').append(title).append("\n\n");
    }

    /** Appends a table header plus the separator row. */
    public static void tableHeader(StringBuilder sb, String... headers) {
        row(sb, (Object[]) headers);
        sb.append('|');
        for (int i = 0; i < headers.length; i++) {
            sb.append(" --- |");
        }
        sb.append('\n');
    }

    /** Appends one table row, escaping every cell. */
    public static void row(StringBuilder sb, Object... values) {
        sb.append('|');
        for (Object value : values) {
            sb.append(' ').append(cell(value)).append(" |");
        }
        sb.append('\n');
    }

    /** Escapes a value so it can live in a Markdown table cell. */
    public static String cell(Object value) {
        String text = String.valueOf(value);
        return text.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("`", "\\`")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    /** Appends a fenced code block, using a longer fence if the body contains one. */
    public static void fence(StringBuilder sb, String info, String body) {
        String trimmed = body.stripTrailing();
        String fence = trimmed.contains("```") ? "````" : "```";
        sb.append(fence).append(info).append('\n').append(trimmed).append('\n').append(fence).append("\n\n");
    }

    /** Wraps a value in inline code, avoiding collisions with backticks in the value. */
    public static String inlineCode(String value) {
        if (!value.contains("`")) {
            return "`" + value + "`";
        }
        return "`` " + value + " ``";
    }

    /** Appends a bullet list. */
    public static void bullets(StringBuilder sb, Iterable<String> lines) {
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
        sb.append('\n');
    }

    /** Appends a blockquote line (used for notes and "not available" hints). */
    public static void note(StringBuilder sb, String text) {
        sb.append("> ").append(text).append("\n\n");
    }
}
