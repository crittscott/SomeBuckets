package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

/** Resolves Fabric fluid variant tints and representative RGB colors. */
final class FabricClientFluidColors {
    private FabricClientFluidColors() {}

    static int color(StoredFluid stored, int fallback) {
        FluidVariant variant = FabricFluidVariants.toVariant(stored);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        return ClientTextureColors.color(sprite, FluidVariantRendering.getColor(variant), fallback);
    }

    static FluidDiagnostics.FluidColorSample sampleFor(Fluid fluid) {
        FluidVariant variant = FabricFluidVariants.toVariant(fluid, null);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        ClientTextureColors.SpriteAverage average = ClientTextureColors.average(sprite);
        ResourceLocation stillTexture = sprite == null ? null : sprite.contents().name();
        return new FluidDiagnostics.FluidColorSample(stillTexture, average.rgb(),
                FluidVariantRendering.getColor(variant), average.spriteMissing(),
                average.sourceImageMissing(), average.fullyTransparent(), average.ioError());
    }

    static int tint(StoredFluid stored) {
        if (stored.isEmpty()) return -1;
        FluidVariant variant = FabricFluidVariants.toVariant(stored);
        return FluidVariantRendering.getColor(variant);
    }

    static void clearCache() {
        ClientTextureColors.clearCache();
    }
}
