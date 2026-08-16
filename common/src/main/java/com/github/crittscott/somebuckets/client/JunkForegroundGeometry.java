package com.github.crittscott.somebuckets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

/** Defines the vessel rectangles that cover stored Junk Bucket icons outside the opening. */
@Environment(EnvType.CLIENT)
final class JunkForegroundGeometry {
    private JunkForegroundGeometry() {}

    record Rectangle(float minX, float maxX, float minY, float maxY) {}

    static List<Rectangle> cover() {
        List<BucketMouth.Span> mouth = BucketMouth.spans();
        if (mouth.isEmpty()) {
            return List.of(new Rectangle(0.0F, BucketMouth.ITEM_MODEL_SIZE,
                    0.0F, BucketMouth.ITEM_MODEL_SIZE));
        }

        List<Rectangle> out = new ArrayList<>();
        float cursorY = 0.0F;
        for (BucketMouth.Span span : mouth) {
            if (span.minY() > cursorY) {
                out.add(new Rectangle(0.0F, BucketMouth.ITEM_MODEL_SIZE,
                        cursorY, span.minY()));
            }
            if (span.minX() > 0.0F) {
                out.add(new Rectangle(0.0F, span.minX(), span.minY(), span.maxY()));
            }
            if (span.maxX() < BucketMouth.ITEM_MODEL_SIZE) {
                out.add(new Rectangle(span.maxX(), BucketMouth.ITEM_MODEL_SIZE,
                        span.minY(), span.maxY()));
            }
            cursorY = Math.max(cursorY, span.maxY());
        }
        if (cursorY < BucketMouth.ITEM_MODEL_SIZE) {
            out.add(new Rectangle(0.0F, BucketMouth.ITEM_MODEL_SIZE,
                    cursorY, BucketMouth.ITEM_MODEL_SIZE));
        }
        return List.copyOf(out);
    }
}
