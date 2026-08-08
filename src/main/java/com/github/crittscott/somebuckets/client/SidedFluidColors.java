package com.github.crittscott.somebuckets.client;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/** Common-to-client bridge for stack-aware fluid colors with a dedicated-server fallback. */
public final class SidedFluidColors {
    private SidedFluidColors() {}

    /**
     * Resolves a fluid stack's still-texture base color and stack-aware tint on the physical
     * client, using {@code fallbackRgb} as the base when the texture has no readable color. A
     * dedicated server, null stack, or empty stack returns {@code fallbackRgb} without loading
     * client implementation classes.
     *
     * @return the client-resolved RGB color, or the exact fallback when client resolution does not run
     */
    public static int getColorRgb(@Nullable FluidStack stack, int fallbackRgb) {
        if (stack == null || stack.isEmpty()) return fallbackRgb;
        return DistExecutor.unsafeRunForDist(
                () -> () -> ClientFluidColors.getColorRgb(stack, fallbackRgb),
                () -> () -> fallbackRgb
        );
    }
}
