package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
    private static final Map<ResourceLocation, Integer> BASE_COLORS = new ConcurrentHashMap<>();

    private ClientTextureColors() {}

    /** Why a sprite has an average color, or why it has none. */
    enum Outcome {
        /** Every pixel of the first frame is fully transparent. */
        FULLY_TRANSPARENT,
        /** The sprite's source image is not a readable resource. */
        NO_IMAGE,
        /** There is no sprite, or it is the missing-texture sprite. */
        NO_SPRITE,
        /** The average was read. */
        OK,
        /** The source image could not be decoded. */
        READ_ERROR
    }

    /**
     * Averaged opaque color of a sprite's first animation frame, or why there is none.
     *
     * @param rgb the average color; meaningful only when {@code outcome} is {@link Outcome#OK}, 0 otherwise
     */
    record SpriteAverage(Outcome outcome, int rgb) {
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
     * @param fallbackRgb the color to use when the sprite is missing or fully transparent
     * @return the tinted RGB color
     */
    static int color(@Nullable TextureAtlasSprite sprite, int argbTint, int fallbackRgb) {
        SpriteAverage average = average(sprite);
        return FastColor.ARGB32.multiply(average.hasColor() ? average.rgb() : fallbackRgb, argbTint) & 0xFFFFFF;
    }

    /**
     * Resolves a sprite's cached or freshly averaged base color with the diagnostic signals. A
     * readable color is cached by sprite name; a failure is not, so a later resource reload can
     * still succeed.
     */
    static SpriteAverage average(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null || isMissing(sprite)) {
            return new SpriteAverage(Outcome.NO_SPRITE, 0);
        }
        Integer cached = BASE_COLORS.get(sprite.contents().name());
        if (cached != null) {
            return new SpriteAverage(Outcome.OK, cached);
        }
        SpriteAverage sampled = readAverage(sprite);
        if (sampled.hasColor()) {
            BASE_COLORS.put(sprite.contents().name(), sampled.rgb());
        }
        return sampled;
    }

    static void clearCache() {
        BASE_COLORS.clear();
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    private static SpriteAverage readAverage(TextureAtlasSprite sprite) {
        ResourceLocation name = sprite.contents().name();
        ResourceLocation file = SpriteSource.TEXTURE_ID_CONVERTER.idToFile(name);
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
                    int abgr = image.getPixelRGBA(x, y);
                    int alpha = FastColor.ABGR32.alpha(abgr);
                    if (alpha == 0) continue;
                    red += (long) FastColor.ABGR32.red(abgr) * alpha;
                    green += (long) FastColor.ABGR32.green(abgr) * alpha;
                    blue += (long) FastColor.ABGR32.blue(abgr) * alpha;
                    weight += alpha;
                }
            }
            if (weight == 0) return new SpriteAverage(Outcome.FULLY_TRANSPARENT, 0);
            int rgb = FastColor.ARGB32.color(0, (int) (red / weight), (int) (green / weight), (int) (blue / weight));
            return new SpriteAverage(Outcome.OK, rgb);
        } catch (IOException ignored) {
            return new SpriteAverage(Outcome.READ_ERROR, 0);
        }
    }
}
