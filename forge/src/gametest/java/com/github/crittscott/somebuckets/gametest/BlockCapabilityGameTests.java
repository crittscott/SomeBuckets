package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

/** Forge GameTest wrappers for {@link BlockCapabilityScenarios}. */
@GameTestHolder(SomeBuckets.MODID)
public final class BlockCapabilityGameTests {
    private BlockCapabilityGameTests() {}

    /** See {@link BlockCapabilityScenarios#player_big_bucket_take_is_exact_observable_and_accounted}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_big_bucket_take_is_exact_observable_and_accounted(GameTestHelper helper) {
        BlockCapabilityScenarios.player_big_bucket_take_is_exact_observable_and_accounted(helper);
    }

    /** See {@link BlockCapabilityScenarios#source_bucket_place_uses_handler_infinity_and_automation_event}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_bucket_place_uses_handler_infinity_and_automation_event(GameTestHelper helper) {
        BlockCapabilityScenarios.source_bucket_place_uses_handler_infinity_and_automation_event(helper);
    }

    /** See {@link BlockCapabilityScenarios#block_capability_uses_contacted_side}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void block_capability_uses_contacted_side(GameTestHelper helper) {
        BlockCapabilityScenarios.block_capability_uses_contacted_side(helper);
    }

    /** See {@link BlockCapabilityScenarios#partial_block_transactions_refuse_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_block_transactions_refuse_without_mutation(GameTestHelper helper) {
        BlockCapabilityScenarios.partial_block_transactions_refuse_without_mutation(helper);
    }

    /** See {@link BlockCapabilityScenarios#protection_denial_keeps_tank_and_bucket_atomic}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void protection_denial_keeps_tank_and_bucket_atomic(GameTestHelper helper) {
        BlockCapabilityScenarios.protection_denial_keeps_tank_and_bucket_atomic(helper);
    }

    /** See {@link BlockCapabilityScenarios#dispenser_source_bucket_fills_sided_tank_without_consumption}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_source_bucket_fills_sided_tank_without_consumption(GameTestHelper helper) {
        BlockCapabilityScenarios.dispenser_source_bucket_fills_sided_tank_without_consumption(helper);
    }

    /** See {@link BlockCapabilityScenarios#dispenser_assigned_source_bucket_drains_matching_sided_tank}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_assigned_source_bucket_drains_matching_sided_tank(GameTestHelper helper) {
        BlockCapabilityScenarios.dispenser_assigned_source_bucket_drains_matching_sided_tank(helper);
    }
}
