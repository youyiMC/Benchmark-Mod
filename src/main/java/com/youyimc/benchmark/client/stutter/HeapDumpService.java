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

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.youyimc.benchmark.Benchmark;

/**
 * Writes a heap dump on demand. Runs on a background thread, but
 * {@code dumpHeap(live=true)} pauses the whole JVM (full GC) while it walks the
 * heap, so the UI must warn the player before triggering it.
 */
public final class HeapDumpService {
    public enum State {
        IDLE,
        RUNNING,
        DONE,
        FAILED
    }

    private static volatile State state = State.IDLE;
    private static volatile String detail = "";
    private static volatile Path file;

    private HeapDumpService() {
    }

    public static State getState() {
        return state;
    }

    public static String getDetail() {
        return detail;
    }

    public static Path getFile() {
        return file;
    }

    /** Starts a dump unless one is already running. */
    public static void request(Path dir) {
        if (state == State.RUNNING) {
            return;
        }
        state = State.RUNNING;
        detail = "";
        file = null;
        Thread worker = new Thread(() -> run(dir), "BenchMark-HeapDump");
        worker.setDaemon(true);
        worker.start();
    }

    private static void run(Path dir) {
        Path target = dir.resolve("heap_dump_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".hprof");
        try {
            Files.createDirectories(dir);
            Files.deleteIfExists(target);
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            if (bean == null) {
                throw new IllegalStateException("HotSpotDiagnosticMXBean 不可用");
            }
            bean.dumpHeap(target.toString(), true);
            file = target;
            detail = String.valueOf(Math.max(1, Files.size(target) / (1024 * 1024))) + " MB";
            state = State.DONE;
            Benchmark.LOGGER.info("Heap dump written to {}", target.toAbsolutePath());
        } catch (Throwable t) {
            Benchmark.LOGGER.error("Heap dump failed", t);
            detail = String.valueOf(t.getMessage());
            state = State.FAILED;
        }
    }
}
