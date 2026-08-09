package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelEvent;

/** Custom geometry-loader and baked-model replacement wiring delegated by {@link ClientSetup}. */
final class ClientModelLoaders {
    private static final ModelResourceLocation JUNK_BUCKET =
            new ModelResourceLocation(SomeBuckets.MODID, "junk_bucket", "inventory");

    private ClientModelLoaders() {}

    static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(NbtFluidContainerModel.Loader.NAME, NbtFluidContainerModel.Loader.INSTANCE);
    }

    /** Retains the baked vessel for the BEWLR and marks the inventory model as custom-rendered. */
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel vessel = event.getModels().get(JUNK_BUCKET);
        JBRenderer.setVesselModel(vessel);
        event.getModels().put(JUNK_BUCKET, new JBModel(vessel));
    }
}
