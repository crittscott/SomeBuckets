package com.github.crittscott.somebuckets.data;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.loot.AddBucketLootModifier;
import com.github.crittscott.somebuckets.loot.BucketLootTables.Reward;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

/** Generates NeoForge loot modifiers from the shared reward manifest. */
public final class NeoForgeBucketLootModifierProvider extends GlobalLootModifierProvider {
    /** Writes loot modifiers and their global registration index to the pack output. */
    public NeoForgeBucketLootModifierProvider(PackOutput output,
            CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, SomeBuckets.MODID);
    }

    /** Registers the provider when server data generation is requested. */
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new NeoForgeBucketLootModifierProvider(event.getGenerator().getPackOutput(),
                        event.getLookupProvider()));
    }

    @Override
    protected void start() {
        for (Reward reward : Reward.values()) {
            LootItemCondition.Builder[] targets = reward.targets().stream()
                    .map(LootTableIdCondition::builder)
                    .toArray(LootItemCondition.Builder[]::new);
            LootItemCondition[] conditions = {
                    AnyOfCondition.anyOf(targets).build(),
                    LootItemRandomChanceCondition.randomChance(reward.chance()).build()
            };
            add(reward.modifierId().getPath(), new AddBucketLootModifier(conditions,
                    BuiltInRegistries.ITEM.get(reward.itemId()), reward.powderUnits()));
        }
    }
}
