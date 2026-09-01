package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(SomeBuckets.MODID)
public final class MBGameTests {
    private MBGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void eligible_mob_capture_stores_snapshot_and_discards_entity(GameTestHelper helper) {
        MBScenarios.eligible_mob_capture_stores_snapshot_and_discards_entity(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void aquatic_capture_uses_native_water_pickup_observability(GameTestHelper helper) {
        MBScenarios.aquatic_capture_uses_native_water_pickup_observability(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_capture_fires_filled_bucket_criterion_but_automation_does_not(GameTestHelper helper) {
        MBScenarios.player_capture_fires_filled_bucket_criterion_but_automation_does_not(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void blacklisted_boss_is_not_capturable(GameTestHelper helper) {
        MBScenarios.blacklisted_boss_is_not_capturable(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void passenger_and_vehicle_are_not_capturable(GameTestHelper helper) {
        MBScenarios.passenger_and_vehicle_are_not_capturable(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void bucket_accepts_eight_same_type_and_rejects_ninth(GameTestHelper helper) {
        MBScenarios.bucket_accepts_eight_same_type_and_rejects_ninth(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void bucket_rejects_different_entity_type(GameTestHelper helper) {
        MBScenarios.bucket_rejects_different_entity_type(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void release_restores_state_and_uuid_and_normalizes(GameTestHelper helper) {
        MBScenarios.release_restores_state_and_uuid_and_normalizes(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void player_release_stat_is_awarded_only_after_success(GameTestHelper helper) {
        MBScenarios.player_release_stat_is_awarded_only_after_success(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void release_replaces_uuid_that_is_already_in_use(GameTestHelper helper) {
        MBScenarios.release_replaces_uuid_that_is_already_in_use(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void failed_collision_preserves_snapshot_and_fifo_order(GameTestHelper helper) {
        MBScenarios.failed_collision_preserves_snapshot_and_fifo_order(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void aquatic_release_creates_water(GameTestHelper helper) {
        MBScenarios.aquatic_release_creates_water(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void aquatic_collision_failure_precedes_water_placement(GameTestHelper helper) {
        MBScenarios.aquatic_collision_failure_precedes_water_placement(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void aquatic_release_waterlogs_native_liquid_container(GameTestHelper helper) {
        MBScenarios.aquatic_release_waterlogs_native_liquid_container(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void aquatic_release_into_existing_water_emits_no_fluid_event(GameTestHelper helper) {
        MBScenarios.aquatic_release_into_existing_water_emits_no_fluid_event(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void aquatic_release_activates_sculk_sensor(GameTestHelper helper) {
        MBScenarios.aquatic_release_activates_sculk_sensor(helper);
    }

}
