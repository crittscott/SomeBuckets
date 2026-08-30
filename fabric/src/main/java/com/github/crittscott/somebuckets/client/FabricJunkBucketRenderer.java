package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/** Draws the Junk Bucket vessel with stable, visible icons for its stored stack entries. */
final class FabricJunkBucketRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final float CHILD_DEPTH_SCALE = 1.0F / 256.0F;
    private static final ResourceLocation VESSEL_MODEL =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "item/junk_bucket_vessel");

    private BakedModel foregroundSource;
    private BakedModel southForegroundModel;
    private BakedModel northForegroundModel;

    static void registerModel() {
        ModelLoadingPlugin.register(context -> context.addModels(VESSEL_MODEL));
    }

    @Override
    public void render(ItemStack bucket, ItemDisplayContext context, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer renderer = minecraft.getItemRenderer();
        BakedModel vessel = minecraft.getModelManager().getModel(VESSEL_MODEL);
        renderModel(renderer, bucket, vessel, pose, buffers, light, overlay);

        boolean leftHand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        JunkBucketRenderData.Frame frame = JunkBucketRenderData.get(bucket, minecraft.level);
        List<ItemStack> contents = frame.contents();
        for (JunkIconLayout.Placement placement : frame.placements()) {
            float depth = leftHand
                    ? BucketMouth.ITEM_MODEL_SIZE - placement.depth()
                    : placement.depth();
            pose.pushPose();
            pose.translate(placement.centerX() / BucketMouth.ITEM_MODEL_SIZE,
                    placement.centerY() / BucketMouth.ITEM_MODEL_SIZE,
                    depth / BucketMouth.ITEM_MODEL_SIZE);
            if (leftHand) pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.mulPose(Axis.ZP.rotation(placement.angle()));
            float scale = placement.size() / BucketMouth.ITEM_MODEL_SIZE;
            pose.scale(scale, scale, CHILD_DEPTH_SCALE);
            renderer.renderStatic(contents.get(placement.index()), ItemDisplayContext.GUI,
                    light, overlay, pose, buffers, minecraft.level, placement.index());
            pose.popPose();
        }

        if (!contents.isEmpty()) {
            if (foregroundSource != vessel) {
                BucketMouth.clearCache();
                JunkBucketRenderData.clearCache();
                foregroundSource = vessel;
                southForegroundModel = new ForegroundModel(vessel, Direction.SOUTH);
                northForegroundModel = new ForegroundModel(vessel, Direction.NORTH);
            }
            if (context == ItemDisplayContext.GUI && !vessel.usesBlockLight()) {
                Lighting.setupForFlatItems();
            }
            BakedModel foreground = leftHand ? northForegroundModel : southForegroundModel;
            renderModel(renderer, bucket, foreground, pose, buffers, light, overlay);
        }
    }

    private static void renderModel(ItemRenderer renderer, ItemStack stack, BakedModel model,
                                    PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        renderer.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        pose.popPose();
    }

    /** Repaints the vessel outside its opening at a depth in front of the stored items. */
    private static final class ForegroundModel extends DelegatingBakedModel {
        private static final float DEPTH = 8.875F;
        private static final int VERTEX_STRIDE = 8;
        private static final int POSITION = 0;
        private static final int COLOR = 3;
        private static final int UV = 4;
        private static final int NORMAL = 7;
        private static final int SOUTH_NORMAL = 127 << 16;
        private static final int NORTH_NORMAL = (-127 & 0xFF) << 16;
        private static final int VERTEX_COLOR = 0xFFFFFFFF;

        private final Direction face;
        private volatile List<BakedQuad> cover;

        private ForegroundModel(BakedModel vessel, Direction face) {
            super(vessel);
            this.face = face;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource random) {
            return side == null ? cover() : List.of();
        }

        private List<BakedQuad> cover() {
            List<BakedQuad> cached = cover;
            if (cached == null) {
                cached = buildCover(delegate, face);
                cover = cached;
            }
            return cached;
        }

        private static List<BakedQuad> buildCover(BakedModel vessel, Direction face) {
            TextureAtlasSprite sprite = faceSprite(vessel, face);
            if (sprite == null) return List.of();

            return JunkForegroundGeometry.cover().stream()
                    .map(rectangle -> rectangle(sprite, rectangle.minX(), rectangle.maxX(),
                            rectangle.minY(), rectangle.maxY(), face))
                    .toList();
        }

        @Nullable
        private static TextureAtlasSprite faceSprite(BakedModel vessel, Direction face) {
            List<BakedQuad> quads = vessel.getQuads(null, null, RandomSource.create(0L));
            for (BakedQuad quad : quads) {
                if (quad.getDirection() == face) return quad.getSprite();
            }
            return quads.isEmpty() ? null : quads.get(0).getSprite();
        }

        private static BakedQuad rectangle(TextureAtlasSprite sprite, float minX, float maxX,
                                           float minY, float maxY, Direction face) {
            float[][] corners = face == Direction.SOUTH
                    ? new float[][] {{minX, maxY}, {minX, minY}, {maxX, minY}, {maxX, maxY}}
                    : new float[][] {{maxX, maxY}, {maxX, minY}, {minX, minY}, {minX, maxY}};
            int[] vertices = new int[VERTEX_STRIDE * 4];
            float depth = face == Direction.SOUTH
                    ? DEPTH
                    : BucketMouth.ITEM_MODEL_SIZE - DEPTH;
            int normal = face == Direction.SOUTH ? SOUTH_NORMAL : NORTH_NORMAL;

            for (int vertex = 0; vertex < 4; vertex++) {
                float x = corners[vertex][0];
                float y = corners[vertex][1];
                int base = vertex * VERTEX_STRIDE;
                vertices[base + POSITION] = Float.floatToRawIntBits(x / BucketMouth.ITEM_MODEL_SIZE);
                vertices[base + POSITION + 1] =
                        Float.floatToRawIntBits(y / BucketMouth.ITEM_MODEL_SIZE);
                vertices[base + POSITION + 2] =
                        Float.floatToRawIntBits(depth / BucketMouth.ITEM_MODEL_SIZE);
                vertices[base + COLOR] = VERTEX_COLOR;
                vertices[base + UV] = Float.floatToRawIntBits(
                        lerp(sprite.getU0(), sprite.getU1(), x / BucketMouth.ITEM_MODEL_SIZE));
                vertices[base + UV + 1] = Float.floatToRawIntBits(
                        lerp(sprite.getV1(), sprite.getV0(), y / BucketMouth.ITEM_MODEL_SIZE));
                vertices[base + NORMAL] = normal;
            }
            return new BakedQuad(vertices, -1, face, sprite, true);
        }

        private static float lerp(float from, float to, float fraction) {
            return from + (to - from) * fraction;
        }
    }
}
