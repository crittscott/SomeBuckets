package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Fabric custom ingredient matching one specified Some Buckets item only while empty. */
public record FabricEmptyBucketIngredient(Item item) implements CustomIngredient {
    public static final ResourceLocation ID = new ResourceLocation(SomeBuckets.MODID, "empty_bucket");
    public static final Serializer SERIALIZER = new Serializer();

    @Override public boolean test(ItemStack stack) { return stack.is(item) && NBTUtil.isEmptyBucket(stack); }
    @Override public List<ItemStack> getMatchingStacks() { return List.of(new ItemStack(item)); }
    @Override public boolean requiresTesting() { return true; }
    @Override public CustomIngredientSerializer<?> getSerializer() { return SERIALIZER; }

    public static void register() {
        CustomIngredientSerializer.register(SERIALIZER);
    }

    public static final class Serializer implements CustomIngredientSerializer<FabricEmptyBucketIngredient> {
        @Override public ResourceLocation getIdentifier() { return ID; }

        @Override
        public FabricEmptyBucketIngredient read(JsonObject json) {
            return new FabricEmptyBucketIngredient(readItem(
                    new ResourceLocation(GsonHelper.getAsString(json, "item"))));
        }

        @Override
        public void write(JsonObject json, FabricEmptyBucketIngredient ingredient) {
            json.addProperty("item", BuiltInRegistries.ITEM.getKey(ingredient.item()).toString());
        }

        @Override public FabricEmptyBucketIngredient read(FriendlyByteBuf buf) {
            return new FabricEmptyBucketIngredient(readItem(buf.readResourceLocation()));
        }

        @Override public void write(FriendlyByteBuf buf, FabricEmptyBucketIngredient ingredient) {
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(ingredient.item()));
        }

        private static Item readItem(ResourceLocation id) {
            if (!BuiltInRegistries.ITEM.containsKey(id)) throw new JsonSyntaxException("Unknown item '" + id + "'");
            return BuiltInRegistries.ITEM.get(id);
        }
    }
}
