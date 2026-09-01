package com.github.crittscott.somebuckets.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Builds the quads that repaint the Junk Bucket vessel outside its opening, so stored icons stay
 * behind its front face. The {@link JunkBucketIcons#cover()} rectangles are packed into vertex data
 * at a fixed depth in front of the icons, sampling the vessel's own front (or back) sprite.
 *
 * <p>The block vertex layout is passed in by each loader's renderer so this stays free of loader
 * vertex-format constants. {@code face} selects {@link Direction#SOUTH} for a right-hand render and
 * {@link Direction#NORTH} for the mirrored left-hand render.
 */
@Environment(EnvType.CLIENT)
final class JunkBucketCoverQuads {
    /** Depth of the cover, in item-model units, placing it just in front of the stored icons. */
    private static final float DEPTH = 8.875F;
    private static final int VERTEX_COLOR = 0xFFFFFFFF;

    private JunkBucketCoverQuads() {}

    /**
     * @param vessel the baked vessel model to sample the cover sprite from
     * @param face {@link Direction#SOUTH} for the front cover, {@link Direction#NORTH} for the
     *             mirrored back cover
     * @param stride ints per vertex in the target vertex format
     * @param positionOffset int offset of the XYZ position within a vertex
     * @param colorOffset int offset of the packed color within a vertex
     * @param uvOffset int offset of the UV pair within a vertex
     * @param normalOffset int offset of the packed normal within a vertex
     * @param packedNormal packed normal value pointing toward the viewer for {@code face}
     * @return the cover quads, or an empty list when the vessel exposes no usable sprite
     */
    static List<BakedQuad> build(BakedModel vessel, Direction face, int stride, int positionOffset,
                                 int colorOffset, int uvOffset, int normalOffset, int packedNormal) {
        TextureAtlasSprite sprite = faceSprite(vessel, face);
        if (sprite == null) return List.of();

        return JunkBucketIcons.cover().stream()
                .map(rectangle -> quad(sprite, rectangle, face, stride, positionOffset, colorOffset,
                        uvOffset, normalOffset, packedNormal))
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

    private static BakedQuad quad(TextureAtlasSprite sprite, JunkBucketIcons.Rectangle rect,
                                  Direction face, int stride, int positionOffset, int colorOffset,
                                  int uvOffset, int normalOffset, int packedNormal) {
        float minX = rect.minX();
        float maxX = rect.maxX();
        float minY = rect.minY();
        float maxY = rect.maxY();
        boolean front = face == Direction.SOUTH;
        float[][] corners = front
                ? new float[][] {{minX, maxY}, {minX, minY}, {maxX, minY}, {maxX, maxY}}
                : new float[][] {{maxX, maxY}, {maxX, minY}, {minX, minY}, {minX, maxY}};
        float depth = front ? DEPTH : JunkBucketIcons.ITEM_MODEL_SIZE - DEPTH;

        int[] vertices = new int[stride * 4];
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = corners[vertex][0];
            float y = corners[vertex][1];
            int base = vertex * stride;
            vertices[base + positionOffset] =
                    Float.floatToRawIntBits(x / JunkBucketIcons.ITEM_MODEL_SIZE);
            vertices[base + positionOffset + 1] =
                    Float.floatToRawIntBits(y / JunkBucketIcons.ITEM_MODEL_SIZE);
            vertices[base + positionOffset + 2] =
                    Float.floatToRawIntBits(depth / JunkBucketIcons.ITEM_MODEL_SIZE);
            vertices[base + colorOffset] = VERTEX_COLOR;
            vertices[base + uvOffset] = Float.floatToRawIntBits(
                    lerp(sprite.getU0(), sprite.getU1(), x / JunkBucketIcons.ITEM_MODEL_SIZE));
            vertices[base + uvOffset + 1] = Float.floatToRawIntBits(
                    lerp(sprite.getV1(), sprite.getV0(), y / JunkBucketIcons.ITEM_MODEL_SIZE));
            vertices[base + normalOffset] = packedNormal;
        }
        return new BakedQuad(vertices, -1, face, sprite, true);
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }
}
