package com.github.crittscott.somebuckets.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Quads in vanilla's block vertex format: the fluid layer retextures the content mask model's
 * generated quads, and the Junk Bucket cover packs its rectangles by hand because they are derived
 * from the opening mask when the renderer bakes, not from a model.
 */
@Environment(EnvType.CLIENT)
public final class FluidMaskGeometry {
    // Pushes the fluid's front and back faces just outside the vessel's so they draw ahead of it.
    private static final float FACE_OFFSET = 0.01F / 16.0F;
    private static final int INTS_PER_VERTEX =
            DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
    private static final int NO_LIGHTMAP = 0;
    private static final int NORMAL_COMPONENT_SCALE = 127;
    private static final int POSITION_INDEX =
            DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.POSITION) / Integer.BYTES;
    private static final int UV_INDEX =
            DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.UV0) / Integer.BYTES;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int WHITE = 0xFFFFFFFF;

    private FluidMaskGeometry() {}

    /** One outward-facing quad. */
    public record Face(Direction direction, Vertex first, Vertex second, Vertex third,
                       Vertex fourth) {}

    /** One vertex with normalized item-model coordinates. */
    public record Vertex(float x, float y, float z) {}

    /**
     * Packs {@code face} as a white-vertex quad whose texture coordinates map x and y linearly
     * onto {@code sprite}.
     *
     * @param tintIndex the layer tint index, or {@code -1} for none
     * @param lightEmission block light the quad emits, {@code 0} for none
     */
    static BakedQuad quad(Face face, TextureAtlasSprite sprite, int tintIndex, int lightEmission) {
        Direction direction = face.direction();
        int normal = (direction.getStepX() * NORMAL_COMPONENT_SCALE & 0xFF)
                | (direction.getStepY() * NORMAL_COMPONENT_SCALE & 0xFF) << 8
                | (direction.getStepZ() * NORMAL_COMPONENT_SCALE & 0xFF) << 16;
        Vertex[] corners = {face.first(), face.second(), face.third(), face.fourth()};
        int[] vertices = new int[INTS_PER_VERTEX * VERTICES_PER_QUAD];
        for (int index = 0; index < VERTICES_PER_QUAD; index++) {
            Vertex point = corners[index];
            int base = index * INTS_PER_VERTEX;
            int cursor = base;
            vertices[cursor++] = Float.floatToRawIntBits(point.x());
            vertices[cursor++] = Float.floatToRawIntBits(point.y());
            vertices[cursor++] = Float.floatToRawIntBits(point.z());
            vertices[cursor++] = WHITE;
            vertices[cursor++] = Float.floatToRawIntBits(Mth.lerp(point.x(), sprite.getU0(), sprite.getU1()));
            vertices[cursor++] = Float.floatToRawIntBits(Mth.lerp(point.y(), sprite.getV1(), sprite.getV0()));
            vertices[cursor++] = NO_LIGHTMAP;
            vertices[cursor++] = normal;
            if (cursor - base != INTS_PER_VERTEX) {
                throw new IllegalStateException("Unexpected block vertex format size: " + INTS_PER_VERTEX);
            }
        }
        return new BakedQuad(vertices, tintIndex, direction, sprite, true, lightEmission);
    }

    /**
     * Copies one quad of the generated mask model onto {@code sprite}: each texture coordinate keeps
     * its relative place within the sprite, and front and back faces move just outside the vessel's.
     *
     * @param tintIndex the layer tint index, or {@code -1} for none
     * @param lightEmission block light the quad emits, {@code 0} for none
     */
    static BakedQuad retexture(BakedQuad maskQuad, TextureAtlasSprite sprite, int tintIndex, int lightEmission) {
        TextureAtlasSprite mask = maskQuad.sprite();
        Direction direction = maskQuad.direction();
        float zOffset = direction == Direction.SOUTH ? FACE_OFFSET
                : direction == Direction.NORTH ? -FACE_OFFSET : 0.0F;
        int[] vertices = maskQuad.vertices().clone();
        for (int base = 0; base < vertices.length; base += INTS_PER_VERTEX) {
            int z = base + POSITION_INDEX + 2; // x, y, z
            int u = base + UV_INDEX;
            int v = u + 1;
            vertices[z] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[z]) + zOffset);
            float uFraction = (Float.intBitsToFloat(vertices[u]) - mask.getU0()) / (mask.getU1() - mask.getU0());
            float vFraction = (Float.intBitsToFloat(vertices[v]) - mask.getV0()) / (mask.getV1() - mask.getV0());
            vertices[u] = Float.floatToRawIntBits(Mth.lerp(uFraction, sprite.getU0(), sprite.getU1()));
            vertices[v] = Float.floatToRawIntBits(Mth.lerp(vFraction, sprite.getV0(), sprite.getV1()));
        }
        return new BakedQuad(vertices, tintIndex, direction, sprite, maskQuad.shade(), lightEmission);
    }
}
