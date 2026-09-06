package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class AutomationGameTests {
    private AutomationGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_big_bucket_collects_world_source(GameTestHelper helper) {
        AutomationScenarios.dispenser_big_bucket_collects_world_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_huge_bucket_collects_world_source(GameTestHelper helper) {
        AutomationScenarios.dispenser_huge_bucket_collects_world_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_big_bucket_places_world_fluid_and_consumes_unit(GameTestHelper helper) {
        AutomationScenarios.dispenser_big_bucket_places_world_fluid_and_consumes_unit(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_fluid_does_not_fall_through_solid_front_block(GameTestHelper helper) {
        AutomationScenarios.dispenser_fluid_does_not_fall_through_solid_front_block(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_powder_does_not_fall_through_solid_front_block(GameTestHelper helper) {
        AutomationScenarios.dispenser_powder_does_not_fall_through_solid_front_block(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_source_does_not_fall_through_solid_front_block(GameTestHelper helper) {
        AutomationScenarios.dispenser_source_does_not_fall_through_solid_front_block(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_big_bucket_round_trips_powder_snow(GameTestHelper helper) {
        AutomationScenarios.dispenser_big_bucket_round_trips_powder_snow(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_big_bucket_round_trips_full_cauldron(GameTestHelper helper) {
        AutomationScenarios.dispenser_big_bucket_round_trips_full_cauldron(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_source_round_trips_cauldron_without_consumption(GameTestHelper helper) {
        AutomationScenarios.dispenser_source_round_trips_cauldron_without_consumption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_collects_but_does_not_place_powder_cauldron(GameTestHelper helper) {
        AutomationScenarios.dispenser_collects_but_does_not_place_powder_cauldron(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_source_places_repeatedly_without_consumption(GameTestHelper helper) {
        AutomationScenarios.dispenser_source_places_repeatedly_without_consumption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_assigned_source_takes_matching_world_source(GameTestHelper helper) {
        AutomationScenarios.dispenser_assigned_source_takes_matching_world_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_empty_source_milks_adult_cow(GameTestHelper helper) {
        AutomationScenarios.dispenser_empty_source_milks_adult_cow(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_empty_mob_bucket_captures_one_entity(GameTestHelper helper) {
        AutomationScenarios.dispenser_empty_mob_bucket_captures_one_entity(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_stacked_empty_mob_buckets_settle_one_result_and_release_it(GameTestHelper helper) {
        AutomationScenarios.dispenser_stacked_empty_mob_buckets_settle_one_result_and_release_it(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_stacked_empty_buckets_settle_each_bucket_family(GameTestHelper helper) {
        AutomationScenarios.dispenser_stacked_empty_buckets_settle_each_bucket_family(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_full_inventory_ejects_stacked_bucket_result(GameTestHelper helper) {
        AutomationScenarios.dispenser_full_inventory_ejects_stacked_bucket_result(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_nonempty_mob_bucket_captures_matching_entity(GameTestHelper helper) {
        AutomationScenarios.dispenser_nonempty_mob_bucket_captures_matching_entity(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_full_mob_bucket_does_nothing_when_matching_mob_occupies_front(GameTestHelper helper) {
        AutomationScenarios.dispenser_full_mob_bucket_does_nothing_when_matching_mob_occupies_front(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_nonempty_mob_bucket_releases_entity(GameTestHelper helper) {
        AutomationScenarios.dispenser_nonempty_mob_bucket_releases_entity(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_mob_bucket_releases_aquatic_entity_with_water(GameTestHelper helper) {
        AutomationScenarios.dispenser_mob_bucket_releases_aquatic_entity_with_water(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_mob_bucket_releases_second_aquatic_entity_after_front_is_cleared(GameTestHelper helper) {
        AutomationScenarios.dispenser_mob_bucket_releases_second_aquatic_entity_after_front_is_cleared(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_nonempty_mob_bucket_does_not_capture_another_entity(GameTestHelper helper) {
        AutomationScenarios.dispenser_nonempty_mob_bucket_does_not_capture_another_entity(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_junk_bucket_absorbs_and_merges_front_items(GameTestHelper helper) {
        AutomationScenarios.dispenser_junk_bucket_absorbs_and_merges_front_items(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_full_junk_bucket_does_not_eject_when_input_is_blocked(GameTestHelper helper) {
        AutomationScenarios.dispenser_full_junk_bucket_does_not_eject_when_input_is_blocked(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_trash_bucket_replaces_one_front_item(GameTestHelper helper) {
        AutomationScenarios.dispenser_trash_bucket_replaces_one_front_item(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_junk_bucket_feeds_one_adult_animal(GameTestHelper helper) {
        AutomationScenarios.dispenser_junk_bucket_feeds_one_adult_animal(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_feeding_precedes_item_collection(GameTestHelper helper) {
        AutomationScenarios.dispenser_feeding_precedes_item_collection(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_junk_bucket_grows_one_baby_animal(GameTestHelper helper) {
        AutomationScenarios.dispenser_junk_bucket_grows_one_baby_animal(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_animal_blocks_junk_bucket_output_when_it_cannot_be_fed(GameTestHelper helper) {
        AutomationScenarios.dispenser_animal_blocks_junk_bucket_output_when_it_cannot_be_fed(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_claim_denial_preserves_every_automation_path(GameTestHelper helper) {
        AutomationScenarios.dispenser_claim_denial_preserves_every_automation_path(helper);
    }

}
