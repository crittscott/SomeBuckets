package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;

/**
 * Items that stack like a vanilla empty bucket (16) while empty and like a vanilla filled bucket
 * (1) once they hold any content. Each loader supplies the actual max-stack-size hook; this only
 * computes the value.
 */
public interface VariableStackItem {
    int EMPTY_STACK_SIZE = 16;
    int FILLED_STACK_SIZE = 1;

    boolean isEmpty(ItemStack stack);

    default int variableMaxStackSize(ItemStack stack) {
        return isEmpty(stack) ? EMPTY_STACK_SIZE : FILLED_STACK_SIZE;
    }
}
