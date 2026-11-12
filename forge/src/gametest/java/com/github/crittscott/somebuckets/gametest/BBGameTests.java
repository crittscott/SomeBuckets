package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/** Forge GameTest wrappers for {@link BBScenarios}. */
public final class BBGameTests {
    private BBGameTests() {}

    /** See {@link BBScenarios#empty_bucket_collects_source}. */
    public static void empty_bucket_collects_source(GameTestHelper helper) {
        BBScenarios.empty_bucket_collects_source(helper);
    }

    /** See {@link BBScenarios#player_big_world_pickup_awards_one_use_and_filled_bucket_criterion}. */
    public static void player_big_world_pickup_awards_one_use_and_filled_bucket_criterion(GameTestHelper helper) {
        BBScenarios.player_big_world_pickup_awards_one_use_and_filled_bucket_criterion(helper);
    }

    /** See {@link BBScenarios#partial_bucket_collects_matching_source}. */
    public static void partial_bucket_collects_matching_source(GameTestHelper helper) {
        BBScenarios.partial_bucket_collects_matching_source(helper);
    }

    /** See {@link BBScenarios#bucket_refuses_different_source_without_mutation}. */
    public static void bucket_refuses_different_source_without_mutation(GameTestHelper helper) {
        BBScenarios.bucket_refuses_different_source_without_mutation(helper);
    }

    /** See {@link BBScenarios#full_bucket_refuses_another_source}. */
    public static void full_bucket_refuses_another_source(GameTestHelper helper) {
        BBScenarios.full_bucket_refuses_another_source(helper);
    }

    /** See {@link BBScenarios#waterlogged_block_gives_up_only_its_water}. */
    public static void waterlogged_block_gives_up_only_its_water(GameTestHelper helper) {
        BBScenarios.waterlogged_block_gives_up_only_its_water(helper);
    }

    /** See {@link BBScenarios#flowing_fluid_is_not_collected}. */
    public static void flowing_fluid_is_not_collected(GameTestHelper helper) {
        BBScenarios.flowing_fluid_is_not_collected(helper);
    }

    /** See {@link BBScenarios#placement_consumes_one_unit_and_final_unit_normalizes}. */
    public static void placement_consumes_one_unit_and_final_unit_normalizes(GameTestHelper helper) {
        BBScenarios.placement_consumes_one_unit_and_final_unit_normalizes(helper);
    }

    /** See {@link BBScenarios#placement_falls_through_solid_clicked_block}. */
    public static void placement_falls_through_solid_clicked_block(GameTestHelper helper) {
        BBScenarios.placement_falls_through_solid_clicked_block(helper);
    }

    /** See {@link BBScenarios#placement_waterlogs_liquid_container}. */
    public static void placement_waterlogs_liquid_container(GameTestHelper helper) {
        BBScenarios.placement_waterlogs_liquid_container(helper);
    }

    /** See {@link BBScenarios#powder_snow_collects_and_places_one_block}. */
    public static void powder_snow_collects_and_places_one_block(GameTestHelper helper) {
        BBScenarios.powder_snow_collects_and_places_one_block(helper);
    }

    /** See {@link BBScenarios#failed_powder_snow_placement_is_atomic}. */
    public static void failed_powder_snow_placement_is_atomic(GameTestHelper helper) {
        BBScenarios.failed_powder_snow_placement_is_atomic(helper);
    }

    /** See {@link BBScenarios#powder_snow_protection_denial_precedes_native_placement}. */
    public static void powder_snow_protection_denial_precedes_native_placement(GameTestHelper helper) {
        BBScenarios.powder_snow_protection_denial_precedes_native_placement(helper);
    }

    /** See {@link BBScenarios#powder_snow_player_placement_emits_native_observability}. */
    public static void powder_snow_player_placement_emits_native_observability(GameTestHelper helper) {
        BBScenarios.powder_snow_player_placement_emits_native_observability(helper);
    }

    /** See {@link BBScenarios#fluid_player_placement_emits_native_observability}. */
    public static void fluid_player_placement_emits_native_observability(GameTestHelper helper) {
        BBScenarios.fluid_player_placement_emits_native_observability(helper);
    }

