package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.EggDiagnostics;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.platform.FabricFluidColors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Fabric client bootstrap for item-definition types, fluid appearance and colors, color-cache
 * reloads, and diagnostic commands.
 */
public final class SomeBucketsFabricClient implements ClientModInitializer {
    private static final int DEFAULT_FLUID_COLOR = 0x4A90E2;
    private static final ResourceLocation COLOR_CACHE_RELOADER =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "color_caches");

    /** Registers client item-definition types, colors, reload listeners, and diagnostic commands. */
    @Override
    public void onInitializeClient() {
        FabricSBPolicyClientNetworking.register();
        // Fabric API's transitive access wideners open these vanilla id mappers to mods.
        ItemModels.ID_MAPPER.put(ClientModelTypes.FLUID_BUCKET, FluidBucketModel.Unbaked.MAP_CODEC);
        SpecialModelRenderers.ID_MAPPER.put(ClientModelTypes.JUNK_CONTENTS, JunkContentsRenderer.Unbaked.MAP_CODEC);
        ItemTintSources.ID_MAPPER.put(ClientModelTypes.MOB_EGG, MobEggColors.Tint.MAP_CODEC);
        FluidBucketModel.installAppearance(FabricClientFluidColors::look);
        FabricFluidColors.install(fluid -> FabricClientFluidColors.color(fluid, DEFAULT_FLUID_COLOR));
        FluidDiagnostics.installProbe(FabricClientFluidColors::sampleFor);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return COLOR_CACHE_RELOADER;
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        FabricClientFluidColors.clearCache();
                        MobEggColors.reload(resourceManager);
                    }
                });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("sb")
                        .then(ClientCommandManager.literal("fluids").executes(context -> {
                            var source = context.getSource();
                            return FluidDiagnostics.run(source::sendFeedback) ? 1 : 0;
                        }))
                        .then(ClientCommandManager.literal("eggs").executes(context -> {
                            EggDiagnostics.runReport(context.getSource()::sendFeedback);
                            return 1;
                        }))));

        SomeBuckets.LOGGER.info(
                "Some Buckets (Fabric client): item models, tints, fluid colors, and diagnostics registered");
    }
}
