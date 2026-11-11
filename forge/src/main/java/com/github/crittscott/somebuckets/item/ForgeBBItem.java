package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import javax.annotation.Nullable;

/** Forge item shell providing the stack-aware crafting remainder for a finite bucket. */
public final class ForgeBBItem extends BBItem {
    /** Creates a Forge finite bucket with the given whole-bucket capacity. */
    public ForgeBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    @Nullable
    public ItemStackTemplate getCraftingRemainder(ItemStack stack) {
        return unitRemainderTemplate(stack);
    }
}
