package com.github.crittscott.somebuckets.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.DistExecutor;

/**
 * Client-safe fluid tint reader. Returns a 24-bit RGB suitable for durability bars.
 * On a dedicated server, this returns the provided fallback without touching client classes.
 */
public final class FluidTintHelper {
    private FluidTintHelper() {}

    /**
     * Get the fluid's client tint as 24-bit RGB.
     * Falls back to the provided color if unavailable or on server.
     */
    public static int getTintRgb(FluidStack stack, int fallbackRgb) {
        if (stack == null || stack.isEmpty()) return fallbackRgb;
        try {
            return DistExecutor.unsafeRunForDist(
                    () -> () -> ClientImpl.getTintRgb(stack),
                    () -> () -> fallbackRgb
            );
        } catch (Throwable t) {
            return fallbackRgb;
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientImpl {
        static int getTintRgb(FluidStack stack) {
            int argb = IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);
            return argb & 0x00FFFFFF; // durability bar expects RGB (no alpha)
        }
    }
}
