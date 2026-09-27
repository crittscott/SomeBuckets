package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@GameTestHolder(SomeBuckets.MODID)
public final class ProtectionGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);
    private static final BlockPos AUTOMATION_TARGET = TARGET.east(2);

    private ProtectionGameTests() {}

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_take_fluid}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void automation_outside_world_border_cannot_take_fluid(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_take_fluid(helper);
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_use_cauldron}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void automation_outside_world_border_cannot_use_cauldron(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_use_cauldron(helper);
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_release}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void automation_outside_world_border_cannot_release(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_release(helper);
    }

    /** See {@link ProtectionScenarios#player_without_build_permission_cannot_place_fluid}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_without_build_permission_cannot_place_fluid(GameTestHelper helper) {
        ProtectionScenarios.player_without_build_permission_cannot_place_fluid(helper);
    }

    /** See {@link ProtectionScenarios#player_without_build_permission_cannot_eject}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_without_build_permission_cannot_eject(GameTestHelper helper) {
        ProtectionScenarios.player_without_build_permission_cannot_eject(helper);
    }

    /** See {@link ProtectionScenarios#dispenser_acts_as_stable_automation_player}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_acts_as_stable_automation_player(GameTestHelper helper) {
        ProtectionScenarios.dispenser_acts_as_stable_automation_player(helper);
    }

    /** See {@link ProtectionScenarios#adventure_player_without_placement_permission_cannot_collect}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_without_placement_permission_cannot_collect(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_capture_mob}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_outside_world_border_cannot_capture_mob(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_capture_mob(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_milk}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_outside_world_border_cannot_milk(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_milk(helper);
    }

    /** See {@link ProtectionScenarios#automation_outside_world_border_cannot_milk}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void automation_outside_world_border_cannot_milk(GameTestHelper helper) {
        ProtectionScenarios.automation_outside_world_border_cannot_milk(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_vacuum_items}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_outside_world_border_cannot_vacuum_items(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_vacuum_items(helper);
    }

    /** See {@link ProtectionScenarios#player_outside_world_border_cannot_feed_animal}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_outside_world_border_cannot_feed_animal(GameTestHelper helper) {
        ProtectionScenarios.player_outside_world_border_cannot_feed_animal(helper);
    }

    /** See {@link ProtectionScenarios#automation_ignores_build_permission}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void automation_ignores_build_permission(GameTestHelper helper) {
        ProtectionScenarios.automation_ignores_build_permission(helper);
    }

    /** See {@link ProtectionScenarios#player_cannot_capture_another_players_pet}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_cannot_capture_another_players_pet(GameTestHelper helper) {
        ProtectionScenarios.player_cannot_capture_another_players_pet(helper);
    }

    /** See {@link ProtectionScenarios#automation_cannot_capture_owned_pet}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void automation_cannot_capture_owned_pet(GameTestHelper helper) {
        ProtectionScenarios.automation_cannot_capture_owned_pet(helper);
    }

    /** See {@link ProtectionScenarios#mob_bucket_refuses_leashed_mob}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void mob_bucket_refuses_leashed_mob(GameTestHelper helper) {
        ProtectionScenarios.mob_bucket_refuses_leashed_mob(helper);
    }

    /** See {@link ProtectionScenarios#automation_does_not_feed_untamed_cat}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void automation_does_not_feed_untamed_cat(GameTestHelper helper) {
        ProtectionScenarios.automation_does_not_feed_untamed_cat(helper);
    }

    /**
     * Automation-only: cancels each Forge block-break event and verifies it denies a player's world
     * fluid pickup at the exact target, while automation posts no such check and still collects.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void cancelled_break_check_denies_player_but_not_automation(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        helper.setBlock(AUTOMATION_TARGET, Blocks.WATER);
        ItemStack playerBucket = GameTestSupport.big8();
        ItemStack automationBucket = GameTestSupport.big8();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        ProtectionContext automation = ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel()));
        List<BlockPos> denied = new ArrayList<>();

        boolean[] acted = new boolean[2];
        denyingBreaks(helper, denied, () -> {
            acted[0] = FluidTransactions.tryTakeFinite(helper.getLevel(),
                    GameTestSupport.hit(helper, TARGET, Direction.UP), playerBucket, player,
                    InteractionHand.MAIN_HAND);
            acted[1] = GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                    GameTestSupport.hit(helper, AUTOMATION_TARGET, Direction.UP), automationBucket,
                    automation);
        });

        GameTestSupport.check(!acted[0], "A cancelled break check did not deny the player's pickup");
        GameTestSupport.check(denied.equals(List.of(helper.absolutePos(TARGET))),
                "Expected one break check at the player's target, got " + denied);
        GameTestSupport.assertEmpty(playerBucket);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        GameTestSupport.check(acted[1], "Automation was denied by a player break check");
        GameTestSupport.assertFluid(automationBucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, AUTOMATION_TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * Automation-only: cancels each Forge entity block-place event and verifies it denies a
     * player's fluid placement at the resolved neighbor, leaving world and bucket unchanged.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void cancelled_place_check_denies_player_fluid_place(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(TARGET.above(), Blocks.AIR);
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        List<BlockPos> denied = new ArrayList<>();

        boolean[] acted = new boolean[1];
        denyingPlacements(helper, denied, () -> acted[0] = FluidTransactions.tryPlaceFinite(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, player,
                InteractionHand.MAIN_HAND));

        GameTestSupport.check(!acted[0], "A cancelled place check did not deny the player's placement");
        GameTestSupport.check(denied.equals(List.of(helper.absolutePos(TARGET.above()))),
                "Expected one place check at the resolved target, got " + denied);
        GameTestSupport.assertSameStack(before, bucket, "Denied placement drained the bucket");
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        helper.succeed();
    }

    /**
     * Cancels Forge block-break events inside this test's structure, recording each position,
     * while {@code action} runs.
     */
    private static void denyingBreaks(GameTestHelper helper, List<BlockPos> denied, Runnable action) {
        Consumer<BlockEvent.BreakEvent> listener = new Consumer<BlockEvent.BreakEvent>() {
            @Override public void accept(BlockEvent.BreakEvent event) {
                if (!helper.getBounds().contains(Vec3.atCenterOf(event.getPos()))) return;
                denied.add(event.getPos().immutable());
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            action.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }

    /**
     * Cancels Forge entity block-place events inside this test's structure, recording each position,
     * while {@code action} runs.
     */
    private static void denyingPlacements(GameTestHelper helper, List<BlockPos> denied, Runnable action) {
        Consumer<BlockEvent.EntityPlaceEvent> listener = new Consumer<BlockEvent.EntityPlaceEvent>() {
            @Override public void accept(BlockEvent.EntityPlaceEvent event) {
                if (!helper.getBounds().contains(Vec3.atCenterOf(event.getPos()))) return;
                denied.add(event.getPos().immutable());
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            action.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }
}
