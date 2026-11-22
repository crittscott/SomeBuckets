package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.TwoColorPalette.Colors;
import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.Constant;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import org.joml.Matrix4fc;

import java.util.List;
import java.util.Optional;

/** Includes the completed palette in GUI model identity so a pending bucket redraws when ready. */
@Environment(EnvType.CLIENT)
public final class MobBucketModel implements ItemModel {
    private static final Object MISSING_PALETTE = new Object();

    private final ItemModel delegate;

    private MobBucketModel(ItemModel delegate) {
        this.delegate = delegate;
    }

    /** The bucket vessel and its two tinted overlays use the normal block-model renderer. */
    public record Unbaked(Identifier model) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = Identifier.CODEC.fieldOf("model")
                .xmap(Unbaked::new, Unbaked::model);

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transform) {
            return new MobBucketModel(new CuboidItemModelWrapper.Unbaked(model, Optional.empty(),
                    List.of(new Constant(-1), new MobEggColors.Tint(0), new MobEggColors.Tint(1))).bake(context, transform));
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(model);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext context, @Nullable ClientLevel level,
                       @Nullable ItemOwner entity, int seed) {
        Colors colors = MobEggColors.colors(stack);
        state.appendModelIdentityElement(colors == null ? MISSING_PALETTE : colors);
        if (MobEggColors.pending(stack)) state.setAnimated();
        delegate.update(state, stack, resolver, context, level, entity, seed);
    }


}
