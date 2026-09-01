package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Single-tank item capability for mode-based Some Buckets fluid storage.
 *
 * <p>The base class rejects tank indices other than zero, exposes content only in fluid mode, and
 * performs mode and fluid-equality dispatch. Subclasses define finite or infinite capacity and
 * mutation policy through the protected hooks while preserving NeoForge simulation semantics.
 */
public abstract class AbstractFluidHandler implements IFluidHandlerItem {
    protected final ItemStack container;

    public AbstractFluidHandler(ItemStack container) {
        this.container = container;
    }

    @Override
    public final ItemStack getContainer() {
        return container;
    }

    @Override
    public final int getTanks() {
        return 1;
    }

    @Override
    public final boolean isFluidValid(int tank, FluidStack stack) {
        if (tank != 0) return false;
        return canAcceptFluid(stack);
    }

    @Override
    public final FluidStack getFluidInTank(int tank) {
        if (tank != 0) return FluidStack.EMPTY;

        BucketState.Mode mode = BucketState.getMode(container);
        if (mode != BucketState.Mode.FLUID) return FluidStack.EMPTY;

        return NeoForgeFluidStacks.get(container);
    }

    @Override
    public final int fill(FluidStack resource, FluidAction action) {
        if (!canAcceptFluid(resource)) return 0;

        BucketState.Mode mode = BucketState.getMode(container);

        if (mode == BucketState.Mode.NONE) {
            return fillEmpty(resource, action);
        } else if (mode == BucketState.Mode.FLUID) {
            FluidStack current = NeoForgeFluidStacks.get(container);
            if (current.isEmpty()) {
                return fillEmpty(resource, action);
            } else if (NeoForgeFluidStacks.sameFluid(current, resource)) {
                return fillExisting(resource, current, action);
            }
        }
        return 0;
    }

    @Override
    public final FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        FluidStack current = getFluidInTank(0);
        if (current.isEmpty() || !NeoForgeFluidStacks.sameFluid(current, resource)) return FluidStack.EMPTY;
        return performDrain(resource, action);
    }

    @Override
    public final FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack current = getFluidInTank(0);
        if (current.isEmpty()) return FluidStack.EMPTY;
        return performDrain(NeoForgeFluidStacks.resized(current, maxDrain), action);
    }

    /**
     * Accepts fluid into an empty container.
     *
     * <p>The resource is nonempty and has passed {@link #canAcceptFluid}; the container is in
     * {@code NONE} mode or has an empty fluid payload. A simulated action must report the same
     * accepted amount without mutating {@link #container}. An executing action may mutate it.
     *
     * @return amount accepted, from zero through {@code resource.getAmount()}
     */
    protected abstract int fillEmpty(FluidStack resource, FluidAction action);

    /**
     * Accepts fluid into an existing compatible payload.
     *
     * <p>{@code current} is nonempty and fluid-equal to the accepted, nonempty {@code resource}.
     * A simulated action must not mutate {@link #container}; an executing action may apply the
     * subclass's finite or infinite sink policy.
     *
     * @return amount accepted, from zero through {@code resource.getAmount()}
     */
    protected abstract int fillExisting(FluidStack resource, FluidStack current, FluidAction action);

    /**
     * Drains a requested amount from the current compatible fluid payload.
     *
     * <p>The base class has established that the container holds a nonempty fluid equal to
     * {@code resource}. Simulation must return what execution can supply without mutating the
     * container. Execution owns any finite debit; infinite handlers may return fluid unchanged.
     *
     * @return fluid actually supplied, matching the stored content and never exceeding the request
     */
    protected abstract FluidStack performDrain(FluidStack resource, FluidAction action);

    /**
     * Applies subclass admission policy before the base class checks container mode and contents.
     * Returning {@code false} makes the fill invalid and accepts nothing.
     */
    protected boolean canAcceptFluid(FluidStack resource) {
        return !resource.isEmpty();
    }
}
