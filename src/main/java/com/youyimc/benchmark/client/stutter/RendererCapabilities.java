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

import net.neoforged.fml.ModList;

/**
 * What the client is rendering with, which decides which stutter metrics are
 * meaningful. Render-pipeline replacing mods (Sodium/Iris/VulkanMod) break
 * vanilla stage hooks and per-mod GPU attribution, so the report has to say so.
 */
public record RendererCapabilities(boolean sodium, boolean iris, boolean vulkanMod, String summary) {
    public static RendererCapabilities detect() {
        ModList mods = ModList.get();
        boolean sodium = isLoaded(mods, "sodium") || isLoaded(mods, "embeddium");
        boolean iris = isLoaded(mods, "iris");
        boolean vulkan = isLoaded(mods, "vulkanmod");

        StringBuilder sb = new StringBuilder(64);
        if (vulkan) {
            sb.append("VulkanMod");
        } else if (sodium && iris) {
            sb.append("Sodium + Iris");
        } else if (sodium) {
            sb.append("Sodium");
        } else if (iris) {
            sb.append("Iris");
        } else {
            sb.append("vanilla renderer");
        }
        return new RendererCapabilities(sodium, iris, vulkan, sb.toString());
    }

    private static boolean isLoaded(ModList mods, String modId) {
        return mods != null && mods.isLoaded(modId);
    }

    /** {@return true if vanilla render-stage hooks can be trusted} */
    public boolean hasVanillaPipeline() {
        return !sodium && !iris && !vulkanMod;
    }
}
