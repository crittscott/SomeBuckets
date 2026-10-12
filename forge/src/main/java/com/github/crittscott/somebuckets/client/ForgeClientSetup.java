package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.register.ForgeItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Single client lifecycle bootstrap for the client platform, item properties, colors, model loading
 * and replacement, resource-reload listeners, and the client {@code /sb} commands. Focused client
 * classes implement those presentation concerns; this type owns their Forge event subscription.
 */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClientSetup {
    private ForgeClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientPlatform.install(ForgeClientColorHandlers::fluidFacts, ForgeSpawnEggItem::fromEntityType,
                FMLPaths.CONFIGDIR.get(), "Forge");
        event.enqueueWork(() -> {
            ItemProperties.register(ForgeItems.BIG_BUCKET_8.get(), FluidBucketItem.CONTENT_PROPERTY,
                    (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
            ItemProperties.register(ForgeItems.BIG_BUCKET_64.get(), FluidBucketItem.CONTENT_PROPERTY,
                    (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
            ItemProperties.register(ForgeItems.SOURCE_BUCKET.get(), FluidBucketItem.CONTENT_PROPERTY,
                    (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
            ItemProperties.register(ForgeItems.MOB_BUCKET.get(), MBItem.FILLED_PROPERTY,
                    (stack, level, entity, seed) -> MBItem.getFilledProperty(stack));
        });
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        ForgeClientColorHandlers.registerItemColors(event);
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        ForgeClientColorHandlers.registerReloadListeners(event);
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        ForgeClientModelLoaders.registerGeometryLoaders(event);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ForgeClientModelLoaders.modifyBakingResult(event);
    }

    /** Forge-bus client events. */
    @Mod.EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBusEvents {
        private ForgeBusEvents() {}

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(FluidDiagnostics.commandTree(source -> source::sendSystemMessage));
        }
    }
}
