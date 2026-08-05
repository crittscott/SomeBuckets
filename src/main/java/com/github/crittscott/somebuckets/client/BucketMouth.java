package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The opening of the Junk Bucket, expressed as horizontal spans in item-model space.
 *
 * <p>The shape is read from a mask texture, so the region that accepts drawn contents is exactly
 * the region the art calls the mouth. Only each row's leftmost and rightmost opaque pixels matter:
 * a row is taken as filled between them, so a gap in the middle of a row is not preserved. Rows
 * sharing a horizontal extent are merged, leaving one span per distinct width of the opening.
 *
 * <p>Model space runs 0..16 with y increasing upward, while image rows run downward from the top,
 * so row indices are mirrored on the way out. A mask at a resource pack's higher resolution is
 * scaled to the same 0..16 range.
 */
@OnlyIn(Dist.CLIENT)
final class BucketMouth {
    private static final ResourceLocation MASK =
            new ResourceLocation("somebuckets", "textures/item/junk_bucket_opening.png");

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
            float scaleX = 16.0F / image.getWidth();
            float scaleY = 16.0F / image.getHeight();
            List<Span> out = new ArrayList<>();

            // Walked from the bottom of the image up so the resulting spans run bottom-first.
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
                float bottom = 16.0F - (row + 1) * scaleY;
                float top = 16.0F - row * scaleY;

                Span previous = out.isEmpty() ? null : out.get(out.size() - 1);
                if (previous != null && previous.minX() == left && previous.maxX() == right
                        && previous.maxY() == bottom) {
                    out.set(out.size() - 1, new Span(left, right, previous.minY(), top));
                } else {
                    out.add(new Span(left, right, bottom, top));
                }
            }
            return List.copyOf(out);
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
    }
}
