package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.BucketState;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a Junk Bucket: its vessel, a stable icon for each stored stack, and a foreground that
 * repaints the vessel outside its opening so the icons appear to sit inside it. Each loader supplies
 * the baked vessel from its own custom item renderer hook.
 */
@Environment(EnvType.CLIENT)
final class JunkBucketRenderer {
    // Compress child-model thickness so every item remains between the vessel and foreground.
    private static final float CHILD_DEPTH_SCALE = 1.0F / 256.0F;
    /** Depth of the foreground cover, in front of every icon {@link JunkIconLayout} places. */
    static final float FOREGROUND_DEPTH = 8.875F;

    private static volatile Foregrounds foregrounds;

    private JunkBucketRenderer() {}

    /**
     * The foreground models built for one baked vessel. A resource reload bakes a new vessel, which
     * rebuilds them.
     */
    private record Foregrounds(BakedModel vessel, BakedModel south, BakedModel north) {}

    /** One cover rectangle in item-model space. */
    private record Rectangle(float minX, float maxX, float minY, float maxY) {}

    /**
     * Repaints the vessel outside its opening at a depth in front of the stored items. The quads are
     * packed by hand because this model is drawn through vanilla's {@link ItemRenderer}, which takes
     * {@link BakedQuad}s in vanilla's block vertex format ({@code DefaultVertexFormat.BLOCK}: position,
     * color, texture, light, normal).
     */
    private static final class ForegroundModel extends DelegatingBakedModel {
        private static final int COLOR = 3;
        private static final int NORMAL = 7;
        private static final int NORTH_NORMAL = (-127 & 0xFF) << 16;
        private static final int POSITION = 0;
        private static final int SOUTH_NORMAL = 127 << 16;
        private static final int UV = 4;
        private static final int VERTEX_COLOR = 0xFFFFFFFF;
        private static final int VERTEX_STRIDE = 8;

        private volatile List<BakedQuad> cover;
        private final Direction face;

        private ForegroundModel(BakedModel vessel, Direction face) {
            super(vessel);
            this.face = face;
        }

        private static List<BakedQuad> buildCover(BakedModel vessel, Direction face) {
            TextureAtlasSprite sprite = faceSprite(vessel, face);
            if (sprite == null) return List.of();

            return rectangles().stream()
                    .map(rectangle -> quad(sprite, rectangle, face))
                    .toList();
        }

        private List<BakedQuad> cover() {
            List<BakedQuad> cached = cover;
            if (cached == null) {
                cached = buildCover(delegate, face);
                cover = cached;
            }
            return cached;
        }

        @Nullable
        private static TextureAtlasSprite faceSprite(BakedModel vessel, Direction face) {
            List<BakedQuad> quads = vessel.getQuads(null, null, RandomSource.create(0L));
            for (BakedQuad quad : quads) {
                if (quad.getDirection() == face) return quad.getSprite();
            }
            return quads.isEmpty() ? null : quads.get(0).getSprite();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource random) {
            return side == null ? cover() : List.of();
        }

        private static BakedQuad quad(TextureAtlasSprite sprite, Rectangle rectangle, Direction face) {
            float minX = rectangle.minX();
            float maxX = rectangle.maxX();
            float minY = rectangle.minY();
            float maxY = rectangle.maxY();
            float[][] corners = face == Direction.SOUTH
                    ? new float[][] {{minX, maxY}, {minX, minY}, {maxX, minY}, {maxX, maxY}}
                    : new float[][] {{maxX, maxY}, {maxX, minY}, {minX, minY}, {minX, maxY}};
            int[] vertices = new int[VERTEX_STRIDE * 4];
            float depth = face == Direction.SOUTH
                    ? FOREGROUND_DEPTH
                    : BucketMouth.ITEM_MODEL_SIZE - FOREGROUND_DEPTH;
            int normal = face == Direction.SOUTH ? SOUTH_NORMAL : NORTH_NORMAL;

            for (int vertex = 0; vertex < 4; vertex++) {
                float x = corners[vertex][0] / BucketMouth.ITEM_MODEL_SIZE;
                float y = corners[vertex][1] / BucketMouth.ITEM_MODEL_SIZE;
                int base = vertex * VERTEX_STRIDE;
                vertices[base + POSITION] = Float.floatToRawIntBits(x);
                vertices[base + POSITION + 1] = Float.floatToRawIntBits(y);
                vertices[base + POSITION + 2] = Float.floatToRawIntBits(depth / BucketMouth.ITEM_MODEL_SIZE);
                vertices[base + COLOR] = VERTEX_COLOR;
                vertices[base + UV] = Float.floatToRawIntBits(Mth.lerp(x, sprite.getU0(), sprite.getU1()));
                vertices[base + UV + 1] = Float.floatToRawIntBits(Mth.lerp(y, sprite.getV1(), sprite.getV0()));
                vertices[base + NORMAL] = normal;
            }
            return new BakedQuad(vertices, -1, face, sprite, true);
        }
    }

