package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.JunkContents;
import com.github.crittscott.somebuckets.util.BucketState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

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

    private final ResourceLocation texture;
    private final List<JunkBucketIcons.Span> mouth;
    /*
     * Keyed by the immutable stored-junk component, which is replaced wholesale on every edit and
     * compares by value. Entries expire once no stack holds their component instance.
     */
    private final Map<JunkContents, Frame> frames = new WeakHashMap<>();
    private final List<ItemStackRenderState> childStates = new ArrayList<>();
    private TextureAtlasSprite coverSprite;
    private List<BakedQuad> frontCover = List.of();
    private List<BakedQuad> backCover = List.of();

    private JunkContentsRenderer(ResourceLocation texture, List<JunkBucketIcons.Span> mouth) {
        this.texture = texture;
        this.mouth = mouth;
    }

    /** A bucket's stored stacks and the placements drawn for them, oldest entry in front. */
    public record Frame(List<ItemStack> contents, List<JunkBucketIcons.Placement> placements) {}

    @Nullable
    @Override
    public synchronized Frame extractArgument(ItemStack bucket) {
        JunkContents junk = BucketState.getStoredItemsComponent(bucket);
        if (junk == null || junk.items().isEmpty()) return null;
        Frame frame = frames.computeIfAbsent(junk, this::arrange);
        return frame.contents().isEmpty() ? null : frame;
    }

    private Frame arrange(JunkContents junk) {
        List<ItemStack> contents = new ArrayList<>(BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS);
        for (ItemStack stack : junk.items()) {
            if (contents.size() >= BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS) break;
            if (!stack.isEmpty() && !(stack.getItem() instanceof JBItem)) contents.add(stack);
        }
        return new Frame(List.copyOf(contents),
                JunkBucketIcons.arrange(mouth, contents, junk.layoutSeed()));
    }

    @Override
    public void render(@Nullable Frame frame, ItemDisplayContext displayContext, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay, boolean hasFoil) {
        if (frame == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        ItemModelResolver resolver = minecraft.getItemModelResolver();
        boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

        List<ItemStack> contents = frame.contents();
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
            ItemStackRenderState child = childState(slot);
            resolver.updateForTopItem(child, contents.get(placement.index()), ItemDisplayContext.GUI,
                    false, minecraft.level, null, placement.index());
            child.render(pose, buffers, light, overlay);
            pose.popPose();
        }

        VertexConsumer consumer = buffers.getBuffer(Sheets.translucentItemSheet());
        for (BakedQuad quad : cover(leftHand)) {
            consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
        }
    }

    // Stored stacks never include Junk Buckets, so child renders never re-enter this renderer.
    private ItemStackRenderState childState(int slot) {
        while (childStates.size() <= slot) childStates.add(new ItemStackRenderState());
        return childStates.get(slot);
    }

    private List<BakedQuad> cover(boolean leftHand) {
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        if (sprite != coverSprite) {
            coverSprite = sprite;
            frontCover = buildCover(sprite, true);
            backCover = buildCover(sprite, false);
        }
        return leftHand ? backCover : frontCover;
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
                            new FluidMaskGeometry.Vertex(minX, maxY, z), new FluidMaskGeometry.Vertex(minX, minY, z),
                            new FluidMaskGeometry.Vertex(maxX, minY, z), new FluidMaskGeometry.Vertex(maxX, maxY, z))
                    : new FluidMaskGeometry.Face(Direction.NORTH,
                            new FluidMaskGeometry.Vertex(maxX, maxY, z), new FluidMaskGeometry.Vertex(maxX, minY, z),
                            new FluidMaskGeometry.Vertex(minX, minY, z), new FluidMaskGeometry.Vertex(minX, maxY, z));
            quads.add(FluidMaskGeometry.quad(face, sprite, -1, 0));
        }
        return List.copyOf(quads);
    }

    /**
     * Item-definition form:
     * {@code {"type": "somebuckets:junk_contents", "texture": <vessel sprite for the cover>}}.
     */
    public record Unbaked(ResourceLocation texture) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("texture").forGetter(Unbaked::texture)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(EntityModelSet modelSet) {
            return new JunkContentsRenderer(texture, JunkBucketIcons.read());
        }
    }
}
