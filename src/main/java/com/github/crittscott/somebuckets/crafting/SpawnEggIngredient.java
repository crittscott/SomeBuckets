package com.github.crittscott.somebuckets.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.stream.Stream;

/** Matches every loaded item that participates in Minecraft's standard spawn-egg system. */
public final class SpawnEggIngredient extends AbstractIngredient {
    public static final ResourceLocation ID = new ResourceLocation("somebuckets", "spawn_egg");

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
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        return json();
    }

    private static JsonObject json() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        return json;
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

        @Override
        public JsonObject serialize() {
            return json();
        }
    }

    public static final class Serializer implements IIngredientSerializer<SpawnEggIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {}

        @Override
        public SpawnEggIngredient parse(JsonObject json) {
            return new SpawnEggIngredient();
        }

        @Override
        public SpawnEggIngredient parse(FriendlyByteBuf buffer) {
            return new SpawnEggIngredient();
        }

        @Override
        public void write(FriendlyByteBuf buffer, SpawnEggIngredient ingredient) {}
    }

    public static void register() {
        CraftingHelper.register(ID, Serializer.INSTANCE);
    }
}
