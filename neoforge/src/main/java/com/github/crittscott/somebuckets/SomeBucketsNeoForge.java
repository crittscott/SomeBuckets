package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.compat.ftbchunks.FtbChunksProtection;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.config.ServerConfig;
import com.github.crittscott.somebuckets.crafting.EmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.SpawnEggIngredient;
import com.github.crittscott.somebuckets.fluid.FluidProvider;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Dispensers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.NeoForgeBucketOperations;
import com.github.crittscott.somebuckets.protection.AutomationPlayers;
import com.github.crittscott.somebuckets.protection.NeoForgeDispenserFakePlayer;
import com.github.crittscott.somebuckets.register.ModCreativeTabs;
import com.github.crittscott.somebuckets.register.ModDataComponents;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.register.ModLootModifiers;
import com.github.crittscott.somebuckets.register.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * NeoForge mod entry point. The constructor installs shared runtime services, registers the server
 * config and mod content on the mod event bus, and listens for config (re)load. {@link #commonSetup}
 * then registers dispenser behaviors and cauldron interactions once the mod bus reaches the
 * common-setup phase.
 */
@Mod(SomeBuckets.MODID)
public final class SomeBucketsNeoForge {

    public SomeBucketsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        AutomationPlayers.install(NeoForgeDispenserFakePlayer::get);
        BucketOperations.install(new NeoForgeBucketOperations());

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modEventBus.addListener(this::configLoaded);
        modEventBus.addListener(this::configReloaded);

        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModLootModifiers.register(modEventBus);
        ModSounds.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        EmptyBucketIngredient.register(modEventBus);
        SpawnEggIngredient.register(modEventBus);
        FluidProvider.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        if (ModList.get().isLoaded("ftbchunks")) {
            FtbChunksProtection.register();
        }

        SomeBuckets.LOGGER.info(
                "Some Buckets (NeoForge) initializing; content registered on the mod event bus "
                        + "(data components, items, sounds, loot modifiers, creative tab, "
                        + "ingredient serializers, fluid capability provider)");
    }

    private void configLoaded(final ModConfigEvent.Loading event) {
        refreshSourceBucketPolicy(event.getConfig());
    }

    private void configReloaded(final ModConfigEvent.Reloading event) {
        refreshSourceBucketPolicy(event.getConfig());
    }

    private static void refreshSourceBucketPolicy(ModConfig config) {
        if (config.getSpec() == ServerConfig.SPEC) {
            SBPolicy.refresh(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), config.getFileName());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Dispensers.register();

            // Register BB cauldron-map adapters; shared transitions also serve SB and dispensers.
            Cauldrons.register();

            SomeBuckets.LOGGER.info("Some Buckets: dispenser and cauldron interactions registered");
        });
    }
}
