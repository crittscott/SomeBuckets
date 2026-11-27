package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;

/** Forge shell providing the stack-aware max-stack-size hook for the Trash Bucket. */
public final class ForgeTBItem extends TBItem {
    public ForgeTBItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return variableMaxStackSize(stack);
    }
}
