package com.github.crittscott.somebuckets.client;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The box of drawn pixels within a sprite, as fractions of the sprite's own extent.
 *
 * <p>Most item icons leave a transparent margin, and several fill only a diagonal band. Measuring
 * where a sprite actually draws lets an icon be placed by its visible edge rather than by the
 * square it is mapped onto, so an icon with a tall margin is not pushed down for clearance it does
 * not need.
 *
 * <p>The vertical fractions are measured from the bottom, matching item-model space rather than
 * image rows. Only the first animation frame is read.
 */
@OnlyIn(Dist.CLIENT)
final class SpriteBounds {
    /** Used for a sprite that draws nothing, and as the answer when a sprite cannot be read. */
    private static final Bounds FULL = new Bounds(0.0F, 1.0F, 0.0F, 1.0F);

    private static final Map<ResourceLocation, Bounds> BOUNDS = new ConcurrentHashMap<>();

    private SpriteBounds() {}

    /** Fractions of the sprite's width and height, left/right and bottom/top. */
    record Bounds(float minX, float maxX, float minY, float maxY) {}

    static Bounds of(TextureAtlasSprite sprite) {
        return BOUNDS.computeIfAbsent(sprite.contents().name(), name -> measure(sprite.contents()));
    }

    static void clearCache() {
        BOUNDS.clear();
    }

    private static Bounds measure(SpriteContents contents) {
        int width = contents.width();
        int height = contents.height();
        if (width <= 0 || height <= 0) return FULL;

        int left = width;
        int right = -1;
        int top = height;
        int bottom = -1;

        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (contents.isTransparent(0, x, y)) continue;
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        } catch (RuntimeException ignored) {
            return FULL;
        }

        if (right < 0) return FULL;

        // Image rows run downward, so the last drawn row is the bottom of the box.
        return new Bounds(
                (float) left / width,
                (float) (right + 1) / width,
                1.0F - (float) (bottom + 1) / height,
                1.0F - (float) top / height
        );
    }
}
