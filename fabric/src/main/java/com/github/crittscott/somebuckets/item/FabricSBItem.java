package com.github.crittscott.somebuckets.item;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import javax.annotation.Nullable;

/** Fabric shell providing the stack-aware recipe remainder for a Source Bucket. */
public final class FabricSBItem extends SBItem implements FabricItem {
    /** Creates a Fabric Source Bucket. */
    public FabricSBItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public ItemStackTemplate getCraftingRemainder(ItemStack stack) {
        return unitRemainderTemplate(stack);
    }
}
