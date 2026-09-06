package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Client-only fluid color resolution: reads and caches a fluid's still-texture average color, then
 * multiplies it by the stack's tint. Backs {@link SidedFluidColors} on the physical client and
 * supplies the {@code /sb fluids} diagnostic probe.
 */
@OnlyIn(Dist.CLIENT)
final class ClientFluidColors {
    private ClientFluidColors() {}

    static int getColorRgb(FluidStack stack, int fallbackRgb) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        TextureAtlasSprite sprite = stillTexture == null ? null : Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        return ClientTextureColors.color(sprite, extensions.getTintColor(stack), fallbackRgb);
    }

    static FluidDiagnostics.FluidColorSample sampleFor(Fluid fluid) {
        FluidStack stack = NeoForgeFluidStacks.of(fluid, FluidBucketItem.BUCKET_VOLUME_MB, null);
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        TextureAtlasSprite sprite = stillTexture == null ? null : Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        ClientTextureColors.SpriteAverage average = ClientTextureColors.average(sprite);
        return new FluidDiagnostics.FluidColorSample(stillTexture, average.rgb(),
                extensions.getTintColor(stack), average.spriteMissing(), average.sourceImageMissing(),
                average.fullyTransparent(), average.ioError());
    }

    static void clearCache() {
        ClientTextureColors.clearCache();
    }
}
