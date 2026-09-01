package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * NeoForge item fluid handler for a finite Big or Huge Bucket. It exposes one tank whose capacity
 * comes from the item tier and persists executed fills and drains in the container's shared NBT
 * format.
 */
public class BBFluidHandler extends AbstractFluidHandler {

    public BBFluidHandler(ItemStack container) {
        super(container);
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank != 0) return 0;
        return ((BBItem) container.getItem()).getCapacityMb();
    }

    @Override
    protected int fillEmpty(FluidStack resource, FluidAction action) {
        int capacity = getTankCapacity(0);
        int toFill = Math.min(capacity, resource.getAmount());

        if (toFill > 0 && action.execute()) {
            NeoForgeFluidStacks.set(container, NeoForgeFluidStacks.resized(resource, toFill));
        }
        return toFill;
    }

    @Override
    protected int fillExisting(FluidStack resource, FluidStack current, FluidAction action) {
        int capacity = getTankCapacity(0);
        int currentAmount = current.getAmount();
        int available = capacity - currentAmount;
        int toFill = Math.min(available, resource.getAmount());

        if (toFill > 0 && action.execute()) {
            NeoForgeFluidStacks.set(container, NeoForgeFluidStacks.resized(current, currentAmount + toFill));
        }
        return toFill;
    }

    @Override
    protected FluidStack performDrain(FluidStack resource, FluidAction action) {
        FluidStack current = NeoForgeFluidStacks.get(container);
        if (current.isEmpty()) return FluidStack.EMPTY;

        ItemStack drainTarget = action.execute() ? container : container.copy();
        int drainedAmount = BucketState.drainFiniteContent(drainTarget, resource.getAmount());
        return drainedAmount <= 0
                ? FluidStack.EMPTY
                : NeoForgeFluidStacks.resized(current, drainedAmount);
    }
}
