package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.ForgeBBItem;
import com.github.crittscott.somebuckets.item.ForgeSBItem;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
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
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "fluid_handler");

    private final LazyOptional<IFluidHandlerItem> opt;

    public FluidProvider(NonNullSupplier<IFluidHandlerItem> handlerFactory) {
        this.opt = LazyOptional.of(handlerFactory);
    }

    /** Attaches one stack-bound handler to each fluid-capable Some Buckets item stack. */
    public static void attach(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        NonNullSupplier<IFluidHandlerItem> factory;
        if (stack.getItem() instanceof ForgeBBItem) {
            factory = () -> new BBFluidHandler(stack);
        } else if (stack.getItem() instanceof ForgeSBItem) {
            factory = () -> new SBFluidHandler(stack);
        } else {
            return;
        }

        FluidProvider provider = new FluidProvider(factory);
        event.addCapability(ID, provider);
        event.addListener(provider::invalidate);
    }

    private void invalidate() {
        opt.invalidate();
    }

    /** @return the fluid handler capability cast to {@code T}, or empty for any other capability */
    @Nonnull @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER_ITEM) return opt.cast();
        return LazyOptional.empty();
    }
}
