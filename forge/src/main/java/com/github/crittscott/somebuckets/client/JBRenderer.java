package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.List;

/** Renders a Junk Bucket vessel and delegates each stored stack to Minecraft's item renderer. */
@OnlyIn(Dist.CLIENT)
public final class JBRenderer extends BlockEntityWithoutLevelRenderer {
    // Compress child-model thickness so every item remains between the vessel and foreground.
    private static final float CHILD_DEPTH_SCALE = 1.0F / 256.0F;

    private static JBRenderer instance;
    private static volatile BakedModel vesselModel;
    private static volatile BakedModel foregroundModel;

    private JBRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Forge expects one BEWLR instance for the mod rather than one per rendered stack. */
    public static JBRenderer getInstance() {
        if (instance == null) instance = new JBRenderer();
        return instance;
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

    /** Supplies the newly baked vessel and invalidates foreground geometry on resource reload. */
    static void setVesselModel(BakedModel vessel) {
        vesselModel = vessel;
        foregroundModel = null;
    }

    @Override
    public void renderByItem(ItemStack bucket, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int combinedLight,
                             int combinedOverlay) {
        BakedModel vessel = vesselModel;

        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        renderModel(itemRenderer, bucket, vessel, poseStack, bufferSource,
                combinedLight, combinedOverlay);

        JunkBucketRenderData.Frame frame = JunkBucketRenderData.get(bucket, minecraft.level);
        List<ItemStack> contents = frame.contents();
        for (JunkBucketIcons.Placement placement : frame.placements()) {
            ItemStack stored = contents.get(placement.index());

            poseStack.pushPose();
            poseStack.translate(placement.centerX() / JunkBucketIcons.ITEM_MODEL_SIZE,
                    placement.centerY() / JunkBucketIcons.ITEM_MODEL_SIZE,
                    placement.depth() / JunkBucketIcons.ITEM_MODEL_SIZE);
            poseStack.mulPose(Axis.ZP.rotation(placement.angle()));
            float scale = placement.size() / JunkBucketIcons.ITEM_MODEL_SIZE;
            poseStack.scale(scale, scale, CHILD_DEPTH_SCALE);
            itemRenderer.renderStatic(stored, ItemDisplayContext.GUI, combinedLight,
                    combinedOverlay, poseStack, bufferSource, minecraft.level, placement.index());
            poseStack.popPose();
        }

        if (!contents.isEmpty()) {
            BakedModel foreground = foregroundModel;
            if (foreground == null) {
                foreground = new ForegroundModel(vessel);
                foregroundModel = foreground;
            }
            // A nested flat GUI-item render restores global lighting to 3D when it finishes.
            // Reinstate the outer Junk Bucket's flat lighting before drawing its foreground.
            if (context == ItemDisplayContext.GUI && !vessel.usesBlockLight()) {
                Lighting.setupForFlatItems();
            }
            renderModel(itemRenderer, bucket, foreground, poseStack, bufferSource,
                    combinedLight, combinedOverlay);
        }
    }

    /*
     * The outer item renderer has applied the bucket transform and translated model coordinates by
     * -0.5. Compensate by +0.5 so the nested render applies its own -0.5 exactly once.
     */
    private static void renderModel(ItemRenderer itemRenderer, ItemStack stack, BakedModel model,
                                    PoseStack poseStack, MultiBufferSource bufferSource,
                                    int combinedLight, int combinedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, poseStack, bufferSource,
                combinedLight, combinedOverlay, model);
        poseStack.popPose();
    }

    // Repaint the vessel outside the mouth so stored items remain behind its front.
    private static final class ForegroundModel extends BakedModelWrapper<BakedModel> {
        private static final int NORMAL_TOWARD_VIEWER = 127 << 16;

        private volatile List<BakedQuad> cover;

        private ForegroundModel(BakedModel vessel) {
            super(vessel);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack,
                                         boolean leftHand) {
            originalModel.applyTransform(context, poseStack, leftHand);
            return this;
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return List.of(this);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource random) {
            return side == null ? cover() : List.of();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource random, ModelData data,
                                        @Nullable RenderType renderType) {
            return side == null ? cover() : List.of();
        }

        private List<BakedQuad> cover() {
            List<BakedQuad> cached = cover;
            if (cached == null) {
                cached = JunkBucketCoverQuads.build(originalModel, Direction.SOUTH,
                        IQuadTransformer.STRIDE, IQuadTransformer.POSITION, IQuadTransformer.COLOR,
                        IQuadTransformer.UV0, IQuadTransformer.NORMAL, NORMAL_TOWARD_VIEWER);
                cover = cached;
            }
            return cached;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
