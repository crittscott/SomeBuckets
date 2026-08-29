package com.github.crittscott.somebuckets.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.fluids.FluidStack;

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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return ClientHolder.color(stack, fallbackRgb);
        }
        return fallbackRgb;
    }

    /** Loaded only when {@link #getColorRgb} takes the client branch, so a dedicated server never
     *  classloads {@link ClientFluidColors}. */
    private static final class ClientHolder {
        private ClientHolder() {}

        static int color(FluidStack stack, int fallbackRgb) {
            return ClientFluidColors.getColorRgb(stack, fallbackRgb);
        }
    }
}
