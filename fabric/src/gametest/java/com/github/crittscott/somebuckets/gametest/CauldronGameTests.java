package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class CauldronGameTests {
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void both_big_bucket_tiers_are_registered(GameTestHelper helper) {
        CauldronScenarios.both_big_bucket_tiers_are_registered(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void full_water_cauldron_fills_empty_big_bucket(GameTestHelper helper) {
        CauldronScenarios.full_water_cauldron_fills_empty_big_bucket(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_big_bucket_takes_from_full_water_cauldron_first(GameTestHelper helper) {
        CauldronScenarios.partial_big_bucket_takes_from_full_water_cauldron_first(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_water_cauldron_is_not_collected(GameTestHelper helper) {
        CauldronScenarios.partial_water_cauldron_is_not_collected(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(GameTestHelper helper) {
        CauldronScenarios.big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void lava_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        CauldronScenarios.lava_cauldron_round_trip_normalizes_final_unit(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void powder_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        CauldronScenarios.powder_cauldron_round_trip_normalizes_final_unit(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void milk_does_not_fill_empty_cauldron(GameTestHelper helper) {
        CauldronScenarios.milk_does_not_fill_empty_cauldron(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_big_bucket_cauldron_round_trip_has_vanilla_accounting(GameTestHelper helper) {
        CauldronScenarios.player_big_bucket_cauldron_round_trip_has_vanilla_accounting(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_source_cauldron_round_trip_assigns_and_remains_infinite(GameTestHelper helper) {
        CauldronScenarios.player_source_cauldron_round_trip_assigns_and_remains_infinite(helper);
    }
}
