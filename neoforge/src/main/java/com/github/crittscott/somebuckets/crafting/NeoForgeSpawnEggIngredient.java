package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.stream.Stream;

/** Matches every loaded item that participates in Minecraft's standard spawn-egg system. */
public final class NeoForgeSpawnEggIngredient implements ICustomIngredient {
    /** Registry id for the spawn-egg ingredient type. */
    /** Stateless ingredient instance shared by every recipe. */
    public static final NeoForgeSpawnEggIngredient INSTANCE = new NeoForgeSpawnEggIngredient();

    /** Unit map codec for the stateless ingredient. */
    public static final MapCodec<NeoForgeSpawnEggIngredient> CODEC = MapCodec.unit(INSTANCE);
    /** Network codec for the stateless ingredient. */
    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeSpawnEggIngredient> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    /** Registered NeoForge ingredient type. */
    public static final IngredientType<NeoForgeSpawnEggIngredient> TYPE =
            new IngredientType<>(CODEC, STREAM_CODEC);

    private static final DeferredRegister<IngredientType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, SomeBuckets.MODID);

    static {
        TYPES.register(BucketDefinitions.SPAWN_EGG_INGREDIENT_ID.getPath(), () -> TYPE);
    }

    private NeoForgeSpawnEggIngredient() {}

    /** Subscribes the ingredient-type registration to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    @Override
    public boolean test(ItemStack stack) {
        return stack.getItem() instanceof SpawnEggItem;
    }

    @Override
    public Stream<Holder<Item>> items() {
        return BuiltInRegistries.ITEM.stream()
                .filter(SpawnEggItem.class::isInstance)
                .map(Item::builtInRegistryHolder);
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE;
    }
}
