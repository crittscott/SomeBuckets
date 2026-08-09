package com.github.crittscott.somebuckets.item;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.ItemStack;

/** Fabric shell providing the stack-aware recipe remainder for a finite bucket. */
public final class FabricBBItem extends BBItem implements FabricItem {
    public FabricBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    public ItemStack getRecipeRemainder(ItemStack stack) {
        return getUnitRemainder(stack);
    }
}
