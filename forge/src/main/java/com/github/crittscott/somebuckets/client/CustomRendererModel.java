package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.BakedModelWrapper;

/** Selects an item's BEWLR custom renderer while retaining its resource-pack transforms. */
@OnlyIn(Dist.CLIENT)
final class CustomRendererModel extends BakedModelWrapper<BakedModel> {
    CustomRendererModel(BakedModel wrapped) {
        super(wrapped);
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack,
                                     boolean leftHand) {
        originalModel.applyTransform(context, poseStack, leftHand);
        return this;
    }
}
