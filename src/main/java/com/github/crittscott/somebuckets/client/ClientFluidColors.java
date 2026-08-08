package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only fluid color resolution: reads and caches a fluid's still-texture average color, then
 * multiplies it by the stack's tint. Backs {@link SidedFluidColors} on the physical client.
 */
@OnlyIn(Dist.CLIENT)
final class ClientFluidColors {
    private static final int NO_COLOR = -1;
    private static final Map<ResourceLocation, Integer> BASE_COLORS = new ConcurrentHashMap<>();

    private ClientFluidColors() {}

    static int getColorRgb(FluidStack stack, int fallbackRgb) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        int baseColor = stillTexture == null
                ? fallbackRgb
                : BASE_COLORS.computeIfAbsent(stillTexture, ClientFluidColors::readAverageColor);
        if (baseColor == NO_COLOR) baseColor = fallbackRgb;

        return multiply(baseColor, extensions.getTintColor(stack));
    }

    static void clearCache() {
        BASE_COLORS.clear();
    }

    private static int readAverageColor(ResourceLocation texture) {
        ResourceLocation file = new ResourceLocation(
                texture.getNamespace(), "textures/" + texture.getPath() + ".png"
        );
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return NO_COLOR;

        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            long red = 0;
            long green = 0;
            long blue = 0;
            long weight = 0;

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int alpha = abgr >>> 24;
                    if (alpha == 0) continue;

                    red += (long) (abgr & 0xFF) * alpha;
                    green += (long) ((abgr >>> 8) & 0xFF) * alpha;
                    blue += (long) ((abgr >>> 16) & 0xFF) * alpha;
                    weight += alpha;
                }
            }

            if (weight == 0) return NO_COLOR;
            return ((int) (red / weight) << 16)
                    | ((int) (green / weight) << 8)
                    | (int) (blue / weight);
        } catch (IOException ignored) {
            return NO_COLOR;
        }
    }

    private static int multiply(int rgb, int argbTint) {
        int red = ((rgb >>> 16) & 0xFF) * ((argbTint >>> 16) & 0xFF) / 255;
        int green = ((rgb >>> 8) & 0xFF) * ((argbTint >>> 8) & 0xFF) / 255;
        int blue = (rgb & 0xFF) * (argbTint & 0xFF) / 255;
        return (red << 16) | (green << 8) | blue;
    }
}
