package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers this mod's custom item geometry loaders and its baked model replacements. */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModelLoaders {
    private static final ModelResourceLocation JUNK_BUCKET =
            new ModelResourceLocation(SomeBuckets.MODID, "junk_bucket", "inventory");

    private ClientModelLoaders() {}

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(NbtFluidContainerModel.Loader.NAME, NbtFluidContainerModel.Loader.INSTANCE);
    }

    /**
     * The Junk Bucket keeps the vanilla generated item geometry and gains an override handler that
     * draws its contents, so it is wrapped after baking rather than loaded as custom geometry.
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel vessel = event.getModels().get(JUNK_BUCKET);
        if (vessel != null) {
            event.getModels().put(JUNK_BUCKET, JunkContentsModel.wrap(vessel));
        }
    }
}
