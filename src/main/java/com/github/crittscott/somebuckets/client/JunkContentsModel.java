package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.NBTUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The Junk Bucket's item model, showing what the bucket is holding.
 *
 * <p>The vessel is an ordinary generated item model. This wrapper resolves the stored stacks for
 * the rendered item and appends the overlay quads {@link JunkIconLayout} builds for them. Item
 * quads carry no cull face, so the overlay joins the unculled list.
 *
 * <p>An arrangement depends only on the item's stored data, so resolved models are cached under the
 * stack's tag and rebuilt only when it changes. Reading the tag is cheaper than deserializing the
 * stored stacks, which then happens only on a miss. The cache is bounded and is discarded with the
 * baked model on resource reload.
 */
@OnlyIn(Dist.CLIENT)
public final class JunkContentsModel {
    private JunkContentsModel() {}

    /** Wraps the baked Junk Bucket model so it draws its contents. */
    public static BakedModel wrap(BakedModel vessel) {
        return new OverrideSwap(vessel, new ContentsOverrides(vessel.getOverrides()));
    }

    /** Carries the vessel's geometry with a different override handler in front of it. */
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

    private record ContentsKey(BakedModel model, CompoundTag tag) {}

    /**
     * The side and render type are null for some render calls, which the record handles. The two
     * {@code getQuads} overloads are keyed apart because a delegate may answer them differently.
     */
    private record QuadKey(@Nullable Direction side, @Nullable RenderType renderType, boolean withModelData) {}

    /** Resolves the vessel for the stack and hangs the stack's contents on it. */
    private static final class ContentsOverrides extends ItemOverrides {
        /** Bounds the cache against buckets holding many different combinations of items. */
        private static final int CACHE_LIMIT = 256;

        private final ItemOverrides nested;
        private final Map<ContentsKey, BakedModel> models = new ConcurrentHashMap<>();

        private ContentsOverrides(ItemOverrides nested) {
            this.nested = nested;
        }

        @Nullable
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            BakedModel resolved = nested.resolve(model, stack, level, entity, seed);
            if (resolved == null) return null;

            CompoundTag tag = stack.getTag();
            if (tag == null) return resolved;

            BakedModel cached = models.get(new ContentsKey(resolved, tag));
            if (cached != null) return cached;

            List<ItemStack> contents = NBTUtil.getStoredItems(stack);
            // Built outside the map: laying the icons out asks the item renderer for other items'
            // models, which resolves their own overrides.
            BakedModel built = contents.isEmpty()
                    ? resolved
                    : new ContentsLayer(resolved, JunkIconLayout.build(contents));

            if (models.size() >= CACHE_LIMIT) models.clear();
            models.put(new ContentsKey(resolved, tag.copy()), built);
            return built;
        }
    }

    /**
     * Adds the overlay to the vessel's quads. Item rendering walks render passes and then asks each
     * pass for its quads per face and render type, so both are wrapped and the combined results are
     * held rather than rebuilt every frame.
     */
    private static final class ContentsLayer extends BakedModelWrapper<BakedModel> {
        private final List<BakedQuad> overlay;
        private final Map<QuadKey, List<BakedQuad>> quads = new ConcurrentHashMap<>();
        private final Map<Boolean, List<BakedModel>> passes = new ConcurrentHashMap<>();

        private ContentsLayer(BakedModel originalModel, List<BakedQuad> overlay) {
            super(originalModel);
            this.overlay = overlay;
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
                    wrapped.add(pass == originalModel ? this : new ContentsLayer(pass, overlay));
                }
                return List.copyOf(wrapped);
            });
        }

        /**
         * Item rendering calls this overload, so it carries the overlay. {@code state} is null and
         * {@code rand} unused for items, so neither keys the cache.
         */
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource rand) {
            return combine(new QuadKey(side, null, false),
                    () -> originalModel.getQuads(state, side, rand));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource rand, ModelData data, @Nullable RenderType renderType) {
            return combine(new QuadKey(side, renderType, true),
                    () -> originalModel.getQuads(state, side, rand, data, renderType));
        }

        /** Appends the overlay to the delegate's quads. Item quads carry no cull face. */
        private List<BakedQuad> combine(QuadKey key, Supplier<List<BakedQuad>> base) {
            return quads.computeIfAbsent(key, cached -> {
                List<BakedQuad> original = base.get();
                if (cached.side() != null || overlay.isEmpty()) return original;

                List<BakedQuad> combined = new ArrayList<>(original.size() + overlay.size());
                combined.addAll(original);
                combined.addAll(overlay);
                return List.copyOf(combined);
            });
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
