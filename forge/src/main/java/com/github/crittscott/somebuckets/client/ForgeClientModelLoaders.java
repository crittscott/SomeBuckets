package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelEvent;

/** Custom geometry-loader and baked-model replacement wiring delegated by {@link ForgeClientSetup}. */
final class ForgeClientModelLoaders {
    private static final ModelResourceLocation JUNK_BUCKET =
            new ModelResourceLocation(BucketDefinitions.JUNK_BUCKET_ID.getNamespace(),
                    BucketDefinitions.JUNK_BUCKET_ID.getPath(), "inventory");

    private ForgeClientModelLoaders() {}

    static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ForgeFluidContainerModel.Loader.NAME, ForgeFluidContainerModel.Loader.INSTANCE);
    }

    /** Retains the baked vessel for the BEWLR and marks the inventory model as custom-rendered. */
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel vessel = event.getModels().get(JUNK_BUCKET);
        ForgeJBRenderer.setVesselModel(vessel);
        event.getModels().put(JUNK_BUCKET, new ForgeJBModel(vessel));
    }
}
