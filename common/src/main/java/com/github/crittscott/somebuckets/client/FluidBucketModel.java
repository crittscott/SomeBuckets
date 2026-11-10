package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item model for Big, Huge, and Source Buckets, registered as {@code somebuckets:fluid_bucket}. It
 * renders the vessel model, then, when the bucket holds a fluid, a second, opaque layer shaped by the
 * content mask, textured with the fluid's still sprite, and tinted per stack. Fluid layers are built
 * once per sprite and discarded with the model on resource reload.
 */
@Environment(EnvType.CLIENT)
public final class FluidBucketModel implements ItemModel {
    private static final int FLUID_TINT_INDEX = 0;
    private static final int CACHE_LIMIT = 256;
    private static final int EMISSIVE_LIGHT = 15;

    private final Geometry vessel;
    private final ModelRenderProperties properties;
    private final List<FluidMaskGeometry.Face> mask;
    private final Map<LayerKey, Geometry> fluidLayers = new ConcurrentHashMap<>();

    private FluidBucketModel(List<BakedQuad> vessel, ModelRenderProperties properties,
                             List<FluidMaskGeometry.Face> mask) {
        this.vessel = new Geometry(vessel);
        this.properties = properties;
        this.mask = mask;
    }

    /** The fluid layer's still sprite, stack-aware ARGB tint, and emissiveness. */
    public record Look(TextureAtlasSprite sprite, int tint, boolean emissive) {}

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable LivingEntity entity, int seed) {
        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(stack.hasFoil());
        if (vessel.animated() || stack.hasFoil()) renderState.setAnimated();
        ItemStackRenderState.LayerRenderState vesselLayer = renderState.newLayer();
        if (stack.hasFoil()) vesselLayer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
        applyGeometry(vesselLayer, vessel, Sheets.translucentItemSheet(), displayContext);

        StoredFluid stored = BucketState.getStoredFluid(stack);
        if (stored.isEmpty() || mask.isEmpty()) return;
        Look look = ClientPlatform.look(stored);
        if (look == null) return;
        renderState.appendModelIdentityElement(look);
        if (look.sprite().isAnimated()) renderState.setAnimated();

        if (fluidLayers.size() >= CACHE_LIMIT) fluidLayers.clear();
        Geometry fluid = fluidLayers.computeIfAbsent(new LayerKey(look.sprite(), look.emissive()),
                this::bakeFluidLayer);
        // Cutout draws the fluid opaque over the vessel, as vanilla's filled-bucket art does. A
        // translucent fluid layer would be depth-sorted against the vessel and, when drawn first,
        // would hide the vessel behind it and leave the bucket see-through.
        ItemStackRenderState.LayerRenderState fluidLayer = renderState.newLayer();
        applyGeometry(fluidLayer, fluid, Sheets.cutoutBlockSheet(), displayContext);
        fluidLayer.prepareTintLayers(FLUID_TINT_INDEX + 1)[FLUID_TINT_INDEX] = look.tint();
    }

    private Geometry bakeFluidLayer(LayerKey key) {
        int emission = key.emissive() ? EMISSIVE_LIGHT : 0;
        List<BakedQuad> quads = mask.stream()
                .map(face -> FluidMaskGeometry.quad(face, key.sprite(), FLUID_TINT_INDEX, emission))
                .toList();
        return new Geometry(quads);
    }

    /* Sprites compare by identity, which is stable until the atlas is rebuilt with this model. */
    private record LayerKey(TextureAtlasSprite sprite, boolean emissive) {}

    private void applyGeometry(ItemStackRenderState.LayerRenderState layer, Geometry geometry,
                               RenderType renderType, ItemDisplayContext displayContext) {
        layer.setExtents(geometry::extents);
        layer.setRenderType(renderType);
        properties.applyToLayer(layer, displayContext);
        layer.prepareQuadList().addAll(geometry.quads());
    }

    private record Geometry(List<BakedQuad> quads, Vector3f[] extents, boolean animated) {
        private Geometry(List<BakedQuad> quads) {
            this(quads, BlockModelWrapper.computeExtents(quads),
                    quads.stream().anyMatch(quad -> quad.sprite().isAnimated()));
        }
    }

    /** Item-definition form: {@code {"type": "somebuckets:fluid_bucket", "model": <vessel model>}}. */
    public record Unbaked(ResourceLocation model) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("model").forGetter(Unbaked::model)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(model);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context) {
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel resolved = baker.getModel(model);
            TextureSlots textures = resolved.getTopTextureSlots();
            List<BakedQuad> quads = resolved.bakeTopGeometry(textures, baker, BlockModelRotation.X0_Y0).getAll();
            return new FluidBucketModel(quads, ModelRenderProperties.fromResolvedModel(baker, resolved, textures),
                    FluidMaskGeometry.read());
        }
    }
}
