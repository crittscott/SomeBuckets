package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;

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

    private ClientTextureColors() {}

    /** Why a texture has an average color, or why it has none. */
    enum Outcome {
        /** Every pixel is fully transparent. */
        FULLY_TRANSPARENT,
        /** The texture id names no readable resource. */
        NO_IMAGE,
        /** There is no texture id at all. */
        NO_TEXTURE,
        /** The average was read. */
        OK,
        /** The image file could not be decoded. */
        READ_ERROR
    }

    /**
     * The alpha-weighted average color of a texture, or why there is none.
     *
     * @param rgb the average color; meaningful only when {@code outcome} is {@link Outcome#OK}, 0 otherwise
     */
    record TextureAverage(Outcome outcome, int rgb) {
        static final TextureAverage NO_TEXTURE = new TextureAverage(Outcome.NO_TEXTURE, 0);

        boolean hasColor() {
            return outcome == Outcome.OK;
        }
    }

    /** The texture's cached average, read on first use. */
    static TextureAverage average(@Nullable ResourceLocation texture) {
        return texture == null ? TextureAverage.NO_TEXTURE
                : AVERAGES.computeIfAbsent(texture, ClientTextureColors::readAverage);
    }

    static void clearCache() {
        AVERAGES.clear();
    }

    /** The texture's average color, or {@code fallbackRgb} when it has none, multiplied by the tint. */
    static int color(@Nullable ResourceLocation texture, int argbTint, int fallbackRgb) {
        TextureAverage average = average(texture);
        return FastColor.ARGB32.multiply(average.hasColor() ? average.rgb() : fallbackRgb, argbTint) & 0xFFFFFF;
    }

    private static TextureAverage readAverage(ResourceLocation texture) {
        ResourceLocation file = SpriteSource.TEXTURE_ID_CONVERTER.idToFile(texture);
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return new TextureAverage(Outcome.NO_IMAGE, 0);

        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            long red = 0;
            long green = 0;
            long blue = 0;
            long weight = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int alpha = FastColor.ABGR32.alpha(abgr);
                    if (alpha == 0) continue;
                    red += (long) FastColor.ABGR32.red(abgr) * alpha;
                    green += (long) FastColor.ABGR32.green(abgr) * alpha;
                    blue += (long) FastColor.ABGR32.blue(abgr) * alpha;
                    weight += alpha;
                }
            }
            if (weight == 0) return new TextureAverage(Outcome.FULLY_TRANSPARENT, 0);
            int rgb = FastColor.ARGB32.color(0, (int) (red / weight), (int) (green / weight), (int) (blue / weight));
            return new TextureAverage(Outcome.OK, rgb);
        } catch (IOException ignored) {
            return new TextureAverage(Outcome.READ_ERROR, 0);
        }
    }
}
