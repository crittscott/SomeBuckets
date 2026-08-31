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

    /** Reads the container's persisted fluid as a Forge {@link FluidStack}. */
    public static FluidStack get(ItemStack stack) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        return stored.isEmpty() ? FluidStack.EMPTY
                : of(stored.fluid(), stored.amount(), stored.variantTag());
    }

    /** Writes a Forge {@link FluidStack} into the container's persisted fluid schema. */
    public static void set(ItemStack stack, FluidStack fluidStack) {
        NBTUtil.setStoredFluid(stack, fluidStack.isEmpty() ? StoredFluid.EMPTY
                : new StoredFluid(fluidStack.getFluid(), fluidStack.getAmount(), variantTag(fluidStack)));
    }

    /** Builds a stack for a bare fluid plus an optional stored variant payload. */
    public static FluidStack of(Fluid fluid, int amount, @Nullable CompoundTag variantTag) {
        return new FluidStack(fluid, amount, variantTag);
    }

    /** A copy of {@code src} with a new amount, preserving fluid identity and variant payload. */
    public static FluidStack resized(FluidStack src, int amount) {
        if (src.isEmpty()) return FluidStack.EMPTY;
        FluidStack copy = src.copy();
        copy.setAmount(amount);
        return copy;
    }

    /** Whether the two stacks hold the same fluid with an equal variant payload. */
    public static boolean sameFluid(FluidStack a, FluidStack b) {
        return a.isFluidEqual(b);
    }

    /** The stack's detached variant payload, or {@code null} when it has none. */
    @Nullable
    public static CompoundTag variantTag(FluidStack fluidStack) {
        return fluidStack.getTag();
    }
}
