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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;

/**
 * Maps a class name from a JFR stack sample back to the mod that ships it.
 *
 * <p>JFR only records class names, so the mapping is built by scanning the
 * packages of every mod jar once (on the analysis thread). Classes from shaded
 * libraries are filtered out; packages that two different mods both ship are
 * reported as {@code shared} instead of being attributed to the wrong mod.</p>
 */
public final class ModAttribution {
    /** Packages that belong to the game, the JVM or common shaded libraries. */
    private static final List<String> IGNORED_PREFIXES = List.of(
            "net.minecraft", "com.mojang", "net.neoforged", "cpw.mods", "net.minecraftforge",
            "java", "javax", "jdk", "sun", "com.sun",
            "org.lwjgl", "io.netty", "com.google", "org.apache", "it.unimi", "org.objectweb",
            "org.slf4j", "com.electronwill", "org.jetbrains", "org.joml", "org.spongepowered",
            "org.jline", "oshi", "com.github", "org.checkerframework", "org.intellij", "com.ibm");

    private static volatile ModAttribution instance;

    private final Map<String, String> packageToMod;

    private ModAttribution(Map<String, String> packageToMod) {
        this.packageToMod = packageToMod;
    }

    /** {@return the shared instance, building the index on first use (slow, call off-thread)} */
    public static ModAttribution get() {
        ModAttribution local = instance;
        if (local == null) {
            synchronized (ModAttribution.class) {
                local = instance;
                if (local == null) {
                    local = new ModAttribution(scanModJars());
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * {@return the mod id a class belongs to}
     *
     * <p>Falls back to {@code minecraft}, {@code jvm}, {@code lwjgl} or
     * {@code unknown} for classes that no mod jar owns.</p>
     */
    public String modForClass(String className) {
        if (className == null || className.isBlank()) {
            return "unknown";
        }
        if (className.startsWith("net.minecraft.") || className.startsWith("com.mojang.")) {
            return "minecraft";
        }
        if (className.startsWith("java.") || className.startsWith("javax.")
                || className.startsWith("jdk.") || className.startsWith("sun.")) {
            return "jvm";
        }
        if (className.startsWith("org.lwjgl.")) {
            return "lwjgl";
        }
        String pkg = className;
        int dot = pkg.lastIndexOf('.');
        pkg = dot > 0 ? pkg.substring(0, dot) : "";
        while (!pkg.isEmpty()) {
            String mod = packageToMod.get(pkg);
            if (mod != null) {
                return mod;
            }
            int next = pkg.lastIndexOf('.');
            pkg = next > 0 ? pkg.substring(0, next) : "";
        }
        return "unknown";
    }

    private static Map<String, String> scanModJars() {
        Map<String, String> map = new HashMap<>(8192);
        ModList mods = ModList.get();
        if (mods == null) {
            return map;
        }
        List<IModFileInfo> files;
        try {
            files = new ArrayList<>(mods.getModFiles());
        } catch (Throwable t) {
            return map;
        }
        for (IModFileInfo info : files) {
            IModFile file;
            try {
                file = info.getFile();
            } catch (Throwable t) {
                continue;
            }
            if (file == null) {
                continue;
            }
            String modId = file.getModInfos().isEmpty() ? "unknown" : file.getModInfos().get(0).getModId();
            Path path;
            try {
                path = file.getFilePath();
            } catch (Throwable t) {
                continue;
            }
            if (path == null || !Files.isRegularFile(path)) {
                // Jar-in-jar mods: we cannot open the nested file directly, leave them as unknown.
                continue;
            }
            scanJar(path, modId, map);
        }
        return map;
    }

    private static void scanJar(Path path, String modId, Map<String, String> map) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/")) {
                    continue;
                }
                String pkg = packageOf(name);
                if (pkg.isEmpty() || isIgnored(pkg)) {
                    continue;
                }
                String previous = map.putIfAbsent(pkg, modId);
                if (previous != null && !previous.equals(modId)) {
                    map.put(pkg, "shared");
                }
            }
        } catch (Throwable t) {
            // unreadable jar (nested mod, broken file) - classes fall back to unknown
        }
    }

    private static String packageOf(String entryName) {
        int slash = entryName.lastIndexOf('/');
        if (slash <= 0) {
            return "";
        }
        return entryName.substring(0, slash).replace('/', '.');
    }

    private static boolean isIgnored(String pkg) {
        for (String prefix : IGNORED_PREFIXES) {
            if (pkg.equals(prefix) || pkg.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /** {@return the number of indexed packages, for diagnostics} */
    public int packageCount() {
        return packageToMod.size();
    }
}
