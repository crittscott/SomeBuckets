package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.fluid.ForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge client bootstrap for the client platform, item predicates, item tints, the custom fluid
 * container geometry, the Junk Bucket's custom-renderer model, the resource-reload listener, and the
 * client {@code /sb} commands.
 */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClientSetup {
    private static final ModelResourceLocation JUNK_BUCKET =
            new ModelResourceLocation(BucketDefinitions.JUNK_BUCKET_ID.getNamespace(),
                    BucketDefinitions.JUNK_BUCKET_ID.getPath(), "inventory");

    private ForgeClientSetup() {}

    /** Forge-bus client events. */
    @Mod.EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBusEvents {
        private ForgeBusEvents() {}

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(ClientDiagnostics.commandTree(source -> source::sendSystemMessage));
        }
    }

    /* The Forge client fluid type's still texture and stack-aware tint. */
    private static ClientPlatform.FluidFacts fluidFacts(StoredFluid fluid) {
        FluidStack stack = ForgeFluidStacks.of(fluid);
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.fluid());
        return new ClientPlatform.FluidFacts(extensions.getStillTexture(stack), extensions.getTintColor(stack));
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientPlatform.install(ForgeClientSetup::fluidFacts, ForgeSpawnEggItem::fromEntityType,
                FMLPaths.CONFIGDIR.get(), "Forge");
        event.enqueueWork(() -> ClientPlatform.registerItemProperties(ItemProperties::register));
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        ClientPlatform.registerItemColors((color, item) -> event.register(color, item));
    }

    /** Retains the baked vessel for the BEWLR and marks the inventory model as custom-rendered. */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel vessel = event.getModels().get(JUNK_BUCKET);
        ForgeJBRenderer.setVesselModel(vessel);
        event.getModels().put(JUNK_BUCKET, new ForgeJBRenderer.CustomRendererModel(vessel));
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                ClientPlatform.reloadResources());
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ForgeFluidContainerModel.Loader.NAME, ForgeFluidContainerModel.Loader.INSTANCE);
    }
}
