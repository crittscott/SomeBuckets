package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.ServerConfig;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.crafting.EmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.SpawnEggIngredient;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Dispensers;
import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.register.ModCreativeTabs;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.register.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SomeBuckets.MODID)
public class SomeBuckets {
    public static final String MODID = "somebuckets";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SomeBuckets() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        bus.addListener(this::configLoaded);
        bus.addListener(this::configReloaded);

        // Register all mod content
        ModItems.register(bus);
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
            SBPolicy.refresh(config.getFileName());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Custom ingredient types used by bucket recipes
            EmptyBucketIngredient.register();
            SpawnEggIngredient.register();
            ClaimProtections.initialize();

            Dispensers.register();

            // Register BB cauldron-map adapters; shared transitions also serve SB and dispensers.
            Cauldrons.register();
        });
    }

}
