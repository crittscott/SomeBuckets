package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.world.item.ItemStack;

/** Forge finite-bucket item shell providing stack-aware crafting remainder hooks. */
public final class ForgeBBItem extends BBItem {
    public ForgeBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return !BucketState.isEmptyBucket(stack);
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return getUnitRemainder(stack);
    }
}
