package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.fuel.BucketFuel;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import javax.annotation.Nullable;

/**
 * NeoForge Source Bucket item shell providing stack-aware crafting remainder hooks and the
 * lava-fuel burn time. An allowed-lava Source Bucket burns without depletion because its crafting
 * remainder is the unchanged bucket.
 */
public final class NeoForgeSBItem extends SBItem {
    public NeoForgeSBItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return !BucketState.isEmptyBucket(stack);
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return getUnitRemainder(stack);
    }

    // NeoForge's item burn-time hook must return a non-negative value; 0 means "not fuel". There is
    // no vanilla furnace-fuel entry for this item to fall back to, so 0 is the correct non-lava case.
    @Override
    public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType) {
        return BucketFuel.isLavaFuel(itemStack) ? FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS : 0;
    }
}
