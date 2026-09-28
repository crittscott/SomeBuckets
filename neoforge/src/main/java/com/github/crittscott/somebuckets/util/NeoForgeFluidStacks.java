package com.github.crittscott.somebuckets.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Boundary conversions between NeoForge's fluid value and the loader-neutral persisted
 * representation. Both carry variant data as a {@link DataComponentPatch}, so the conversion is
 * lossless.
 */
public final class NeoForgeFluidStacks {
    private NeoForgeFluidStacks() {}

    /**
     * Reads the container's persisted fluid as a NeoForge {@link FluidStack}.
     *
     * @param stack the bucket stack to read
     * @return the persisted fluid, or {@link FluidStack#EMPTY} when none is stored
     */
    public static FluidStack get(ItemStack stack) {
        return of(BucketState.getStoredFluid(stack));
    }

    /**
     * Converts a NeoForge {@link FluidStack} to the loader-neutral fluid value.
     *
     * @param fluidStack the fluid stack to convert
     * @return the stored fluid value, or {@link StoredFluid#EMPTY} when {@code fluidStack} is empty
     */
    public static StoredFluid stored(FluidStack fluidStack) {
        return fluidStack.isEmpty() ? StoredFluid.EMPTY
                : new StoredFluid(fluidStack.getFluid(), fluidStack.getAmount(), fluidStack.getComponentsPatch());
    }

    /**
     * Builds the NeoForge stack for a stored fluid value.
     *
     * @param stored the loader-neutral fluid value
     * @return the assembled fluid stack, or {@link FluidStack#EMPTY} when {@code stored} is empty
     */
    public static FluidStack of(StoredFluid stored) {
        if (stored.isEmpty()) return FluidStack.EMPTY;
        FluidStack fluidStack = new FluidStack(stored.fluid(), stored.amount());
        fluidStack.applyComponents(stored.components());
        return fluidStack;
    }
}
