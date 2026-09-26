package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(SomeBuckets.MODID)
public final class CauldronGameTests {
    private CauldronGameTests() {}

    /** See {@link CauldronScenarios#both_big_bucket_tiers_are_registered}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void both_big_bucket_tiers_are_registered(GameTestHelper helper) {
        CauldronScenarios.both_big_bucket_tiers_are_registered(helper);
    }

    /** See {@link CauldronScenarios#full_water_cauldron_fills_empty_big_bucket}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void full_water_cauldron_fills_empty_big_bucket(GameTestHelper helper) {
        CauldronScenarios.full_water_cauldron_fills_empty_big_bucket(helper);
    }

    /** See {@link CauldronScenarios#partial_big_bucket_takes_from_full_water_cauldron_first}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_big_bucket_takes_from_full_water_cauldron_first(GameTestHelper helper) {
        CauldronScenarios.partial_big_bucket_takes_from_full_water_cauldron_first(helper);
    }

    /** See {@link CauldronScenarios#partial_water_cauldron_is_not_collected}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_water_cauldron_is_not_collected(GameTestHelper helper) {
        CauldronScenarios.partial_water_cauldron_is_not_collected(helper);
    }

    /** See {@link CauldronScenarios#big_bucket_fills_empty_water_cauldron_and_consumes_one_unit}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(GameTestHelper helper) {
        CauldronScenarios.big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(helper);
    }

    /** See {@link CauldronScenarios#lava_cauldron_round_trip_normalizes_final_unit}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        CauldronScenarios.lava_cauldron_round_trip_normalizes_final_unit(helper);
    }

    /** See {@link CauldronScenarios#powder_cauldron_round_trip_normalizes_final_unit}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        CauldronScenarios.powder_cauldron_round_trip_normalizes_final_unit(helper);
    }

    /** See {@link CauldronScenarios#milk_does_not_fill_empty_cauldron}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void milk_does_not_fill_empty_cauldron(GameTestHelper helper) {
        CauldronScenarios.milk_does_not_fill_empty_cauldron(helper);
    }

    /** See {@link CauldronScenarios#player_big_bucket_cauldron_round_trip_has_vanilla_accounting}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_big_bucket_cauldron_round_trip_has_vanilla_accounting(GameTestHelper helper) {
        CauldronScenarios.player_big_bucket_cauldron_round_trip_has_vanilla_accounting(helper);
    }

    /** See {@link CauldronScenarios#player_source_cauldron_round_trip_assigns_and_remains_infinite}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_source_cauldron_round_trip_assigns_and_remains_infinite(GameTestHelper helper) {
        CauldronScenarios.player_source_cauldron_round_trip_assigns_and_remains_infinite(helper);
    }
}
