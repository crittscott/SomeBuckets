package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.config.FabricServerConfig;
import com.github.crittscott.somebuckets.crafting.FabricEmptyBucketIngredient;
import com.github.crittscott.somebuckets.crafting.FabricSpawnEggIngredient;
import com.github.crittscott.somebuckets.fluid.FabricBucketStorage;
import com.github.crittscott.somebuckets.interaction.FabricHeldTransferEvents;
import com.github.crittscott.somebuckets.interaction.FabricMobCaptureEvents;
import com.github.crittscott.somebuckets.loot.FabricBucketLoot;
import com.github.crittscott.somebuckets.network.FabricSBPolicyPayload;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.FabricBucketOperations;
import com.github.crittscott.somebuckets.register.FabricCreativeTabs;
import com.github.crittscott.somebuckets.register.FabricDataComponents;
import com.github.crittscott.somebuckets.register.FabricItems;
import com.github.crittscott.somebuckets.register.FabricSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

/**
 * Fabric common entry point. Loader-specific adapters are installed before shared item behavior is
 * registered so every runtime interaction observes a complete platform environment.
 */
public final class SomeBucketsFabric implements ModInitializer {
    /** Installs platform services and registers all common-side Fabric content and callbacks. */
    @Override
    public void onInitialize() {
        FabricBucketOperations bucketOperations = new FabricBucketOperations();
        BucketOperations.install(bucketOperations);
        FabricEmptyBucketIngredient.register();
        FabricSpawnEggIngredient.register();
        FabricDataComponents.register();
        FabricSBPolicyPayload.registerServer();
        FabricSounds.register();
        FabricItems.register();
        FabricBucketLoot.register();
        FabricBucketStorage.register();
        FabricCreativeTabs.register();
        SomeBuckets.registerBehaviors();
        FabricHeldTransferEvents.register();
        FabricMobCaptureEvents.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> FabricServerConfig.load(false));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (FabricServerConfig.load(true)) {
                FabricSBPolicyPayload.broadcast(server);
            }
        });
        List<String> integrations = FabricBucketOperations.activeIntegrations();
        SomeBuckets.LOGGER.info("Some Buckets (Fabric): optional protection integrations: {}",
                integrations.isEmpty() ? "none" : String.join(", ", integrations));
        SomeBuckets.logStarted("Fabric", FabricLoader.getInstance().getModContainer(SomeBuckets.MODID)
                .orElseThrow().getMetadata().getVersion().getFriendlyString());
    }
}
