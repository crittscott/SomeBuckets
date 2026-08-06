package com.github.crittscott.somebuckets.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.DynamicFluidContainerModel;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A fluid container item model that colors its fluid layer from the held stack rather than from the
 * fluid alone.
 *
 * <p>{@code forge:fluid_container} bakes one model per fluid and takes its color from the
 * stack-independent {@link IClientFluidTypeExtensions#getTintColor()}. Fluids that carry their
 * identity in the {@link FluidStack} tag rather than in the registry entry — a potion fluid, a dyed
 * or mixed fluid — override only {@link IClientFluidTypeExtensions#getTintColor(FluidStack)}, so
 * every variant renders identically in the untinted still texture.
 *
 * <p>This geometry delegates to {@code forge:fluid_container} for the shape and sprite, then
 * multiplies the fluid layer by the stack-aware tint when the model is resolved for an item. Only
 * quads drawn with the fluid's still sprite are touched; the vessel and any cover layer are left
 * alone. Recolored quads carry no tint index, so an item color handler cannot tint them a second
 * time.
 */
@OnlyIn(Dist.CLIENT)
public final class NbtFluidContainerModel implements IUnbakedGeometry<NbtFluidContainerModel> {
    private final IUnbakedGeometry<?> delegate;

    private NbtFluidContainerModel(IUnbakedGeometry<?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                           ItemOverrides overrides, ResourceLocation modelLocation) {
        BakedModel baked = delegate.bake(context, baker, spriteGetter, modelState, overrides, modelLocation);
        return new OverrideSwap(baked, new StackTintOverrides(baked.getOverrides()));
    }

    public static final class Loader implements IGeometryLoader<NbtFluidContainerModel> {
        public static final Loader INSTANCE = new Loader();
        public static final String NAME = "nbt_fluid_container";

        private Loader() {}

        @Override
        public NbtFluidContainerModel read(JsonObject modelContents, JsonDeserializationContext context) {
            JsonObject delegateContents = modelContents.deepCopy();
            // The stack tint is applied per resolved model below, so the stack-independent tint
            // must not also be baked into the quads.
            delegateContents.addProperty("apply_tint", false);
            return new NbtFluidContainerModel(
                    DynamicFluidContainerModel.Loader.INSTANCE.read(delegateContents, context));
        }
    }

    /** Carries the delegate's geometry with a different override handler in front of it. */
    private static final class OverrideSwap extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        private OverrideSwap(BakedModel originalModel, ItemOverrides overrides) {
            super(originalModel);
            this.overrides = overrides;
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }

    /**
     * Resolves the delegate's model for the stack — which already selects the right fluid and
     * honors the model's own JSON overrides — and wraps it when the stack asks for a color the
     * baked model could not know.
     */
    private static final class StackTintOverrides extends ItemOverrides {
        /** Bounds the cache against a fluid whose tint varies continuously with its tag. */
        private static final int CACHE_LIMIT = 256;

        private final ItemOverrides nested;
        private final Map<TintKey, BakedModel> models = new ConcurrentHashMap<>();

        private StackTintOverrides(ItemOverrides nested) {
            this.nested = nested;
        }

        @Nullable
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            BakedModel resolved = nested.resolve(model, stack, level, entity, seed);
            if (resolved == null) return null;

            FluidStack contents = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            if (contents.isEmpty()) return resolved;

            IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(contents.getFluid());
            ResourceLocation stillTexture = extensions.getStillTexture(contents);
            if (stillTexture == null) return resolved;

            int tint = extensions.getTintColor(contents) & 0xFFFFFF;
            if (tint == 0xFFFFFF) return resolved;

            if (models.size() >= CACHE_LIMIT) models.clear();
            return models.computeIfAbsent(new TintKey(resolved, stillTexture, tint),
                    key -> new TintedFluidLayer(key.model(), key.stillTexture(), key.tint()));
        }
    }

    private record TintKey(BakedModel model, ResourceLocation stillTexture, int tint) {}

    /**
     * The side and render type are null for some render calls, which the record handles. The two
     * {@code getQuads} overloads are keyed apart because a delegate may answer them differently.
     */
    private record QuadKey(@Nullable Direction side, @Nullable RenderType renderType, boolean withModelData) {}

    /**
     * Multiplies the quads drawn with {@code stillTexture} by {@code tint}. Item rendering walks
     * render passes and then asks each pass for its quads per face and render type, so both are
     * wrapped and the recolored results are held rather than rebuilt every frame.
     */
    private static final class TintedFluidLayer extends BakedModelWrapper<BakedModel> {
        private final ResourceLocation stillTexture;
        private final int tint;
        private final Map<QuadKey, List<BakedQuad>> quads = new ConcurrentHashMap<>();
        private final Map<Boolean, List<BakedModel>> passes = new ConcurrentHashMap<>();

        private TintedFluidLayer(BakedModel originalModel, ResourceLocation stillTexture, int tint) {
            super(originalModel);
            this.stillTexture = stillTexture;
            this.tint = tint;
        }

        /**
         * Applies the delegate's transform but keeps this wrapper. The inherited version returns
         * whatever the delegate returns, which is the delegate itself, and item rendering uses that
         * return value for the render passes and quads that follow.
         */
        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack,
                                         boolean leftHand) {
            originalModel.applyTransform(context, poseStack, leftHand);
            return this;
        }

        /** The delegate's pass list depends only on {@code fabulous}, so only that keys the cache. */
        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return passes.computeIfAbsent(fabulous, key -> {
                List<BakedModel> original = originalModel.getRenderPasses(stack, key);
                List<BakedModel> wrapped = new ArrayList<>(original.size());
                for (BakedModel pass : original) {
                    // A model that reports itself as its only pass becomes this wrapper, which
                    // already delegates its quads.
                    wrapped.add(pass == originalModel ? this : new TintedFluidLayer(pass, stillTexture, tint));
                }
                return List.copyOf(wrapped);
            });
        }

        /**
         * Item rendering calls this overload, so the recolor has to be applied here. {@code state}
         * is null and {@code rand} unused for items, so neither keys the cache.
         */
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource rand) {
            return quads.computeIfAbsent(new QuadKey(side, null, false),
                    key -> recolor(originalModel.getQuads(state, key.side(), rand)));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource rand, ModelData data, @Nullable RenderType renderType) {
            return quads.computeIfAbsent(new QuadKey(side, renderType, true),
                    key -> recolor(originalModel.getQuads(state, key.side(), rand, data, key.renderType())));
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        private List<BakedQuad> recolor(List<BakedQuad> source) {
            List<BakedQuad> out = new ArrayList<>(source.size());
            for (BakedQuad quad : source) {
                if (!stillTexture.equals(quad.getSprite().contents().name())) {
                    out.add(quad);
                    continue;
                }
                int[] vertices = quad.getVertices().clone();
                for (int vertex = 0; vertex < vertices.length; vertex += IQuadTransformer.STRIDE) {
                    int index = vertex + IQuadTransformer.COLOR;
                    vertices[index] = multiply(vertices[index], tint);
                }
                out.add(new BakedQuad(vertices, -1, quad.getDirection(), quad.getSprite(), quad.isShade()));
            }
            return List.copyOf(out);
        }

        /**
         * Multiplies a packed ABGR vertex color by an RGB tint. Vertex alpha is left alone: the
         * mask sprite's alpha is what gives the bucket its shape, and a fluid's tint alpha would
         * otherwise cut holes in the vessel.
         */
        private static int multiply(int packedAbgr, int rgb) {
            int red = (packedAbgr & 0xFF) * ((rgb >>> 16) & 0xFF) / 255;
            int green = ((packedAbgr >>> 8) & 0xFF) * ((rgb >>> 8) & 0xFF) / 255;
            int blue = ((packedAbgr >>> 16) & 0xFF) * (rgb & 0xFF) / 255;
            return (packedAbgr & 0xFF000000) | (blue << 16) | (green << 8) | red;
        }
    }
}
