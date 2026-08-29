package com.github.crittscott.somebuckets.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Client-only fluid color resolution: reads and caches a fluid's still-texture average color, then
 * multiplies it by the stack's tint. Backs {@link SidedFluidColors} on the physical client.
 */
@OnlyIn(Dist.CLIENT)
final class ClientFluidColors {
    private ClientFluidColors() {}

    static int getColorRgb(FluidStack stack, int fallbackRgb) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        return ClientTextureColors.color(stillTexture, extensions.getTintColor(stack), fallbackRgb);
    }

    static void clearCache() {
        ClientTextureColors.clearCache();
    }
}
