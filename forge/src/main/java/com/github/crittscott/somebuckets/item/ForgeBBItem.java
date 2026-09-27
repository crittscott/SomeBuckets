package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import javax.annotation.Nullable;

/**
 * Forge finite-bucket item shell providing stack-aware crafting remainder hooks and the lava-fuel
 * burn time.
 */
public final class ForgeBBItem extends BBItem {
    /** Creates a Forge finite bucket with the given whole-bucket capacity. */
    public ForgeBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    public ItemStack getCraftingRemainder(ItemStack stack) {
        return getUnitRemainder(stack);
    }

    // There is no vanilla furnace-fuel entry for this item to fall back to, so 0 is the correct
    // non-lava case.
    @Override
    public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType) {
        return FluidBucketItem.isLavaFuel(itemStack) ? FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS : 0;
    }
}
