package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.fluid.ForgeBucketFluidHandler;
import com.github.crittscott.somebuckets.fluid.ForgeFluidProvider;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;

/** Forge shell providing capability and stack-aware remainder hooks for a finite bucket. */
public final class ForgeBBItem extends BBItem {
    public ForgeBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ForgeFluidProvider(() -> new ForgeBucketFluidHandler(stack));
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
