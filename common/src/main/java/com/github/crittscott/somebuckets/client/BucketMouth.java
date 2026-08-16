package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Supplies the Junk Bucket opening as bottom-first horizontal spans in item-model space. The
 * active resource-pack mask defines the opening; a missing or unreadable mask yields no spans.
 */
@Environment(EnvType.CLIENT)
final class BucketMouth {
    static final float ITEM_MODEL_SIZE = 16.0F;

    private static final ResourceLocation MASK =
            new ResourceLocation(SomeBuckets.MODID, "textures/item/junk_bucket_opening.png");

    private static volatile List<Span> spans;

    private BucketMouth() {}

    /** One horizontal slice of the opening. */
    record Span(float minX, float maxX, float minY, float maxY) {}

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
