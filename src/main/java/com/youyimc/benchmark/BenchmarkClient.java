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

import com.youyimc.benchmark.client.BenchmarkScreen;
import com.youyimc.benchmark.client.BenchmarkSession;
import com.youyimc.benchmark.client.stutter.StutterSession;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Benchmark.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Benchmark.MODID, value = Dist.CLIENT)
public class BenchmarkClient {
    public BenchmarkClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        Benchmark.LOGGER.info("BenchMark client initialized");
    }

    // Adds a button to the pause screen to open the benchmark menu.
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen) {
            event.addListener(Button.builder(
                    Component.translatable("benchmark.gui.pause_button"),
                    btn -> Minecraft.getInstance().setScreen(new BenchmarkScreen())
            ).bounds(8, 8, 100, 20).build());
        }
    }

    // Feeds per-frame data into the benchmark / stutter sessions while a test is running.
    @SubscribeEvent
    static void onRenderFrame(RenderFrameEvent.Post event) {
        BenchmarkSession.getInstance().onFrame();
        StutterSession.getInstance().onFrame();
    }

    // Ensures the test completes on time even if frames stall (e.g. alt-tab / minimized).
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        BenchmarkSession.getInstance().tick();
        StutterSession.getInstance().tick();
    }
}