    /**
     * Draws the Junk Bucket. A left-hand view shows the vessel from behind, so the icons are mirrored
     * through the vessel's depth and turned to face that side, and the foreground covers the back face.
     *
     * @param vessel the baked vessel model, without the custom-renderer flag
     */
    static void render(BakedModel vessel, ItemStack bucket, ItemDisplayContext context, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer renderer = minecraft.getItemRenderer();
        renderModel(renderer, bucket, vessel, pose, buffers, light, overlay);

        boolean leftHand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        List<ItemStack> contents = BucketState.storedItemsView(bucket);
        for (JunkIconLayout.Placement placement : JunkIconLayout.arrange(
                contents, BucketState.getJunkLayoutSeed(bucket))) {
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
            Foregrounds current = foregroundsFor(vessel);
            // A nested flat GUI-item render restores global lighting to 3D when it finishes.
            // Reinstate the outer Junk Bucket's flat lighting before drawing its foreground.
            if (context == ItemDisplayContext.GUI && !vessel.usesBlockLight()) {
                Lighting.setupForFlatItems();
            }
            renderModel(renderer, bucket, leftHand ? current.north() : current.south(),
                    pose, buffers, light, overlay);
        }
    }

    private static Foregrounds foregroundsFor(BakedModel vessel) {
        Foregrounds current = foregrounds;
        if (current == null || current.vessel() != vessel) {
            current = new Foregrounds(vessel, new ForegroundModel(vessel, Direction.SOUTH),
                    new ForegroundModel(vessel, Direction.NORTH));
            foregrounds = current;
        }
        return current;
    }

    /** The parts of the item square outside the opening, bottom first. */
    private static List<Rectangle> rectangles() {
        List<BucketMouth.Span> mouth = BucketMouth.spans();
        if (mouth.isEmpty()) {
            return List.of(new Rectangle(0.0F, BucketMouth.ITEM_MODEL_SIZE,
                    0.0F, BucketMouth.ITEM_MODEL_SIZE));
        }

        List<Rectangle> out = new ArrayList<>();
        float cursorY = 0.0F;
        for (BucketMouth.Span span : mouth) {
            if (span.minY() > cursorY) {
                out.add(new Rectangle(0.0F, BucketMouth.ITEM_MODEL_SIZE, cursorY, span.minY()));
            }
            if (span.minX() > 0.0F) {
                out.add(new Rectangle(0.0F, span.minX(), span.minY(), span.maxY()));
            }
            if (span.maxX() < BucketMouth.ITEM_MODEL_SIZE) {
                out.add(new Rectangle(span.maxX(), BucketMouth.ITEM_MODEL_SIZE, span.minY(), span.maxY()));
            }
            cursorY = Math.max(cursorY, span.maxY());
        }
        if (cursorY < BucketMouth.ITEM_MODEL_SIZE) {
            out.add(new Rectangle(0.0F, BucketMouth.ITEM_MODEL_SIZE, cursorY, BucketMouth.ITEM_MODEL_SIZE));
        }
        return List.copyOf(out);
    }

    /*
     * The outer item renderer has applied the bucket transform and translated model coordinates by
     * -0.5. Compensate by +0.5 so the nested render applies its own -0.5 exactly once.
     */
    private static void renderModel(ItemRenderer renderer, ItemStack stack, BakedModel model,
                                    PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        renderer.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        pose.popPose();
    }
}
