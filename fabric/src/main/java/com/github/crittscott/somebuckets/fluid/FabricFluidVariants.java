package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

/** Converts between Fabric's fluid variant and the loader-neutral {@link StoredFluid}. */
public final class FabricFluidVariants {
    private FabricFluidVariants() {}

    /** The Fabric variant of {@code fluid}, or the blank variant for empty content. */
    public static FluidVariant toVariant(StoredFluid fluid) {
        return fluid.isEmpty() ? FluidVariant.blank() : FluidVariant.of(fluid.fluid(), fluid.variantTag());
    }

    /** {@code amountMb} millibuckets of {@code variant}, or {@link StoredFluid#EMPTY} for a blank variant. */
    public static StoredFluid stored(FluidVariant variant, int amountMb) {
        return variant.isBlank() ? StoredFluid.EMPTY
                : new StoredFluid(variant.getFluid(), amountMb, variant.copyNbt());
    }
}
