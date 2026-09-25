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
     * Writes a NeoForge {@link FluidStack} into the container's persisted fluid schema.
     *
     * @param stack the bucket stack to mutate in place
     * @param fluidStack the fluid to store; an empty stack clears the persisted fluid
     */
    public static void set(ItemStack stack, FluidStack fluidStack) {
        BucketState.setStoredFluid(stack, fluidStack.isEmpty() ? StoredFluid.EMPTY
                : new StoredFluid(fluidStack.getFluid(), fluidStack.getAmount(), fluidStack.getComponentsPatch()));
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

    /**
     * Copies {@code src} with a new amount, preserving fluid identity and components.
     *
     * @param src the source stack
     * @param amount the new amount in millibuckets
     * @return the resized copy, or {@link FluidStack#EMPTY} when {@code src} is empty
     */
    public static FluidStack resized(FluidStack src, int amount) {
        if (src.isEmpty()) return FluidStack.EMPTY;
        FluidStack copy = src.copy();
        copy.setAmount(amount);
        return copy;
    }

    /**
     * Tests whether two stacks hold the same fluid with equal components.
     *
     * @param a first stack
     * @param b second stack
     * @return {@code true} when fluid identity and components match
     */
    public static boolean sameFluid(FluidStack a, FluidStack b) {
        return FluidStack.isSameFluidSameComponents(a, b);
    }
}
