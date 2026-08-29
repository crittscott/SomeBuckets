package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Loads, averages, caches, and tints client texture colors. */
@Environment(EnvType.CLIENT)
final class ClientTextureColors {
    private static final int NO_COLOR = -1;
    private static final Map<ResourceLocation, Integer> BASE_COLORS = new ConcurrentHashMap<>();

    private ClientTextureColors() {}

    static int color(@Nullable ResourceLocation texture, int argbTint, int fallbackRgb) {
        int baseColor = texture == null
                ? fallbackRgb
                : BASE_COLORS.computeIfAbsent(texture, ClientTextureColors::readAverageColor);
        if (baseColor == NO_COLOR) baseColor = fallbackRgb;
        return multiply(baseColor, argbTint);
    }

    static void clearCache() {
        BASE_COLORS.clear();
    }

    private static int readAverageColor(ResourceLocation texture) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                texture.getNamespace(), "textures/" + texture.getPath() + ".png");
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
