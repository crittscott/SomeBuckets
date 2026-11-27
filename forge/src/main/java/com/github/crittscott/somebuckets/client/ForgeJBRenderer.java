package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.model.BakedModelWrapper;

/** The Junk Bucket's Forge custom item renderer, drawing through the common {@link JunkBucketRenderer}. */
@OnlyIn(Dist.CLIENT)
public final class ForgeJBRenderer extends BlockEntityWithoutLevelRenderer {
    private static ForgeJBRenderer instance;
    private static volatile BakedModel vesselModel;

    private ForgeJBRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Selects the Junk Bucket's custom renderer while retaining its resource-pack transforms. */
    static final class CustomRendererModel extends BakedModelWrapper<BakedModel> {
        CustomRendererModel(BakedModel vessel) {
            super(vessel);
        }

        // Return this wrapper so the custom-renderer selection remains active after the transform.
        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack,
                                         boolean leftHand) {
            originalModel.applyTransform(context, poseStack, leftHand);
            return this;
        }

        @Override
        public boolean isCustomRenderer() {
            return true;
        }
    }

    /** Client item extensions that route Junk Bucket rendering through the shared BEWLR. */
    public static IClientItemExtensions createItemExtensions() {
        return new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return getInstance();
            }
        };
    }

    /** Forge expects one BEWLR instance for the mod rather than one per rendered stack. */
    public static ForgeJBRenderer getInstance() {
        if (instance == null) instance = new ForgeJBRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack bucket, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        JunkBucketRenderer.render(vesselModel, bucket, context, poseStack, bufferSource,
                combinedLight, combinedOverlay);
    }

    /** Supplies the newly baked vessel on each model bake. */
    static void setVesselModel(BakedModel vessel) {
        vesselModel = vessel;
    }
}
