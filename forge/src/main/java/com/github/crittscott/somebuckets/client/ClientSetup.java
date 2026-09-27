package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Single client lifecycle bootstrap: registers the item-definition types from
 * {@link ClientModelTypes}, installs the fluid appearance and diagnostics, and clears client color
 * caches on resource reload.
 */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    /**
     * Registers the item-definition types directly with vanilla's id mappers, which
     * {@code META-INF/accesstransformer.cfg} makes public. Item registration dispatches mod by mod
     * after construction and before the first resource load, so these unsynchronized maps are not
     * written concurrently with other mods and the codecs are known when item definitions load.
     */
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) return;
        ItemModels.ID_MAPPER.put(ClientModelTypes.FLUID_BUCKET, FluidBucketModel.Unbaked.MAP_CODEC);
        SpecialModelRenderers.ID_MAPPER.put(ClientModelTypes.JUNK_CONTENTS, JunkContentsRenderer.Unbaked.MAP_CODEC);
        ItemTintSources.ID_MAPPER.put(ClientModelTypes.MOB_EGG, MobEggColors.Tint.MAP_CODEC);
    }

    /** Installs the fluid appearance, the client diagnostics, and the client {@code /sb} tree. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        FluidBucketModel.installAppearance(ClientFluidColors::look);
        FluidDiagnostics.installProbe(ClientFluidColors::sampleFor);
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent commands) ->
                FluidDiagnostics.registerCommand(commands.getDispatcher()));
        SomeBuckets.LOGGER.info("Some Buckets (Forge client): fluid appearance and diagnostics installed");
    }

    /** Clears cached fluid and spawn-egg colors whenever client resources reload. */
    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            ClientFluidColors.clearCache();
            MobEggColors.clearCache();
        });
    }
}
