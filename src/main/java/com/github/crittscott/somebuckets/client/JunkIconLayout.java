package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the stacks held in a storage bucket as their own inventory icons, cropped to the bucket's
 * mouth so each one reads as an object sticking up out of the bucket.
 *
 * <p>Every icon is a rotated square of the item's atlas sprite, clipped against the spans of
 * {@link BucketMouth} and emitted as triangles in {@link BakedQuad} form. Icons are placed from a
 * generator seeded on the item's registry name and its position in the bucket, so the arrangement
 * is scattered but identical every frame and for every bucket holding the same things. Placement
 * does not avoid collisions; contents overlap as they fill up.
 *
 * <p>The oldest stack is drawn nearest the viewer, matching the order the bucket ejects them.
 *
 * <p>Item rendering ignores the color baked into a quad's vertices, so each icon instead carries a
 * tint index of its own, which {@link #colorAt} resolves back to the stored item's color. Without
 * it a dyed leather cap or a spawn egg would show its untinted template.
 *
 * <p>An item drawn by a custom renderer rather than by a model — a shield or a trident — has no
 * atlas icon to borrow, and is drawn as cobblestone.
 */
@OnlyIn(Dist.CLIENT)
final class JunkIconLayout {
    /** Item-model depth of the vessel's front face; icons sit in front of it. */
    private static final float FRONT_Z = 8.5F;
    private static final float DEPTH_STEP = 0.05F;

    private static final float MIN_SIZE = 4.5F;
    private static final float MAX_SIZE = 6.0F;
    private static final float MAX_TILT_RADIANS = (float) Math.toRadians(25.0);

    /** Keeps an icon's center clear of the mouth's outer edge. */
    private static final float EDGE_INSET = 1.5F;
    /**
     * How far below the rim an icon may sit. Zero puts its highest drawn pixel exactly at the rim;
     * the range below that keeps the contents from lining up at one height.
     */
    private static final float MAX_SINK = 1.0F;

    /** Packed normal for a face pointing at the viewer. */
    private static final int NORMAL_TOWARD_VIEWER = 127 << 16;
    /** Opaque white; item rendering takes an icon's color from its tint index instead. */
    private static final int VERTEX_COLOR = 0xFFFFFFFF;

    /**
     * Tint index of the first icon. The vessel's own layers are numbered from zero, so the icons
     * start past anything a generated item model can produce.
     */
    static final int TINT_BASE = 100;

    /** One bucket's icon colors, held between the several tint lookups a single frame makes. */
    @Nullable
    private static CompoundTag memoTag;
    private static int[] memoColors = new int[0];

    private JunkIconLayout() {}

    /** Builds the overlay quads for {@code contents}, oldest first. */
    static List<BakedQuad> build(List<ItemStack> contents) {
        List<BucketMouth.Span> mouth = BucketMouth.spans();
        if (mouth.isEmpty() || contents.isEmpty()) return List.of();

        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float rim = -Float.MAX_VALUE;
        for (BucketMouth.Span span : mouth) {
            left = Math.min(left, span.minX());
            right = Math.max(right, span.maxX());
            rim = Math.max(rim, span.maxY());
        }

        List<BakedQuad> out = new ArrayList<>();
        // Emitted newest first so the oldest stack is both nearest and drawn last.
        for (int index = contents.size() - 1; index >= 0; index--) {
            float depth = FRONT_Z + (contents.size() - index) * DEPTH_STEP;
            emit(out, contents.get(index), index, mouth, left, right, rim, depth);
        }
        return List.copyOf(out);
    }

    private static void emit(List<BakedQuad> out, ItemStack stack, int index,
                             List<BucketMouth.Span> mouth, float left, float right, float rim,
                             float depth) {
        TextureAtlasSprite sprite = iconFor(stack);
        if (sprite == null) return;

        RandomSource random = RandomSource.create(seedFor(stack, index));
        float size = MIN_SIZE + random.nextFloat() * (MAX_SIZE - MIN_SIZE);
        float angle = (random.nextFloat() * 2.0F - 1.0F) * MAX_TILT_RADIANS;

        float spread = Math.max(0.0F, (right - left) - 2.0F * EDGE_INSET);
        float centerX = left + EDGE_INSET + random.nextFloat() * spread;

        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        float half = size * 0.5F;

        // Hung from the rim by the icon's own drawn pixels, so nothing is cut off at the top.
        float centerY = rim - drawnRise(sprite, size, sin, cos) - random.nextFloat() * MAX_SINK;

        // Corner order matches Direction.SOUTH: top-left, bottom-left, bottom-right, top-right.
        float[][] corners = {
                {-half, half}, {-half, -half}, {half, -half}, {half, half}
        };
        List<float[]> square = new ArrayList<>(4);
        for (float[] corner : corners) {
            square.add(new float[]{
                    centerX + corner[0] * cos - corner[1] * sin,
                    centerY + corner[0] * sin + corner[1] * cos
            });
        }

        for (BucketMouth.Span span : mouth) {
            List<float[]> visible = clipToSpan(square, span);
            if (visible.size() < 3) continue;
            triangulate(out, visible, sprite, TINT_BASE + index, depth,
                    centerX, centerY, sin, cos, size);
        }
    }

    /**
     * How far the icon's highest drawn pixel sits above its center once rotated. The sprite's
     * drawn box is mapped onto the icon square, and the tallest of its four rotated corners is
     * what has to clear the rim.
     */
    private static float drawnRise(TextureAtlasSprite sprite, float size, float sin, float cos) {
        SpriteBounds.Bounds drawn = SpriteBounds.of(sprite);
        float minX = (drawn.minX() - 0.5F) * size;
        float maxX = (drawn.maxX() - 0.5F) * size;
        float minY = (drawn.minY() - 0.5F) * size;
        float maxY = (drawn.maxY() - 0.5F) * size;

        float rise = minX * sin + minY * cos;
        rise = Math.max(rise, maxX * sin + minY * cos);
        rise = Math.max(rise, minX * sin + maxY * cos);
        return Math.max(rise, maxX * sin + maxY * cos);
    }

    /** Clips a convex polygon against one span with the Sutherland-Hodgman algorithm. */
    private static List<float[]> clipToSpan(List<float[]> polygon, BucketMouth.Span span) {
        List<float[]> result = clipToEdge(polygon, 0, span.minX(), true);
        result = clipToEdge(result, 0, span.maxX(), false);
        result = clipToEdge(result, 1, span.minY(), true);
        return clipToEdge(result, 1, span.maxY(), false);
    }

    private static List<float[]> clipToEdge(List<float[]> polygon, int axis, float limit,
                                            boolean keepAbove) {
        if (polygon.size() < 3) return List.of();

        List<float[]> out = new ArrayList<>(polygon.size() + 2);
        for (int i = 0; i < polygon.size(); i++) {
            float[] current = polygon.get(i);
            float[] next = polygon.get((i + 1) % polygon.size());
            boolean currentIn = keepAbove ? current[axis] >= limit : current[axis] <= limit;
            boolean nextIn = keepAbove ? next[axis] >= limit : next[axis] <= limit;

            if (currentIn) out.add(current);
            if (currentIn != nextIn) {
                float t = (limit - current[axis]) / (next[axis] - current[axis]);
                out.add(new float[]{
                        current[0] + (next[0] - current[0]) * t,
                        current[1] + (next[1] - current[1]) * t
                });
            }
        }
        return out;
    }

    /**
     * Fans the clipped polygon into triangles, each carried by a quad whose last vertex repeats
     * the third. Texture coordinates come from rotating each point back into the icon's own frame.
     */
    private static void triangulate(List<BakedQuad> out, List<float[]> polygon,
                                    TextureAtlasSprite sprite, int tintIndex, float depth,
                                    float centerX, float centerY, float sin, float cos,
                                    float size) {
        for (int i = 1; i + 1 < polygon.size(); i++) {
            float[][] triangle = {polygon.get(0), polygon.get(i), polygon.get(i + 1), polygon.get(i + 1)};
            int[] vertices = new int[IQuadTransformer.STRIDE * 4];

            for (int vertex = 0; vertex < 4; vertex++) {
                float x = triangle[vertex][0];
                float y = triangle[vertex][1];
                float localX = (x - centerX) * cos + (y - centerY) * sin;
                float localY = -(x - centerX) * sin + (y - centerY) * cos;
                float u = clamp01(localX / size + 0.5F);
                float v = clamp01(localY / size + 0.5F);

                int base = vertex * IQuadTransformer.STRIDE;
                vertices[base + IQuadTransformer.POSITION] = Float.floatToRawIntBits(x / 16.0F);
                vertices[base + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(y / 16.0F);
                vertices[base + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(depth / 16.0F);
                vertices[base + IQuadTransformer.COLOR] = VERTEX_COLOR;
                vertices[base + IQuadTransformer.UV0] =
                        Float.floatToRawIntBits(lerp(sprite.getU0(), sprite.getU1(), u));
                // The sprite's vertical axis runs opposite to model space.
                vertices[base + IQuadTransformer.UV0 + 1] =
                        Float.floatToRawIntBits(lerp(sprite.getV1(), sprite.getV0(), v));
                // The light coordinate is left at zero, as item quads carry no baked light.
                vertices[base + IQuadTransformer.NORMAL] = NORMAL_TOWARD_VIEWER;
            }

            out.add(new BakedQuad(vertices, tintIndex, Direction.SOUTH, sprite, true));
        }
    }

    /** The icon an item shows in an inventory slot, or cobblestone when it has none. */
    @Nullable
    private static TextureAtlasSprite iconFor(ItemStack stack) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
        if (!model.isCustomRenderer()) {
            TextureAtlasSprite sprite = modelSprite(model);
            if (sprite == null) sprite = model.getParticleIcon(ModelData.EMPTY);
            if (sprite != null && !isMissing(sprite)) return sprite;
        }

        BakedModel fallback = Minecraft.getInstance().getItemRenderer()
                .getModel(new ItemStack(Items.COBBLESTONE), null, null, 0);
        TextureAtlasSprite sprite = fallback.getParticleIcon(ModelData.EMPTY);
        return sprite == null || isMissing(sprite) ? null : sprite;
    }

    /**
     * The sprite of the model's front face, which is the first layer of a flat item. A model built
     * from block geometry keeps its faces under their own directions, so those are searched too.
     */
    @Nullable
    private static TextureAtlasSprite modelSprite(BakedModel model) {
        RandomSource random = RandomSource.create(0L);
        List<BakedQuad> quads = model.getQuads(null, null, random, ModelData.EMPTY, null);
        for (BakedQuad quad : quads) {
            if (quad.getDirection() == Direction.SOUTH) return quad.getSprite();
        }
        if (!quads.isEmpty()) return quads.get(0).getSprite();

        for (Direction direction : Direction.values()) {
            List<BakedQuad> sided = model.getQuads(null, direction, random, ModelData.EMPTY, null);
            if (!sided.isEmpty()) return sided.get(0).getSprite();
        }
        return null;
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        return MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }

    /**
     * The color of the icon carrying {@code tintIndex}, taken from the stored item's own color
     * handler. Returns no tint for the vessel's own layers and for an index past the contents.
     *
     * <p>The colors of one bucket are held between calls, since a frame asks for every icon's tint
     * in turn and reading them means deserializing the whole of the bucket's storage.
     */
    static int colorAt(ItemStack bucket, int tintIndex) {
        int index = tintIndex - TINT_BASE;
        if (index < 0) return -1;

        CompoundTag tag = bucket.getTag();
        if (tag == null) return -1;

        if (tag != memoTag) {
            List<ItemStack> contents = NBTUtil.getStoredItems(bucket);
            int[] colors = new int[contents.size()];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = Minecraft.getInstance().getItemColors().getColor(contents.get(i), 0);
            }
            memoTag = tag;
            memoColors = colors;
        }
        return index < memoColors.length ? memoColors[index] : -1;
    }

    private static long seedFor(ItemStack stack, int index) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return (id == null ? 0L : id.hashCode()) * 31L + index;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }
}
