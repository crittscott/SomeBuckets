package com.github.crittscott.somebuckets.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

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
        TextureAtlasSprite sprite = stillTexture == null ? null : Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        return ClientTextureColors.color(sprite, extensions.getTintColor(stack), fallbackRgb);
    }

    static void clearCache() {
        ClientTextureColors.clearCache();
    }
}
