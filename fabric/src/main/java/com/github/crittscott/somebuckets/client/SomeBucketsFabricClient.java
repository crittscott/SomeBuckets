package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.EggDiagnostics;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.platform.FabricFluidColors;
import com.github.crittscott.somebuckets.register.FabricItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

/** Fabric client bootstrap for predicates, item tints, fluid colors, and dynamic item rendering. */
public final class SomeBucketsFabricClient implements ClientModInitializer {
    private static final int DEFAULT_FLUID_COLOR = 0x4A90E2;
    private static final ResourceLocation JUNK_BUCKET_VESSEL_MODEL =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "item/junk_bucket_vessel");

    @Override
    public void onInitializeClient() {
        registerPredicates();
        ItemTints.register((color, item) -> ColorProviderRegistry.ITEM.register(color, item),
                FabricClientFluidColors::tint);
        FabricFluidContainerModel.registerModels();
        ModelLoadingPlugin.register(context -> context.addModels(JUNK_BUCKET_VESSEL_MODEL));
        BuiltinItemRendererRegistry.INSTANCE.register(FabricItems.JUNK_BUCKET,
                (stack, context, pose, buffers, light, overlay) -> JunkBucketRenderer.render(
                        Minecraft.getInstance().getModelManager().getModel(JUNK_BUCKET_VESSEL_MODEL),
                        stack, context, pose, buffers, light, overlay));
        FabricFluidColors.install(fluid -> FabricClientFluidColors.color(fluid, DEFAULT_FLUID_COLOR));
        FluidDiagnostics.installProbe(FabricClientFluidColors::sampleFor);
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
                "Some Buckets (Fabric client): model predicates, item tints, fluid colors, and "
                        + "dynamic item renderers registered");
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
}
