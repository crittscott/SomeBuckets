package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ProtectionGameTests {
    /** See {@link ProtectionScenarios#unowned_automation_is_permitted}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void unowned_automation_is_permitted(GameTestHelper helper) {
        ProtectionScenarios.unowned_automation_is_permitted(helper);
    }

    /** See {@link ProtectionScenarios#automation_without_build_permission_cannot_take_fluid}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_without_build_permission_cannot_take_fluid(GameTestHelper helper) {
        ProtectionScenarios.automation_without_build_permission_cannot_take_fluid(helper);
    }

    /** See {@link ProtectionScenarios#automation_without_build_permission_cannot_use_cauldron}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_without_build_permission_cannot_use_cauldron(GameTestHelper helper) {
        ProtectionScenarios.automation_without_build_permission_cannot_use_cauldron(helper);
    }

    /** See {@link ProtectionScenarios#automation_without_build_permission_cannot_release}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_without_build_permission_cannot_release(GameTestHelper helper) {
        ProtectionScenarios.automation_without_build_permission_cannot_release(helper);
    }

    /** See {@link ProtectionScenarios#player_without_build_permission_cannot_place_fluid}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_without_build_permission_cannot_place_fluid(GameTestHelper helper) {
        ProtectionScenarios.player_without_build_permission_cannot_place_fluid(helper);
    }

    /** See {@link ProtectionScenarios#player_without_build_permission_cannot_eject}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_without_build_permission_cannot_eject(GameTestHelper helper) {
        ProtectionScenarios.player_without_build_permission_cannot_eject(helper);
    }

    /** See {@link ProtectionScenarios#dispenser_acts_as_stable_automation_player}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public void dispenser_acts_as_stable_automation_player(GameTestHelper helper) {
        ProtectionScenarios.dispenser_acts_as_stable_automation_player(helper);
    }

    /** See {@link ProtectionScenarios#adventure_player_without_placement_permission_cannot_collect}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_without_placement_permission_cannot_collect(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_capture_mob}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_outside_world_border_cannot_capture_mob(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_capture_mob(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_milk}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_outside_world_border_cannot_milk(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_milk(helper);
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_milk}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_outside_world_border_cannot_milk(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_milk(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_vacuum_items}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_outside_world_border_cannot_vacuum_items(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_vacuum_items(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_feed_animal}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_outside_world_border_cannot_feed_animal(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_feed_animal(helper);
    }
}
