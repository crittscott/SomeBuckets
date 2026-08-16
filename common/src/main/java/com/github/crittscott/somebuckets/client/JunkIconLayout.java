package com.github.crittscott.somebuckets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Produces stable positions for the item stacks drawn in a Junk Bucket. */
@Environment(EnvType.CLIENT)
final class JunkIconLayout {
    private static final float CONTENT_Z = 8.575F;
    private static final float DEPTH_STEP = 0.025F;
    private static final float MIN_SIZE = 4.5F;
    private static final float MAX_SIZE = 6.0F;
    private static final float MAX_TILT_RADIANS = (float) Math.toRadians(25.0);
    private static final float EDGE_INSET = 1.5F;
    private static final float SILHOUETTE_INSET = 1.0F;
    private static final float MAX_SINK = 1.0F;

    private JunkIconLayout() {}

    /** One child item's transform in the bucket's item-model coordinate system. */
    record Placement(int index, float centerX, float centerY, float size, float angle, float depth) {}

    /**
     * Places the contents newest first. The oldest entry receives the greatest depth and is
     * therefore nearest the viewer, matching FIFO ejection order.
     */
    static List<Placement> arrange(List<ItemStack> contents, long layoutSeed) {
        List<BucketMouth.Span> mouth = BucketMouth.spans();
        if (mouth.isEmpty() || contents.isEmpty()) return List.of();

        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float rim = -Float.MAX_VALUE;
        for (BucketMouth.Span span : mouth) {
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
                    BucketMouth.ITEM_MODEL_SIZE - SILHOUETTE_INSET - rise);
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
}
