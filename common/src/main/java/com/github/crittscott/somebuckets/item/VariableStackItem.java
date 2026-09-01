package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;

/**
 * Items that stack like a vanilla empty bucket (16) while empty and like a vanilla filled bucket
 * (1) once they hold any content. {@code BucketState} writes the matching {@code MAX_STACK_SIZE}
 * component from the serialized state whenever content changes.
 */
public interface VariableStackItem {
    int EMPTY_STACK_SIZE = 16;
    int FILLED_STACK_SIZE = 1;

    /** Pixel width of a full item-durability-style bar, shared by every content-bearing bucket. */
    int ITEM_BAR_WIDTH = 13;
    /** Fallback bar color used when content has no fluid-derived tint. */
    int DEFAULT_BUCKET_BAR_COLOR = 0x4A90E2;

    boolean isEmpty(ItemStack stack);
}
