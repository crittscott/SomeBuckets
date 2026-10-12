package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.network.FabricSBPolicyPayload;
import com.github.crittscott.somebuckets.register.FabricItems;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Fabric client bootstrap for the client platform, predicates, item tints, dynamic item rendering,
 * the resource-reload listener, the Source Bucket policy receiver, and the client {@code /sb}
 * commands.
 */
public final class SomeBucketsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlatform.install(SomeBucketsFabricClient::fluidFacts, SpawnEggItem::byId,
                FabricLoader.getInstance().getConfigDir(), "Fabric");
        registerPredicates();
        registerColors();
        FabricFluidContainerModel.registerModels();
        FabricJunkBucketRenderer.registerModel();
        BuiltinItemRendererRegistry.INSTANCE.register(FabricItems.JUNK_BUCKET, new FabricJunkBucketRenderer());
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
                dispatcher.register(FluidDiagnostics.commandTree(source -> source::sendFeedback)));
    }

    /*
     * Applies the server's Source Bucket policy on the client thread and drops it on disconnect, so
     * policy cannot leak between servers. The local config file is never read as remote policy.
     */
    private static void registerPolicyReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(FabricSBPolicyPayload.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    FabricSBPolicyPayload payload = FabricSBPolicyPayload.read(buf);
                    client.execute(payload::applyToClient);
                });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SBPolicy.resetToDefaults());
    }

    /* The Fabric variant's still sprite and stack-aware tint. */
    private static ClientPlatform.FluidFacts fluidFacts(StoredFluid fluid) {
        FluidVariant variant = FabricFluidVariants.toVariant(fluid);
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        return new ClientPlatform.FluidFacts(sprite == null ? null : sprite.contents().name(),
                FluidVariantRendering.getColor(variant));
    }

    private static void registerPredicates() {
        ItemProperties.register(FabricItems.BIG_BUCKET_8, FluidBucketItem.CONTENT_PROPERTY,
                (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
        ItemProperties.register(FabricItems.BIG_BUCKET_64, FluidBucketItem.CONTENT_PROPERTY,
                (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
        ItemProperties.register(FabricItems.SOURCE_BUCKET, FluidBucketItem.CONTENT_PROPERTY,
                (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
        ItemProperties.register(FabricItems.MOB_BUCKET, MBItem.FILLED_PROPERTY,
                (stack, level, entity, seed) -> MBItem.getFilledProperty(stack));
    }

    private static void registerColors() {
        ColorProviderRegistry.ITEM.register(SomeBucketsFabricClient::bucketTint,
                FabricItems.BIG_BUCKET_8, FabricItems.BIG_BUCKET_64, FabricItems.SOURCE_BUCKET);
        ColorProviderRegistry.ITEM.register(SomeBucketsFabricClient::mobTint, FabricItems.MOB_BUCKET);
        ColorProviderRegistry.ITEM.register((stack, tint) -> tint == 1 ? 0x000000 : -1,
                FabricItems.TRASH_BUCKET);
    }

    private static int bucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex != 1) return -1;
        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode == BucketState.Mode.MILK) return 0xFFFFFF;
        if (mode == BucketState.Mode.FLUID) {
            StoredFluid stored = BucketState.getStoredFluid(stack);
            if (!stored.isEmpty()) return FluidVariantRendering.getColor(FabricFluidVariants.toVariant(stored));
        }
        return -1;
    }

    private static int mobTint(ItemStack stack, int tintIndex) {
        if (tintIndex == 0) return -1;
        return ClientPlatform.mobBucketTint(BucketState.getCurrentEntityType(stack), tintIndex - 1);
    }
}
