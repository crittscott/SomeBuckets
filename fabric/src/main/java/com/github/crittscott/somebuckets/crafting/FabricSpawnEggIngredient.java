package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;

/** Fabric custom ingredient matching every loaded vanilla-style spawn egg. */
public final class FabricSpawnEggIngredient implements CustomIngredient {
    public static final ResourceLocation ID = new ResourceLocation(SomeBuckets.MODID, "spawn_egg");
    public static final Serializer SERIALIZER = new Serializer();

    @Override public boolean test(ItemStack stack) { return stack.getItem() instanceof SpawnEggItem; }
    @Override public List<ItemStack> getMatchingStacks() {
        return BuiltInRegistries.ITEM.stream().filter(SpawnEggItem.class::isInstance)
                .map(ItemStack::new).toList();
    }
    @Override public boolean requiresTesting() { return false; }
    @Override public CustomIngredientSerializer<?> getSerializer() { return SERIALIZER; }

    public static void register() { CustomIngredientSerializer.register(SERIALIZER); }

    public static final class Serializer implements CustomIngredientSerializer<FabricSpawnEggIngredient> {
        @Override public ResourceLocation getIdentifier() { return ID; }
        @Override public FabricSpawnEggIngredient read(JsonObject json) { return new FabricSpawnEggIngredient(); }
        @Override public void write(JsonObject json, FabricSpawnEggIngredient ingredient) {}
        @Override public FabricSpawnEggIngredient read(FriendlyByteBuf buf) { return new FabricSpawnEggIngredient(); }
        @Override public void write(FriendlyByteBuf buf, FabricSpawnEggIngredient ingredient) {}
    }
}
