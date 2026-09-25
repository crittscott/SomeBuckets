package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.component.DataComponentPatch;

/**
 * Converts between the loader-neutral {@link StoredFluid} value and Fabric's {@link FluidVariant}.
 * Both carry variant data as a {@link DataComponentPatch}, so the conversion is lossless.
 */
public final class FabricFluidVariants {
    private FabricFluidVariants() {}

    /**
     * Builds a variant for a stored fluid.
     *
     * @param stored the loader-neutral fluid value
     * @return the matching {@link FluidVariant}, or {@link FluidVariant#blank()} when empty
     */
    public static FluidVariant toVariant(StoredFluid stored) {
        return stored.isEmpty() ? FluidVariant.blank() : FluidVariant.of(stored.fluid(), stored.components());
    }
}
