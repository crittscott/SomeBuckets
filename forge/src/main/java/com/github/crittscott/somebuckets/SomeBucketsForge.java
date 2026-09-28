package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.config.ServerConfig;
import com.github.crittscott.somebuckets.crafting.EmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.SpawnEggIngredient;
import com.github.crittscott.somebuckets.data.BucketLootModifierProvider;
import com.github.crittscott.somebuckets.fluid.FluidProvider;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.ForgeBucketOperations;
import com.github.crittscott.somebuckets.protection.ForgeDispenserFakePlayer;
import com.github.crittscott.somebuckets.register.ModCreativeTabs;
import com.github.crittscott.somebuckets.register.ModDataComponents;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.register.ModLootModifiers;
import com.github.crittscott.somebuckets.register.ModSounds;
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
public class SomeBucketsForge {

    /**
     * Installs Forge platform services and registers config, content, and lifecycle listeners.
     *
     * @param context Forge mod-loading context
     */
    public SomeBucketsForge(FMLJavaModLoadingContext context) {
        BucketOperations.install(new ForgeBucketOperations());
        IEventBus bus = context.getModEventBus();

        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        bus.addListener(this::configLoaded);
        bus.addListener(this::configReloaded);
        bus.addListener(this::gatherData);

        // Register all mod content
        ModDataComponents.register(bus);
        ModItems.register(bus);
        ModLootModifiers.register(bus);
        ModSounds.register(bus);
        ModCreativeTabs.register(bus);
        EmptyBucketIngredient.register(bus);
        SpawnEggIngredient.register(bus);
        FluidProvider.register();
        MinecraftForge.EVENT_BUS.addListener(ForgeDispenserFakePlayer::onLevelUnload);

        bus.addListener(this::commonSetup);
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

    private void gatherData(GatherDataEvent event) {
        DataProvider.Factory<BucketLootModifierProvider> provider = output ->
                new BucketLootModifierProvider(output, event.getLookupProvider());
        event.getGenerator().addProvider(event.includeServer(), provider);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SomeBuckets.registerBehaviors(ModItems.BIG_BUCKET_8.get(), ModItems.BIG_BUCKET_64.get(),
                    ModItems.SOURCE_BUCKET.get(), ModItems.MOB_BUCKET.get(), ModItems.JUNK_BUCKET.get(),
                    ModItems.TRASH_BUCKET.get(), "Forge");
        });
    }

}
