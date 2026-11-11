package com.github.crittscott.somebuckets.loot;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;

/** Adds Some Buckets rolls to vanilla structure loot tables on Fabric. */
public final class FabricBucketLoot {
    private FabricBucketLoot() {}

    /** Registers the loot-table callback that adds each target table's bucket inject tables. */
    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            for (BucketLootTables.Reward reward : BucketLootTables.rewardsFor(key.identifier())) {
                var injectTable = registries.lookupOrThrow(Registries.LOOT_TABLE).getOrThrow(reward.injectTable());
                tableBuilder.withPool(LootPool.lootPool()
                        .setRolls(Holder.direct(new ConstantValue(1)))
                        .add(NestedLootTable.lootTableReference(injectTable)));
            }
        });
    }
}
