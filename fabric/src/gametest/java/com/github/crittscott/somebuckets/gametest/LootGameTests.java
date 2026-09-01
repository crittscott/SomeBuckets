package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.loot.BucketLootTables.Reward;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
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

public final class LootGameTests {
    private static final int TRIALS = 1000;

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void loot_manifest_has_intended_targets_and_overlaps(GameTestHelper helper) {
        LootScenarios.loot_manifest_has_intended_targets_and_overlaps(helper);
    }

    /**
     * Rolls the server-resolved Fabric loot tables repeatedly and requires every applicable manifest
     * reward to appear, exercising the tables produced by the loot-modification callback.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public void fabric_loot_registration_adds_manifest_rewards_to_target_tables(GameTestHelper helper) {
        assertInjected(helper, "village/village_armorer", Reward.JUNK_BUCKET);
        assertInjected(helper, "stronghold_library", Reward.BIG_BUCKET, Reward.TRASH_BUCKET, Reward.MOB_BUCKET);
        assertInjected(helper, "bastion_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_BASTION);
        assertInjected(helper, "buried_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_OCEAN);
        assertInjected(helper, "ancient_city_ice_box", Reward.BIG_BUCKET, Reward.HUGE_POWDER_SNOW_BUCKET);
        helper.succeed();
    }

    private void assertInjected(GameTestHelper helper, String chestPath, Reward... expected) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/" + chestPath);
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
        LootTable table = server.reloadableRegistries().getLootTable(key);
        GameTestSupport.check(table != LootTable.EMPTY, "Loot table " + id + " did not resolve");

        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.getLevel().getSharedSpawnPos().getCenter())
                .create(LootContextParamSets.CHEST);

        boolean[] seen = new boolean[expected.length];
        for (int trial = 0; trial < TRIALS; trial++) {
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
                    + " across " + TRIALS + " rolls");
        }
    }

}
