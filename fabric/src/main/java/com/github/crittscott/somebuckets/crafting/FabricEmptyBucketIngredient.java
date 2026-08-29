package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Fabric custom ingredient matching one specified Some Buckets item only while empty. */
public record FabricEmptyBucketIngredient(Item item) implements CustomIngredient {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "empty_bucket");
    public static final Serializer SERIALIZER = new Serializer();

    @Override public boolean test(ItemStack stack) { return stack.is(item) && NBTUtil.isEmptyBucket(stack); }
    @Override public List<ItemStack> getMatchingStacks() { return List.of(new ItemStack(item)); }
    @Override public boolean requiresTesting() { return true; }
    @Override public CustomIngredientSerializer<?> getSerializer() { return SERIALIZER; }

    public static void register() {
        CustomIngredientSerializer.register(SERIALIZER);
    }

    public static final class Serializer implements CustomIngredientSerializer<FabricEmptyBucketIngredient> {
        private static final MapCodec<FabricEmptyBucketIngredient> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        BuiltInRegistries.ITEM.byNameCodec().fieldOf("item")
                                .forGetter(FabricEmptyBucketIngredient::item)
                ).apply(instance, FabricEmptyBucketIngredient::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, FabricEmptyBucketIngredient> PACKET_CODEC =
                ByteBufCodecs.registry(Registries.ITEM)
                        .map(FabricEmptyBucketIngredient::new, FabricEmptyBucketIngredient::item);

        @Override public ResourceLocation getIdentifier() { return ID; }

        @Override public MapCodec<FabricEmptyBucketIngredient> getCodec(boolean allowEmpty) { return CODEC; }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FabricEmptyBucketIngredient> getPacketCodec() {
            return PACKET_CODEC;
        }
    }
}
