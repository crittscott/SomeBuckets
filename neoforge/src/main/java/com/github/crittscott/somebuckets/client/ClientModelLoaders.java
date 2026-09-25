package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Custom baked-model replacement wiring delegated by {@link ClientSetup}. */
final class ClientModelLoaders {
    private static final ModelResourceLocation BIG_BUCKET =
            ModelResourceLocation.inventory(BucketDefinitions.BIG_BUCKET_ID);
    private static final ModelResourceLocation HUGE_BUCKET =
            ModelResourceLocation.inventory(BucketDefinitions.HUGE_BUCKET_ID);
    private static final ModelResourceLocation SOURCE_BUCKET =
            ModelResourceLocation.inventory(BucketDefinitions.SOURCE_BUCKET_ID);
    private static final ModelResourceLocation JUNK_BUCKET =
            ModelResourceLocation.inventory(BucketDefinitions.JUNK_BUCKET_ID);

    private ClientModelLoaders() {}

    /**
     * Retains baked vessels and marks dynamic inventory models as custom-rendered.
     *
     * @throws IllegalStateException when a registered bucket item has no baked inventory model
     */
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel big = requireModel(event, BIG_BUCKET);
        BakedModel huge = requireModel(event, HUGE_BUCKET);
        BakedModel source = requireModel(event, SOURCE_BUCKET);
        FluidBucketRenderer.setVesselModels(big, huge, source);
        event.getModels().put(BIG_BUCKET, new CustomRendererModel(big));
        event.getModels().put(HUGE_BUCKET, new CustomRendererModel(huge));
        event.getModels().put(SOURCE_BUCKET, new CustomRendererModel(source));

        BakedModel junk = requireModel(event, JUNK_BUCKET);
        JBRenderer.setVesselModel(junk);
        event.getModels().put(JUNK_BUCKET, new CustomRendererModel(junk));
    }

    private static BakedModel requireModel(ModelEvent.ModifyBakingResult event, ModelResourceLocation id) {
        BakedModel model = event.getModels().get(id);
        if (model == null) throw new IllegalStateException("Baked inventory model " + id + " is missing");
        return model;
    }
}
