package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.ingredients.AbstractIngredient;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/** Matches every loaded item that participates in Minecraft's standard spawn-egg system. */
public final class SpawnEggIngredient extends AbstractIngredient {
    /** Registry id for the spawn-egg ingredient serializer. */
    /** Stateless ingredient instance shared by every recipe. */
    public static final SpawnEggIngredient INSTANCE = new SpawnEggIngredient();
    /** Unit map codec for the stateless ingredient. */
    public static final MapCodec<SpawnEggIngredient> CODEC = MapCodec.unit(INSTANCE);
    /** Forge serializer for spawn-egg ingredients. */
    public static final IIngredientSerializer<SpawnEggIngredient> SERIALIZER = new Serializer();

    private static final DeferredRegister<IIngredientSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.INGREDIENT_SERIALIZERS, SomeBuckets.MODID);

    static {
        SERIALIZERS.register(BucketDefinitions.SPAWN_EGG_INGREDIENT_ID.getPath(), () -> SERIALIZER);
    }

    /** Subscribes the ingredient-serializer registration to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }

    private List<Holder<Item>> items;

    private SpawnEggIngredient() {
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        return input != null && input.getItem() instanceof SpawnEggItem;
    }

    /**
     * Computed lazily rather than at construction, since this singleton is created well before
     * other mods' items are registered; a constructor-baked list would miss modded spawn eggs.
     */
    @Override
    public Stream<Holder<Item>> items() {
        if (items == null) {
            items = Collections.unmodifiableList(ForgeRegistries.ITEMS.getValues().stream()
                    .filter(SpawnEggItem.class::isInstance)
                    .<Holder<Item>>map(Item::builtInRegistryHolder)
                    .toList());
        }
        return items.stream();
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> serializer() {
        return SERIALIZER;
    }

    private static final class Serializer implements IIngredientSerializer<SpawnEggIngredient> {
        private Serializer() {}

        @Override
        public MapCodec<? extends SpawnEggIngredient> codec() {
            return CODEC;
        }

        @Override
        public SpawnEggIngredient read(RegistryFriendlyByteBuf buffer) {
            return INSTANCE;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer, SpawnEggIngredient ingredient) {}
    }
}
