package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.loot.AddBucketLootModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registers the NeoForge global-loot-modifier codecs used by Some Buckets. */
public final class ModLootModifiers {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    SomeBuckets.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddBucketLootModifier>>
            ADD_BUCKET = TYPES.register("add_bucket", () -> AddBucketLootModifier.CODEC);

    private ModLootModifiers() {}

    public static void register(IEventBus eventBus) {
        TYPES.register(eventBus);
    }
}
