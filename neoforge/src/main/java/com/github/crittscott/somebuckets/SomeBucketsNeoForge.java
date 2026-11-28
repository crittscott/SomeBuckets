package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.NeoForgeServerConfig;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.crafting.NeoForgeEmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.NeoForgeSpawnEggIngredient;
import com.github.crittscott.somebuckets.data.NeoForgeBucketLootModifierProvider;
import com.github.crittscott.somebuckets.fluid.NeoForgeFluidProvider;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.NeoForgeBucketOperations;
import com.github.crittscott.somebuckets.register.NeoForgeCreativeTabs;
import com.github.crittscott.somebuckets.register.NeoForgeDataComponents;
import com.github.crittscott.somebuckets.register.NeoForgeItems;
import com.github.crittscott.somebuckets.register.NeoForgeSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * NeoForge mod entry point. The constructor installs shared runtime services, registers the server
 * config and mod content on the mod event bus, and listens for config (re)load. {@link #commonSetup}
 * then registers dispenser behaviors and cauldron interactions, and logs the startup milestone,
 * once the mod bus reaches the common-setup phase.
 */
@Mod(SomeBuckets.MODID)
public final class SomeBucketsNeoForge {
    private final String version;

    /**
     * Installs NeoForge platform services and registers config, content, and lifecycle listeners.
     *
     * @param modEventBus mod lifecycle event bus
     * @param modContainer owning mod container
     */
    public SomeBucketsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        version = modContainer.getModInfo().getVersion().toString();
        BucketOperations.install(new NeoForgeBucketOperations());

        modContainer.registerConfig(ModConfig.Type.SYNCED, NeoForgeServerConfig.SPEC, "somebuckets-server.toml");
        modEventBus.addListener(this::configLoaded);
        modEventBus.addListener(this::configReloaded);
        modEventBus.addListener(this::gatherData);

        NeoForgeDataComponents.register(modEventBus);
        NeoForgeItems.register(modEventBus);
        NeoForgeSounds.register(modEventBus);
        NeoForgeCreativeTabs.register(modEventBus);
        NeoForgeEmptyBucketIngredient.register(modEventBus);
        NeoForgeSpawnEggIngredient.register(modEventBus);
        NeoForgeFluidProvider.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void configLoaded(final ModConfigEvent.Loading event) {
        refreshSourceBucketPolicy(event.getConfig(), false);
    }

    private void configReloaded(final ModConfigEvent.Reloading event) {
        refreshSourceBucketPolicy(event.getConfig(), true);
    }

    private static void refreshSourceBucketPolicy(ModConfig config, boolean reload) {
        if (config.getSpec() == NeoForgeServerConfig.SPEC) {
            SBPolicy.refresh(NeoForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(),
                    config.getFileName(), reload);
        }
    }

    private void gatherData(GatherDataEvent.Client event) {
        event.createProvider(NeoForgeBucketLootModifierProvider::new);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SomeBuckets.registerBehaviors();
            SomeBuckets.logStarted("NeoForge", version);
        });
    }
}
