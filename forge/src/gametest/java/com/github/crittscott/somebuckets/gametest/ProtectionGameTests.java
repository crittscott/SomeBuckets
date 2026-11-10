package com.github.crittscott.somebuckets.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.level.BlockEvent;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Forge protection GameTests, including shared scenarios and loader event cancellation. */
public final class ProtectionGameTests {
    private ProtectionGameTests() {}

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_take_fluid}. */
    public static void automation_outside_world_border_cannot_take_fluid(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_take_fluid(helper);
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_use_cauldron}. */
    public static void automation_outside_world_border_cannot_use_cauldron(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_use_cauldron(helper);
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_release}. */
    public static void automation_outside_world_border_cannot_release(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_release(helper);
    }

    /** See {@link ProtectionScenarios#player_without_build_permission_cannot_place_fluid}. */
    public static void player_without_build_permission_cannot_place_fluid(GameTestHelper helper) {
        ProtectionScenarios.player_without_build_permission_cannot_place_fluid(helper);
    }

    /** See {@link ProtectionScenarios#player_without_build_permission_cannot_eject}. */
    public static void player_without_build_permission_cannot_eject(GameTestHelper helper) {
        ProtectionScenarios.player_without_build_permission_cannot_eject(helper);
    }

    /** See {@link ProtectionScenarios#dispenser_acts_as_stable_automation_player}. */
    public static void dispenser_acts_as_stable_automation_player(GameTestHelper helper) {
        ProtectionScenarios.dispenser_acts_as_stable_automation_player(helper);
    }

    /** See {@link ProtectionScenarios#adventure_player_without_placement_permission_cannot_collect}. */
    public static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_without_placement_permission_cannot_collect(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_capture_mob}. */
    public static void player_outside_world_border_cannot_capture_mob(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_capture_mob(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_milk}. */
    public static void player_outside_world_border_cannot_milk(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_milk(helper);
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_milk}. */
    public static void automation_outside_world_border_cannot_milk(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_milk(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_vacuum_items}. */
    public static void player_outside_world_border_cannot_vacuum_items(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_vacuum_items(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_feed_animal}. */
    public static void player_outside_world_border_cannot_feed_animal(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_feed_animal(helper);
    }

    /** See {@link ProtectionScenarios#automation_ignores_build_permission}. */
    public static void automation_ignores_build_permission(GameTestHelper helper) {
        ProtectionScenarios.automation_ignores_build_permission(helper);
    }

    /** See {@link ProtectionScenarios#player_cannot_capture_another_players_pet}. */
    public static void player_cannot_capture_another_players_pet(GameTestHelper helper) {
        ProtectionScenarios.player_cannot_capture_another_players_pet(helper);
    }

    /** See {@link ProtectionScenarios#automation_cannot_capture_owned_pet}. */
    public static void automation_cannot_capture_owned_pet(GameTestHelper helper) {
        ProtectionScenarios.automation_cannot_capture_owned_pet(helper);
    }

    /** See {@link ProtectionScenarios#mob_bucket_refuses_leashed_mob}. */
    public static void mob_bucket_refuses_leashed_mob(GameTestHelper helper) {
        ProtectionScenarios.mob_bucket_refuses_leashed_mob(helper);
    }

    /** See {@link ProtectionScenarios#automation_does_not_feed_untamed_cat}. */
    public static void automation_does_not_feed_untamed_cat(GameTestHelper helper) {
        ProtectionScenarios.automation_does_not_feed_untamed_cat(helper);
    }

    /**
     * Manual: configure a protection mod to deny a player access to source water; the player's empty
     * Big Bucket cannot collect it, while a dispenser can. Automation: cancels Forge's block-break
     * event and verifies it names only the player's exact target.
     */
    public static void cancelled_break_check_denies_player_but_not_automation(GameTestHelper helper) {
        ProtectionScenarios.cancelled_break_check_denies_player_but_not_automation(
                helper, (denied, action) -> denyingBreaks(helper, denied, action));
    }

    /**
     * Manual: configure a protection mod to deny fluid placement beside a solid block, then use a
     * water-filled Big Bucket on that block; no water is placed and the bucket is unchanged.
     * Automation: cancels Forge's entity-place event and verifies it reports water at the resolved
     * neighbor.
     */
    public static void cancelled_place_check_denies_player_fluid_place(GameTestHelper helper) {
        ProtectionScenarios.cancelled_place_check_denies_player_fluid_place(
                helper, (denied, action) -> denyingPlacements(helper, denied, action));
    }

    /** See {@link ProtectionScenarios#cancelled_place_check_restores_replaced_plant_without_drops}. */
    public static void cancelled_place_check_restores_replaced_plant_without_drops(GameTestHelper helper) {
        ProtectionScenarios.cancelled_place_check_restores_replaced_plant_without_drops(
                helper, (denied, action) -> denyingPlacements(helper, denied, action));
    }

    /**
     * Denies Forge block-break events inside this test's structure, recording each position,
     * while {@code action} runs.
     */
    private static void denyingBreaks(GameTestHelper helper, List<BlockPos> denied, Runnable action) {
        Consumer<BlockEvent.BreakEvent> listener = event -> {
            if (!helper.getBounds().contains(Vec3.atCenterOf(event.getPos()))) return;
            denied.add(event.getPos().immutable());
            event.setResult(Result.DENY);
        };
        var registration = BlockEvent.BreakEvent.BUS.addListener(listener);
        try {
            action.run();
        } finally {
            BlockEvent.BreakEvent.BUS.removeListener(registration);
        }
    }

    /**
     * Cancels Forge entity block-place events inside this test's structure, recording each position
     * and reported placed block, while {@code action} runs.
     */
    static void denyingPlacements(GameTestHelper helper, List<ProtectionScenarios.DeniedPlacement> denied,
                                  Runnable action) {
        Predicate<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (!helper.getBounds().contains(Vec3.atCenterOf(event.getPos()))) return false;
            denied.add(new ProtectionScenarios.DeniedPlacement(
                    event.getPos().immutable(), event.getPlacedBlock()));
            return true;
        };
        var registration = BlockEvent.EntityPlaceEvent.BUS.addListener(listener);
        try {
            action.run();
        } finally {
            BlockEvent.EntityPlaceEvent.BUS.removeListener(registration);
        }
    }
}
