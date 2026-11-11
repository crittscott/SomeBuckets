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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Item model for the Junk Bucket's stored stacks, registered as
 * {@code somebuckets:junk_contents}. The item definition draws the vessel as its own layer; this
 * renderer draws each stored stack inside the opening, then repaints the vessel outside the opening
 * in front of them so the stacks appear to sit inside. Layouts and cover quads belong to the baked
 * instance and are discarded with it on resource reload.
 */
@Environment(EnvType.CLIENT)
public final class JunkContentsRenderer implements ItemModel, SpecialModelRenderer<JunkContentsRenderer.Frame> {
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
    private final Vector3fc[] extents;
    private final ModelRenderProperties properties;
    private final Matrix4fc transform;
    private final boolean animatedCover;

    private JunkContentsRenderer(TextureAtlasSprite sprite, List<JunkBucketIcons.Span> mouth,
                                 ModelRenderProperties properties, Matrix4fc transform) {
        this.mouth = mouth;
        this.properties = properties;
        this.transform = transform;
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(sprite, ChunkSectionLayer.TRANSLUCENT,
                Sheets.translucentItemSheet(), -1, true, 0);
        frontCover = buildCover(material, true);
        backCover = buildCover(material, false);
        animatedCover = sprite.contents().isAnimated();
        float front = COVER_DEPTH / JunkBucketIcons.ITEM_MODEL_SIZE;
        float back = 1.0F - front;
        extents = new Vector3fc[] {
                new Vector3f(0, 0, front), new Vector3f(0, 1, front),
                new Vector3f(1, 0, front), new Vector3f(1, 1, front),
                new Vector3f(0, 0, back), new Vector3f(0, 1, back),
                new Vector3f(1, 0, back), new Vector3f(1, 1, back)
        };
    }

    private record Layout(List<ItemStack> contents, List<JunkBucketIcons.Placement> placements) {}

    /** Independently extracted child models and placements, retained through deferred submission. */
    public record Frame(List<ItemStackRenderState> children, List<JunkBucketIcons.Placement> placements,
                        ItemDisplayContext displayContext) {}

    @Override
    public void update(ItemStackRenderState state, ItemStack bucket, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner entity, int seed) {
        JunkContents junk = BucketState.getStoredItemsComponent(bucket);
        if (junk == null || junk.items().isEmpty()) return;
        Frame frame = extractFrame(junk, resolver, level, displayContext);
        state.appendModelIdentityElement(this);
        state.appendModelIdentityElement(junk);
        if (animatedCover || frame.children().stream().anyMatch(ItemStackRenderState::isAnimated)) {
            state.setAnimated();
        }
        ItemStackRenderState.LayerRenderState layer = state.newLayer();
        properties.applyToLayer(layer, displayContext);
        layer.setLocalTransform(transform);
        layer.setExtents(() -> extents);
        layer.setupSpecialModel(this, frame);
    }

    @Nullable
    @Override
    public Frame extractArgument(ItemStack bucket) {
        JunkContents junk = BucketState.getStoredItemsComponent(bucket);
        if (junk == null || junk.items().isEmpty()) return null;
        Minecraft minecraft = Minecraft.getInstance();
        return extractFrame(junk, minecraft.getItemModelResolver(), minecraft.level, ItemDisplayContext.NONE);
    }

    private synchronized Frame extractFrame(JunkContents junk, ItemModelResolver resolver,
                                            @Nullable ClientLevel level, ItemDisplayContext displayContext) {
        Layout layout = frames.computeIfAbsent(junk, this::arrange);
        List<ItemStackRenderState> children = new ArrayList<>();
        for (int index = 0; index < layout.contents().size(); index++) {
            ItemStackRenderState child = new ItemStackRenderState();
            resolver.updateForTopItem(child, layout.contents().get(index), ItemDisplayContext.GUI,
                    level, null, index);
            children.add(child);
        }
        return new Frame(List.copyOf(children), layout.placements(), displayContext);
    }

    private Layout arrange(JunkContents junk) {
        List<ItemStack> contents = List.copyOf(junk.items());
        return new Layout(contents, JunkBucketIcons.arrange(mouth, contents, junk.layoutSeed()));
    }

    /** Bounds the stored icons between the front and mirrored back cover planes. */
    @Override
    public void getExtents(Consumer<Vector3fc> extents) {
        for (Vector3fc point : this.extents) extents.accept(point);
    }

    @Override
    public void submit(@Nullable Frame frame, PoseStack pose, SubmitNodeCollector collector,
                       int light, int overlay, boolean hasFoil, int outlineColor) {
        if (frame == null) return;
        ItemDisplayContext displayContext = frame.displayContext();
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

        collector.submitItem(pose, displayContext, light, overlay, outlineColor,
                ItemStackRenderState.LayerRenderState.EMPTY_TINTS,
                leftHand ? backCover : frontCover, ItemStackRenderState.FoilType.NONE);
    }

    /*
     * Packs the vessel rectangles outside the opening at a fixed depth in front of the stored
     * icons: the south face for right-hand and other renders, the mirrored north face for the left
     * hand.
     */
    private List<BakedQuad> buildCover(BakedQuad.MaterialInfo material, boolean front) {
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
            quads.add(FluidMaskGeometry.quad(face, material));
        }
        return List.copyOf(quads);
    }

    /**
     * Item-definition form:
     * {@code {"type": "somebuckets:junk_contents", "base": <vessel model>, "texture": <cover sprite>}}.
     */
    public record Unbaked(Identifier base, Identifier texture) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(base);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transform) {
            TextureAtlasSprite sprite = context.sprites().get(new SpriteId(TextureAtlas.LOCATION_ITEMS, texture));
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel resolved = baker.getModel(base);
            ModelRenderProperties properties =
                    ModelRenderProperties.fromResolvedModel(baker, resolved, resolved.getTopTextureSlots());
            return new JunkContentsRenderer(sprite, JunkBucketIcons.read(), properties, transform);
        }
    }
}
