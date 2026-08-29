package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.stream.Stream;

/**
 * Matches one of this mod's buckets only while it is empty.
 *
 * <p>A bucket holding content returns itself as a crafting remainder, so a recipe that consumes a
 * bucket as material must reject filled ones or the ingredient would survive the craft. The
 * ingredient is component-sensitive, so {@link #isSimple()} is {@code false} and the recipe system
 * calls {@link #test}.
 */
public record EmptyBucketIngredient(Item item) implements ICustomIngredient {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "empty_bucket");

    public static final MapCodec<EmptyBucketIngredient> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item")
                            .forGetter(EmptyBucketIngredient::item)
            ).apply(instance, EmptyBucketIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EmptyBucketIngredient> STREAM_CODEC =
            ByteBufCodecs.registry(Registries.ITEM)
                    .map(EmptyBucketIngredient::new, EmptyBucketIngredient::item);

    public static final IngredientType<EmptyBucketIngredient> TYPE =
            new IngredientType<>(CODEC, STREAM_CODEC);

    private static final DeferredRegister<IngredientType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, SomeBuckets.MODID);

    static {
        TYPES.register(ID.getPath(), () -> TYPE);
    }

    /** Subscribes the ingredient-type registration to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    @Override
    public boolean test(ItemStack stack) {
        return stack.is(item) && NBTUtil.isEmptyBucket(stack);
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(new ItemStack(item));
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE;
    }
}
