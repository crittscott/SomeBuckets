package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.DynamicFluidContainerModel;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Forge's dynamic fluid-container geometry with item-state discovery supplied directly from the
 * bucket's persisted fluid value. This Forge branch disables FluidUtil's item-handler lookup, so the
 * native model cannot discover capability-backed contents on its own.
 */
@OnlyIn(Dist.CLIENT)
public final class StoredFluidContainerModel implements IUnbakedGeometry<StoredFluidContainerModel> {
    private final DynamicFluidContainerModel delegate;

    private StoredFluidContainerModel(DynamicFluidContainerModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                           ItemOverrides overrides) {
        BakedModel emptyModel = delegate.bake(
                context, baker, spriteGetter, modelState, ItemOverrides.EMPTY);
        ItemOverrides directOverrides = new StoredFluidOverrides(
                overrides, delegate, context, baker);
        return new OverrideSwap(emptyModel, directOverrides);
    }

    public static final class Loader implements IGeometryLoader<StoredFluidContainerModel> {
        public static final Loader INSTANCE = new Loader();
        public static final String NAME = "stored_fluid_container";

        private Loader() {}

        @Override
        public StoredFluidContainerModel read(JsonObject modelContents,
                                              JsonDeserializationContext context) {
            DynamicFluidContainerModel delegate =
                    DynamicFluidContainerModel.Loader.INSTANCE.read(modelContents, context);
            return new StoredFluidContainerModel(delegate);
        }
    }

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

    private static final class StoredFluidOverrides extends ItemOverrides {
        private final ItemOverrides nested;
        private final DynamicFluidContainerModel template;
        private final IGeometryBakingContext context;
        private final ModelBaker baker;
        private final Map<Fluid, BakedModel> models = new ConcurrentHashMap<>();

        private StoredFluidOverrides(ItemOverrides nested, DynamicFluidContainerModel template,
                                     IGeometryBakingContext context, ModelBaker baker) {
            this.nested = nested;
            this.template = template;
            this.context = context;
            this.baker = baker;
        }

        @Nullable
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            BakedModel overridden = nested.resolve(model, stack, level, entity, seed);
            if (overridden != model) return overridden;

            FluidStack contents = ForgeFluidStacks.get(stack);
            if (contents.isEmpty()) return model;

            return models.computeIfAbsent(contents.getFluid(), fluid -> template.withFluid(fluid)
                    .bake(context, baker, Material::sprite, BlockModelRotation.X0_Y0,
                            ItemOverrides.EMPTY));
        }
    }
}
