package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class StorageBucketGameTests {
    private StorageBucketGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_absorbs_and_merges_nearby_items(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_absorbs_and_merges_nearby_items(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_absorbs_multiple_entities_in_one_activation(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_absorbs_multiple_entities_in_one_activation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_skips_pickup_delay(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_skips_pickup_delay(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_splits_large_input_across_entries(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_splits_large_input_across_entries(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void full_junk_bucket_still_merges_compatible_partial_entry(GameTestHelper helper) {
        StorageBucketScenarios.full_junk_bucket_still_merges_compatible_partial_entry(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_world_ejection_is_fifo(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_world_ejection_is_fifo(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_feeds_adult_animal(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_feeds_adult_animal(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_feeds_baby_animal(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_feeds_baby_animal(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_replaces_incompatible_world_stack(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_replaces_incompatible_world_stack(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_world_intake_preserves_excess_entity_items(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_world_intake_preserves_excess_entity_items(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_processes_only_one_world_entity_per_use(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_processes_only_one_world_entity_per_use(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void storage_bucket_with_stored_food_tempts_animal(GameTestHelper helper) {
        StorageBucketScenarios.storage_bucket_with_stored_food_tempts_animal(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void storage_bucket_tempting_follows_content_changes(GameTestHelper helper) {
        StorageBucketScenarios.storage_bucket_tempting_follows_content_changes(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SENSOR_TIMEOUT)
    public static void storage_bucket_with_stored_wheat_tempts_goat(GameTestHelper helper) {
        StorageBucketScenarios.storage_bucket_with_stored_wheat_tempts_goat(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SENSOR_TIMEOUT)
    public static void empty_storage_bucket_does_not_tempt_goat(GameTestHelper helper) {
        StorageBucketScenarios.empty_storage_bucket_does_not_tempt_goat(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void stack_of_empty_junk_buckets_fills_one(GameTestHelper helper) {
        StorageBucketScenarios.stack_of_empty_junk_buckets_fills_one(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void stack_of_empty_junk_buckets_refuses_inventory_insertion(GameTestHelper helper) {
        StorageBucketScenarios.stack_of_empty_junk_buckets_refuses_inventory_insertion(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_leaves_items_dropped_for_another_player(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_leaves_items_dropped_for_another_player(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_refuses_items_carrying_an_inventory(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_refuses_items_carrying_an_inventory(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void sneak_use_in_air_throws_oldest_stack(GameTestHelper helper) {
        StorageBucketScenarios.sneak_use_in_air_throws_oldest_stack(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_inventory_right_click_inserts_and_extracts(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_inventory_right_click_inserts_and_extracts(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_intake_awards_picked_up_statistic(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_intake_awards_picked_up_statistic(helper);
    }
}
