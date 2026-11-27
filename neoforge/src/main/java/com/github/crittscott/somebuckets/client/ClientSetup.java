package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.fluid.NeoForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Single client lifecycle bootstrap for item properties, item tints, model loading and replacement,
 * resource-reload listeners, and the Junk Bucket custom renderer. Fluid-container content,
 * the Trash Bucket void, and Mob Bucket overlays are tinted; layer 0 metal remains unchanged.
 */
@EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private static final ModelResourceLocation JUNK_BUCKET =
            ModelResourceLocation.inventory(BucketDefinitions.JUNK_BUCKET_ID);

    private ClientSetup() {}

    // The stored fluid's stack-aware tint from its client fluid type.
    private static int fluidTint(StoredFluid stored) {
        FluidStack stack = NeoForgeFluidStacks.of(stored.fluid(), stored.amount(), stored.variantTag());
        return IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        FluidDiagnostics.installProbe(ClientFluidColors::sampleFor);
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent commands) ->
                FluidDiagnostics.registerCommand(commands.getDispatcher()));
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.BIG_BUCKET_8.get(), FluidBucketItem.CONTENT_PROPERTY,
                    (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
            ItemProperties.register(ModItems.BIG_BUCKET_64.get(), FluidBucketItem.CONTENT_PROPERTY,
                    (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
            ItemProperties.register(ModItems.SOURCE_BUCKET.get(), FluidBucketItem.CONTENT_PROPERTY,
                    (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
            ItemProperties.register(ModItems.MOB_BUCKET.get(), MBItem.FILLED_PROPERTY,
                    (stack, level, entity, seed) -> MBItem.getFilledProperty(stack));

            SomeBuckets.LOGGER.info(
                    "Some Buckets (NeoForge client): model predicates, item tints, fluid colors, and "
                            + "dynamic item renderers registered");
        });
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        ItemTints.register((color, item) -> event.register(color, item), ClientSetup::fluidTint);
    }

    /** Retains the baked vessel for the BEWLR and marks the inventory model as custom-rendered. */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel vessel = event.getModels().get(JUNK_BUCKET);
        JBRenderer.setVesselModel(vessel);
        event.getModels().put(JUNK_BUCKET, new JBModel(vessel));
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(JBRenderer.createItemExtensions(), ModItems.JUNK_BUCKET.get());
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            ClientFluidColors.clearCache();
        });
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(StoredFluidContainerModel.Loader.ID, StoredFluidContainerModel.Loader.INSTANCE);
    }
}
