package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Single client lifecycle bootstrap: registers the item-definition types from
 * {@link ClientModelTypes}, installs the fluid appearance and diagnostics, and clears client color
 * caches on resource reload.
 */
@EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private static final ResourceLocation COLOR_CACHE_RELOADER =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "color_caches");

    private ClientSetup() {}

    /** Installs the fluid appearance, the client diagnostics, and the client {@code /sb} tree. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        FluidBucketModel.installAppearance(ClientFluidColors::look);
        FluidDiagnostics.installProbe(ClientFluidColors::sampleFor);
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent commands) ->
                FluidDiagnostics.registerCommand(commands.getDispatcher()));
        SomeBuckets.LOGGER.info("Some Buckets (NeoForge client): fluid appearance and diagnostics installed");
    }

    /** Registers the Big, Huge, and Source Bucket item model. */
    @SubscribeEvent
    public static void onRegisterItemModels(RegisterItemModelsEvent event) {
        event.register(ClientModelTypes.FLUID_BUCKET, FluidBucketModel.Unbaked.MAP_CODEC);
    }

    /** Registers the Junk Bucket contents renderer. */
    @SubscribeEvent
    public static void onRegisterSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(ClientModelTypes.JUNK_CONTENTS, JunkContentsRenderer.Unbaked.MAP_CODEC);
    }

    /** Registers the Mob Bucket spawn-egg tint. */
    @SubscribeEvent
    public static void onRegisterItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ClientModelTypes.MOB_EGG, MobEggColors.Tint.MAP_CODEC);
    }

    /** Clears cached fluid colors and reloads spawn-egg colors whenever client resources reload. */
    @SubscribeEvent
    public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(COLOR_CACHE_RELOADER, (ResourceManagerReloadListener) resourceManager -> {
            ClientFluidColors.clearCache();
            MobEggColors.reload(resourceManager);
        });
    }
}
