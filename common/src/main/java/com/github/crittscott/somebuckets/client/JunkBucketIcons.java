package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lays out the stored-item icons drawn inside the Junk Bucket opening. The active resource-pack mask
 * defines the opening as bottom-first horizontal {@link Span}s; from those this derives both the
 * vessel {@link Rectangle}s that cover icons outside the opening and the per-item {@link Placement}s
 * drawn within it.
 */
@Environment(EnvType.CLIENT)
final class JunkBucketIcons {
    static final float ITEM_MODEL_SIZE = 16.0F;

    private static final ResourceLocation MASK =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "textures/item/junk_bucket_opening.png");

    private static final float CONTENT_Z = 8.575F;
    private static final float DEPTH_STEP = 0.025F;
    private static final float MIN_SIZE = 4.5F;
    private static final float MAX_SIZE = 6.0F;
    private static final float MAX_TILT_RADIANS = (float) Math.toRadians(25.0);
    private static final float EDGE_INSET = 1.5F;
    private static final float SILHOUETTE_INSET = 1.0F;
    private static final float MAX_SINK = 1.0F;

    private static volatile List<Span> spans;

    private JunkBucketIcons() {}

    /** One horizontal slice of the opening. */
    record Span(float minX, float maxX, float minY, float maxY) {}

    /** A vessel rectangle that covers stored icons outside the opening. */
    record Rectangle(float minX, float maxX, float minY, float maxY) {}

    /** One child item's transform in the bucket's item-model coordinate system. */
    record Placement(int index, float centerX, float centerY, float size, float angle, float depth) {}

    /** The opening's slices, bottom row first. Empty when the mask cannot be read. */
    static List<Span> spans() {
        List<Span> cached = spans;
        if (cached == null) {
            cached = read();
            spans = cached;
        }
        return cached;
    }

    static void clearCache() {
        spans = null;
    }

    /** The vessel rectangles that cover stored icons outside the opening. */
    static List<Rectangle> cover() {
        List<Span> mouth = spans();
        if (mouth.isEmpty()) {
            return List.of(new Rectangle(0.0F, ITEM_MODEL_SIZE, 0.0F, ITEM_MODEL_SIZE));
        }

        List<Rectangle> out = new ArrayList<>();
        float cursorY = 0.0F;
        for (Span span : mouth) {
            if (span.minY() > cursorY) {
                out.add(new Rectangle(0.0F, ITEM_MODEL_SIZE, cursorY, span.minY()));
            }
            if (span.minX() > 0.0F) {
                out.add(new Rectangle(0.0F, span.minX(), span.minY(), span.maxY()));
            }
            if (span.maxX() < ITEM_MODEL_SIZE) {
                out.add(new Rectangle(span.maxX(), ITEM_MODEL_SIZE, span.minY(), span.maxY()));
            }
            cursorY = Math.max(cursorY, span.maxY());
        }
        if (cursorY < ITEM_MODEL_SIZE) {
            out.add(new Rectangle(0.0F, ITEM_MODEL_SIZE, cursorY, ITEM_MODEL_SIZE));
        }
        return List.copyOf(out);
    }

    /**
     * Places the contents newest first. The oldest entry receives the greatest depth and is
     * therefore nearest the viewer, matching FIFO ejection order.
     */
    static List<Placement> arrange(List<ItemStack> contents, long layoutSeed) {
        List<Span> mouth = spans();
        if (mouth.isEmpty() || contents.isEmpty()) return List.of();

        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float rim = -Float.MAX_VALUE;
        for (Span span : mouth) {
            left = Math.min(left, span.minX());
            right = Math.max(right, span.maxX());
            rim = Math.max(rim, span.maxY());
        }

        List<Placement> placements = new ArrayList<>(contents.size());
        for (int index = contents.size() - 1; index >= 0; index--) {
            RandomSource random = RandomSource.create(seedFor(contents.get(index), index) ^ layoutSeed);
            float size = MIN_SIZE + random.nextFloat() * (MAX_SIZE - MIN_SIZE);
            float angle = (random.nextFloat() * 2.0F - 1.0F) * MAX_TILT_RADIANS;
            float rise = size * 0.5F * (Math.abs((float) Math.sin(angle))
                    + Math.abs((float) Math.cos(angle)));
            float minCenterX = Math.max(left + EDGE_INSET, SILHOUETTE_INSET + rise);
            float maxCenterX = Math.min(right - EDGE_INSET,
                    ITEM_MODEL_SIZE - SILHOUETTE_INSET - rise);
            float centerX = minCenterX <= maxCenterX
                    ? minCenterX + random.nextFloat() * (maxCenterX - minCenterX)
                    : (left + right) * 0.5F;
            float centerY = rim - rise - random.nextFloat() * MAX_SINK;
            float depth = CONTENT_Z + (contents.size() - 1 - index) * DEPTH_STEP;
            placements.add(new Placement(index, centerX, centerY, size, angle, depth));
        }
        return List.copyOf(placements);
    }

    private static long seedFor(ItemStack stack, int index) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.hashCode() * 31L + index;
    }

    private static List<Span> read() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MASK);
        if (resource.isEmpty()) return List.of();

        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            float scaleX = ITEM_MODEL_SIZE / image.getWidth();
            float scaleY = ITEM_MODEL_SIZE / image.getHeight();
            List<Span> out = new ArrayList<>();

            for (int row = image.getHeight() - 1; row >= 0; row--) {
                int minX = -1;
                int maxX = -1;
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getPixelRGBA(x, row) >>> 24) == 0) continue;
                    if (minX < 0) minX = x;
                    maxX = x;
                }
                if (minX < 0) continue;

                float left = minX * scaleX;
                float right = (maxX + 1) * scaleX;
                float bottom = ITEM_MODEL_SIZE - (row + 1) * scaleY;
                float top = ITEM_MODEL_SIZE - row * scaleY;

                Span previous = out.isEmpty() ? null : out.get(out.size() - 1);
                if (previous != null && previous.minX() == left && previous.maxX() == right
                        && previous.maxY() == bottom) {
                    out.set(out.size() - 1, new Span(left, right, previous.minY(), top));
                } else {
                    out.add(new Span(left, right, bottom, top));
                }
            }
            return List.copyOf(out);
        } catch (IOException ignored) {
            return List.of();
        }
    }
}
