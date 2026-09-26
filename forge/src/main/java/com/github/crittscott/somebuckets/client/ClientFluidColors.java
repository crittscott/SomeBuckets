package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Client-only fluid color resolution: reads and caches a fluid's still-texture average color, then
 * multiplies it by the stack's tint. Backs {@link SidedFluidColors} on the physical client and
 * supplies the {@link FluidBucketModel} appearance and the {@code /sb fluids} diagnostic probe.
 */
@OnlyIn(Dist.CLIENT)
final class ClientFluidColors {
    private ClientFluidColors() {}

    static int getColorRgb(FluidStack stack, int fallbackRgb) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        TextureAtlasSprite sprite = stillTexture == null ? null : Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
        return ClientTextureColors.color(sprite, extensions.getTintColor(stack), fallbackRgb);
    }

    /** Resolves the {@link FluidBucketModel} fluid layer's sprite, tint, and emissiveness. */
    @Nullable
    static FluidBucketModel.Look look(StoredFluid stored) {
        FluidStack stack = ForgeFluidStacks.of(stored);
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        if (stillTexture == null) return null;
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
        return new FluidBucketModel.Look(sprite, extensions.getTintColor(stack),
                stack.getFluid().getFluidType().getLightLevel() > 0);
    }

    static FluidDiagnostics.FluidColorSample sampleFor(Fluid fluid) {
        FluidStack stack = ForgeFluidStacks.of(new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB));
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        TextureAtlasSprite sprite = stillTexture == null ? null : Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
        ClientTextureColors.SpriteAverage average = ClientTextureColors.average(sprite);
        return new FluidDiagnostics.FluidColorSample(stillTexture, average.rgb(),
                extensions.getTintColor(stack), average.spriteMissing(), average.sourceImageMissing(),
                average.fullyTransparent(), average.ioError());
    }

    static void clearCache() {
        ClientTextureColors.clearCache();
    }
}
