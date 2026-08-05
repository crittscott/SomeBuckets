package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.BakedModelWrapper;

/** Selects the Junk Bucket's custom renderer while retaining its resource-pack transforms. */
@OnlyIn(Dist.CLIENT)
final class JunkBucketModel extends BakedModelWrapper<BakedModel> {
    JunkBucketModel(BakedModel vessel) {
        super(vessel);
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    /**
     * ItemRenderer uses the model returned here for the rest of the render. Returning the vessel
     * would silently bypass {@link #isCustomRenderer()}.
     */
    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack,
                                     boolean leftHand) {
        originalModel.applyTransform(context, poseStack, leftHand);
        return this;
    }
}
