package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.fuel.BucketFuel;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import javax.annotation.Nullable;

/** Forge finite-bucket item shell providing stack-aware crafting remainder hooks and the lava-fuel burn time. */
public final class ForgeBBItem extends BBItem {
    public ForgeBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    // Forge's item burn-time hook returns -1 for "use the vanilla fuel map", which has no entry for
    // this item, so super covers every non-lava case.
    @Override
    public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType) {
        return BucketFuel.isLavaFuel(itemStack)
                ? FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS : super.getBurnTime(itemStack, recipeType);
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return getUnitRemainder(stack);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return !BucketState.isEmptyBucket(stack);
    }
}
