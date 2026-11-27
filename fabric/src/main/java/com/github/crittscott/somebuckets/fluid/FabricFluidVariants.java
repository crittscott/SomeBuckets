package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

/**
 * Converts between Fabric's fluid values and the loader-neutral {@link StoredFluid}: variants, and
 * Fabric's droplet amounts to and from whole millibuckets.
 */
public final class FabricFluidVariants {
    /** Fabric droplets in one millibucket. */
    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / FluidBucketItem.BUCKET_VOLUME_MB;

    private FabricFluidVariants() {}

    /** {@code mb} millibuckets in droplets. */
    public static long droplets(int mb) {
        return mb * DROPLETS_PER_MB;
    }

    /** {@code amountMb} millibuckets of {@code variant}, or {@link StoredFluid#EMPTY} for a blank variant. */
    public static StoredFluid stored(FluidVariant variant, int amountMb) {
        return variant.isBlank() ? StoredFluid.EMPTY
                : new StoredFluid(variant.getFluid(), amountMb, variant.copyNbt());
    }

    /** The Fabric variant of {@code fluid}, or the blank variant for empty content. */
    public static FluidVariant toVariant(StoredFluid fluid) {
        return fluid.isEmpty() ? FluidVariant.blank() : FluidVariant.of(fluid.fluid(), fluid.variantTag());
    }

    /** Whole millibuckets in a droplet amount, capped to the int range the item rules work in. */
    public static int wholeMb(long droplets) {
        return (int) Math.min(Integer.MAX_VALUE, droplets / DROPLETS_PER_MB);
    }
}
