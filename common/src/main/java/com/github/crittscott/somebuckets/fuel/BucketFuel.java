package com.github.crittscott.somebuckets.fuel;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

/** Shared stack-sensitive furnace-fuel policy for fluid buckets. */
public final class BucketFuel {
    private BucketFuel() {}

    public static boolean isLavaFuel(ItemStack stack) {
        if (!(stack.getItem() instanceof FluidBucketItem)
                || BucketState.getMode(stack) != BucketState.Mode.FLUID) return false;

        StoredFluid fluid = BucketState.getStoredFluid(stack);
        if (stack.getItem() instanceof SBItem && !SBPolicy.allows(fluid.fluid())) return false;
        return fluid.fluid() == Fluids.LAVA
                && fluid.amount() >= FluidBucketItem.BUCKET_VOLUME_MB;
    }
}
