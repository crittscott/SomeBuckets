package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.EggDiagnostics;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Fabric client bootstrap for item-definition types, the {@link ClientPlatform} seam, color-cache
 * reloads, and diagnostic commands.
 */
public final class SomeBucketsFabricClient implements ClientModInitializer {
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
        ClientPlatform.install(SomeBucketsFabricClient::fluidFacts, FabricLoader.getInstance().getConfigDir(),
                "Fabric");
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return COLOR_CACHE_RELOADER;
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        ClientPlatform.clearCaches();
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

    /* Fabric's fluid variant rendering supplies the still sprite and stack-aware tint. */
    private static ClientPlatform.FluidFacts fluidFacts(StoredFluid stored) {
        FluidVariant variant = FabricFluidVariants.toVariant(stored);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        return new ClientPlatform.FluidFacts(sprite, sprite == null ? null : sprite.contents().name(),
                FluidVariantRendering.getColor(variant), FluidVariantAttributes.getLuminance(variant) > 0);
    }
}
