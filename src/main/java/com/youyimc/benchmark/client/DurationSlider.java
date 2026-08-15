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

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A slider that picks the test duration in whole seconds (min..max).
 */
public class DurationSlider extends AbstractSliderButton {
    private final int min;
    private final int max;

    public DurationSlider(int x, int y, int width, int height, int min, int max, int value) {
        super(x, y, width, height, Component.empty(), clamp01((value - min) / (double) (max - min)));
        this.min = min;
        this.max = max;
        this.updateMessage();
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    public int getValue() {
        return (int) Math.round(min + this.value * (max - min));
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.translatable("benchmark.gui.duration_format", getValue()));
    }

    @Override
    protected void applyValue() {
        this.updateMessage();
    }
}
