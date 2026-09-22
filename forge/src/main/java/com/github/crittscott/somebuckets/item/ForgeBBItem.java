package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;

/** Forge finite-bucket item shell providing stack-aware crafting remainder hooks. */
public final class ForgeBBItem extends BBItem {
    public ForgeBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    public ItemStack getCraftingRemainder(ItemStack stack) {
        return getUnitRemainder(stack);
    }
}
