package com.github.crittscott.somebuckets.loot;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.loot.BucketLootTables.Reward;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;
import net.minecraftforge.data.event.GatherDataEvent;

/** Generates Forge's inject-table modifiers from the shared structure-loot manifest. */
public final class ForgeBucketLootProvider extends GlobalLootModifierProvider {
    /** Writes modifier resources and their global registration list to the pack output. */
    public ForgeBucketLootProvider(PackOutput output) {
        super(output, SomeBuckets.MODID);
    }

    /** Registers the modifier provider when server data generation is requested. */
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new ForgeBucketLootProvider(event.getGenerator().getPackOutput()));
    }

    @Override
    protected void start() {
        for (Reward reward : Reward.values()) {
            LootItemCondition.Builder[] targets = reward.targets().stream()
                    .map(LootTableIdCondition::builder)
                    .toArray(LootItemCondition.Builder[]::new);
            LootItemCondition[] conditions = {AnyOfCondition.anyOf(targets).build()};
            add(reward.id(), new AddTableLootModifier(conditions, reward.injectTable()));
        }
    }
}
