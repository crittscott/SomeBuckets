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

    boolean isEmpty(ItemStack stack);
}
