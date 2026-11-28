package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.component.DataComponentPatch;

/**
 * Converts between the loader-neutral {@link StoredFluid} value and Fabric's {@link FluidVariant}
 * variants, and droplet amounts to and from whole millibuckets. Both values carry variant data as a
 * {@link DataComponentPatch}, so the variant conversion is lossless.
 */
public final class FabricFluidVariants {
    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / FluidBucketItem.BUCKET_VOLUME_MB;

    private FabricFluidVariants() {}

    /** The droplet amount of {@code mb} millibuckets. */
    public static long droplets(int mb) {
        return (long) mb * DROPLETS_PER_MB;
    }

    /**
     * Builds a variant for a stored fluid.
     *
     * @param stored the loader-neutral fluid value
     * @return the matching {@link FluidVariant}, or {@link FluidVariant#blank()} when empty
     */
    public static FluidVariant toVariant(StoredFluid stored) {
        return stored.isEmpty() ? FluidVariant.blank() : FluidVariant.of(stored.fluid(), stored.components());
    }

    /** Converts a nonblank Fabric variant and millibucket amount to the common value. */
    public static StoredFluid stored(FluidVariant variant, int amountMb) {
        return variant.isBlank() || amountMb <= 0 ? StoredFluid.EMPTY
                : new StoredFluid(variant.getFluid(), amountMb, variant.getComponents());
    }

    /** Whole millibuckets in a droplet amount, capped to the int range the item rules work in. */
    public static int wholeMb(long droplets) {
        return (int) Math.min(Integer.MAX_VALUE, droplets / DROPLETS_PER_MB);
    }
}
