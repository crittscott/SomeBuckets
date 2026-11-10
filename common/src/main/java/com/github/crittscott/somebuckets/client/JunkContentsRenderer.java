package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.register.ModDataComponentTypes.JunkContents;
import com.github.crittscott.somebuckets.util.BucketState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Special item renderer for the Junk Bucket's stored stacks, registered as
 * {@code somebuckets:junk_contents}. The item definition draws the vessel as its own layer; this
 * renderer draws each stored stack inside the opening, then repaints the vessel outside the opening
 * in front of them so the stacks appear to sit inside. Layouts and cover quads belong to the baked
 * instance and are discarded with it on resource reload.
 */
@Environment(EnvType.CLIENT)
public final class JunkContentsRenderer implements SpecialModelRenderer<JunkContentsRenderer.Frame> {
    // Compress child-model thickness so every item remains between the vessel and the cover.
    private static final float CHILD_DEPTH_SCALE = 1.0F / 256.0F;
    // Depth of the cover, in item-model units, placing it just in front of the stored icons.
    private static final float COVER_DEPTH = 8.875F;

    private final List<JunkBucketIcons.Span> mouth;
    /*
     * Keyed by the immutable stored-junk component, which is replaced wholesale on every edit and
     * compares by value. Entries expire once no stack holds their component instance.
     */
    private final Map<JunkContents, Layout> frames = new WeakHashMap<>();
    private final List<BakedQuad> frontCover;
    private final List<BakedQuad> backCover;

    private JunkContentsRenderer(TextureAtlasSprite sprite, List<JunkBucketIcons.Span> mouth) {
        this.mouth = mouth;
        frontCover = buildCover(sprite, true);
        backCover = buildCover(sprite, false);
    }

    private record Layout(List<ItemStack> contents, List<JunkBucketIcons.Placement> placements) {}

    /** Independently extracted child models and placements, retained through deferred submission. */
    public record Frame(List<ItemStackRenderState> children, List<JunkBucketIcons.Placement> placements) {}

    @Nullable
    @Override
    public synchronized Frame extractArgument(ItemStack bucket) {
        JunkContents junk = BucketState.getStoredItemsComponent(bucket);
        if (junk == null || junk.items().isEmpty()) return null;
        Layout layout = frames.computeIfAbsent(junk, this::arrange);
        Minecraft minecraft = Minecraft.getInstance();
        ItemModelResolver resolver = minecraft.getItemModelResolver();
        List<ItemStackRenderState> children = new ArrayList<>();
        for (int index = 0; index < layout.contents().size(); index++) {
            ItemStackRenderState child = new ItemStackRenderState();
            resolver.updateForTopItem(child, layout.contents().get(index), ItemDisplayContext.GUI,
                    minecraft.level, null, index);
            children.add(child);
        }
        return new Frame(List.copyOf(children), layout.placements());
    }

    private Layout arrange(JunkContents junk) {
        List<ItemStack> contents = List.copyOf(junk.items());
        return new Layout(contents, JunkBucketIcons.arrange(mouth, contents, junk.layoutSeed()));
    }

    /** Bounds the stored icons between the front and mirrored back cover planes. */
    @Override
    public void getExtents(Consumer<Vector3fc> extents) {
        float front = COVER_DEPTH / JunkBucketIcons.ITEM_MODEL_SIZE;
        float back = 1.0F - front;
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                extents.accept(new Vector3f(x, y, front));
                extents.accept(new Vector3f(x, y, back));
            }
        }
    }

    @Override
    public void submit(@Nullable Frame frame, ItemDisplayContext displayContext, PoseStack pose,
                       SubmitNodeCollector collector, int light, int overlay, boolean hasFoil,
                       int outlineColor) {
        if (frame == null) return;
        boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

        List<JunkBucketIcons.Placement> placements = frame.placements();
        for (int slot = 0; slot < placements.size(); slot++) {
            JunkBucketIcons.Placement placement = placements.get(slot);
            float depth = leftHand
                    ? JunkBucketIcons.ITEM_MODEL_SIZE - placement.depth()
                    : placement.depth();
            pose.pushPose();
            pose.translate(placement.centerX() / JunkBucketIcons.ITEM_MODEL_SIZE,
                    placement.centerY() / JunkBucketIcons.ITEM_MODEL_SIZE,
                    depth / JunkBucketIcons.ITEM_MODEL_SIZE);
            if (leftHand) pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.mulPose(Axis.ZP.rotation(placement.angle()));
            float scale = placement.size() / JunkBucketIcons.ITEM_MODEL_SIZE;
            pose.scale(scale, scale, CHILD_DEPTH_SCALE);
            frame.children().get(placement.index()).submit(pose, collector, light, overlay, outlineColor);
            pose.popPose();
        }

        collector.submitItem(pose, displayContext, light, overlay, outlineColor, new int[0],
                leftHand ? backCover : frontCover, Sheets.translucentItemSheet(), ItemStackRenderState.FoilType.NONE);
    }

    /*
     * Packs the vessel rectangles outside the opening at a fixed depth in front of the stored
     * icons: the south face for right-hand and other renders, the mirrored north face for the left
     * hand.
     */
    private List<BakedQuad> buildCover(TextureAtlasSprite sprite, boolean front) {
        float size = JunkBucketIcons.ITEM_MODEL_SIZE;
        float z = (front ? COVER_DEPTH : size - COVER_DEPTH) / size;
        List<BakedQuad> quads = new ArrayList<>();
        for (JunkBucketIcons.Rectangle rect : JunkBucketIcons.cover(mouth)) {
            float minX = rect.minX() / size;
            float maxX = rect.maxX() / size;
            float minY = rect.minY() / size;
            float maxY = rect.maxY() / size;
            FluidMaskGeometry.Face face = front
                    ? new FluidMaskGeometry.Face(Direction.SOUTH,
                            new Vector3f(minX, maxY, z), new Vector3f(minX, minY, z),
                            new Vector3f(maxX, minY, z), new Vector3f(maxX, maxY, z))
                    : new FluidMaskGeometry.Face(Direction.NORTH,
                            new Vector3f(maxX, maxY, z), new Vector3f(maxX, minY, z),
                            new Vector3f(minX, minY, z), new Vector3f(minX, maxY, z));
            quads.add(FluidMaskGeometry.quad(face, sprite, -1, 0));
        }
        return List.copyOf(quads);
    }

    /**
     * Item-definition form:
     * {@code {"type": "somebuckets:junk_contents", "texture": <vessel sprite for the cover>}}.
     */
    public record Unbaked(Identifier texture) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            TextureAtlasSprite sprite = context.materials().get(new Material(TextureAtlas.LOCATION_ITEMS, texture));
            return new JunkContentsRenderer(sprite, JunkBucketIcons.read());
        }
    }
}
