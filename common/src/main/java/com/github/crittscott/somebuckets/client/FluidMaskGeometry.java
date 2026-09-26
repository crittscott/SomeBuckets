package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Loads the bucket content mask and exposes its generated-item surface geometry. */
@Environment(EnvType.CLIENT)
public final class FluidMaskGeometry {
    private static final float FRONT_DEPTH = 8.51F / 16.0F;
    private static final float BACK_DEPTH = 7.49F / 16.0F;
    private static final ResourceLocation MASK = ResourceLocation.fromNamespaceAndPath(
            SomeBuckets.MODID, "textures/item/big_bucket_full.png");

    private static volatile List<Face> cachedFaces;

    private FluidMaskGeometry() {}

    /** Returns the mask's front, back, and exposed edge faces in normalized item-model space. */
    public static List<Face> faces() {
        List<Face> cached = cachedFaces;
        if (cached == null) {
            cached = readFaces();
            cachedFaces = cached;
        }
        return cached;
    }

    /** Invalidates geometry derived from the active resource pack. */
    public static void clear() {
        cachedFaces = null;
    }

    private static List<Face> readFaces() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MASK);
        if (resource.isEmpty()) {
            SomeBuckets.LOGGER.warn("Fluid mask texture {} is missing; fluid layers will not render", MASK);
            return List.of();
        }

        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            boolean[][] opaque = new boolean[image.getHeight()][image.getWidth()];
            for (int row = 0; row < image.getHeight(); row++) {
                for (int column = 0; column < image.getWidth(); column++) {
                    opaque[row][column] = (image.getPixel(column, row) >>> 24) != 0;
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
