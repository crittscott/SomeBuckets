package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

public class SBFluidHandler extends AbstractFluidHandler {

    public SBFluidHandler(ItemStack container) {
        super(container);
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank != 0) return 0;
        return FluidType.BUCKET_VOLUME; // Always reports one bucket of capacity.
    }

    @Override
    protected int fillEmpty(FluidStack resource, FluidAction action) {
        int toFill = Math.min(FluidType.BUCKET_VOLUME, resource.getAmount());

        if (toFill > 0 && action.execute()) {
            // Store the fluid type, but SB acts as an infinite source and always shows one bucket.
            NBTUtil.setFluidStack(container, new FluidStack(resource.getFluid(), FluidType.BUCKET_VOLUME, resource.getTag()));
        }
        return toFill;
    }

    @Override
    protected int fillExisting(FluidStack resource, FluidStack current, FluidAction action) {
        // SB is infinite sink for same fluid type - accept any amount
        if (current.isFluidEqual(resource)) {
            return Math.min(FluidType.BUCKET_VOLUME, resource.getAmount());
        }
        return 0;
    }

    @Override
    protected FluidStack performDrain(FluidStack resource, FluidAction action) {
        FluidStack current = NBTUtil.getFluidStack(container);
        if (!SBPolicy.allows(current)) return FluidStack.EMPTY;

        int toDrain = Math.min(FluidType.BUCKET_VOLUME, resource.getAmount());

        // SB is infinite source - don't modify container, just return the drained amount
        return new FluidStack(current.getFluid(), toDrain, current.getTag());
    }

    @Override
    protected boolean canAcceptFluid(FluidStack resource) {
        return SBPolicy.allows(resource);
    }
}
