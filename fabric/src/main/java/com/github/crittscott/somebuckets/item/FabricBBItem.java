package com.github.crittscott.somebuckets.item;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import javax.annotation.Nullable;

/** Fabric shell providing the stack-aware recipe remainder for a finite bucket. */
public final class FabricBBItem extends BBItem implements FabricItem {
    /** Creates a Fabric finite bucket with the given whole-bucket capacity. */
    public FabricBBItem(Item.Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    @Nullable
    public ItemStackTemplate getCraftingRemainder(ItemStack stack) {
        return unitRemainderTemplate(stack);
    }
}
