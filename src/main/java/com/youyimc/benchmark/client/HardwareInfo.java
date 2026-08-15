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

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.blaze3d.platform.GlUtil;
import com.sun.management.OperatingSystemMXBean;
import com.youyimc.benchmark.Config;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Collects detailed hardware / OS / JVM info, evaluates an early bottleneck
 * hint, and provides best-effort GPU usage querying (via nvidia-smi on a
 * background thread so it never blocks the render thread).
 */
public class HardwareInfo {
    public final String cpuName;
    public final int cpuThreads;
    public final String gpuVendor;
    public final String gpuRenderer;
    public final String gpuDriverInfo;
    public final String osName;
    public final String osVersion;
    public final String osArch;
    public final String javaVersion;
    public final String javaVm;
    public final long totalMemoryBytes;
    public final long maxHeapBytes;
    public final List<String> bottleneckHints = new ArrayList<>();

    public HardwareInfo(String cpuName, int cpuThreads, String gpuVendor, String gpuRenderer,
                        String gpuDriverInfo, String osName, String osVersion, String osArch,
                        String javaVersion, String javaVm, long totalMemoryBytes, long maxHeapBytes) {
        this.cpuName = cpuName;
        this.cpuThreads = cpuThreads;
        this.gpuVendor = gpuVendor;
        this.gpuRenderer = gpuRenderer;
        this.gpuDriverInfo = gpuDriverInfo;
        this.osName = osName;
        this.osVersion = osVersion;
        this.osArch = osArch;
        this.javaVersion = javaVersion;
        this.javaVm = javaVm;
        this.totalMemoryBytes = totalMemoryBytes;
        this.maxHeapBytes = maxHeapBytes;
    }

    /**
     * Detects hardware. Must be called from the render thread (GL context) for
     * accurate GL-based GPU info.
     */
    public static HardwareInfo detect() {
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        String cpuName = detectCpuName();
        int threads = Runtime.getRuntime().availableProcessors();
        String gpuRenderer = safeGl(() -> GlUtil.getRenderer(), "Unknown");
        String gpuVendor = safeGl(() -> GlUtil.getVendor(), "Unknown");
        vendorCache = gpuVendor.toLowerCase(Locale.ROOT);
        String gpuDriver = safeGl(() -> GlUtil.getOpenGLVersion(), "Unknown");
        String osName = System.getProperty("os.name", "Unknown");
        String osVersion = System.getProperty("os.version", "Unknown");
        String osArch = System.getProperty("os.arch", "Unknown");
        String javaVersion = System.getProperty("java.version", "Unknown");
        String javaVm = System.getProperty("java.vm.name", "Unknown");
        long totalMemory = 0;
        try {
            totalMemory = os.getTotalMemorySize();
        } catch (Throwable t) {
            // ignore
        }
        long maxHeap = Runtime.getRuntime().maxMemory();

        HardwareInfo info = new HardwareInfo(cpuName, threads, gpuVendor, gpuRenderer, gpuDriver,
                osName, osVersion, osArch, javaVersion, javaVm, totalMemory, maxHeap);
        info.evaluateEarlyBottleneck();
        return info;
    }

    private interface GlCall<T> {
        T get() throws Exception;
    }

