package com.github.crittscott.somebuckets.item;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.ItemStack;

/** Fabric shell providing the stack-aware recipe remainder for a Source Bucket. */
public final class FabricSBItem extends SBItem implements FabricItem {
    public FabricSBItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getRecipeRemainder(ItemStack stack) {
        return getUnitRemainder(stack);
    }
}
