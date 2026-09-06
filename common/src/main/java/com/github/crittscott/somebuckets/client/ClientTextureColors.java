package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
    static final int NO_COLOR = -1;
    private static final Map<ResourceLocation, Integer> BASE_COLORS = new ConcurrentHashMap<>();

    private ClientTextureColors() {}

    /**
     * Averaged opaque color of a sprite's first animation frame plus the signals a diagnostic needs
     * to explain a failure. {@code rgb} is {@link #NO_COLOR} whenever any flag is set.
     */
    record SpriteAverage(int rgb, boolean spriteMissing, boolean sourceImageMissing,
                         boolean fullyTransparent, boolean ioError) {}

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
        int baseColor = average(sprite).rgb();
        if (baseColor == NO_COLOR) baseColor = fallbackRgb;
        return multiply(baseColor, argbTint);
    }

    /**
     * Resolves a sprite's cached or freshly averaged base color with the diagnostic signals. A
     * readable color is cached by sprite name; a failure is not, so a later resource reload can
     * still succeed.
     */
    static SpriteAverage average(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null || isMissing(sprite)) {
            return new SpriteAverage(NO_COLOR, true, false, false, false);
        }
        Integer cached = BASE_COLORS.get(sprite.contents().name());
        if (cached != null) {
            return new SpriteAverage(cached, false, false, false, false);
        }
        SpriteAverage sampled = readAverage(sprite);
        if (sampled.rgb() != NO_COLOR) {
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
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                name.getNamespace(), "textures/" + name.getPath() + ".png");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return new SpriteAverage(NO_COLOR, false, true, false, false);

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
                    int alpha = abgr >>> 24;
                    if (alpha == 0) continue;
                    red += (long) (abgr & 0xFF) * alpha;
                    green += (long) ((abgr >>> 8) & 0xFF) * alpha;
                    blue += (long) ((abgr >>> 16) & 0xFF) * alpha;
                    weight += alpha;
                }
            }
            if (weight == 0) return new SpriteAverage(NO_COLOR, false, false, true, false);
            int rgb = ((int) (red / weight) << 16)
                    | ((int) (green / weight) << 8)
                    | (int) (blue / weight);
            return new SpriteAverage(rgb, false, false, false, false);
        } catch (IOException ignored) {
            return new SpriteAverage(NO_COLOR, false, false, false, true);
        }
    }

    private static int multiply(int rgb, int argbTint) {
        int red = ((rgb >>> 16) & 0xFF) * ((argbTint >>> 16) & 0xFF) / 255;
        int green = ((rgb >>> 8) & 0xFF) * ((argbTint >>> 8) & 0xFF) / 255;
        int blue = (rgb & 0xFF) * (argbTint & 0xFF) / 255;
        return (red << 16) | (green << 8) | blue;
    }
}
