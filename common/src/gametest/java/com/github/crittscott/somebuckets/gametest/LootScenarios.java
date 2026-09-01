package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.loot.BucketLootTables;
import com.github.crittscott.somebuckets.loot.BucketLootTables.Reward;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

/** Loader-neutral structure-loot manifest scenarios. */
final class LootScenarios {
    private LootScenarios() {}

    private static final int INJECTION_TRIALS = 1000;

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

    private static void assertRewards(String chestPath, Reward... expected) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/" + chestPath);
        List<Reward> actual = BucketLootTables.rewardsFor(id);
        GameTestSupport.check(actual.equals(List.of(expected)),
                id + " rewards were " + actual + " instead of " + List.of(expected));
    }

    /**
     * Rolls the server-resolved loot tables repeatedly and requires every applicable manifest reward
     * to appear, exercising the tables as each loader's loot injection leaves them: the Fabric
     * loot-modification callback, or the Forge and NeoForge global loot modifiers.
     */
    static void loot_injection_reaches_target_tables(GameTestHelper helper) {
        assertInjected(helper, "village/village_armorer", Reward.JUNK_BUCKET);
        assertInjected(helper, "stronghold_library", Reward.BIG_BUCKET, Reward.TRASH_BUCKET, Reward.MOB_BUCKET);
        assertInjected(helper, "bastion_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_BASTION);
        assertInjected(helper, "buried_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_OCEAN);
        assertInjected(helper, "ancient_city_ice_box", Reward.BIG_BUCKET, Reward.HUGE_POWDER_SNOW_BUCKET);
        helper.succeed();
    }

    private static void assertInjected(GameTestHelper helper, String chestPath, Reward... expected) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/" + chestPath);
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
        LootTable table = server.reloadableRegistries().getLootTable(key);
        GameTestSupport.check(table != LootTable.EMPTY, "Loot table " + id + " did not resolve");

        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.getLevel().getSharedSpawnPos().getCenter())
                .create(LootContextParamSets.CHEST);

        boolean[] seen = new boolean[expected.length];
        for (int trial = 0; trial < INJECTION_TRIALS; trial++) {
            List<ItemStack> generated = table.getRandomItems(params);
            for (int i = 0; i < expected.length; i++) {
                Item item = BuiltInRegistries.ITEM.get(expected[i].itemId());
                ItemStack matching = generated.stream().filter(stack -> stack.is(item)).findFirst()
                        .orElse(ItemStack.EMPTY);
                if (!seen[i] && !matching.isEmpty()) {
                    if (expected[i].powderUnits() > 0) {
                        GameTestSupport.check(BucketState.getMode(matching) == BucketState.Mode.POWDER_SNOW,
                                expected[i] + " did not carry powder-snow mode");
                        GameTestSupport.check(BucketState.getPowderUnits(matching) == expected[i].powderUnits(),
                                expected[i] + " carried the wrong powder-snow amount");
                    }
                    seen[i] = true;
                }
            }
        }

        for (int i = 0; i < expected.length; i++) {
            GameTestSupport.check(seen[i], chestPath + " never produced " + expected[i]
                    + " across " + INJECTION_TRIALS + " rolls");
        }
    }
}
