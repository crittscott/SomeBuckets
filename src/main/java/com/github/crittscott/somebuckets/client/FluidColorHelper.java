package com.github.crittscott.somebuckets.client;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.DistExecutor;

public final class FluidColorHelper {
    private FluidColorHelper() {}

    public static int getColorRgb(FluidStack stack, int fallbackRgb) {
        if (stack == null || stack.isEmpty()) return fallbackRgb;
        try {
            return DistExecutor.unsafeRunForDist(
                    () -> () -> ClientFluidColors.getColorRgb(stack, fallbackRgb),
                    () -> () -> fallbackRgb
            );
        } catch (Throwable ignored) {
            return fallbackRgb;
        }
    }
}
