package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import eu.pb4.common.protection.api.CommonProtection;
import eu.pb4.common.protection.api.ProtectionProvider;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Fabric protection GameTests, including shared scenarios and loader callback cancellation. */
public final class ProtectionGameTests {
    private static final ThreadLocal<List<BlockPos>> DENIED_BREAKS = new ThreadLocal<>();
    private static final ThreadLocal<ClaimProbe> COMMON_PROTECTION_PROBE = new ThreadLocal<>();

    static {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            List<BlockPos> denied = DENIED_BREAKS.get();
            if (denied == null) return true;
            denied.add(pos.immutable());
            return false;
        });
        CommonProtection.register(SomeBuckets.id("gametest_claim_provider"), new ProtectionProvider() {
            @Override
            public boolean isProtected(Level level, BlockPos pos) {
                ClaimProbe probe = COMMON_PROTECTION_PROBE.get();
                if (probe == null) return false;
                BlockPos checked = pos.immutable();
                probe.checked().add(checked);
                return probe.denied().contains(checked);
            }

            @Override
            public boolean isAreaProtected(Level level, AABB area) {
                return false;
            }
        });
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_take_fluid}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_outside_world_border_cannot_take_fluid(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_take_fluid(helper);
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_use_cauldron}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_outside_world_border_cannot_use_cauldron(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_use_cauldron(helper);
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_release}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_outside_world_border_cannot_release(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_release(helper);
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

    /** See {@link ProtectionScenarios#automation_ignores_build_permission}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_ignores_build_permission(GameTestHelper helper) {
        ProtectionScenarios.automation_ignores_build_permission(helper);
    }

    /** See {@link ProtectionScenarios#player_cannot_capture_another_players_pet}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_cannot_capture_another_players_pet(GameTestHelper helper) {
        ProtectionScenarios.player_cannot_capture_another_players_pet(helper);
    }

    /** See {@link ProtectionScenarios#automation_cannot_capture_owned_pet}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_cannot_capture_owned_pet(GameTestHelper helper) {
        ProtectionScenarios.automation_cannot_capture_owned_pet(helper);
    }

    /** See {@link ProtectionScenarios#mob_bucket_refuses_leashed_mob}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void mob_bucket_refuses_leashed_mob(GameTestHelper helper) {
        ProtectionScenarios.mob_bucket_refuses_leashed_mob(helper);
    }

    /** See {@link ProtectionScenarios#automation_does_not_feed_untamed_cat}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void automation_does_not_feed_untamed_cat(GameTestHelper helper) {
        ProtectionScenarios.automation_does_not_feed_untamed_cat(helper);
    }

    /**
     * Manual: configure a protection mod to deny a player access to source water; the player's empty
     * Big Bucket cannot collect it, while a dispenser can. Automation: cancels Fabric's player
     * block-break callback and verifies it names only the player's exact target.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void cancelled_break_check_denies_player_but_not_automation(GameTestHelper helper) {
        ProtectionScenarios.cancelled_break_check_denies_player_but_not_automation(
                helper, (denied, action) -> denyingBreaks(denied, action));
    }

    /**
     * Manual: with a Common Protection API claim denying the targets, verify player fluid pickup,
     * fluid placement, and powder-snow placement fail atomically while dispenser pickup still works.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void common_protection_denies_player_world_edits_but_not_automation(GameTestHelper helper) {
        BlockPos playerPickup = new BlockPos(1, 2, 2);
        BlockPos automationPickup = new BlockPos(3, 2, 2);
        BlockPos fluidPlacement = new BlockPos(5, 2, 2);
        BlockPos powderPlacement = new BlockPos(7, 2, 2);
        helper.setBlock(playerPickup, Blocks.WATER);
        helper.setBlock(automationPickup, Blocks.WATER);
        helper.setBlock(fluidPlacement, Blocks.AIR);
        helper.setBlock(powderPlacement, Blocks.AIR);

        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        ClaimProbe probe = denyingCommonProtection(helper,
                List.of(playerPickup, automationPickup, fluidPlacement, powderPlacement), () -> {
                    ItemStack playerTake = GameTestSupport.big8();
                    player.setItemInHand(InteractionHand.MAIN_HAND, playerTake);
                    boolean playerTook = FluidTransactions.tryTakeFinite(
                            helper.getLevel(), GameTestSupport.hit(helper, playerPickup, Direction.UP),
                            playerTake, player, InteractionHand.MAIN_HAND);
                    GameTestSupport.check(!playerTook, "Common Protection allowed player fluid pickup");
                    GameTestSupport.assertEmpty(playerTake);
                    GameTestSupport.assertBlock(helper, playerPickup, Blocks.WATER);

                    ItemStack automationTake = GameTestSupport.big8();
                    boolean automationTook = FluidTransactions.tryTakeFinite(
                            helper.getLevel(), GameTestSupport.hit(helper, automationPickup, Direction.UP),
                            automationTake, ProtectionContext.dispenser(
                                    BucketOperations.get().automationPlayer(helper.getLevel())));
                    GameTestSupport.check(automationTook,
                            "Common Protection incorrectly denied dispenser fluid pickup");
                    GameTestSupport.assertFluid(automationTake, Fluids.WATER, 1_000);
                    GameTestSupport.assertBlock(helper, automationPickup, Blocks.AIR);

                    ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2_000);
                    player.setItemInHand(InteractionHand.MAIN_HAND, fluid);
                    boolean fluidPlaced = FluidTransactions.tryPlaceFinite(
                            helper.getLevel(), GameTestSupport.hit(helper, fluidPlacement, Direction.UP),
                            fluid, player, InteractionHand.MAIN_HAND);
                    GameTestSupport.check(!fluidPlaced, "Common Protection allowed player fluid placement");
                    GameTestSupport.assertFluid(fluid, Fluids.WATER, 2_000);
                    GameTestSupport.assertBlock(helper, fluidPlacement, Blocks.AIR);

                    ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 2);
                    player.setItemInHand(InteractionHand.MAIN_HAND, powder);
                    boolean powderPlaced = FluidTransactions.tryPlacePowder(
                            helper.getLevel(), GameTestSupport.hit(helper, powderPlacement, Direction.UP),
                            powder, player, InteractionHand.MAIN_HAND);
                    GameTestSupport.check(!powderPlaced,
                            "Common Protection allowed player powder-snow placement");
                    GameTestSupport.assertPowder(powder, 2);
                    GameTestSupport.assertBlock(helper, powderPlacement, Blocks.AIR);
                });

        GameTestSupport.check(probe.checked().contains(helper.absolutePos(playerPickup)),
                "Common Protection received no player fluid-pickup check");
        GameTestSupport.check(probe.checked().contains(helper.absolutePos(fluidPlacement)),
                "Common Protection received no player fluid-place check");
        GameTestSupport.check(probe.checked().contains(helper.absolutePos(powderPlacement)),
                "Common Protection received no player powder-place check");
        GameTestSupport.check(!probe.checked().contains(helper.absolutePos(automationPickup)),
                "Dispenser pickup consulted the player claim provider");
        helper.succeed();
    }

    /**
     * Denies every Fabric player block break, recording its position, while {@code action} runs.
     * Fabric listeners cannot be unregistered, so one listener is gated by a flag.
     */
    private static void denyingBreaks(List<BlockPos> denied, Runnable action) {
        DENIED_BREAKS.set(denied);
        try {
            action.run();
        } finally {
            DENIED_BREAKS.set(null);
        }
    }

    private static ClaimProbe denyingCommonProtection(GameTestHelper helper, List<BlockPos> denied,
                                                       Runnable action) {
        ClaimProbe probe = new ClaimProbe(
                denied.stream().map(helper::absolutePos).map(BlockPos::immutable).toList(),
                new ArrayList<>());
        COMMON_PROTECTION_PROBE.set(probe);
        try {
            action.run();
        } finally {
            COMMON_PROTECTION_PROBE.remove();
        }
        return probe;
    }

    private record ClaimProbe(List<BlockPos> denied, List<BlockPos> checked) {}
}
