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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.youyimc.benchmark.Benchmark;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.event.ChunkRegionReadEvent;
import net.minecraft.util.profiling.jfr.event.ChunkRegionWriteEvent;
import net.minecraft.util.profiling.jfr.event.PacketReceivedEvent;
import net.minecraft.util.profiling.jfr.event.PacketSentEvent;
import net.minecraft.util.profiling.jfr.event.ServerTickTimeEvent;

/**
 * Owns one Java Flight Recorder session for the stutter analysis.
 *
 * <p>Instead of {@code JfrProfiler.start()} (which uses Minecraft's full
 * profiling config and always writes into {@code debug/}) this creates its own
 * recording: a trimmed event set, a destination inside the report folder, and
 * Minecraft's {@code minecraft.*} events enabled so the vanilla packet / region
 * file / tick data is captured without any mixins.</p>
 */
public final class JfrRecorder implements AutoCloseable {
    /** Upper bound for one recording; the JVM drops the oldest events past this. */
    private static final long MAX_SIZE_BYTES = 256L * 1024 * 1024L;

    private Recording recording;
    private Path destination;

    /** {@return true if this JVM has the {@code jdk.jfr} module} */
    public static boolean isSupported() {
        try {
            return FlightRecorder.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /** {@return true if some other recording (e.g. spark) is already running} */
    public static boolean isAnotherRecordingActive() {
        try {
            for (Recording candidate : FlightRecorder.getFlightRecorder().getRecordings()) {
                if (candidate.getState() == RecordingState.RUNNING) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // JFR not usable - treat as "no other recording"
        }
        return false;
    }

    /**
     * Starts a recording.
     *
     * @param dir            report directory that receives the {@code .jfr} file
     * @param baseName       file name without extension
     * @param samplePeriodMs execution sampling period, 10 or 20 ms
     * @return the destination file
     */
    public Path start(Path dir, String baseName, int samplePeriodMs) throws IOException {
        Files.createDirectories(dir);
        // Force Minecraft's profiler to initialize: it registers the minecraft.*
        // event classes and the periodic tick / network summaries.
        try {
            JvmProfiler.INSTANCE.isAvailable();
        } catch (Throwable ignored) {
            // the event classes are registered explicitly below anyway
        }
        registerMinecraftEvents();

        Path file = dir.resolve(baseName + ".jfr");
        Recording created = new Recording();
        created.setName("BenchMark stutter analysis");
        created.setToDisk(true);
        created.setDumpOnExit(false);
        created.setMaxSize(MAX_SIZE_BYTES);
        created.setMaxAge(Duration.ofMinutes(15));
        created.setDestination(file);
        enableStandardEvents(created, samplePeriodMs);
        enableMinecraftEvents(created);
        created.start();

        this.recording = created;
        this.destination = file;
        Benchmark.LOGGER.info("Benchmark stutter analysis started (JFR -> {})", file.toAbsolutePath());
        return file;
    }

    /** {@return the written file, or {@code null} if nothing was recording} */
    public Path stop() {
        Recording active = this.recording;
        this.recording = null;
        if (active == null) {
            return null;
        }
        try {
            active.stop();
        } catch (Throwable t) {
            Benchmark.LOGGER.warn("Failed to stop the stutter analysis recording", t);
        } finally {
            try {
                active.close();
            } catch (Throwable ignored) {
                // already closed
            }
        }
        Benchmark.LOGGER.info("Benchmark stutter analysis recording written to {}", this.destination);
        return this.destination;
    }

    @Override
    public void close() {
        stop();
    }

    /** Deletes old {@code stutter_*.jfr} recordings, keeping the newest {@code keep} files. */
    public static void cleanupOldRecordings(Path dir, int keep) {
        if (keep < 1) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "stutter_*.jfr")) {
            for (Path file : stream) {
                files.add(file);
            }
        } catch (Throwable t) {
            return;
        }
        if (files.size() <= keep) {
            return;
        }
        files.sort(Comparator.comparingLong(file -> file.toFile().lastModified()));
        while (files.size() > keep) {
            Path oldest = files.remove(0);
            try {
                Files.deleteIfExists(oldest);
                Benchmark.LOGGER.info("Deleted old stutter recording {}", oldest.toAbsolutePath());
            } catch (Throwable t) {
                Benchmark.LOGGER.warn("Could not delete old stutter recording {}", oldest, t);
            }
        }
    }

    private static void registerMinecraftEvents() {
        register(PacketReceivedEvent.class);
        register(PacketSentEvent.class);
        register(ChunkRegionReadEvent.class);
        register(ChunkRegionWriteEvent.class);
        register(ServerTickTimeEvent.class);
    }

    private static void register(Class<? extends jdk.jfr.Event> eventClass) {
        try {
            FlightRecorder.register(eventClass);
        } catch (Throwable ignored) {
            // already registered by Minecraft's own profiler, or JFR unavailable
        }
    }

    private static void enableMinecraftEvents(Recording recording) {
        enableQuietly(recording, PacketReceivedEvent.class);
        enableQuietly(recording, PacketSentEvent.class);
        enableQuietly(recording, ChunkRegionReadEvent.class);
        enableQuietly(recording, ChunkRegionWriteEvent.class);
        enableQuietly(recording, ServerTickTimeEvent.class);
    }

    private static void enableQuietly(Recording recording, Class<? extends jdk.jfr.Event> eventClass) {
        try {
            recording.enable(eventClass);
        } catch (Throwable ignored) {
            // event not available on this JVM
        }
    }

    private static void enableStandardEvents(Recording recording, int samplePeriodMs) {
        int period = Math.max(10, Math.min(20, samplePeriodMs));
        enablePeriod(recording, "jdk.ExecutionSample", Duration.ofMillis(period));
        enablePeriod(recording, "jdk.NativeMethodSample", Duration.ofMillis(Math.max(20, period * 2L)));

        Duration waitThreshold = Duration.ofMillis(1);
        enableThreshold(recording, "jdk.ThreadPark", waitThreshold);
        enableThreshold(recording, "jdk.JavaMonitorEnter", waitThreshold);
        enableThreshold(recording, "jdk.JavaMonitorWait", waitThreshold);
        enableThreshold(recording, "jdk.ThreadSleep", waitThreshold);

        enablePeriod(recording, "jdk.CPULoad", Duration.ofSeconds(1));
        enablePeriod(recording, "jdk.ThreadAllocationStatistics", Duration.ofSeconds(5));

        enableQuietly(recording, "jdk.GCPhasePause");
        enableQuietly(recording, "jdk.GCHeapSummary");
        enableQuietly(recording, "jdk.GarbageCollection");
        enableQuietly(recording, "jdk.YoungGarbageCollection");
        enableQuietly(recording, "jdk.OldGarbageCollection");
        enableQuietly(recording, "jdk.ObjectAllocationSample");
        enableQuietly(recording, "jdk.FileRead");
        enableQuietly(recording, "jdk.FileWrite");
        enableQuietly(recording, "jdk.SocketRead");
        enableQuietly(recording, "jdk.SocketWrite");
    }

    private static void enableQuietly(Recording recording, String eventName) {
        try {
            recording.enable(eventName);
        } catch (Throwable ignored) {
            // event not available on this JVM
        }
    }

    private static void enablePeriod(Recording recording, String eventName, Duration period) {
        try {
            recording.enable(eventName).withPeriod(period);
        } catch (Throwable ignored) {
            // event not available on this JVM
        }
    }

    private static void enableThreshold(Recording recording, String eventName, Duration threshold) {
        try {
            recording.enable(eventName).withThreshold(threshold);
        } catch (Throwable ignored) {
            // event not available on this JVM
        }
    }
}
