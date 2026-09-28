package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.config.ForgeServerConfig;
import com.github.crittscott.somebuckets.crafting.ForgeEmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.ForgeSpawnEggIngredient;
import com.github.crittscott.somebuckets.data.ForgeBucketLootModifierProvider;
import com.github.crittscott.somebuckets.fluid.ForgeFluidProvider;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.ForgeBucketOperations;
import com.github.crittscott.somebuckets.protection.ForgeAutomationPlayer;
import com.github.crittscott.somebuckets.register.ForgeCreativeTabs;
import com.github.crittscott.somebuckets.register.ForgeDataComponents;
import com.github.crittscott.somebuckets.register.ForgeItems;
import com.github.crittscott.somebuckets.register.ForgeLootModifiers;
import com.github.crittscott.somebuckets.register.ForgeSounds;
import net.minecraft.data.DataProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge mod entry point. The constructor installs shared runtime services, registers the server
 * config and mod content on the mod event bus, and listens for config (re)load. {@link #commonSetup}
 * then registers dispenser behaviors and cauldron interactions once the mod bus reaches the
 * common-setup phase.
 */
@Mod(SomeBuckets.MODID)
public final class SomeBucketsForge {

    /**
     * Installs Forge platform services and registers config, content, and lifecycle listeners.
     *
     * @param context Forge mod-loading context
     */
    public SomeBucketsForge(FMLJavaModLoadingContext context) {
        BucketOperations.install(new ForgeBucketOperations());
        IEventBus bus = context.getModEventBus();

        context.registerConfig(ModConfig.Type.SERVER, ForgeServerConfig.SPEC);
        bus.addListener(this::configLoaded);
        bus.addListener(this::configReloaded);
        bus.addListener(this::gatherData);

        // Register all mod content
        ForgeDataComponents.register(bus);
        ForgeItems.register(bus);
        ForgeLootModifiers.register(bus);
        ForgeSounds.register(bus);
        ForgeCreativeTabs.register(bus);
        ForgeEmptyBucketIngredient.register(bus);
        ForgeSpawnEggIngredient.register(bus);
        ForgeFluidProvider.register();
        MinecraftForge.EVENT_BUS.addListener(ForgeAutomationPlayer::onLevelUnload);

        bus.addListener(this::commonSetup);
    }

    private void configLoaded(final ModConfigEvent.Loading event) {
        refreshSourceBucketPolicy(event.getConfig(), false);
    }

    private void configReloaded(final ModConfigEvent.Reloading event) {
        refreshSourceBucketPolicy(event.getConfig(), true);
    }

    private static void refreshSourceBucketPolicy(ModConfig config, boolean reload) {
        if (config.getSpec() == ForgeServerConfig.SPEC) {
            SBPolicy.refresh(ForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(),
                    config.getFileName(), reload);
        }
    }

    private void gatherData(GatherDataEvent event) {
        DataProvider.Factory<ForgeBucketLootModifierProvider> provider = output ->
                new ForgeBucketLootModifierProvider(output, event.getLookupProvider());
        event.getGenerator().addProvider(event.includeServer(), provider);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SomeBuckets.registerBehaviors(ForgeItems.BIG_BUCKET_8.get(), ForgeItems.BIG_BUCKET_64.get(),
                    ForgeItems.SOURCE_BUCKET.get(), ForgeItems.MOB_BUCKET.get(), ForgeItems.JUNK_BUCKET.get(),
                    ForgeItems.TRASH_BUCKET.get(), "Forge");
        });
    }

}
