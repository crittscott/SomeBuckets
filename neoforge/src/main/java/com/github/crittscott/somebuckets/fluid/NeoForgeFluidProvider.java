package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.register.NeoForgeItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Attaches each fluid-capable Some Buckets item its transactional
 * {@link net.neoforged.neoforge.transfer.ResourceHandler}.
 *
 * <p>Registration is keyed by item through {@link RegisterCapabilitiesEvent}. The provider is
 * invoked per stack, so no eager construction or invalidation bookkeeping is needed.
 */
public final class NeoForgeFluidProvider {
    private NeoForgeFluidProvider() {}

    /** Subscribes the capability registration to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeFluidProvider::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Fluid.ITEM,
                (stack, access) -> new NeoForgeBucketFluidHandler(access),
                NeoForgeItems.BIG_BUCKET_64.get(), NeoForgeItems.BIG_BUCKET_8.get());
        event.registerItem(Capabilities.Fluid.ITEM,
                (stack, access) -> new NeoForgeBucketFluidHandler(access),
                NeoForgeItems.SOURCE_BUCKET.get());
    }
}
