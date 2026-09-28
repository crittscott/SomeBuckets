package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.config.ServerConfig;
import com.github.crittscott.somebuckets.crafting.EmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.SpawnEggIngredient;
import com.github.crittscott.somebuckets.data.BucketLootModifierProvider;
import com.github.crittscott.somebuckets.fluid.FluidProvider;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.NeoForgeBucketOperations;
import com.github.crittscott.somebuckets.register.ModCreativeTabs;
import com.github.crittscott.somebuckets.register.ModDataComponents;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.register.ModSounds;
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
 * then registers dispenser behaviors and cauldron interactions once the mod bus reaches the
 * common-setup phase.
 */
@Mod(SomeBuckets.MODID)
public final class SomeBucketsNeoForge {

    /**
     * Installs NeoForge platform services and registers config, content, and lifecycle listeners.
     *
     * @param modEventBus mod lifecycle event bus
     * @param modContainer owning mod container
     */
    public SomeBucketsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        BucketOperations.install(new NeoForgeBucketOperations());

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.addListener(this::configLoaded);
        modEventBus.addListener(this::configReloaded);
        modEventBus.addListener(this::gatherData);

        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        EmptyBucketIngredient.register(modEventBus);
        SpawnEggIngredient.register(modEventBus);
        FluidProvider.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void configLoaded(final ModConfigEvent.Loading event) {
        refreshSourceBucketPolicy(event.getConfig(), false);
    }

    private void configReloaded(final ModConfigEvent.Reloading event) {
        refreshSourceBucketPolicy(event.getConfig(), true);
    }

    private static void refreshSourceBucketPolicy(ModConfig config, boolean reload) {
        if (config.getSpec() == ServerConfig.SPEC) {
            SBPolicy.refresh(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(),
                    config.getFileName(), reload);
        }
    }

    private void gatherData(GatherDataEvent.Client event) {
        event.createProvider(BucketLootModifierProvider::new);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SomeBuckets.registerBehaviors(ModItems.BIG_BUCKET_8.get(), ModItems.BIG_BUCKET_64.get(),
                    ModItems.SOURCE_BUCKET.get(), ModItems.MOB_BUCKET.get(), ModItems.JUNK_BUCKET.get(),
                    ModItems.TRASH_BUCKET.get(), "NeoForge");
        });
    }
}
