package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.fluid.ForgeBucketFluidHandler;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;

/** Forge shell providing capability, stack-aware remainder, and lava burn-time hooks for a Source Bucket. */
public final class ForgeSBItem extends SBItem {
    public ForgeSBItem(Properties properties) {
        super(properties);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ForgeBucketFluidHandler(stack);
    }

    /** Lava fuel as {@link FluidBucketItem#isLavaFuel} decides; otherwise Forge's default applies. */
    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return FluidBucketItem.isLavaFuel(stack) ? FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS
                : super.getBurnTime(stack, recipeType);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return variableMaxStackSize(stack);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return !BucketState.isEmptyBucket(stack);
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return getUnitRemainder(stack);
    }
}
