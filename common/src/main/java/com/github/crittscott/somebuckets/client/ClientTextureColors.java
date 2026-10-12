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
    private static final Map<ResourceLocation, TextureAverage> AVERAGES = new ConcurrentHashMap<>();

    /**
     * The alpha-weighted average color of a texture, or why there is none.
     *
     * @param rgb the average color, or {@code -1} when the texture produced no readable color
     */
    record TextureAverage(int rgb, boolean textureMissing, boolean imageMissing, boolean fullyTransparent,
                          boolean ioError) {
        static final TextureAverage NO_TEXTURE = new TextureAverage(-1, true, false, false, false);
    }

    private ClientTextureColors() {}

    /** The texture's average color, or {@code fallbackRgb} when it has none, multiplied by the tint. */
    static int color(@Nullable ResourceLocation texture, int argbTint, int fallbackRgb) {
        int baseColor = average(texture).rgb();
        return multiply(baseColor < 0 ? fallbackRgb : baseColor, argbTint);
    }

    /** The texture's cached average, read on first use. */
    static TextureAverage average(@Nullable ResourceLocation texture) {
        return texture == null ? TextureAverage.NO_TEXTURE
                : AVERAGES.computeIfAbsent(texture, ClientTextureColors::readAverage);
    }

    static void clearCache() {
        AVERAGES.clear();
    }

    private static TextureAverage readAverage(ResourceLocation texture) {
        ResourceLocation file = new ResourceLocation(
                texture.getNamespace(), "textures/" + texture.getPath() + ".png");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return new TextureAverage(-1, false, true, false, false);

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
            if (weight == 0) return new TextureAverage(-1, false, false, true, false);
            int rgb = ((int) (red / weight) << 16) | ((int) (green / weight) << 8) | (int) (blue / weight);
            return new TextureAverage(rgb, false, false, false, false);
        } catch (IOException ignored) {
            return new TextureAverage(-1, false, false, false, true);
        }
    }

    private static int multiply(int rgb, int argbTint) {
        int red = ((rgb >>> 16) & 0xFF) * ((argbTint >>> 16) & 0xFF) / 255;
        int green = ((rgb >>> 8) & 0xFF) * ((argbTint >>> 8) & 0xFF) / 255;
        int blue = (rgb & 0xFF) * (argbTint & 0xFF) / 255;
        return (red << 16) | (green << 8) | blue;
    }
}
