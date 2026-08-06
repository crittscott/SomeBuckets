package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

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

        NBTUtil.Mode mode = NBTUtil.getMode(container);
        if (mode != NBTUtil.Mode.FLUID) return FluidStack.EMPTY;

        return NBTUtil.getFluidStack(container);
    }

    @Override
    public final int fill(FluidStack resource, FluidAction action) {
        if (!canAcceptFluid(resource)) return 0;

        NBTUtil.Mode mode = NBTUtil.getMode(container);

        if (mode == NBTUtil.Mode.NONE) {
            return fillEmpty(resource, action);
        } else if (mode == NBTUtil.Mode.FLUID) {
            FluidStack current = NBTUtil.getFluidStack(container);
            if (current.isEmpty()) {
                return fillEmpty(resource, action);
            } else if (current.isFluidEqual(resource)) {
                return fillExisting(resource, current, action);
            }
        }
        return 0;
    }

    @Override
    public final FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        FluidStack current = getFluidInTank(0);
        if (current.isEmpty() || !current.isFluidEqual(resource)) return FluidStack.EMPTY;
        return performDrain(resource, action);
    }

    @Override
    public final FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack current = getFluidInTank(0);
        if (current.isEmpty()) return FluidStack.EMPTY;
        return performDrain(new FluidStack(current.getFluid(), maxDrain, current.getTag()), action);
    }

    // Abstract methods for subclass-specific behavior
    protected abstract int fillEmpty(FluidStack resource, FluidAction action);
    protected abstract int fillExisting(FluidStack resource, FluidStack current, FluidAction action);
    protected abstract FluidStack performDrain(FluidStack resource, FluidAction action);

    protected boolean canAcceptFluid(FluidStack resource) {
        return !resource.isEmpty();
    }
}
