package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.loot.BucketLootTables;
import com.github.crittscott.somebuckets.loot.BucketLootTables.Reward;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

public final class LootGameTests {
    private static final int TRIALS = 1000;

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void loot_manifest_has_intended_targets_and_overlaps(GameTestHelper helper) {
        GameTestSupport.check(BucketLootTables.BIG_BUCKET_TARGETS.size() == 26,
                "Big Bucket did not have 26 non-village structure targets");
        GameTestSupport.check(BucketLootTables.JUNK_BUCKET_TARGETS.size() == 16,
                "Junk Bucket did not have all 16 village targets");

        assertRewards("village/village_armorer", Reward.JUNK_BUCKET);
        assertRewards("stronghold_library", Reward.BIG_BUCKET, Reward.TRASH_BUCKET, Reward.MOB_BUCKET);
        assertRewards("bastion_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_TEN);
        assertRewards("buried_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_FIVE);
        assertRewards("ancient_city_ice_box", Reward.BIG_BUCKET, Reward.HUGE_POWDER_SNOW_BUCKET);
        assertRewards("spawn_bonus_chest");
        helper.succeed();
    }

    /**
     * Forge declares its structure-loot injection as data-driven global-loot-modifier JSON that can be
     * diffed against {@link BucketLootTables} directly. Fabric injects the same rewards through a
     * {@code LootTableEvents.MODIFY} callback registered in Java, so there is no declarative resource to
     * diff; instead this rolls the already-modified, server-resolved tables many times and checks that
     * each manifest reward actually turns up.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public void fabric_loot_registration_adds_manifest_rewards_to_target_tables(GameTestHelper helper) {
        assertInjected(helper, "village/village_armorer", Reward.JUNK_BUCKET);
        assertInjected(helper, "stronghold_library", Reward.BIG_BUCKET, Reward.TRASH_BUCKET, Reward.MOB_BUCKET);
        assertInjected(helper, "bastion_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_TEN);
        assertInjected(helper, "buried_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_FIVE);
        assertInjected(helper, "ancient_city_ice_box", Reward.BIG_BUCKET, Reward.HUGE_POWDER_SNOW_BUCKET);
        helper.succeed();
    }

    private void assertInjected(GameTestHelper helper, String chestPath, Reward... expected) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = new ResourceLocation("minecraft", "chests/" + chestPath);
        LootTable table = server.getLootData().getLootTable(id);
        GameTestSupport.check(table != LootTable.EMPTY, "Loot table " + id + " did not resolve");

        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.getLevel().getSharedSpawnPos().getCenter())
                .create(LootContextParamSets.CHEST);

        boolean[] seen = new boolean[expected.length];
        for (int trial = 0; trial < TRIALS; trial++) {
            List<ItemStack> generated = table.getRandomItems(params);
            for (int i = 0; i < expected.length; i++) {
                Item item = BuiltInRegistries.ITEM.get(expected[i].itemId());
                if (!seen[i] && generated.stream().anyMatch(stack -> stack.is(item))) seen[i] = true;
            }
        }

        for (int i = 0; i < expected.length; i++) {
            GameTestSupport.check(seen[i], chestPath + " never produced " + expected[i]
                    + " across " + TRIALS + " rolls");
        }
    }

    private void assertRewards(String chestPath, Reward... expected) {
        ResourceLocation id = new ResourceLocation("minecraft", "chests/" + chestPath);
        List<Reward> actual = BucketLootTables.rewardsFor(id);
        GameTestSupport.check(actual.equals(List.of(expected)),
                id + " rewards were " + actual + " instead of " + List.of(expected));
    }
}
