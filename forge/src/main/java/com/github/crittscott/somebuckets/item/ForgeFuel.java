package com.github.crittscott.somebuckets.item;

import net.minecraft.world.item.ItemStack;

/** Forge-only fuel policy for fluid buckets; Forge's item hook provides no current fuel-values context. */
public final class ForgeFuel {
    public static final int LAVA_BUCKET_BURN_TIME_TICKS = 20_000;

    private ForgeFuel() {}

    public static int burnTime(ItemStack stack) {
        return FluidBucketItem.isLavaFuel(stack) ? LAVA_BUCKET_BURN_TIME_TICKS : 0;
    }
}