    /** See {@link BBScenarios#powder_snow_capacity_is_enforced}. */
    public static void powder_snow_capacity_is_enforced(GameTestHelper helper) {
        BBScenarios.powder_snow_capacity_is_enforced(helper);
    }

    /** See {@link BBScenarios#powder_snow_sneak_use_places_on_existing_block}. */
    public static void powder_snow_sneak_use_places_on_existing_block(GameTestHelper helper) {
        BBScenarios.powder_snow_sneak_use_places_on_existing_block(helper);
    }

    /** See {@link BBScenarios#adult_cow_adds_milk_but_baby_does_not}. */
    public static void adult_cow_adds_milk_but_baby_does_not(GameTestHelper helper) {
        BBScenarios.adult_cow_adds_milk_but_baby_does_not(helper);
    }

    /** See {@link BBScenarios#drinking_milk_removes_effect_and_consumes_one_unit}. */
    public static void drinking_milk_removes_effect_and_consumes_one_unit(GameTestHelper helper) {
        BBScenarios.drinking_milk_removes_effect_and_consumes_one_unit(helper);
    }

    /** See {@link BBScenarios#shift_use_in_air_discards_contents}. */
    public static void shift_use_in_air_discards_contents(GameTestHelper helper) {
        BBScenarios.shift_use_in_air_discards_contents(helper);
    }

    /** See {@link BBScenarios#stacked_empty_pickup_moves_one_filled_bucket_to_inventory}. */
    public static void stacked_empty_pickup_moves_one_filled_bucket_to_inventory(GameTestHelper helper) {
        BBScenarios.stacked_empty_pickup_moves_one_filled_bucket_to_inventory(helper);
    }

    /** See {@link BBScenarios#stacked_empty_pickup_drops_filled_bucket_when_inventory_is_full}. */
    public static void stacked_empty_pickup_drops_filled_bucket_when_inventory_is_full(GameTestHelper helper) {
        BBScenarios.stacked_empty_pickup_drops_filled_bucket_when_inventory_is_full(helper);
    }

    /** See {@link BBScenarios#stacked_empty_milking_moves_one_filled_bucket_to_inventory}. */
    public static void stacked_empty_milking_moves_one_filled_bucket_to_inventory(GameTestHelper helper) {
        BBScenarios.stacked_empty_milking_moves_one_filled_bucket_to_inventory(helper);
    }

    /** See {@link BBScenarios#creative_stacked_empty_pickup_keeps_empties}. */
    public static void creative_stacked_empty_pickup_keeps_empties(GameTestHelper helper) {
        BBScenarios.creative_stacked_empty_pickup_keeps_empties(helper);
    }

    /** See {@link BBScenarios#full_bucket_use_places_one_unit}. */
    public static void full_bucket_use_places_one_unit(GameTestHelper helper) {
        BBScenarios.full_bucket_use_places_one_unit(helper);
    }

    /** See {@link BBScenarios#partial_bucket_use_collects_before_placing}. */
    public static void partial_bucket_use_collects_before_placing(GameTestHelper helper) {
        BBScenarios.partial_bucket_use_collects_before_placing(helper);
    }

    /** See {@link BBScenarios#partial_bucket_use_places_when_nothing_to_collect}. */
    public static void partial_bucket_use_places_when_nothing_to_collect(GameTestHelper helper) {
        BBScenarios.partial_bucket_use_places_when_nothing_to_collect(helper);
    }

    /** See {@link BBScenarios#empty_bucket_use_collects_powder_snow}. */
    public static void empty_bucket_use_collects_powder_snow(GameTestHelper helper) {
        BBScenarios.empty_bucket_use_collects_powder_snow(helper);
    }

    /** See {@link BBScenarios#partial_powder_bucket_use_collects_powder_snow}. */
    public static void partial_powder_bucket_use_collects_powder_snow(GameTestHelper helper) {
        BBScenarios.partial_powder_bucket_use_collects_powder_snow(helper);
    }
}
