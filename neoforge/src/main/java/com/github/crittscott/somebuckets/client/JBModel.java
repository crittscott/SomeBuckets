package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Selects the Junk Bucket's custom renderer while retaining its resource-pack transforms. */
@OnlyIn(Dist.CLIENT)
final class JBModel extends BakedModelWrapper<BakedModel> {
    JBModel(BakedModel vessel) {
        super(vessel);
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    // Return this wrapper so the custom-renderer selection remains active after the transform.
    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack,
                                     boolean leftHand) {
        originalModel.applyTransform(context, poseStack, leftHand);
        return this;
    }
}
