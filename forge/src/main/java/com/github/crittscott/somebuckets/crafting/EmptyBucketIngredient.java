package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.BucketState;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.ingredients.AbstractIngredient;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.stream.Stream;

/**
 * Matches one of this mod's buckets only while it is empty.
 * <p>
 * A bucket holding content returns itself as a crafting remainder, so a recipe that consumes a bucket as
 * material must reject filled ones or the ingredient would survive the craft.
 */
public final class EmptyBucketIngredient extends AbstractIngredient {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "empty_bucket");
    public static final MapCodec<EmptyBucketIngredient> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(ingredient -> ingredient.item)
            ).apply(instance, EmptyBucketIngredient::new));
    public static final IIngredientSerializer<EmptyBucketIngredient> SERIALIZER = new Serializer();

    private final Item item;

    private EmptyBucketIngredient(Item item) {
        super(Stream.of(new Ingredient.ItemValue(new ItemStack(item))));
        this.item = item;
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        return input != null && input.is(this.item) && BucketState.isEmptyBucket(input);
    }

    /** NBT-sensitive, so the recipe system must call {@link #test} rather than match by item id alone. */
    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> serializer() {
        return SERIALIZER;
    }

    private static final class Serializer implements IIngredientSerializer<EmptyBucketIngredient> {
        private Serializer() {}

        @Override
        public MapCodec<? extends EmptyBucketIngredient> codec() {
            return CODEC;
        }

        @Override
        public EmptyBucketIngredient read(RegistryFriendlyByteBuf buffer) {
            return new EmptyBucketIngredient(Item.STREAM_CODEC.decode(buffer).get());
        }

        @Override
        public void write(RegistryFriendlyByteBuf buffer, EmptyBucketIngredient ingredient) {
            Item.STREAM_CODEC.encode(buffer, ingredient.item.builtInRegistryHolder());
        }
    }
}
