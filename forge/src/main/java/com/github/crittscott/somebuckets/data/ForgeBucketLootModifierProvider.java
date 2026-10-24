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
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

/** Generates Forge loot modifiers from the shared reward manifest. */
public final class ForgeBucketLootModifierProvider extends GlobalLootModifierProvider {
    /** Writes loot modifiers and their global registration index to the pack output. */
    public ForgeBucketLootModifierProvider(PackOutput output,
            CompletableFuture<HolderLookup.Provider> registries) {
        super(output, SomeBuckets.MODID, registries);
    }

    /** Registers the provider when server data generation is requested. */
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new ForgeBucketLootModifierProvider(event.getGenerator().getPackOutput(),
                        event.getLookupProvider()));
    }

    @Override
    protected void start(HolderLookup.Provider registries) {
        for (Reward reward : Reward.values()) {
            LootItemCondition.Builder[] targets = reward.targets().stream()
                    .map(LootTableIdCondition::builder)
                    .toArray(LootItemCondition.Builder[]::new);
            LootItemCondition[] conditions = {
                    AnyOfCondition.anyOf(targets).build(),
                    LootItemRandomChanceCondition.randomChance(reward.chance()).build()
            };
            add(reward.modifierId().getPath(), new AddBucketLootModifier(conditions,
                    BuiltInRegistries.ITEM.getValue(reward.itemId()), reward.powderUnits()));
        }
    }
}
