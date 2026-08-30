package com.github.crittscott.somebuckets.loot;

import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/** Adds Some Buckets rolls to vanilla structure loot tables on Fabric. */
public final class FabricBucketLoot {
    private FabricBucketLoot() {}

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) return;

            for (BucketLootTables.Reward reward : BucketLootTables.rewardsFor(key.location())) {
                tableBuilder.withPool(pool(reward));
            }
        });
    }

    private static LootPool.Builder pool(BucketLootTables.Reward reward) {
        Item item = BuiltInRegistries.ITEM.get(reward.itemId());
        var entry = LootItem.lootTableItem(item);
        if (reward.powderUnits() > 0) {
            entry.apply(SetComponentsFunction.setComponent(
                    ModDataComponentTypes.POWDER_UNITS, reward.powderUnits()));
        }

        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(LootItemRandomChanceCondition.randomChance(reward.chance()))
                .add(entry);
    }
}
