package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.util.NBTUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Matches one of this mod's buckets only while it is empty.
 * <p>
 * A bucket holding content returns itself as a crafting remainder, so a recipe that consumes a bucket as
 * material must reject filled ones or the ingredient would survive the craft.
 */
public final class EmptyBucketIngredient extends AbstractIngredient {

    public static final ResourceLocation ID = new ResourceLocation("somebuckets", "empty_bucket");

    private final Item item;

    private EmptyBucketIngredient(Item item) {
        super(Stream.of(new EmptyBucketValue(item)));
        this.item = item;
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        return input != null && input.is(this.item) && NBTUtil.isEmptyBucket(input);
    }

    /** NBT-sensitive, so the recipe system must call {@link #test} rather than match by item id alone. */
    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public JsonElement toJson() {
        return toJson(this.item);
    }

    private static JsonObject toJson(Item item) {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        json.addProperty("item", ForgeRegistries.ITEMS.getKey(item).toString());
        return json;
    }

    private record EmptyBucketValue(Item item) implements Ingredient.Value {
        @Override
        public Collection<ItemStack> getItems() {
            return List.of(new ItemStack(item));
        }

        @Override
        public JsonObject serialize() {
            return toJson(item);
        }
    }

    public static final class Serializer implements IIngredientSerializer<EmptyBucketIngredient> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {}

        @Override
        public EmptyBucketIngredient parse(JsonObject json) {
            return new EmptyBucketIngredient(readItem(new ResourceLocation(GsonHelper.getAsString(json, "item"))));
        }

        @Override
        public EmptyBucketIngredient parse(FriendlyByteBuf buffer) {
            return new EmptyBucketIngredient(readItem(buffer.readResourceLocation()));
        }

        @Override
        public void write(FriendlyByteBuf buffer, EmptyBucketIngredient ingredient) {
            buffer.writeResourceLocation(ForgeRegistries.ITEMS.getKey(ingredient.item));
        }

        private static Item readItem(ResourceLocation id) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) throw new JsonSyntaxException("Unknown item '" + id + "'");
            return item;
        }
    }

    public static void register() {
        CraftingHelper.register(ID, Serializer.INSTANCE);
    }
}
