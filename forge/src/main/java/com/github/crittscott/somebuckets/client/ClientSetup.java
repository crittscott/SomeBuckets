package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.register.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Single client lifecycle bootstrap for item properties, colors, model loading and replacement,
 * and resource-reload listeners. Focused client classes implement those presentation concerns;
 * this type owns their Forge event subscription.
 */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
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
                    "Some Buckets (Forge client): model predicates, item tints, fluid colors, and "
                            + "dynamic item renderers registered");
        });
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        ClientColorHandlers.registerItemColors(event);
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        ClientColorHandlers.registerReloadListeners(event);
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        ClientModelLoaders.registerGeometryLoaders(event);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ClientModelLoaders.modifyBakingResult(event);
    }
}
