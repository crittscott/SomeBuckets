package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private ProtectionGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void automation_is_permitted_without_refusal(GameTestHelper helper) {
        ProtectionScenarios.automation_is_permitted_without_refusal(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_fluid_pickup_posts_break_for_each_hand(GameTestHelper helper) {
        ProtectionScenarios.player_fluid_pickup_posts_break_for_each_hand(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_break_denies_dispenser_fluid_pickup(GameTestHelper helper) {
        ProtectionScenarios.refused_break_denies_dispenser_fluid_pickup(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_entity_interaction_denies_dispenser_capture(GameTestHelper helper) {
        ProtectionScenarios.refused_entity_interaction_denies_dispenser_capture(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_entity_interaction_denies_automated_feeding(GameTestHelper helper) {
        ProtectionScenarios.refused_entity_interaction_denies_automated_feeding(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_block_interaction_denies_dispenser_cauldron_use(GameTestHelper helper) {
        ProtectionScenarios.refused_block_interaction_denies_dispenser_cauldron_use(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_block_interaction_denies_dispenser_release(GameTestHelper helper) {
        ProtectionScenarios.refused_block_interaction_denies_dispenser_release(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_without_placement_permission_cannot_collect(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adventure_player_cannot_eject_stored_items(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_cannot_eject_stored_items(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_pickup_denies_dispenser_absorption(GameTestHelper helper) {
        ProtectionScenarios.refused_pickup_denies_dispenser_absorption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_place_denies_aquatic_release_water(GameTestHelper helper) {
        ProtectionScenarios.refused_place_denies_aquatic_release_water(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_place_checks_fallthrough_destination(GameTestHelper helper) {
        ProtectionScenarios.refused_place_checks_fallthrough_destination(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_pickup_denies_player_junk_absorption(GameTestHelper helper) {
        ProtectionScenarios.refused_pickup_denies_player_junk_absorption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_pickup_denies_player_trash_absorption(GameTestHelper helper) {
        ProtectionScenarios.refused_pickup_denies_player_trash_absorption(helper);
    }
}
