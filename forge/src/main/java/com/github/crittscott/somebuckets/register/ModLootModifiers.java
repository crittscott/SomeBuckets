package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.loot.AddBucketLootModifier;
import com.mojang.serialization.MapCodec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers the Forge global-loot-modifier codecs used by Some Buckets. */
public final class ModLootModifiers {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    SomeBuckets.MODID);

    public static final RegistryObject<MapCodec<AddBucketLootModifier>> ADD_BUCKET =
            TYPES.register("add_bucket", () -> AddBucketLootModifier.CODEC);

    private ModLootModifiers() {}

    public static void register(IEventBus eventBus) {
        TYPES.register(eventBus);
    }
}
