package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;

/** Forge Source Bucket item shell providing stack-aware crafting remainder hooks. */
public final class ForgeSBItem extends SBItem {
    public ForgeSBItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getCraftingRemainder(ItemStack stack) {
        return getUnitRemainder(stack);
    }
}
