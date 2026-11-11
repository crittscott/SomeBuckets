package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStackTemplate;

import javax.annotation.Nullable;

/** NeoForge item shell providing the stack-aware crafting remainder for a Source Bucket. */
public final class NeoForgeSBItem extends SBItem {
    /** Creates a NeoForge Source Bucket. */
    public NeoForgeSBItem(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        return unitRemainderTemplate(instance);
    }
}
