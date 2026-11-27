package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.network.FabricSBPolicyPayload;
import com.github.crittscott.somebuckets.register.FabricItems;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Fabric client bootstrap for the client platform, predicates, item tints, dynamic item rendering,
 * the resource-reload listener, the Source Bucket policy receiver, and the client {@code /sb}
 * commands.
 */
public final class SomeBucketsFabricClient implements ClientModInitializer {
    // The plain Junk Bucket vessel, loaded beside the builtin-rendered item model.
    private static final ResourceLocation JUNK_BUCKET_VESSEL_MODEL =
            new ResourceLocation(SomeBuckets.MODID, "item/junk_bucket_vessel");

    /* The Fabric variant's still sprite and stack-aware tint. */
    private static ClientPlatform.FluidFacts fluidFacts(StoredFluid fluid) {
        FluidVariant variant = FabricFluidVariants.toVariant(fluid);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        return new ClientPlatform.FluidFacts(sprite == null ? null : sprite.contents().name(),
                FluidVariantRendering.getColor(variant));
    }

    @Override
    public void onInitializeClient() {
        ClientPlatform.install(SomeBucketsFabricClient::fluidFacts, SpawnEggItem::byId,
                FabricLoader.getInstance().getConfigDir(), "Fabric");
        ClientPlatform.registerItemProperties(ItemProperties::register);
        ClientPlatform.registerItemColors((color, item) -> ColorProviderRegistry.ITEM.register(color, item));
        FabricFluidContainerModel.registerModels();
        ModelLoadingPlugin.register(context -> context.addModels(JUNK_BUCKET_VESSEL_MODEL));
        BuiltinItemRendererRegistry.INSTANCE.register(FabricItems.JUNK_BUCKET,
                (stack, context, pose, buffers, light, overlay) -> JunkBucketRenderer.render(
                        Minecraft.getInstance().getModelManager().getModel(JUNK_BUCKET_VESSEL_MODEL),
                        stack, context, pose, buffers, light, overlay));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return new ResourceLocation(SomeBuckets.MODID, "client_colors");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        ClientPlatform.reloadResources();
                    }
                });
        registerPolicyReceiver();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientDiagnostics.commandTree(source -> source::sendFeedback)));
    }

    /*
     * Applies the server's Source Bucket policy (the typed receiver runs on the client thread) and
     * drops it on disconnect, so policy cannot leak between servers. The local config file is never
     * read as remote policy.
     */
    private static void registerPolicyReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(FabricSBPolicyPayload.TYPE,
                (payload, player, responseSender) -> payload.applyToClient());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SBPolicy.resetToDefaults());
    }
}
