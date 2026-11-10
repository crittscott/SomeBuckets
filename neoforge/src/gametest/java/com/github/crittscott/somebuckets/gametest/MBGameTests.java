package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/** NeoForge GameTest wrappers for {@link MBScenarios}. */
public final class MBGameTests {
    private MBGameTests() {}

    /** See {@link MBScenarios#eligible_mob_capture_stores_snapshot_and_discards_entity}. */
    public static void eligible_mob_capture_stores_snapshot_and_discards_entity(GameTestHelper helper) {
        MBScenarios.eligible_mob_capture_stores_snapshot_and_discards_entity(helper);
    }

    /** See {@link MBScenarios#aquatic_capture_uses_native_water_pickup_observability}. */
    public static void aquatic_capture_uses_native_water_pickup_observability(GameTestHelper helper) {
        MBScenarios.aquatic_capture_uses_native_water_pickup_observability(helper);
    }

    /** See {@link MBScenarios#player_capture_fires_filled_bucket_criterion_but_automation_does_not}. */
    public static void player_capture_fires_filled_bucket_criterion_but_automation_does_not(GameTestHelper helper) {
        MBScenarios.player_capture_fires_filled_bucket_criterion_but_automation_does_not(helper);
    }

    /** See {@link MBScenarios#blacklisted_boss_is_not_capturable}. */
    public static void blacklisted_boss_is_not_capturable(GameTestHelper helper) {
        MBScenarios.blacklisted_boss_is_not_capturable(helper);
    }

    /** See {@link MBScenarios#passenger_and_vehicle_are_not_capturable}. */
    public static void passenger_and_vehicle_are_not_capturable(GameTestHelper helper) {
        MBScenarios.passenger_and_vehicle_are_not_capturable(helper);
    }

    /** See {@link MBScenarios#bucket_accepts_eight_same_type_and_rejects_ninth}. */
    public static void bucket_accepts_eight_same_type_and_rejects_ninth(GameTestHelper helper) {
        MBScenarios.bucket_accepts_eight_same_type_and_rejects_ninth(helper);
    }

    /** See {@link MBScenarios#bucket_rejects_different_entity_type}. */
    public static void bucket_rejects_different_entity_type(GameTestHelper helper) {
        MBScenarios.bucket_rejects_different_entity_type(helper);
    }

    /** See {@link MBScenarios#release_restores_state_and_uuid_and_normalizes}. */
    public static void release_restores_state_and_uuid_and_normalizes(GameTestHelper helper) {
        MBScenarios.release_restores_state_and_uuid_and_normalizes(helper);
    }

    /** See {@link MBScenarios#player_release_stat_is_awarded_only_after_success}. */
    public static void player_release_stat_is_awarded_only_after_success(GameTestHelper helper) {
        MBScenarios.player_release_stat_is_awarded_only_after_success(helper);
    }

    /** See {@link MBScenarios#release_replaces_uuid_that_is_already_in_use}. */
    public static void release_replaces_uuid_that_is_already_in_use(GameTestHelper helper) {
        MBScenarios.release_replaces_uuid_that_is_already_in_use(helper);
    }

    /** See {@link MBScenarios#failed_collision_preserves_snapshot_and_fifo_order}. */
    public static void failed_collision_preserves_snapshot_and_fifo_order(GameTestHelper helper) {
        MBScenarios.failed_collision_preserves_snapshot_and_fifo_order(helper);
    }

    /** See {@link MBScenarios#aquatic_release_creates_water}. */
    public static void aquatic_release_creates_water(GameTestHelper helper) {
        MBScenarios.aquatic_release_creates_water(helper);
    }

    /** See {@link MBScenarios#automation_aquatic_release_water_has_no_source_entity}. */
    public static void automation_aquatic_release_water_has_no_source_entity(GameTestHelper helper) {
        MBScenarios.automation_aquatic_release_water_has_no_source_entity(helper);
    }

    /** See {@link MBScenarios#aquatic_collision_failure_precedes_water_placement}. */
    public static void aquatic_collision_failure_precedes_water_placement(GameTestHelper helper) {
        MBScenarios.aquatic_collision_failure_precedes_water_placement(helper);
    }

    /** See {@link MBScenarios#aquatic_release_waterlogs_native_liquid_container}. */
    public static void aquatic_release_waterlogs_native_liquid_container(GameTestHelper helper) {
        MBScenarios.aquatic_release_waterlogs_native_liquid_container(helper);
    }

    /** See {@link MBScenarios#aquatic_release_into_existing_water_emits_no_fluid_event}. */
    public static void aquatic_release_into_existing_water_emits_no_fluid_event(GameTestHelper helper) {
        MBScenarios.aquatic_release_into_existing_water_emits_no_fluid_event(helper);
    }

    /** See {@link MBScenarios#stacked_empty_capture_moves_one_filled_bucket_to_inventory}. */
    public static void stacked_empty_capture_moves_one_filled_bucket_to_inventory(GameTestHelper helper) {
        MBScenarios.stacked_empty_capture_moves_one_filled_bucket_to_inventory(helper);
    }

    /** See {@link MBScenarios#aquatic_capture_fails_when_water_pickup_is_refused}. */
    public static void aquatic_capture_fails_when_water_pickup_is_refused(GameTestHelper helper) {
        MBScenarios.aquatic_capture_fails_when_water_pickup_is_refused(helper);
    }

    /** See {@link MBScenarios#release_with_unresolved_entity_type_keeps_entry}. */
    public static void release_with_unresolved_entity_type_keeps_entry(GameTestHelper helper) {
        MBScenarios.release_with_unresolved_entity_type_keeps_entry(helper);
    }

    /** See {@link MBScenarios#release_of_blacklisted_stored_type_is_refused}. */
    public static void release_of_blacklisted_stored_type_is_refused(GameTestHelper helper) {
        MBScenarios.release_of_blacklisted_stored_type_is_refused(helper);
    }

    /** See {@link MBScenarios#players_and_non_mob_entities_are_not_capturable}. */
    public static void players_and_non_mob_entities_are_not_capturable(GameTestHelper helper) {
        MBScenarios.players_and_non_mob_entities_are_not_capturable(helper);
    }

    /** See {@link MBScenarios#happy_ghast_survives_bucket_persistence_and_release}. */
    public static void happy_ghast_survives_bucket_persistence_and_release(GameTestHelper helper) {
        MBScenarios.happy_ghast_survives_bucket_persistence_and_release(helper);
    }

    /** See {@link MBScenarios#ridden_happy_ghast_capture_preserves_ghast_rider_and_bucket}. */
    public static void ridden_happy_ghast_capture_preserves_ghast_rider_and_bucket(GameTestHelper helper) {
        MBScenarios.ridden_happy_ghast_capture_preserves_ghast_rider_and_bucket(helper);
    }

    /** See {@link MBScenarios#entity_load_error_preserves_snapshot_and_destination}. */
    public static void entity_load_error_preserves_snapshot_and_destination(GameTestHelper helper) {
        MBScenarios.entity_load_error_preserves_snapshot_and_destination(helper);
    }
}
