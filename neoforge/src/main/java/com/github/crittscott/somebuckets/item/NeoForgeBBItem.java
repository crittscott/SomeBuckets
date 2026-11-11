package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStackTemplate;

import javax.annotation.Nullable;

/** NeoForge item shell providing the stack-aware crafting remainder for a finite bucket. */
public final class NeoForgeBBItem extends BBItem {
    /** Creates a NeoForge finite bucket with the given whole-bucket capacity. */
    public NeoForgeBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    @Nullable
    public ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        return unitRemainderTemplate(instance);
    }
}
