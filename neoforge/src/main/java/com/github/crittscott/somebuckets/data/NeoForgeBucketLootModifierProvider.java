package com.github.crittscott.somebuckets.data;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.loot.BucketLootTables;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.AddTableLootModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Generates NeoForge global loot modifiers from the cross-loader bucket-loot manifest. */
public final class NeoForgeBucketLootModifierProvider extends GlobalLootModifierProvider {
    public NeoForgeBucketLootModifierProvider(PackOutput output,
                                      CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, SomeBuckets.MODID);
    }

    @Override
    protected void start() {
        for (BucketLootTables.Reward reward : BucketLootTables.rewards()) {
            add(reward.id(), new AddTableLootModifier(Optional.of(targetCondition(reward)),
                    IGlobalLootModifier.DEFAULT_PRIORITY, reward.injectTable()));
        }
    }

    private static Holder<LootItemCondition> targetCondition(BucketLootTables.Reward reward) {
        LootItemCondition.Builder[] targets = reward.targets().stream()
                .map(LootTableIdCondition::builder)
                .toArray(LootItemCondition.Builder[]::new);
        return Holder.direct(AnyOfCondition.anyOf(targets).build());
    }
}
