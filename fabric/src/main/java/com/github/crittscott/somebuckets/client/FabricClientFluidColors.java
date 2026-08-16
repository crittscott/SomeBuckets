package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Resolves Fabric fluid variant tints and representative RGB colors. */
final class FabricClientFluidColors {
    private FabricClientFluidColors() {}

    static int color(StoredFluid stored, int fallback) {
        FluidVariant variant = FluidVariant.of(stored.fluid(), stored.variantTag());
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        return ClientTextureColors.color(sprite == null ? null : sprite.contents().name(),
                FluidVariantRendering.getColor(variant), fallback);
    }

    static int tint(StoredFluid stored) {
        if (stored.isEmpty()) return -1;
        FluidVariant variant = FluidVariant.of(stored.fluid(), stored.variantTag());
        return FluidVariantRendering.getColor(variant);
    }

    static void clearCache() {
        ClientTextureColors.clearCache();
    }
}
