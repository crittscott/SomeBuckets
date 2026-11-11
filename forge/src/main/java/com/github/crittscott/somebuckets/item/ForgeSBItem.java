package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import javax.annotation.Nullable;

/** Forge item shell providing the stack-aware crafting remainder for a Source Bucket. */
public final class ForgeSBItem extends SBItem {
    /** Creates a Forge Source Bucket. */
    public ForgeSBItem(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public ItemStackTemplate getCraftingRemainder(ItemStack stack) {
        return unitRemainderTemplate(stack);
    }
}
