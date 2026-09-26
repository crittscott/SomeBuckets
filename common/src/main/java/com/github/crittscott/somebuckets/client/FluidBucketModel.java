package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item model for Big, Huge, and Source Buckets, registered as {@code somebuckets:fluid_bucket}. It
 * renders the vessel model, then, when the bucket holds a fluid, a second layer shaped by the
 * content mask, textured with the fluid's still sprite, and tinted per stack. Fluid layers are built
 * once per sprite and discarded with the model on resource reload.
 */
@Environment(EnvType.CLIENT)
public final class FluidBucketModel implements ItemModel {
    private static final int FLUID_TINT_INDEX = 0;
    private static final int CACHE_LIMIT = 256;
    private static final int EMISSIVE_LIGHT = 15;

    private static volatile Appearance appearance;

    private final BakedModel vessel;
    private final List<FluidMaskGeometry.Face> mask;
    private final Map<LayerKey, BakedModel> fluidLayers = new ConcurrentHashMap<>();

    private FluidBucketModel(BakedModel vessel, List<FluidMaskGeometry.Face> mask) {
        this.vessel = vessel;
        this.mask = mask;
    }

    /** Loader-resolved rendering facts for a stored fluid. */
    public record Look(TextureAtlasSprite sprite, int tint, boolean emissive) {}

    /** Resolves a stored fluid's still sprite, stack-aware ARGB tint, and luminance through loader APIs. */
    @FunctionalInterface
    public interface Appearance {
        /** @return the fluid's look, or {@code null} when it has no still texture */
        @Nullable
        Look look(StoredFluid fluid);
    }

    /** Installs the loader implementation. Called once during client bootstrap. */
    public static void installAppearance(Appearance installed) {
        appearance = Objects.requireNonNull(installed, "fluid appearance");
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable LivingEntity entity, int seed) {
        ItemStackRenderState.LayerRenderState vesselLayer = renderState.newLayer();
        if (stack.hasFoil()) vesselLayer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
        vesselLayer.setupBlockModel(vessel, Sheets.translucentItemSheet());

        StoredFluid stored = BucketState.getStoredFluid(stack);
        if (stored.isEmpty() || mask.isEmpty()) return;
        Appearance current = appearance;
        if (current == null) throw new IllegalStateException("Fluid appearance is not installed");
        Look look = current.look(stored);
        if (look == null) return;

        if (fluidLayers.size() >= CACHE_LIMIT) fluidLayers.clear();
        BakedModel fluid = fluidLayers.computeIfAbsent(new LayerKey(look.sprite(), look.emissive()),
                this::bakeFluidLayer);
        ItemStackRenderState.LayerRenderState fluidLayer = renderState.newLayer();
        fluidLayer.setupBlockModel(fluid, Sheets.translucentItemSheet());
        fluidLayer.prepareTintLayers(FLUID_TINT_INDEX + 1)[FLUID_TINT_INDEX] = look.tint();
    }

    private BakedModel bakeFluidLayer(LayerKey key) {
        int emission = key.emissive() ? EMISSIVE_LIGHT : 0;
        List<BakedQuad> quads = mask.stream()
                .map(face -> FluidMaskGeometry.quad(face, key.sprite(), FLUID_TINT_INDEX, emission))
                .toList();
        return new FluidLayer(quads, vessel);
    }

    /* Sprites compare by identity, which is stable until the atlas is rebuilt with this model. */
    private record LayerKey(TextureAtlasSprite sprite, boolean emissive) {}

    /* Unculled fluid quads that borrow the vessel's transforms and lighting mode. */
    private static final class FluidLayer implements BakedModel {
        private final List<BakedQuad> quads;
        private final BakedModel vessel;

        private FluidLayer(List<BakedQuad> quads, BakedModel vessel) {
            this.quads = quads;
            this.vessel = vessel;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource random) {
            return side == null ? quads : List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return vessel.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return vessel.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return vessel.usesBlockLight();
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return vessel.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return vessel.getTransforms();
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
            resolver.resolve(model);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context) {
            return new FluidBucketModel(context.bake(model), FluidMaskGeometry.read());
        }
    }
}
