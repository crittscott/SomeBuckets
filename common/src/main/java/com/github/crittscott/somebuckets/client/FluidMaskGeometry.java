package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads the bucket content mask as generated-item surface geometry and packs mask-shaped quads in
 * vanilla's block vertex format.
 */
@Environment(EnvType.CLIENT)
public final class FluidMaskGeometry {
    private static final float FRONT_DEPTH = 8.51F / 16.0F;
    private static final float BACK_DEPTH = 7.49F / 16.0F;
    private static final ResourceLocation MASK = SomeBuckets.id("textures/item/big_bucket_full.png");

    private static final int INTS_PER_VERTEX =
            DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int NORMAL_COMPONENT_SCALE = 127;
    private static final int NO_LIGHTMAP = 0;
    private static final int WHITE = 0xFFFFFFFF;

    private FluidMaskGeometry() {}

    /**
     * Reads the active resource pack's mask as front, back, and exposed edge faces in normalized
     * item-model space. Returns an empty list when the mask is missing or unreadable.
     */
    static List<Face> read() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MASK);
        if (resource.isEmpty()) {
            SomeBuckets.LOGGER.warn("Fluid mask texture {} is missing; fluid layers will not render", MASK);
            return List.of();
        }

        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            boolean[][] opaque = new boolean[image.getHeight()][image.getWidth()];
            for (int row = 0; row < image.getHeight(); row++) {
                for (int column = 0; column < image.getWidth(); column++) {
                    opaque[row][column] = ARGB.alpha(image.getPixel(column, row)) != 0;
                }
            }
            return buildFaces(image.getWidth(), image.getHeight(), opaque);
        } catch (IOException exception) {
            SomeBuckets.LOGGER.warn(
                    "Could not read fluid mask texture {}; fluid layers will not render",
                    MASK, exception);
            return List.of();
        }
    }

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
            vertices[cursor++] = Float.floatToRawIntBits(
                    lerp(sprite.getU0(), sprite.getU1(), point.x()));
            vertices[cursor++] = Float.floatToRawIntBits(
                    lerp(sprite.getV1(), sprite.getV0(), point.y()));
            vertices[cursor++] = NO_LIGHTMAP;
            vertices[cursor++] = normal;
            if (cursor - base != INTS_PER_VERTEX) {
                throw new IllegalStateException("Unexpected block vertex format size: " + INTS_PER_VERTEX);
            }
        }
        return new BakedQuad(vertices, tintIndex, direction, sprite, true, lightEmission);
    }

    /*
     * Each horizontal run of opaque cells becomes one front and one back quad with its two end
     * faces; top and bottom faces cover each stretch of the run whose neighboring row is clear.
     * Texture coordinates are linear in position, so merged quads render the same as per-cell ones.
     */
    private static List<Face> buildFaces(int width, int height, boolean[][] opaque) {
        List<Face> faces = new ArrayList<>();
        float cellWidth = 1.0F / width;
        float cellHeight = 1.0F / height;

        for (int row = 0; row < height; row++) {
            float minY = 1.0F - (row + 1) * cellHeight;
            float maxY = 1.0F - row * cellHeight;
            int column = 0;
            while (column < width) {
                if (!isOpaque(opaque, column, row)) {
                    column++;
                    continue;
                }
                int start = column;
                while (isOpaque(opaque, column, row)) column++;

                float minX = start * cellWidth;
                float maxX = column * cellWidth;
                faces.add(face(Direction.SOUTH,
                        point(minX, maxY, FRONT_DEPTH), point(minX, minY, FRONT_DEPTH),
                        point(maxX, minY, FRONT_DEPTH), point(maxX, maxY, FRONT_DEPTH)));
                faces.add(face(Direction.NORTH,
                        point(maxX, maxY, BACK_DEPTH), point(maxX, minY, BACK_DEPTH),
                        point(minX, minY, BACK_DEPTH), point(minX, maxY, BACK_DEPTH)));
                faces.add(face(Direction.WEST,
                        point(minX, maxY, BACK_DEPTH), point(minX, minY, BACK_DEPTH),
                        point(minX, minY, FRONT_DEPTH), point(minX, maxY, FRONT_DEPTH)));
                faces.add(face(Direction.EAST,
                        point(maxX, maxY, FRONT_DEPTH), point(maxX, minY, FRONT_DEPTH),
                        point(maxX, minY, BACK_DEPTH), point(maxX, maxY, BACK_DEPTH)));

                addExposedEdges(faces, opaque, start, column, row - 1, maxY, Direction.UP, cellWidth);
                addExposedEdges(faces, opaque, start, column, row + 1, minY, Direction.DOWN, cellWidth);
            }
        }
        return List.copyOf(faces);
    }

    /** Adds one top or bottom face per stretch of {@code [start, end)} whose neighbor row is clear. */
    private static void addExposedEdges(List<Face> faces, boolean[][] opaque, int start, int end,
                                        int neighborRow, float y, Direction direction,
                                        float cellWidth) {
        int column = start;
        while (column < end) {
            if (isOpaque(opaque, column, neighborRow)) {
                column++;
                continue;
            }
            int stretchStart = column;
            while (column < end && !isOpaque(opaque, column, neighborRow)) column++;

            float minX = stretchStart * cellWidth;
            float maxX = column * cellWidth;
            faces.add(direction == Direction.UP
                    ? face(Direction.UP,
                            point(minX, y, BACK_DEPTH), point(minX, y, FRONT_DEPTH),
                            point(maxX, y, FRONT_DEPTH), point(maxX, y, BACK_DEPTH))
                    : face(Direction.DOWN,
                            point(minX, y, FRONT_DEPTH), point(minX, y, BACK_DEPTH),
                            point(maxX, y, BACK_DEPTH), point(maxX, y, FRONT_DEPTH)));
        }
    }

    private static boolean isOpaque(boolean[][] opaque, int column, int row) {
        return row >= 0 && row < opaque.length && column >= 0 && column < opaque[row].length
                && opaque[row][column];
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    private static Vertex point(float x, float y, float z) {
        return new Vertex(x, y, z);
    }

    private static Face face(Direction direction, Vertex first, Vertex second, Vertex third,
                             Vertex fourth) {
        return new Face(direction, first, second, third, fourth);
    }

    /** One vertex with normalized item-model coordinates. */
    public record Vertex(float x, float y, float z) {}

    /** One outward-facing mask quad. */
    public record Face(Direction direction, Vertex first, Vertex second, Vertex third,
                       Vertex fourth) {}
}
