package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;

/** Fabric custom ingredient matching every loaded vanilla-style spawn egg. */
public final class FabricSpawnEggIngredient implements CustomIngredient {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "spawn_egg");
    public static final FabricSpawnEggIngredient INSTANCE = new FabricSpawnEggIngredient();
    public static final Serializer SERIALIZER = new Serializer();

    private FabricSpawnEggIngredient() {}

    @Override public boolean test(ItemStack stack) { return stack.getItem() instanceof SpawnEggItem; }
    @Override public List<ItemStack> getMatchingStacks() {
        return BuiltInRegistries.ITEM.stream().filter(SpawnEggItem.class::isInstance)
                .map(ItemStack::new).toList();
    }
    @Override public boolean requiresTesting() { return false; }
    @Override public CustomIngredientSerializer<?> getSerializer() { return SERIALIZER; }

    public static void register() { CustomIngredientSerializer.register(SERIALIZER); }

    public static final class Serializer implements CustomIngredientSerializer<FabricSpawnEggIngredient> {
        private static final MapCodec<FabricSpawnEggIngredient> CODEC = MapCodec.unit(INSTANCE);
        private static final StreamCodec<RegistryFriendlyByteBuf, FabricSpawnEggIngredient> PACKET_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override public ResourceLocation getIdentifier() { return ID; }

        @Override public MapCodec<FabricSpawnEggIngredient> getCodec(boolean allowEmpty) { return CODEC; }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FabricSpawnEggIngredient> getPacketCodec() {
            return PACKET_CODEC;
        }
    }
}
