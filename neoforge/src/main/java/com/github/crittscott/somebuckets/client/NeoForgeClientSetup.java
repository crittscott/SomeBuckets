package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Single client lifecycle bootstrap: registers the item-definition types from
 * {@link ClientModelTypes}, installs the {@link ClientPlatform} seam and the client {@code /sb} tree,
 * and reloads client colors on resource reload.
 */
@EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT)
public final class NeoForgeClientSetup {
    private static final ResourceLocation COLOR_CACHE_RELOADER = SomeBuckets.id("color_caches");

    private NeoForgeClientSetup() {}

    /** Installs the client platform seam and the client {@code /sb} tree. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientPlatform.install(NeoForgeClientSetup::fluidFacts, FMLPaths.CONFIGDIR.get(), "NeoForge");
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent commands) ->
                commands.getDispatcher().register(FluidDiagnostics.commandTree(
                        source -> line -> source.sendSuccess(() -> line, false))));
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

    /** Reloads fluid and spawn-egg colors whenever client resources reload. */
    @SubscribeEvent
    public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(COLOR_CACHE_RELOADER, new ClientPlatform.ColorReloadListener());
    }

    /* NeoForge's client fluid-type extensions supply the still texture and stack-aware tint. */
    private static ClientPlatform.FluidFacts fluidFacts(StoredFluid stored) {
        FluidStack stack = NeoForgeFluidStacks.of(stored);
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = extensions.getStillTexture(stack);
        TextureAtlasSprite sprite = stillTexture == null ? null : Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
        return new ClientPlatform.FluidFacts(sprite, stillTexture, extensions.getTintColor(stack),
                stack.getFluid().getFluidType().getLightLevel() > 0);
    }
}
