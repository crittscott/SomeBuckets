package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.compat.ftbchunks.FtbChunksProtection;
import com.github.crittscott.somebuckets.config.ServerConfig;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.crafting.EmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.SpawnEggIngredient;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Dispensers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.ForgeBucketOperations;
import com.github.crittscott.somebuckets.protection.AutomationPlayers;
import com.github.crittscott.somebuckets.protection.DispenserFakePlayer;
import com.github.crittscott.somebuckets.register.ModCreativeTabs;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.register.ModLootModifiers;
import com.github.crittscott.somebuckets.register.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge mod entry point. The constructor registers the server config and mod content (items,
 * sounds, creative tab) on the mod event bus and listens for config (re)load. {@link #commonSetup}
 * then registers crafting ingredients, claim protection, dispenser behaviors, and cauldron
 * interactions once the mod bus reaches the common-setup phase.
 */
@Mod(SomeBuckets.MODID)
public class SomeBucketsForge {

    public SomeBucketsForge() {
        AutomationPlayers.install(DispenserFakePlayer::get);
        BucketOperations.install(new ForgeBucketOperations());
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        bus.addListener(this::configLoaded);
        bus.addListener(this::configReloaded);

        // Register all mod content
        ModItems.register(bus);
        ModLootModifiers.register(bus);
        ModSounds.register(bus);
        ModCreativeTabs.register(bus);

        bus.addListener(this::commonSetup);
    }

    private void configLoaded(final ModConfigEvent.Loading event) {
        refreshSourceBucketPolicy(event.getConfig());
    }

    private void configReloaded(final ModConfigEvent.Reloading event) {
        refreshSourceBucketPolicy(event.getConfig());
    }

    private static void refreshSourceBucketPolicy(ModConfig config) {
        if (config.getSpec() == ServerConfig.SPEC) {
            SBPolicy.refresh(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), ServerConfig.MILK_ID,
                    config.getFileName());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Custom ingredient types used by bucket recipes
            EmptyBucketIngredient.register();
            SpawnEggIngredient.register();
            if (ModList.get().isLoaded("ftbchunks")) {
                FtbChunksProtection.register();
            }

            Dispensers.register();

            // Register BB cauldron-map adapters; shared transitions also serve SB and dispensers.
            Cauldrons.register();
        });
    }

}
