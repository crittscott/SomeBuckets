package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.FabricServerConfig;
import com.github.crittscott.somebuckets.compat.ftbchunks.FabricFtbChunksProtection;
import com.github.crittscott.somebuckets.crafting.FabricEmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.FabricSpawnEggIngredient;
import com.github.crittscott.somebuckets.interaction.NonFluidDispensers;
import com.github.crittscott.somebuckets.interaction.FabricFluidDispensers;
import com.github.crittscott.somebuckets.interaction.FabricHeldTransferEvents;
import com.github.crittscott.somebuckets.interaction.FabricCauldronInteractions;
import com.github.crittscott.somebuckets.fluid.FabricFluidStorages;
import com.github.crittscott.somebuckets.protection.AutomationPlayers;
import com.github.crittscott.somebuckets.protection.FabricDispenserFakePlayer;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.FabricBucketOperations;
import com.github.crittscott.somebuckets.register.FabricCreativeTabs;
import com.github.crittscott.somebuckets.register.FabricItems;
import com.github.crittscott.somebuckets.register.FabricSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric common entry point. Loader-specific adapters are installed before shared item behavior is
 * registered so every runtime interaction observes a complete platform environment.
 */
public final class SomeBucketsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AutomationPlayers.install(FabricDispenserFakePlayer::get);
        FabricBucketOperations bucketOperations = new FabricBucketOperations();
        BucketOperations.install(bucketOperations);
        FabricServerConfig.load();
        FabricEmptyBucketIngredient.register();
        FabricSpawnEggIngredient.register();
        FabricSounds.register();
        FabricItems.register();
        FabricFluidStorages.register();
        FabricCreativeTabs.register();
        NonFluidDispensers.register(FabricItems.MOB_BUCKET, FabricItems.JUNK_BUCKET,
                FabricItems.TRASH_BUCKET);
        FabricFluidDispensers.register(FabricItems.BIG_BUCKET_8, FabricItems.BIG_BUCKET_64,
                FabricItems.SOURCE_BUCKET, bucketOperations);
        FabricCauldronInteractions.register(FabricItems.BIG_BUCKET_8, FabricItems.BIG_BUCKET_64);
        FabricHeldTransferEvents.register();
        if (FabricLoader.getInstance().isModLoaded("ftbchunks")) {
            FabricFtbChunksProtection.register();
        }
        ServerLifecycleEvents.SERVER_STARTING.register(server -> FabricServerConfig.load());
    }
}
