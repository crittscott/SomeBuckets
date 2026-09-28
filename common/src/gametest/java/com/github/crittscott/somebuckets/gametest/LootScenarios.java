package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.SomeBucketItem;
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
    private static final Reward BIG_BUCKET = reward("big_bucket");
    private static final Reward JUNK_BUCKET = reward("junk_bucket");
    private static final Reward SOURCE_BUCKET_OCEAN = reward("source_bucket_ocean");
    private static final Reward SOURCE_BUCKET_BASTION = reward("source_bucket_bastion");
    private static final Reward TRASH_BUCKET = reward("trash_bucket");
    private static final Reward MOB_BUCKET = reward("mob_bucket");
    private static final Reward HUGE_POWDER_SNOW_BUCKET = reward("huge_powder_snow_bucket");

    /** Automation-only: validates the exact target-table sets and intentional overlaps in the shared loot manifest. */
    static void loot_manifest_has_intended_targets_and_overlaps(GameTestHelper helper) {
        GameTestSupport.check(BIG_BUCKET.targets().size() == 26,
                "Big Bucket did not have 26 non-village structure targets");
        GameTestSupport.check(JUNK_BUCKET.targets().size() == 16,
                "Junk Bucket did not have all 16 village targets");

        assertRewards("village/village_armorer", JUNK_BUCKET);
        assertRewards("stronghold_library", BIG_BUCKET, TRASH_BUCKET, MOB_BUCKET);
        assertRewards("bastion_treasure", BIG_BUCKET, SOURCE_BUCKET_BASTION);
        assertRewards("buried_treasure", BIG_BUCKET, SOURCE_BUCKET_OCEAN);
        assertRewards("ancient_city_ice_box", BIG_BUCKET, HUGE_POWDER_SNOW_BUCKET);
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
     * Automation-only: rolls the server-resolved loot tables repeatedly and requires every
     * applicable manifest reward's inject table to contribute its item, exercising the tables as each
     * loader's loot injection leaves them: the Fabric loot-modification callback, or the Forge and
     * NeoForge global loot modifiers.
     */
    static void loot_injection_reaches_target_tables(GameTestHelper helper) {
        assertInjected(helper, "village/village_armorer", JUNK_BUCKET);
        assertInjected(helper, "stronghold_library", BIG_BUCKET, TRASH_BUCKET, MOB_BUCKET);
        assertInjected(helper, "bastion_treasure", BIG_BUCKET, SOURCE_BUCKET_BASTION);
        assertInjected(helper, "buried_treasure", BIG_BUCKET, SOURCE_BUCKET_OCEAN);
        assertInjected(helper, "ancient_city_ice_box", BIG_BUCKET, HUGE_POWDER_SNOW_BUCKET);
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
                Item item = BuiltInRegistries.ITEM.getValue(awardedItem(expected[i]));
                ItemStack matching = generated.stream().filter(stack -> stack.is(item)).findFirst()
                        .orElse(ItemStack.EMPTY);
                if (!seen[i] && !matching.isEmpty()) {
                    if (expected[i].equals(HUGE_POWDER_SNOW_BUCKET)) {
                        GameTestSupport.check(BucketState.getMode(matching) == BucketState.Mode.POWDER_SNOW,
                                expected[i] + " did not carry powder-snow mode");
                        GameTestSupport.check(BucketState.getPowderUnits(matching)
                                        == BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS,
                                expected[i] + " carried the wrong powder-snow amount");
                        GameTestSupport.check(matching.getMaxStackSize() == SomeBucketItem.FILLED_STACK_SIZE,
                                expected[i] + " did not carry the filled stack size");
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

    /* The item each shipped inject table awards. */
    private static ResourceLocation awardedItem(Reward reward) {
        return switch (reward.id()) {
            case "big_bucket" -> BucketDefinitions.BIG_BUCKET_ID;
            case "junk_bucket" -> BucketDefinitions.JUNK_BUCKET_ID;
            case "source_bucket_ocean", "source_bucket_bastion" -> BucketDefinitions.SOURCE_BUCKET_ID;
            case "trash_bucket" -> BucketDefinitions.TRASH_BUCKET_ID;
            case "mob_bucket" -> BucketDefinitions.MOB_BUCKET_ID;
            case "huge_powder_snow_bucket" -> BucketDefinitions.HUGE_BUCKET_ID;
            default -> throw new IllegalArgumentException("Unknown loot reward " + reward.id());
        };
    }

    private static Reward reward(String id) {
        return BucketLootTables.rewards().stream()
                .filter(reward -> reward.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing loot reward " + id));
    }
}
