package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ProtectionGameTests {
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_is_permitted_without_refusal(GameTestHelper helper) {
        ProtectionScenarios.automation_is_permitted_without_refusal(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_fluid_pickup_posts_break_for_each_hand(GameTestHelper helper) {
        ProtectionScenarios.player_fluid_pickup_posts_break_for_each_hand(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void refused_break_denies_dispenser_fluid_pickup(GameTestHelper helper) {
        ProtectionScenarios.refused_break_denies_dispenser_fluid_pickup(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void refused_entity_interaction_denies_dispenser_capture(GameTestHelper helper) {
        ProtectionScenarios.refused_entity_interaction_denies_dispenser_capture(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void refused_entity_interaction_denies_automated_feeding(GameTestHelper helper) {
        ProtectionScenarios.refused_entity_interaction_denies_automated_feeding(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void refused_block_interaction_denies_dispenser_cauldron_use(GameTestHelper helper) {
        ProtectionScenarios.refused_block_interaction_denies_dispenser_cauldron_use(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void refused_block_interaction_denies_dispenser_release(GameTestHelper helper) {
        ProtectionScenarios.refused_block_interaction_denies_dispenser_release(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_without_placement_permission_cannot_collect(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void adventure_player_cannot_eject_stored_items(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_cannot_eject_stored_items(helper);
    }
}
