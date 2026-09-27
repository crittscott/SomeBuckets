package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Single client lifecycle bootstrap: registers the item-definition types from
 * {@link ClientModelTypes}, installs the {@link ClientPlatform} seam and the client {@code /sb} tree,
 * and clears client color caches on resource reload.
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

    /** Installs the client platform seam and the client {@code /sb} tree. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientPlatform.install(ClientSetup::fluidFacts, FMLPaths.CONFIGDIR.get(), "Forge");
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent commands) ->
                FluidDiagnostics.registerCommand(commands.getDispatcher()));
        SomeBuckets.LOGGER.info("Some Buckets (Forge client): fluid appearance and diagnostics installed");
    }

    /** Clears cached fluid colors and reloads spawn-egg colors whenever client resources reload. */
    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            ClientPlatform.clearCaches();
            MobEggColors.reload(resourceManager);
        });
    }

    /* Forge's client fluid-type extensions supply the still texture and stack-aware tint. */
    private static ClientPlatform.FluidFacts fluidFacts(StoredFluid stored) {
        FluidStack stack = ForgeFluidStacks.of(stored);
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        TextureAtlasSprite sprite = stillTexture == null ? null : Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
        return new ClientPlatform.FluidFacts(sprite, stillTexture, extensions.getTintColor(stack),
                stack.getFluid().getFluidType().getLightLevel() > 0);
    }
}
