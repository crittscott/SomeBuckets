package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class BBGameTests {
    private BBGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_bucket_collects_source(GameTestHelper helper) {
        BBScenarios.empty_bucket_collects_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_big_world_pickup_awards_one_use_and_filled_bucket_criterion(GameTestHelper helper) {
        BBScenarios.player_big_world_pickup_awards_one_use_and_filled_bucket_criterion(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_bucket_collects_matching_source(GameTestHelper helper) {
        BBScenarios.partial_bucket_collects_matching_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void bucket_refuses_different_source_without_mutation(GameTestHelper helper) {
        BBScenarios.bucket_refuses_different_source_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void full_bucket_refuses_another_source(GameTestHelper helper) {
        BBScenarios.full_bucket_refuses_another_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void waterlogged_block_gives_up_only_its_water(GameTestHelper helper) {
        BBScenarios.waterlogged_block_gives_up_only_its_water(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void flowing_fluid_is_not_collected(GameTestHelper helper) {
        BBScenarios.flowing_fluid_is_not_collected(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void placement_consumes_one_unit_and_final_unit_normalizes(GameTestHelper helper) {
        BBScenarios.placement_consumes_one_unit_and_final_unit_normalizes(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void placement_falls_through_solid_clicked_block(GameTestHelper helper) {
        BBScenarios.placement_falls_through_solid_clicked_block(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void placement_waterlogs_liquid_container(GameTestHelper helper) {
        BBScenarios.placement_waterlogs_liquid_container(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_collects_and_places_one_block(GameTestHelper helper) {
        BBScenarios.powder_snow_collects_and_places_one_block(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void failed_powder_snow_placement_is_atomic(GameTestHelper helper) {
        BBScenarios.failed_powder_snow_placement_is_atomic(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_protection_denial_precedes_native_placement(GameTestHelper helper) {
        BBScenarios.powder_snow_protection_denial_precedes_native_placement(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_player_placement_emits_native_observability(GameTestHelper helper) {
        BBScenarios.powder_snow_player_placement_emits_native_observability(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_capacity_is_enforced(GameTestHelper helper) {
        BBScenarios.powder_snow_capacity_is_enforced(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_sneak_use_places_on_existing_block(GameTestHelper helper) {
        BBScenarios.powder_snow_sneak_use_places_on_existing_block(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adult_cow_adds_milk_but_baby_does_not(GameTestHelper helper) {
        BBScenarios.adult_cow_adds_milk_but_baby_does_not(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void drinking_milk_removes_effect_and_consumes_one_unit(GameTestHelper helper) {
        BBScenarios.drinking_milk_removes_effect_and_consumes_one_unit(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void shift_use_in_air_discards_contents(GameTestHelper helper) {
        BBScenarios.shift_use_in_air_discards_contents(helper);
    }

}
