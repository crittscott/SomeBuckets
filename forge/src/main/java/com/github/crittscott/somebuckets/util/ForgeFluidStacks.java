package com.github.crittscott.somebuckets.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Boundary conversions between Forge's fluid value and the loader-neutral persisted representation.
 *
 * <p>Forge's {@link FluidStack} carries its variant payload as a {@link CompoundTag}, matching
 * {@link StoredFluid} directly, so the bridge is a plain field copy. The helper surface mirrors
 * {@code neoforge/.../util/NeoForgeFluidStacks} so fluid-logic changes stay diff-clean between the
 * two loaders.
 */
public final class ForgeFluidStacks {
    private ForgeFluidStacks() {}

    /**
     * Reads the container's persisted fluid as a Forge {@link FluidStack}.
     *
     * @param stack the bucket stack to read
     * @return the persisted fluid, or {@link FluidStack#EMPTY} when none is stored
     */
    public static FluidStack get(ItemStack stack) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        return stored.isEmpty() ? FluidStack.EMPTY
                : of(stored.fluid(), stored.amount(), stored.variantTag());
    }

    /**
     * Writes a Forge {@link FluidStack} into the container's persisted fluid schema.
     *
     * @param stack the bucket stack to mutate in place
     * @param fluidStack the fluid to store; an empty stack clears the persisted fluid
     */
    public static void set(ItemStack stack, FluidStack fluidStack) {
        NBTUtil.setStoredFluid(stack, fluidStack.isEmpty() ? StoredFluid.EMPTY
                : new StoredFluid(fluidStack.getFluid(), fluidStack.getAmount(), variantTag(fluidStack)));
    }

    /**
     * Builds a stack for a bare fluid plus an optional stored variant payload.
     *
     * @param fluid the fluid identity
     * @param amount amount in millibuckets
     * @param variantTag optional variant NBT, or {@code null}
     * @return the assembled fluid stack
     */
    public static FluidStack of(Fluid fluid, int amount, @Nullable CompoundTag variantTag) {
        return new FluidStack(fluid, amount, variantTag);
    }

    /**
     * Copies {@code src} with a new amount, preserving fluid identity and variant payload.
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
     * Tests whether two stacks hold the same fluid with an equal variant payload.
     *
     * @param a first stack
     * @param b second stack
     * @return {@code true} when fluid identity and variant NBT match
     */
    public static boolean sameFluid(FluidStack a, FluidStack b) {
        return a.isFluidEqual(b);
    }

    /**
     * Returns a stack's variant payload.
     *
     * @param fluidStack the stack to read
     * @return the detached variant NBT, or {@code null} when it has none
     */
    @Nullable
    public static CompoundTag variantTag(FluidStack fluidStack) {
        return fluidStack.getTag();
    }
}
