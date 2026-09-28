package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric GameTest wrappers for {@link BlockCapabilityScenarios}. */
public final class BlockCapabilityGameTests {
    /** See {@link BlockCapabilityScenarios#player_big_bucket_take_is_exact_observable_and_accounted}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_big_bucket_take_is_exact_observable_and_accounted(GameTestHelper helper) {
        BlockCapabilityScenarios.player_big_bucket_take_is_exact_observable_and_accounted(helper);
    }

    /** See {@link BlockCapabilityScenarios#source_bucket_place_uses_handler_infinity_and_automation_event}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_place_uses_handler_infinity_and_automation_event(GameTestHelper helper) {
        BlockCapabilityScenarios.source_bucket_place_uses_handler_infinity_and_automation_event(helper);
    }

    /** See {@link BlockCapabilityScenarios#block_capability_uses_contacted_side}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void block_capability_uses_contacted_side(GameTestHelper helper) {
        BlockCapabilityScenarios.block_capability_uses_contacted_side(helper);
    }

    /** See {@link BlockCapabilityScenarios#partial_block_transactions_refuse_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_block_transactions_refuse_without_mutation(GameTestHelper helper) {
        BlockCapabilityScenarios.partial_block_transactions_refuse_without_mutation(helper);
    }

    /** See {@link BlockCapabilityScenarios#protection_denial_keeps_tank_and_bucket_atomic}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void protection_denial_keeps_tank_and_bucket_atomic(GameTestHelper helper) {
        BlockCapabilityScenarios.protection_denial_keeps_tank_and_bucket_atomic(helper);
    }

    /** See {@link BlockCapabilityScenarios#dispenser_source_bucket_fills_sided_tank_without_consumption}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public void dispenser_source_bucket_fills_sided_tank_without_consumption(GameTestHelper helper) {
        BlockCapabilityScenarios.dispenser_source_bucket_fills_sided_tank_without_consumption(helper);
    }

    /** See {@link BlockCapabilityScenarios#dispenser_assigned_source_bucket_drains_matching_sided_tank}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public void dispenser_assigned_source_bucket_drains_matching_sided_tank(GameTestHelper helper) {
        BlockCapabilityScenarios.dispenser_assigned_source_bucket_drains_matching_sided_tank(helper);
    }
}
