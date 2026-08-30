package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
    public static void junk_bucket_screen_insert_and_fifo_extract(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_screen_insert_and_fifo_extract(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void storage_eligibility_rule_accepts_buckets_and_refuses_containers(GameTestHelper helper) {
        StorageBucketScenarios.storage_eligibility_rule_accepts_buckets_and_refuses_containers(helper);
    }

}
