package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;

/** Forge shell providing the stack-aware max-stack-size hook for the Mob Bucket. */
public final class ForgeMBItem extends MBItem {
    public ForgeMBItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return variableMaxStackSize(stack);
    }
}
