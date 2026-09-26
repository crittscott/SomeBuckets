package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;

/** Resolves Fabric fluid variant sprites, tints, and representative RGB colors. */
final class FabricClientFluidColors {
    private FabricClientFluidColors() {}

    static int color(StoredFluid stored, int fallback) {
        FluidVariant variant = FabricFluidVariants.toVariant(stored);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        return ClientTextureColors.color(sprite, FluidVariantRendering.getColor(variant), fallback);
    }

    static FluidDiagnostics.FluidColorSample sampleFor(Fluid fluid) {
        FluidVariant variant = FluidVariant.of(fluid);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        ClientTextureColors.SpriteAverage average = ClientTextureColors.average(sprite);
        ResourceLocation stillTexture = sprite == null ? null : sprite.contents().name();
        return new FluidDiagnostics.FluidColorSample(stillTexture, average.rgb(),
                FluidVariantRendering.getColor(variant), average.spriteMissing(),
                average.sourceImageMissing(), average.fullyTransparent(), average.ioError());
    }

    /** Resolves the {@link FluidBucketModel} fluid layer's sprite, tint, and emissiveness. */
    @Nullable
    static FluidBucketModel.Look look(StoredFluid stored) {
        FluidVariant variant = FabricFluidVariants.toVariant(stored);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        if (sprite == null) return null;
        return new FluidBucketModel.Look(sprite, FluidVariantRendering.getColor(variant),
                FluidVariantAttributes.getLuminance(variant) > 0);
    }

    static void clearCache() {
        ClientTextureColors.clearCache();
    }
}
