package com.github.crittscott.somebuckets.loot;

import com.github.crittscott.somebuckets.util.NBTUtil;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/** Adds Some Buckets rolls to vanilla structure loot tables on Fabric. */
public final class FabricBucketLoot {
    private FabricBucketLoot() {}

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!source.isBuiltin()) return;

            for (BucketLootTables.Reward reward : BucketLootTables.rewardsFor(id)) {
                tableBuilder.withPool(pool(reward));
            }
        });
    }

    private static LootPool.Builder pool(BucketLootTables.Reward reward) {
        Item item = BuiltInRegistries.ITEM.get(reward.itemId());
        var entry = LootItem.lootTableItem(item);
        if (reward.powderUnits() > 0) {
            CompoundTag tag = new CompoundTag();
            tag.putString(NBTUtil.MODE, "powder_snow");
            tag.putInt(NBTUtil.POWDER_UNITS, reward.powderUnits());
            entry.apply(SetNbtFunction.setTag(tag));
        }

        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(LootItemRandomChanceCondition.randomChance(reward.chance()))
                .add(entry);
    }
}
