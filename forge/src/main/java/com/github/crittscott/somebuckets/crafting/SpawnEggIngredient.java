package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.ingredients.AbstractIngredient;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.stream.Stream;

/** Matches every loaded item that participates in Minecraft's standard spawn-egg system. */
public final class SpawnEggIngredient extends AbstractIngredient {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "spawn_egg");
    public static final SpawnEggIngredient INSTANCE = new SpawnEggIngredient();
    public static final MapCodec<SpawnEggIngredient> CODEC = MapCodec.unit(INSTANCE);
    public static final IIngredientSerializer<SpawnEggIngredient> SERIALIZER = new Serializer();

    private SpawnEggIngredient() {
        super(Stream.of(SpawnEggValue.INSTANCE));
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        return input != null && input.getItem() instanceof SpawnEggItem;
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> serializer() {
        return SERIALIZER;
    }

    private enum SpawnEggValue implements Ingredient.Value {
        INSTANCE;

        @Override
        public Collection<ItemStack> getItems() {
            return ForgeRegistries.ITEMS.getValues().stream()
                    .filter(SpawnEggItem.class::isInstance)
                    .map(ItemStack::new)
                    .toList();
        }

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
