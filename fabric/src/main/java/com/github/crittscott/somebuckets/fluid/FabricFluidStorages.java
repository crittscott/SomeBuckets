package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.register.FabricItems;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;

/** Registers Some Buckets item storage providers with Fabric Transfer API. */
public final class FabricFluidStorages {
    private FabricFluidStorages() {}

    public static void register() {
        FluidStorage.ITEM.registerForItems(
                (stack, context) -> FabricBucketStorage.finite(context, FabricItems.BIG_BUCKET_8),
                FabricItems.BIG_BUCKET_8);
        FluidStorage.ITEM.registerForItems(
                (stack, context) -> FabricBucketStorage.finite(context, FabricItems.BIG_BUCKET_64),
                FabricItems.BIG_BUCKET_64);
        FluidStorage.ITEM.registerForItems(
                (stack, context) -> FabricBucketStorage.source(context), FabricItems.SOURCE_BUCKET);
    }
}
