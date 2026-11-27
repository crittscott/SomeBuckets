package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ProtectionGameTests {
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_fluid_pickup_posts_break_for_each_hand(GameTestHelper helper) {
        ProtectionScenarios.player_fluid_pickup_posts_break_for_each_hand(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void handled_block_interaction_denies_dispenser_cauldron_fill(GameTestHelper helper) {
        ProtectionScenarios.handled_block_interaction_denies_dispenser_cauldron_fill(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void unhandled_block_interaction_lets_dispenser_fill_cauldron(GameTestHelper helper) {
        ProtectionScenarios.unhandled_block_interaction_lets_dispenser_fill_cauldron(helper);
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
