package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.protection.AutomationPlayers;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Block fluid-storage scenarios against each loader's sided test tank ({@code GameTestSupport.fluidTank}),
 * which exposes native storage on one face and reports its contents as {@link StoredFluid}.
 */
final class BlockCapabilityScenarios {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private BlockCapabilityScenarios() {}

    /**
     * Automation-only: drains a sided test tank as a player and verifies exact transfer, the
     * fluid-pickup game event, and the item-use statistic.
     */
    static void player_big_bucket_take_is_exact_observable_and_accounted(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, new StoredFluid(Fluids.WATER, 2000));
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, TARGET);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        boolean acted = recorder.during(() -> GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.player(player, InteractionHand.MAIN_HAND)));

        GameTestSupport.check(acted, "Big Bucket did not drain the sided tank");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        assertTank(tank, Fluids.WATER, 1000);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player tank drain did not award exactly one bucket-use stat");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Tank drain did not emit exactly one fluid-pickup game event");
        helper.succeed();
    }

    /**
     * Automation-only: fills a sided test tank from a Source Bucket and verifies infinite supply plus
     * automation observability.
     */
    static void source_bucket_place_uses_handler_infinity_and_automation_event(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, StoredFluid.EMPTY);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, TARGET);

        boolean acted = recorder.during(() -> GameTestSupport.trySourcePlaceWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), source,
                ProtectionContext.dispenser(AutomationPlayers.get(helper.getLevel())), false));

        GameTestSupport.check(acted, "Source Bucket did not fill the sided tank");
        GameTestSupport.assertFluid(source, Fluids.LAVA, 1000);
        assertTank(tank, Fluids.LAVA, 1000);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Automated tank fill did not emit exactly one fluid-place game event");
        helper.succeed();
    }

    /**
     * Automation-only: exposes a test tank on one face and verifies only interaction through that
     * contacted side reaches it.
     */
    static void block_capability_uses_contacted_side(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.NORTH, 4000, new StoredFluid(Fluids.WATER, 2000));
        ItemStack bucket = GameTestSupport.big8();

        boolean wrongSide = GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.dispenser(AutomationPlayers.get(helper.getLevel())));

        GameTestSupport.check(!wrongSide, "Tank capability was discovered through the wrong side");
        GameTestSupport.assertEmpty(bucket);
        assertTank(tank, Fluids.WATER, 2000);

        boolean correctSide = GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.NORTH), bucket,
                ProtectionContext.dispenser(AutomationPlayers.get(helper.getLevel())));

        GameTestSupport.check(correctSide, "Tank capability was not discovered through its exposed side");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        assertTank(tank, Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * Automation-only: makes the test tank accept less than one unit and verifies the bucket transaction
     * refuses atomically.
     */
    static void partial_block_transactions_refuse_without_mutation(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity sourceTank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 500, new StoredFluid(Fluids.WATER, 500));
        ItemStack emptyBucket = GameTestSupport.big8();

        boolean took = GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), emptyBucket,
                ProtectionContext.dispenser(AutomationPlayers.get(helper.getLevel())));

        GameTestSupport.check(!took, "Partial tank drain was accepted");
        GameTestSupport.assertEmpty(emptyBucket);
        assertTank(sourceTank, Fluids.WATER, 500);

        BlockPos destinationPos = TARGET.east(2);
        GameTestSupport.SidedFluidBlockEntity destinationTank = GameTestSupport.fluidTank(helper,
                destinationPos, Direction.UP, 500, StoredFluid.EMPTY);
        ItemStack filledBucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);

        boolean placed = GameTestSupport.tryBigPlaceWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, destinationPos, Direction.UP), filledBucket,
                ProtectionContext.dispenser(AutomationPlayers.get(helper.getLevel())), false);

        GameTestSupport.check(!placed, "Partial tank fill was accepted");
        GameTestSupport.assertFluid(filledBucket, Fluids.WATER, 1000);
        GameTestSupport.check(destinationTank.contents().isEmpty(),
                "Refused partial fill mutated the destination tank");
        helper.succeed();
    }

    /**
     * Automation-only: withdraws the automation player's build permission for a sided-tank interaction
     * and verifies both tank and bucket retain their exact prior state.
     */
    static void protection_denial_keeps_tank_and_bucket_atomic(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, new StoredFluid(Fluids.WATER, 2000));
        ItemStack bucket = GameTestSupport.big8();
        ProtectionContext context = ProtectionContext.dispenser(AutomationPlayers.get(helper.getLevel()));

        boolean acted = ProtectionScenarios.withoutBuildPermission(context.actor(), () ->
                GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                        GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context));

        GameTestSupport.check(!acted, "Protected tank transaction succeeded");
        GameTestSupport.assertEmpty(bucket);
        assertTank(tank, Fluids.WATER, 2000);
        helper.succeed();
    }

    /**
     * Automation-only: pulses a Source Bucket into a sided test tank and verifies one-unit fill without
     * source consumption.
     */
    static void dispenser_source_bucket_fills_sided_tank_without_consumption(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.WEST, 4000, StoredFluid.EMPTY);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        var dispenser = GameTestSupport.dispenser(helper, dispenserPos, Direction.EAST, source);

        GameTestSupport.triggerDispenser(helper, dispenserPos);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
            assertTank(tank, Fluids.WATER, 1000);
            helper.succeed();
        });
    }

    /**
     * Automation-only: pulses an assigned Source Bucket at a matching sided test tank and verifies one
     * unit is removed without reassignment.
     */
    static void dispenser_assigned_source_bucket_drains_matching_sided_tank(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.WEST, 4000, new StoredFluid(Fluids.WATER, 2000));
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = source.copy();
        var dispenser = GameTestSupport.dispenser(helper, dispenserPos, Direction.EAST, source);

        GameTestSupport.triggerDispenser(helper, dispenserPos);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertSameStack(before, dispenser.getItem(0),
                    "Matching tank drain changed Source Bucket assignment");
            assertTank(tank, Fluids.WATER, 1000);
            helper.succeed();
        });
    }

    private static void assertTank(GameTestSupport.SidedFluidBlockEntity tank,
                                   Fluid fluid, int amount) {
        StoredFluid contents = tank.contents();
        GameTestSupport.check(contents.fluid() == fluid && contents.amount() == amount,
                "Expected tank to contain " + amount + " mB of " + fluid + ", got " + contents);
    }
}
