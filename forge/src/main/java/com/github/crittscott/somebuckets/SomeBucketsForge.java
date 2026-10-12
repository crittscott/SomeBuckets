package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.ForgeServerConfig;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.crafting.ForgeEmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.ForgeSpawnEggIngredient;
import com.github.crittscott.somebuckets.loot.ForgeBucketLootProvider;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.ForgeBucketOperations;
import com.github.crittscott.somebuckets.register.ForgeCreativeTabs;
import com.github.crittscott.somebuckets.register.ForgeItems;
import com.github.crittscott.somebuckets.register.ForgeLootModifiers;
import com.github.crittscott.somebuckets.register.ForgeSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge mod entry point. The constructor installs the Forge bucket operations, registers the server
 * config and mod content (items, sounds, creative tab, loot modifiers) on the mod event bus, and
 * listens for config (re)load. {@link #commonSetup} then registers crafting ingredients and the
 * shared dispenser and cauldron behaviors once the mod bus reaches the common-setup phase.
 */
@Mod(SomeBuckets.MODID)
public class SomeBucketsForge {

    public SomeBucketsForge() {
        BucketOperations.install(new ForgeBucketOperations());
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ForgeServerConfig.SPEC);
        bus.addListener(this::configLoaded);
        bus.addListener(this::configReloaded);

        ForgeItems.register(bus);
        ForgeLootModifiers.register(bus);
        ForgeSounds.register(bus);
        ForgeCreativeTabs.register(bus);

        bus.addListener(this::commonSetup);
        bus.addListener(ForgeBucketLootProvider::gatherData);
    }

    private void configLoaded(final ModConfigEvent.Loading event) {
        refreshSourceBucketPolicy(event.getConfig(), false);
    }

    private void configReloaded(final ModConfigEvent.Reloading event) {
        refreshSourceBucketPolicy(event.getConfig(), true);
    }

    private static void refreshSourceBucketPolicy(ModConfig config, boolean reload) {
        if (config.getSpec() == ForgeServerConfig.SPEC) {
            SBPolicy.refresh(ForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), config.getFileName(), reload);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ForgeEmptyBucketIngredient.register();
            ForgeSpawnEggIngredient.register();
            SomeBuckets.registerBehaviors(ForgeItems.BIG_BUCKET_8.get(), ForgeItems.BIG_BUCKET_64.get(),
                    ForgeItems.SOURCE_BUCKET.get(), ForgeItems.MOB_BUCKET.get(), ForgeItems.JUNK_BUCKET.get(),
                    ForgeItems.TRASH_BUCKET.get(), "Forge");
        });
    }
}
