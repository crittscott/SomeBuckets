package com.github.crittscott.somebuckets.util;

import net.minecraftforge.fluids.FluidStack;

/** Converts between Forge's fluid value and the loader-neutral {@link StoredFluid}. */
public final class ForgeFluidStacks {
    private ForgeFluidStacks() {}

    /** The Forge fluid stack for {@code fluid}, or {@link FluidStack#EMPTY} for empty content. */
    public static FluidStack of(StoredFluid fluid) {
        return fluid.isEmpty() ? FluidStack.EMPTY
                : new FluidStack(fluid.fluid(), fluid.amount(), fluid.variantTag());
    }

    /** The loader-neutral value of {@code stack}, or {@link StoredFluid#EMPTY} for an empty stack. */
    public static StoredFluid stored(FluidStack stack) {
        return stack.isEmpty() ? StoredFluid.EMPTY
                : new StoredFluid(stack.getFluid(), stack.getAmount(), stack.getTag());
    }

    /** A copy of {@code stack} holding {@code amount} millibuckets. */
    public static FluidStack resized(FluidStack stack, int amount) {
        return new FluidStack(stack.getFluid(), amount, stack.getTag());
    }
}
