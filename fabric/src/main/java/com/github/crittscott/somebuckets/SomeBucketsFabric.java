package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.FabricServerConfig;
import com.github.crittscott.somebuckets.crafting.FabricEmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.FabricSpawnEggIngredient;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticsSupport;
import com.github.crittscott.somebuckets.diagnostic.EggDiagnostics;
import com.github.crittscott.somebuckets.diagnostic.FabricDiagnosticsSupport;
import com.github.crittscott.somebuckets.fluid.FabricFluidStorages;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.FabricHeldTransferEvents;
import com.github.crittscott.somebuckets.interaction.FluidDispensers;
import com.github.crittscott.somebuckets.interaction.NonFluidDispensers;
import com.github.crittscott.somebuckets.loot.FabricBucketLoot;
import com.github.crittscott.somebuckets.network.FabricSBPolicyNetworking;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.FabricBucketOperations;
import com.github.crittscott.somebuckets.protection.AutomationPlayers;
import com.github.crittscott.somebuckets.protection.FabricDispenserFakePlayer;
import com.github.crittscott.somebuckets.register.FabricCreativeTabs;
import com.github.crittscott.somebuckets.register.FabricDataComponents;
import com.github.crittscott.somebuckets.register.FabricItems;
import com.github.crittscott.somebuckets.register.FabricSounds;
import com.github.crittscott.somebuckets.util.CapturedMobNetworkRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric common entry point. Loader-specific adapters are installed before shared item behavior is
 * registered so every runtime interaction observes a complete platform environment.
 */
public final class SomeBucketsFabric implements ModInitializer {
    /** Installs platform services and registers all common-side Fabric content and callbacks. */
    @Override
    public void onInitialize() {
        AutomationPlayers.install(FabricDispenserFakePlayer::get);
        FabricBucketOperations bucketOperations = new FabricBucketOperations();
        BucketOperations.install(bucketOperations);
        DiagnosticsSupport.install(new FabricDiagnosticsSupport());
        // On a physical client the whole /sb tree is a client command (see SomeBucketsFabricClient);
        // a server-side /sb of the same name would shadow it entirely on Fabric, so only a dedicated
        // server registers the server-side /sb eggs.
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                    EggDiagnostics.registerCommand(dispatcher));
        }
        FabricEmptyBucketIngredient.register();
        FabricSpawnEggIngredient.register();
        FabricDataComponents.register();
        FabricSBPolicyNetworking.register();
        FabricSounds.register();
        FabricItems.register();
        FabricBucketLoot.register();
        FabricFluidStorages.register();
        FabricCreativeTabs.register();
        NonFluidDispensers.register(FabricItems.MOB_BUCKET, FabricItems.JUNK_BUCKET,
                FabricItems.TRASH_BUCKET);
        FluidDispensers.register(FabricItems.BIG_BUCKET_8, FabricItems.BIG_BUCKET_64,
                FabricItems.SOURCE_BUCKET);
        Cauldrons.registerPowder(FabricItems.BIG_BUCKET_8, FabricItems.BIG_BUCKET_64);
        FabricHeldTransferEvents.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CapturedMobNetworkRegistry.clear();
            FabricServerConfig.load(false);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CapturedMobNetworkRegistry.clear());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            FabricServerConfig.load(true);
            FabricSBPolicyNetworking.broadcast(server);
        });

        SomeBuckets.LOGGER.info("Some Buckets (Fabric) initialized");
    }
}
