package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.network.FabricSBPolicyPayload;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
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

import java.util.Collection;
import java.util.List;

/**
 * Fabric client bootstrap for item-definition types, the {@link ClientPlatform} seam, color-cache
 * reloads, the Source Bucket policy receiver, and diagnostic commands.
 */
public final class SomeBucketsFabricClient implements ClientModInitializer {
    private static final ResourceLocation COLOR_CACHE_RELOADER = SomeBuckets.id("color_caches");

    /**
     * Registers the policy receiver, client item-definition types, colors, reload listeners, and
     * diagnostic commands.
     */
    @Override
    public void onInitializeClient() {
        registerPolicyReceiver();
        // Fabric API's transitive access wideners open these vanilla id mappers to mods.
        ItemModels.ID_MAPPER.put(ClientModelTypes.FLUID_BUCKET, FluidBucketModel.Unbaked.MAP_CODEC);
        ItemModels.ID_MAPPER.put(ClientModelTypes.MOB_BUCKET, MobBucketModel.Unbaked.MAP_CODEC);
        SpecialModelRenderers.ID_MAPPER.put(ClientModelTypes.JUNK_CONTENTS, JunkContentsRenderer.Unbaked.MAP_CODEC);
        ItemTintSources.ID_MAPPER.put(ClientModelTypes.MOB_EGG, MobEggColors.Tint.MAP_CODEC);
        ClientPlatform.install(SomeBucketsFabricClient::fluidFacts, FabricLoader.getInstance().getConfigDir(),
                "Fabric");
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FabricColorReloadListener());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientDiagnostics.commandTree(
                        source -> source::sendFeedback)));
    }

    /*
     * Applies the server's Source Bucket policy (the typed receiver runs on the client thread) and
     * drops it on disconnect, so policy cannot leak between servers. The local config file is never
     * read as remote policy.
     */
    private static void registerPolicyReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
                FabricSBPolicyPayload.TYPE, (payload, context) -> payload.applyToClient());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SBPolicy.resetToDefaults());
    }

    /* Fabric needs the shared color listener to carry an id; it runs after models and their atlases. */
    private static final class FabricColorReloadListener extends ClientPlatform.ColorReloadListener
            implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return COLOR_CACHE_RELOADER;
        }

        @Override
        public Collection<ResourceLocation> getFabricDependencies() {
            return List.of(ResourceReloadListenerKeys.MODELS);
        }
    }

    /* Fabric's fluid variant rendering supplies the still sprite and stack-aware tint. */
    private static ClientPlatform.FluidFacts fluidFacts(StoredFluid stored) {
        FluidVariant variant = FabricFluidVariants.toVariant(stored);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        return new ClientPlatform.FluidFacts(sprite, sprite == null ? null : sprite.contents().name(),
                FluidVariantRendering.getColor(variant), FluidVariantAttributes.getLuminance(variant) > 0);
    }
}
