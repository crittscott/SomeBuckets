package com.github.crittscott.somebuckets.crafting;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.stream.Stream;

/** Matches every loaded item that participates in Minecraft's standard spawn-egg system. */
public final class SpawnEggIngredient implements ICustomIngredient {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "spawn_egg");

    public static final SpawnEggIngredient INSTANCE = new SpawnEggIngredient();

    public static final MapCodec<SpawnEggIngredient> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnEggIngredient> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public static final IngredientType<SpawnEggIngredient> TYPE =
            new IngredientType<>(CODEC, STREAM_CODEC);

    private static final DeferredRegister<IngredientType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, SomeBuckets.MODID);

    static {
        TYPES.register(ID.getPath(), () -> TYPE);
    }

    private SpawnEggIngredient() {}

    /** Subscribes the ingredient-type registration to the mod event bus. */
    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    @Override
    public boolean test(ItemStack stack) {
        return stack.getItem() instanceof SpawnEggItem;
    }

    @Override
    public Stream<ItemStack> getItems() {
        return BuiltInRegistries.ITEM.stream()
                .filter(SpawnEggItem.class::isInstance)
                .map(ItemStack::new);
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
