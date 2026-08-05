package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.NBTUtil;
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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Renders a Junk Bucket vessel and delegates each stored stack to Minecraft's item renderer. */
@OnlyIn(Dist.CLIENT)
public final class JunkBucketRenderer extends BlockEntityWithoutLevelRenderer {
    /** Compresses child-model thickness so every item remains between the vessel and foreground. */
    private static final float CHILD_DEPTH_SCALE = 1.0F / 256.0F;

    private static JunkBucketRenderer instance;
    private static volatile BakedModel vesselModel;
    private static volatile BakedModel foregroundModel;

    private JunkBucketRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    /** Forge expects one BEWLR instance for the mod rather than one per rendered stack. */
    public static JunkBucketRenderer getInstance() {
        if (instance == null) instance = new JunkBucketRenderer();
        return instance;
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
        if (vessel == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        renderModel(itemRenderer, bucket, vessel, poseStack, bufferSource,
                combinedLight, combinedOverlay);

        List<ItemStack> contents = NBTUtil.getStoredItems(bucket);
        for (JunkIconLayout.Placement placement : JunkIconLayout.arrange(contents)) {
            ItemStack stored = contents.get(placement.index());

            poseStack.pushPose();
            poseStack.translate(placement.centerX() / 16.0F, placement.centerY() / 16.0F,
                    placement.depth() / 16.0F);
            poseStack.mulPose(Axis.ZP.rotation(placement.angle()));
            float scale = placement.size() / 16.0F;
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

    /**
     * The outer ItemRenderer has already applied the Junk Bucket transform and translated model
     * coordinates by -0.5. Compensating by +0.5 lets this nested render apply its own -0.5 exactly
     * once while retaining the outer display transform.
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

    /** Repaints the vessel everywhere outside the mouth, placing stored items behind its front. */
    private static final class ForegroundModel extends BakedModelWrapper<BakedModel> {
        private static final float DEPTH = 8.875F;
        private static final int NORMAL_TOWARD_VIEWER = 127 << 16;
        private static final int VERTEX_COLOR = 0xFFFFFFFF;

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

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        private List<BakedQuad> cover() {
            List<BakedQuad> cached = cover;
            if (cached == null) {
                cached = buildCover(originalModel);
                cover = cached;
            }
            return cached;
        }

        private static List<BakedQuad> buildCover(BakedModel vessel) {
            TextureAtlasSprite sprite = frontSprite(vessel);
            if (sprite == null) return List.of();

            List<BucketMouth.Span> mouth = BucketMouth.spans();
            if (mouth.isEmpty()) return List.of(rectangle(sprite, 0.0F, 16.0F, 0.0F, 16.0F));

            List<BakedQuad> out = new ArrayList<>();
            float cursorY = 0.0F;
            for (BucketMouth.Span span : mouth) {
                if (span.minY() > cursorY) {
                    out.add(rectangle(sprite, 0.0F, 16.0F, cursorY, span.minY()));
                }
                if (span.minX() > 0.0F) {
                    out.add(rectangle(sprite, 0.0F, span.minX(), span.minY(), span.maxY()));
                }
                if (span.maxX() < 16.0F) {
                    out.add(rectangle(sprite, span.maxX(), 16.0F, span.minY(), span.maxY()));
                }
                cursorY = Math.max(cursorY, span.maxY());
            }
            if (cursorY < 16.0F) {
                out.add(rectangle(sprite, 0.0F, 16.0F, cursorY, 16.0F));
            }
            return List.copyOf(out);
        }

        @Nullable
        private static TextureAtlasSprite frontSprite(BakedModel vessel) {
            RandomSource random = RandomSource.create(0L);
            List<BakedQuad> quads = vessel.getQuads(null, null, random, ModelData.EMPTY, null);
            for (BakedQuad quad : quads) {
                if (quad.getDirection() == Direction.SOUTH) return quad.getSprite();
            }
            return quads.isEmpty() ? null : quads.get(0).getSprite();
        }

        private static BakedQuad rectangle(TextureAtlasSprite sprite, float minX, float maxX,
                                           float minY, float maxY) {
            float[][] corners = {
                    {minX, maxY}, {minX, minY}, {maxX, minY}, {maxX, maxY}
            };
            int[] vertices = new int[IQuadTransformer.STRIDE * 4];

            for (int vertex = 0; vertex < 4; vertex++) {
                float x = corners[vertex][0];
                float y = corners[vertex][1];
                int base = vertex * IQuadTransformer.STRIDE;
                vertices[base + IQuadTransformer.POSITION] = Float.floatToRawIntBits(x / 16.0F);
                vertices[base + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(y / 16.0F);
                vertices[base + IQuadTransformer.POSITION + 2] =
                        Float.floatToRawIntBits(DEPTH / 16.0F);
                vertices[base + IQuadTransformer.COLOR] = VERTEX_COLOR;
                vertices[base + IQuadTransformer.UV0] = Float.floatToRawIntBits(
                        lerp(sprite.getU0(), sprite.getU1(), x / 16.0F));
                vertices[base + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(
                        lerp(sprite.getV1(), sprite.getV0(), y / 16.0F));
                vertices[base + IQuadTransformer.NORMAL] = NORMAL_TOWARD_VIEWER;
            }

            return new BakedQuad(vertices, -1, Direction.SOUTH, sprite, true);
        }

        private static float lerp(float from, float to, float fraction) {
            return from + (to - from) * fraction;
        }
    }
}
