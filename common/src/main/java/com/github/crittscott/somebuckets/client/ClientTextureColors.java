package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** Loads, averages, caches, and tints client texture colors. */
@Environment(EnvType.CLIENT)
final class ClientTextureColors {
    private static final Map<Identifier, SpriteAverage> AVERAGES = new ConcurrentHashMap<>();

    private ClientTextureColors() {}

    /** How averaging a sprite's source image ended. */
    enum Outcome {
        /** The image was read but every pixel of its first frame is transparent. */
        FULLY_TRANSPARENT,
        /** The sprite has no source image in the active resource packs. */
        NO_IMAGE,
        /** There is no sprite, or it is the missing-texture sprite. */
        NO_SPRITE,
        /** The image was averaged. */
        OK,
        /** The source image exists but could not be read. */
        READ_ERROR
    }

    /**
     * How averaging a sprite's first animation frame ended, and the averaged opaque color.
     *
     * @param rgb the averaged color; meaningful only when {@code outcome} is {@link Outcome#OK}, 0 otherwise
     */
    record SpriteAverage(Outcome outcome, int rgb) {
        /** Whether averaging produced a color. */
        boolean hasColor() {
            return outcome == Outcome.OK;
        }
    }

    /**
     * Multiplies the average opaque color of {@code sprite}'s first animation frame by a tint.
     *
     * @param sprite the resolved atlas sprite whose source image is averaged, or {@code null} to
     *               skip straight to {@code fallbackRgb}
     * @param argbTint the ARGB tint to multiply the base color by
     * @param fallbackRgb the color to use when averaging produces no color
     * @return the tinted RGB color
     */
    static int color(@Nullable TextureAtlasSprite sprite, int argbTint, int fallbackRgb) {
        SpriteAverage average = average(sprite);
        return ARGB.multiply(ARGB.opaque(average.hasColor() ? average.rgb() : fallbackRgb), argbTint) & 0xFFFFFF;
    }

    /**
     * Resolves a sprite's cached or freshly averaged base color with its outcome. Every result,
     * including a failure, is cached by sprite name until the next resource reload.
     */
    static SpriteAverage average(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null || isMissing(sprite)) return new SpriteAverage(Outcome.NO_SPRITE, 0);
        return AVERAGES.computeIfAbsent(sprite.contents().name(), name -> readAverage(sprite));
    }

    static void clearCache() {
        AVERAGES.clear();
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    private static SpriteAverage readAverage(TextureAtlasSprite sprite) {
        Identifier file = SpriteSource.TEXTURE_ID_CONVERTER.idToFile(sprite.contents().name());
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return new SpriteAverage(Outcome.NO_IMAGE, 0);

        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            int width = Math.min(sprite.contents().width(), image.getWidth());
            int height = Math.min(sprite.contents().height(), image.getHeight());
            long red = 0;
            long green = 0;
            long blue = 0;
            long weight = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getPixel(x, y);
                    int alpha = ARGB.alpha(argb);
                    if (alpha == 0) continue;
                    red += (long) ARGB.red(argb) * alpha;
                    green += (long) ARGB.green(argb) * alpha;
                    blue += (long) ARGB.blue(argb) * alpha;
                    weight += alpha;
                }
            }
            if (weight == 0) return new SpriteAverage(Outcome.FULLY_TRANSPARENT, 0);
            int rgb = ARGB.color(0, (int) (red / weight), (int) (green / weight), (int) (blue / weight));
            return new SpriteAverage(Outcome.OK, rgb);
        } catch (IOException ignored) {
            return new SpriteAverage(Outcome.READ_ERROR, 0);
        }
    }
}
