package com.github.crittscott.somebuckets.data;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.loot.AddTableLootModifier;
import com.github.crittscott.somebuckets.loot.BucketLootTables;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;

import java.util.concurrent.CompletableFuture;

/** Generates Forge global loot modifiers from the cross-loader bucket-loot manifest. */
public final class BucketLootModifierProvider extends GlobalLootModifierProvider {
    public BucketLootModifierProvider(PackOutput output,
                                      CompletableFuture<HolderLookup.Provider> registries) {
        super(output, SomeBuckets.MODID, registries);
    }

    @Override
    protected void start(HolderLookup.Provider registries) {
        for (BucketLootTables.Reward reward : BucketLootTables.rewards()) {
            add(reward.id(), new AddTableLootModifier(targetCondition(reward), reward.injectTable()));
        }
    }

    private static LootItemCondition[] targetCondition(BucketLootTables.Reward reward) {
        LootItemCondition.Builder[] targets = reward.targets().stream()
                .map(LootTableIdCondition::builder)
                .toArray(LootItemCondition.Builder[]::new);
        return new LootItemCondition[]{AnyOfCondition.anyOf(targets).build()};
    }
}