    private static <T> T safeGl(GlCall<T> call, T fallback) {
        try {
            return call.get();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static volatile String cpuNameCache = null;

    private static String detectCpuName() {
        if (cpuNameCache != null) {
            return cpuNameCache;
        }
        String result = null;
        // 1) Friendly model from Minecraft's own bundled OSHI (already initialized
        //    by the game for the F3 screen), e.g. "Intel(R) Core(TM) i5-4590...".
        try {
            String glCpu = GlUtil.getCpuInfo();
            if (glCpu != null && !glCpu.isBlank() && !"<unknown>".equalsIgnoreCase(glCpu.trim())) {
                // GLX returns "<N>x <model>"; strip the "Nx " core-count prefix.
                result = glCpu.replaceFirst("^\\d+x\\s+", "").trim();
            }
        } catch (Throwable t) {
            // ignore
        }
        if (result == null || result.isBlank()) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            try {
                if (os.contains("win")) {
                    // Windows registry holds the friendly processor name.
                    String reg = queryRegistry("HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "ProcessorNameString");
                    if (reg != null && !reg.isBlank()) {
                        result = reg.trim();
                    } else {
                        String env = System.getenv("PROCESSOR_IDENTIFIER");
                        if (env != null && !env.isBlank()) {
                            result = env.trim();
                        }
                    }
                } else if (os.contains("linux")) {
                    List<String> lines = Files.readAllLines(Path.of("/proc/cpuinfo"));
                    for (String line : lines) {
                        if (line.toLowerCase(Locale.ROOT).startsWith("model name")) {
                            int idx = line.indexOf(':');
                            if (idx >= 0) {
                                result = line.substring(idx + 1).trim();
                                break;
                            }
                        }
                    }
                } else if (os.contains("mac")) {
                    Process p = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string")
                            .redirectErrorStream(true).start();
                    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                    if (!out.isBlank()) {
                        result = out;
                    }
                }
            } catch (Exception e) {
                // fall through
            }
        }
        if (result == null || result.isBlank()) {
            result = "Unknown";
        }
        cpuNameCache = result;
        return result;
    }

    private static String queryRegistry(String key, String value) {
        try {
            Process p = new ProcessBuilder("reg", "query", key, "/v", value).redirectErrorStream(true).start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String line : out.split("\r?\n")) {
                int idx = line.indexOf("REG_SZ");
                if (idx >= 0) {
                    return line.substring(idx + "REG_SZ".length()).trim();
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private void evaluateEarlyBottleneck() {
        String g = gpuRenderer.toLowerCase(Locale.ROOT);
        boolean integrated = g.contains("intel") || g.contains("uhd") || g.contains("hd graphics")
                || g.contains("iris") || g.contains("radeon graphics") || g.contains("mali")
                || g.contains("adreno") || g.contains("apple");
        double ramGB = totalMemoryBytes / (1024.0 * 1024.0 * 1024.0);

        if (integrated) {
            bottleneckHints.add("检测到集成显卡，GPU 性能可能成为主要瓶颈（尤其在高分辨率/高画质下）。");
        }
        if (ramGB > 0 && ramGB < 8) {
            bottleneckHints.add("系统内存不足 8GB，可能会影响整体流畅度。");
        }
        if (cpuThreads >= 12 && !integrated) {
            bottleneckHints.add("CPU 核心/线程较多，适合加载较多实体或后台任务。");
        }

        // Best-effort CPU/GPU generation comparison (仅供参考).
        int cpuYear = estimateCpuYear();
        int gpuYear = estimateGpuYear(gpuRenderer);
        if (cpuYear > 0 && gpuYear > 0 && (gpuYear - cpuYear) >= 4) {
            bottleneckHints.add(String.format(
                    "CPU 架构相对较旧（约 %d 年），而显卡较新（约 %d 年），CPU 可能无法完全发挥显卡性能；建议检查主板 PCIe 版本与显卡驱动。",
                    cpuYear, gpuYear));
        }

        if (bottleneckHints.isEmpty()) {
            bottleneckHints.add("未检测到明显硬件失衡，配置较为均衡。");
        }
    }

    private static int estimateCpuYear() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        int family = -1;
        int model = -1;
        try {
            if (os.contains("win")) {
                String env = System.getenv("PROCESSOR_IDENTIFIER");
                if (env != null) {
                    Matcher m = Pattern.compile("Family\\s+(\\d+)\\s+Model\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(env);
                    if (m.find()) {
                        family = Integer.parseInt(m.group(1));
                        model = Integer.parseInt(m.group(2));
                    }
                }
            } else if (os.contains("linux")) {
                List<String> lines = Files.readAllLines(Path.of("/proc/cpuinfo"));
                for (String line : lines) {
                    String l = line.toLowerCase(Locale.ROOT);
                    if (l.startsWith("cpu family")) {
                        family = parseIntAfterColon(line);
                    } else if (l.startsWith("model")) {
                        model = parseIntAfterColon(line);
                    }
                }
            } else if (os.contains("mac")) {
                family = execInt("sysctl", "-n", "machdep.cpu.family");
                model = execInt("sysctl", "-n", "machdep.cpu.model");
            }
        } catch (Exception e) {
            // ignore
        }
        if (family == 6) {
            return intelFamily6ModelYear(model);
        }
        return 0;
    }

    private static int parseIntAfterColon(String line) {
        int idx = line.indexOf(':');
        if (idx < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(line.substring(idx + 1).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int execInt(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return -1;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return Integer.parseInt(out);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int intelFamily6ModelYear(int model) {
        return switch (model) {
            case 0x1A, 0x1E, 0x2A, 0x2D, 0x2E -> 2011; // Sandy Bridge
            case 0x3A -> 2012;                          // Ivy Bridge
            case 0x3C, 0x3D, 0x3E, 0x3F -> 2013;        // Haswell / Ivy Bridge-E
            case 0x45, 0x46 -> 2014;                    // Haswell-E
            case 0x55, 0x5E -> 2015;                    // Skylake
            case 0x4E, 0x5C -> 2016;                    // Apollo Lake
            case 0x8E, 0x9E -> 2017;                    // Kaby Lake
            case 0x9A -> 2017;                          // Coffee Lake
            case 0xA5, 0xA6 -> 2019;                    // Comet Lake
            case 0xA7 -> 2020;                          // Comet Lake
            case 0x97, 0xB7 -> 2021;                    // Alder Lake
            case 0xBF -> 2022;                          // Raptor Lake
            default -> 0;
        };
    }

    private static int estimateGpuYear(String gpuRenderer) {
        String g = gpuRenderer.toLowerCase(Locale.ROOT);
        if (g.contains("gtx 16") || g.contains("rtx 20")) return 2018;
        if (g.contains("gtx 10")) return 2016;
        if (g.contains("rtx 30") || g.contains("rx 6")) return 2020; // Ampere / RDNA2
        if (g.contains("rtx 40") || g.contains("rx 7")) return 2022; // Ada / RDNA3
        if (g.contains("rtx 50")) return 2024;
        if (g.contains("gtx 9") || g.contains("rx 4")) return 2014;
        if (g.contains("gtx 7")) return 2013;
        if (g.contains("uhd") || g.contains("hd graphics")) return 2015;
        if (g.contains("iris")) return 2019;
        return 0;
    }

    // ---------------------------------------------------------------------
    // GPU usage (best effort, background thread).
    // NVIDIA: nvidia-smi ; AMD: rocm-smi / amdsmi-cli (incl. Windows ROCm).
    // ---------------------------------------------------------------------

    private static volatile String vendorCache = ""; // lowercase GPU vendor

    public static class GpuUsage {
        public final double usagePercent;
        public final double vramUsedMB;
        public final double vramTotalMB;

        public GpuUsage(double usagePercent, double vramUsedMB, double vramTotalMB) {
            this.usagePercent = usagePercent;
            this.vramUsedMB = vramUsedMB;
            this.vramTotalMB = vramTotalMB;
        }
    }

    private static final AtomicReference<GpuUsage> GPU_USAGE = new AtomicReference<>();
    private static final AtomicBoolean GPU_QUERY_IN_FLIGHT = new AtomicBoolean(false);
    private static volatile long lastGpuQueryNanos = 0;
    private static final long GPU_QUERY_INTERVAL_NANOS = 4_000_000_000L;

    /**
     * Returns the latest cached GPU usage, scheduling a background refresh at
     * most every 2 seconds. Never blocks the caller.
     */
    public static GpuUsage getGpuUsage() {
        long now = System.nanoTime();
        if (now - lastGpuQueryNanos >= GPU_QUERY_INTERVAL_NANOS && GPU_QUERY_IN_FLIGHT.compareAndSet(false, true)) {
            lastGpuQueryNanos = now;
            Thread thread = new Thread(() -> {
                try {
                    GPU_USAGE.set(queryGpuUsageBlocking());
                } finally {
                    GPU_QUERY_IN_FLIGHT.set(false);
                }
            }, "BenchMark-GPU-Query");
            thread.setDaemon(true);
            thread.start();
        }
        return GPU_USAGE.get();
    }

    private static GpuUsage queryGpuUsageBlocking() {
        if (!Config.ENABLE_GPU_USAGE.get()) {
            return null;
        }
        String v = vendorCache;
        boolean nvidia = v.isEmpty() || v.contains("nvidia");
        boolean amd = v.isEmpty() || v.contains("amd") || v.contains("ati")
                || v.contains("advanced micro devices");
        if (nvidia) {
            GpuUsage nv = queryNvidiaSmi();
            if (nv != null) {
                return nv;
            }
        }
        if (amd) {
            GpuUsage a = queryAmdTool();
            if (a != null) {
                return a;
            }
        }
        // Universal fallback: Windows performance counters (works for both AMD
        // and NVIDIA, no extra driver tool needed).
        GpuUsage win = queryWindowsGpuPerfCounter();
        if (win != null) {
            return win;
        }
        return null;
    }

    private static GpuUsage queryWindowsGpuPerfCounter() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return null;
        }
        // Reads the same Windows performance counters Task Manager uses.
        // Output: "<usage%>;<dedicatedUsedMB>;<totalAdapterRAMMB>" or "NA".
        String script = "$e=(Get-Counter '\\GPU Engine(*)\\Utilization Percentage' -SampleInterval 1 -MaxSamples 2 -ErrorAction SilentlyContinue).CounterSamples|?{$_.InstanceName -match 'engtype_3D'}|Measure-Object CookedValue -Sum;"
                + "$u=(Get-Counter '\\GPU Adapter Memory(*)\\Dedicated Usage' -ErrorAction SilentlyContinue).CounterSamples|Measure-Object CookedValue -Sum;"
                + "$v=(Get-CimInstance Win32_VideoController -ErrorAction SilentlyContinue|Measure-Object AdapterRAM -Maximum).Maximum;"
                + "if($e.Sum){[math]::Round($e.Sum,1).ToString()+';'+[math]::Round(($u.Sum/1MB),1).ToString()+';'+[math]::Round(($v/1MB),1).ToString()}else{'NA'}";
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-Command", script).redirectErrorStream(true).start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (out.isBlank() || "NA".equalsIgnoreCase(out)) {
                return null;
            }
            String[] parts = out.split(";");
            if (parts.length >= 1) {
                double usage = Double.parseDouble(parts[0].trim());
                double used = parts.length >= 2 ? Double.parseDouble(parts[1].trim()) : 0;
                double total = parts.length >= 3 ? Double.parseDouble(parts[2].trim()) : 0;
                return new GpuUsage(Math.min(100, usage), used, total);
            }
        } catch (Throwable t) {
            // PowerShell unavailable - GPU usage stays unavailable
        }
        return null;
    }

    private static GpuUsage queryNvidiaSmi() {
        try {
            Process p = new ProcessBuilder("nvidia-smi",
                    "--query-gpu=utilization.gpu,memory.used,memory.total",
                    "--format=csv,noheader,nounits")
                    .redirectErrorStream(true).start();
            if (!p.waitFor(1, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (out.isBlank()) {
                return null;
            }
            String[] parts = out.split(",");
            if (parts.length >= 3) {
                double usage = Double.parseDouble(parts[0].trim());
                double used = Double.parseDouble(parts[1].trim());
                double total = Double.parseDouble(parts[2].trim());
                return new GpuUsage(usage, used, total);
            }
        } catch (Throwable t) {
            // nvidia-smi not available
        }
        return null;
    }

    // Cached list of discovered AMD monitoring tools (PATH tools + Windows ROCm installs).
    private static volatile List<String[]> amdToolCache = null;

    private static GpuUsage queryAmdTool() {
        List<String[]> tools = amdToolCache;
        if (tools == null) {
            tools = discoverAmdTools();
            amdToolCache = tools;
        }
        for (String[] cmd : tools) {
            try {
                GpuUsage usage = runAndParseAmd(cmd);
                if (usage != null) {
                    return usage;
                }
            } catch (Throwable t) {
                // try the next tool
            }
        }
        return null;
    }

    private static List<String[]> discoverAmdTools() {
        List<String[]> tools = new ArrayList<>();
        tools.add(new String[]{"rocm-smi", "--showuse", "--showmemuse"});
        tools.add(new String[]{"amdsmi-cli", "--showuse", "--showmemuse"});
        // Windows: AMD ROCm installs usually live under "C:\Program Files\AMD\ROCm\<version>\bin"
        try {
            Path rocm = Path.of("C:\\Program Files\\AMD\\ROCm");
            if (Files.isDirectory(rocm)) {
                try (DirectoryStream<Path> versions = Files.newDirectoryStream(rocm)) {
                    for (Path ver : versions) {
                        if (!Files.isDirectory(ver)) {
                            continue;
                        }
                        Path bin = ver.resolve("bin");
                        if (Files.isDirectory(bin)) {
                            tools.add(new String[]{bin.resolve("amdsmi-cli.exe").toString(), "--showuse", "--showmemuse"});
                            tools.add(new String[]{bin.resolve("rocm-smi.exe").toString(), "--showuse", "--showmemuse"});
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return tools;
    }

    private static GpuUsage runAndParseAmd(String[] cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (out.isBlank()) {
                return null;
            }
            double usage = parseAmdPercent(out, "GPU use");
            if (usage < 0) {
                return null;
            }
            // VRAM MB is not reliably reported by these tools in text mode; report 0 (unknown).
            return new GpuUsage(usage, 0, 0);
        } catch (Throwable t) {
            // tool missing or failed - try the next candidate
            return null;
        }
    }

    private static double parseAmdPercent(String out, String key) {
        for (String line : out.split("\r?\n")) {
            if (!line.contains(key) || !line.contains("%")) {
                continue;
            }
            int end = line.lastIndexOf('%');
            int start = end;
            while (start > 0 && Character.isDigit(line.charAt(start - 1))) {
                start--;
            }
            if (start < end) {
                try {
                    return Double.parseDouble(line.substring(start, end).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    public static Path getReportDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(Config.REPORT_DIRECTORY.get());
    }
}
