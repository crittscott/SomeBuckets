package com.github.crittscott.somebuckets.util;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Converts between Forge's fluid value and the loader-neutral persisted representation. */
public final class ForgeFluidStacks {
    private ForgeFluidStacks() {}

    public static FluidStack get(ItemStack stack) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        return stored.isEmpty() ? FluidStack.EMPTY
                : new FluidStack(stored.fluid(), stored.amount(), stored.variantTag());
    }

    public static void set(ItemStack stack, FluidStack fluidStack) {
        NBTUtil.setStoredFluid(stack, fluidStack.isEmpty() ? StoredFluid.EMPTY
                : new StoredFluid(fluidStack.getFluid(), fluidStack.getAmount(), fluidStack.getTag()));
    }
}
