package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelEvent;

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

    /** Retains baked vessels and marks dynamic inventory models as custom-rendered. */
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel big = event.getModels().get(BIG_BUCKET);
        BakedModel huge = event.getModels().get(HUGE_BUCKET);
        BakedModel source = event.getModels().get(SOURCE_BUCKET);
        if (big != null && huge != null && source != null) {
            FluidBucketRenderer.setVesselModels(big, huge, source);
            event.getModels().put(BIG_BUCKET, new CustomRendererModel(big));
            event.getModels().put(HUGE_BUCKET, new CustomRendererModel(huge));
            event.getModels().put(SOURCE_BUCKET, new CustomRendererModel(source));
        } else {
            SomeBuckets.LOGGER.warn(
                    "Skipped dynamic fluid bucket models because a baked vessel model is missing "
                            + "(big={}, huge={}, source={})",
                    big != null, huge != null, source != null);
        }

        BakedModel junk = event.getModels().get(JUNK_BUCKET);
        if (junk == null) {
            SomeBuckets.LOGGER.warn(
                    "Skipped dynamic Junk Bucket model because baked model {} is missing", JUNK_BUCKET);
            return;
        }
        JBRenderer.setVesselModel(junk);
        event.getModels().put(JUNK_BUCKET, new JBModel(junk));
    }
}
