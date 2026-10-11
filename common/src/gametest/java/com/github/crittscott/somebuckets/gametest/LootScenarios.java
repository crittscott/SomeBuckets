package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.loot.BucketLootTables;
import com.github.crittscott.somebuckets.loot.BucketLootTables.Reward;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/** Loader-neutral structure-loot manifest scenarios. */
final class LootScenarios {
    private LootScenarios() {}

    static void loot_manifest_has_intended_targets_and_overlaps(GameTestHelper helper) {
        GameTestSupport.check(Reward.BIG_BUCKET.targets().size() == 26,
                "Big Bucket did not have 26 non-village structure targets");
        GameTestSupport.check(Reward.JUNK_BUCKET.targets().size() == 16,
                "Junk Bucket did not have all 16 village targets");

        assertRewards("village/village_armorer", Reward.JUNK_BUCKET);
        assertRewards("stronghold_library", Reward.BIG_BUCKET, Reward.TRASH_BUCKET, Reward.MOB_BUCKET);
        assertRewards("bastion_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_BASTION);
        assertRewards("buried_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_OCEAN);
        assertRewards("ancient_city_ice_box", Reward.BIG_BUCKET, Reward.HUGE_POWDER_SNOW_BUCKET);
        assertRewards("spawn_bonus_chest");
        helper.succeed();
    }

    /**
     * Every reward's {@code somebuckets:inject/<reward>} loot table loads from the bundled data pack,
     * so data packs can override each roll by replacing that table.
     *
     * <p>Automation-only contract; in game, {@code /loot give @s loot somebuckets:inject/mob_bucket}
     * repeated about twenty times yields a Mob Bucket.
     */
    static void every_reward_has_an_inject_table(GameTestHelper helper) {
        for (Reward reward : Reward.values()) {
            LootTable table = helper.getLevel().getServer().getLootData().getLootTable(reward.injectTable());
            GameTestSupport.check(table != LootTable.EMPTY, reward.injectTable() + " did not load");
        }
        helper.succeed();
    }

    private static void assertRewards(String chestPath, Reward... expected) {
        ResourceLocation id = new ResourceLocation("minecraft", "chests/" + chestPath);
        List<Reward> actual = BucketLootTables.rewardsFor(id);
        GameTestSupport.check(actual.equals(List.of(expected)),
                id + " rewards were " + actual + " instead of " + List.of(expected));
    }
}
