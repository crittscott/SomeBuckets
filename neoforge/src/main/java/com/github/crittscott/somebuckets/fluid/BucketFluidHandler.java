package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Single-tank item fluid capability for both Some Buckets fluid items, exposing content only in
 * fluid mode and dispatching on container mode and fluid equality.
 *
 * <p>A finite Big or Huge Bucket reports its tier capacity and persists executed fills and drains in
 * the container's shared component format. A Source Bucket reports one bucket-volume, admits only
 * fluids the {@link SBPolicy} allowlist permits, stores nothing beyond the assigned identity, and
 * acts as an infinite source and sink without depletion. Simulated actions never mutate the stack.
 */
public final class BucketFluidHandler implements IFluidHandlerItem {
    private final ItemStack container;
    private final boolean source;

    public BucketFluidHandler(ItemStack container) {
        this.container = container;
        this.source = container.getItem() instanceof SBItem;
    }

    @Override
    public ItemStack getContainer() {
        return container;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank != 0) return 0;
        return source ? FluidType.BUCKET_VOLUME : ((BBItem) container.getItem()).getCapacityMb();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && canAcceptFluid(stack);
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0) return FluidStack.EMPTY;
        if (BucketState.getMode(container) != BucketState.Mode.FLUID) return FluidStack.EMPTY;
        return NeoForgeFluidStacks.get(container);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (!canAcceptFluid(resource)) return 0;

        BucketState.Mode mode = BucketState.getMode(container);
        if (mode == BucketState.Mode.NONE) {
            return fillEmpty(resource, action);
        }
        if (mode == BucketState.Mode.FLUID) {
            FluidStack current = NeoForgeFluidStacks.get(container);
            if (current.isEmpty()) return fillEmpty(resource, action);
            if (NeoForgeFluidStacks.sameFluid(current, resource)) {
                return fillExisting(resource, current, action);
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        FluidStack current = getFluidInTank(0);
        if (current.isEmpty() || !NeoForgeFluidStacks.sameFluid(current, resource)) return FluidStack.EMPTY;
        return performDrain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack current = getFluidInTank(0);
        if (current.isEmpty()) return FluidStack.EMPTY;
        return performDrain(NeoForgeFluidStacks.resized(current, maxDrain), action);
    }

    private int fillEmpty(FluidStack resource, FluidAction action) {
        int toFill = Math.min(getTankCapacity(0), resource.getAmount());
        if (toFill > 0 && action.execute()) {
            // A Source Bucket keeps only the identity and always shows one bucket-volume.
            int stored = source ? FluidType.BUCKET_VOLUME : toFill;
            NeoForgeFluidStacks.set(container, NeoForgeFluidStacks.resized(resource, stored));
        }
        return toFill;
    }

    private int fillExisting(FluidStack resource, FluidStack current, FluidAction action) {
        if (source) {
            return Math.min(FluidType.BUCKET_VOLUME, resource.getAmount());
        }
        int currentAmount = current.getAmount();
        int toFill = Math.min(getTankCapacity(0) - currentAmount, resource.getAmount());
        if (toFill > 0 && action.execute()) {
            NeoForgeFluidStacks.set(container, NeoForgeFluidStacks.resized(current, currentAmount + toFill));
        }
        return toFill;
    }

    private FluidStack performDrain(FluidStack resource, FluidAction action) {
        FluidStack current = NeoForgeFluidStacks.get(container);
        if (source) {
            if (!SBPolicy.allows(current.getFluid())) return FluidStack.EMPTY;
            return NeoForgeFluidStacks.resized(current, Math.min(FluidType.BUCKET_VOLUME, resource.getAmount()));
        }
        if (current.isEmpty()) return FluidStack.EMPTY;
        ItemStack drainTarget = action.execute() ? container : container.copy();
        int drainedAmount = BucketState.drainFiniteContent(drainTarget, resource.getAmount());
        return drainedAmount <= 0
                ? FluidStack.EMPTY
                : NeoForgeFluidStacks.resized(current, drainedAmount);
    }

    private boolean canAcceptFluid(FluidStack resource) {
        if (resource.isEmpty()) return false;
        return !source || SBPolicy.allows(resource.getFluid());
    }
}
