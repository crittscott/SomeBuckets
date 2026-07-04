package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class SBFluidHandler extends AbstractFluidHandler {

    public SBFluidHandler(ItemStack container) {
        super(container);
    }

    @Override
    public int getTankCapacity(int tank) {
        return 1000; // Always reports 1 bucket capacity
    }

    @Override
    protected int fillEmpty(FluidStack resource, FluidAction action) {
        int toFill = Math.min(1000, resource.getAmount());

        if (toFill > 0 && action.execute()) {
            // Store the fluid type, but SB acts as infinite source so always shows 1000
            NBTUtil.setFluidStack(container, new FluidStack(resource.getFluid(), 1000, resource.getTag()));
        }
        return toFill;
    }

    @Override
    protected int fillExisting(FluidStack resource, FluidStack current, FluidAction action) {
        // SB is infinite sink for same fluid type - accept any amount
        if (current.isFluidEqual(resource)) {
            return Math.min(1000, resource.getAmount());
        }
        return 0;
    }

    @Override
    protected FluidStack performDrain(FluidStack resource, FluidAction action) {
        FluidStack current = NBTUtil.getFluidStack(container);
        if (current.isEmpty()) return FluidStack.EMPTY;

        int toDrain = Math.min(1000, resource.getAmount());

        // SB is infinite source - don't modify container, just return the drained amount
        return new FluidStack(current.getFluid(), toDrain, current.getTag());
    }
}
