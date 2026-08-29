package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.world.item.ItemStack;

/** Forge Source Bucket item shell providing stack-aware crafting remainder hooks. */
public final class ForgeSBItem extends SBItem {
    public ForgeSBItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return !NBTUtil.isEmptyBucket(stack);
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return getUnitRemainder(stack);
    }
}
