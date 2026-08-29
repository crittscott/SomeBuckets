package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * NeoForge item fluid handler for a Source Bucket. An empty bucket accepts one allowed fluid
 * identity; once assigned, it accepts matching input as an infinite sink and supplies that fluid
 * without depletion. The exposed tank has one bucket-volume of nominal capacity.
 */
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
            NeoForgeFluidStacks.set(container, NeoForgeFluidStacks.resized(resource, FluidType.BUCKET_VOLUME));
        }
        return toFill;
    }

    @Override
    protected int fillExisting(FluidStack resource, FluidStack current, FluidAction action) {
        // SB is infinite sink for same fluid type - accept any amount
        if (NeoForgeFluidStacks.sameFluid(current, resource)) {
            return Math.min(FluidType.BUCKET_VOLUME, resource.getAmount());
        }
        return 0;
    }

    @Override
    protected FluidStack performDrain(FluidStack resource, FluidAction action) {
        FluidStack current = NeoForgeFluidStacks.get(container);
        if (!SBPolicy.allows(current.getFluid())) return FluidStack.EMPTY;

        int toDrain = Math.min(FluidType.BUCKET_VOLUME, resource.getAmount());

        // SB is infinite source - don't modify container, just return the drained amount
        return NeoForgeFluidStacks.resized(current, toDrain);
    }

    @Override
    protected boolean canAcceptFluid(FluidStack resource) {
        return !resource.isEmpty() && SBPolicy.allows(resource.getFluid());
    }
}
