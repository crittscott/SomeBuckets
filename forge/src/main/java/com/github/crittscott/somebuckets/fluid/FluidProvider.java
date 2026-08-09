package com.github.crittscott.somebuckets.fluid;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Capability provider handing Forge a bucket's {@link IFluidHandlerItem}. The wrapped
 * {@link LazyOptional} defers construction: {@code handlerFactory} is not invoked until the
 * capability is first requested.
 */
public class FluidProvider implements ICapabilityProvider {
    private final LazyOptional<IFluidHandlerItem> opt;

    public FluidProvider(NonNullSupplier<IFluidHandlerItem> handlerFactory) {
        this.opt = LazyOptional.of(handlerFactory);
    }

    /** @return the fluid handler capability cast to {@code T}, or empty for any other capability */
    @Nonnull @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER_ITEM) return opt.cast();
        return LazyOptional.empty();
    }
}
