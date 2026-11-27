package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Fabric fluid-storage block coverage, through a structure-block-backed tank fixture that exposes
 * {@code FluidStorage.SIDED} on one face, standing in for a modded tank.
 */
public final class BlockCapabilityGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    /**
     * In game: with a modded tank that exposes Fabric fluid storage on its top and holds two units of
     * water, and a sculk sensor nearby, right-click the tank's top with an empty Big Bucket; the
     * bucket takes exactly one unit, the tank keeps one, the sensor reacts to a fluid pickup, and the
     * bucket's "times used" statistic rises by one.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_big_bucket_take_is_exact_observable_and_accounted(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, new StoredFluid(Fluids.WATER, 2000, null));
        // survivalPlayer creates a ServerPlayer, which owns the statistics read below.
        ServerPlayer player = (ServerPlayer) GameTestSupport.survivalPlayerLookingAt(helper, TARGET.above(2), TARGET);
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        GameTestSupport.GameEventRecorder recorder = new GameTestSupport.GameEventRecorder(helper, TARGET);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        recorder.add(helper.getLevel());
        InteractionResult result;
        try {
            result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET, Direction.UP));
        } finally {
            recorder.remove(helper.getLevel());
        }

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not drain the sided tank");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        assertTank(tank, Fluids.WATER, 1000);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player tank drain did not award exactly one bucket-use stat");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Tank drain did not emit exactly one fluid-pickup game event");
        helper.succeed();
    }

    /**
     * No in-game form: guards that an unowned automated Source Bucket placement into a fluid-storage
     * block (for example, any tank exposing Fabric's fluid storage) fills it with one unit, keeps the
     * Source Bucket assigned, and emits one fluid-place game event.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_place_uses_handler_infinity_and_automation_event(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, StoredFluid.EMPTY);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        GameTestSupport.GameEventRecorder recorder = new GameTestSupport.GameEventRecorder(helper, TARGET);

        recorder.add(helper.getLevel());
        boolean acted;
        try {
            acted = GameTestSupport.trySourcePlaceWithContext(helper.getLevel(),
                    GameTestSupport.hit(helper, TARGET, Direction.UP), source,
                    ProtectionContext.unownedAutomation(), false);
        } finally {
            recorder.remove(helper.getLevel());
        }

        GameTestSupport.check(acted, "Source Bucket did not fill the sided tank");
        GameTestSupport.assertFluid(source, Fluids.LAVA, 1000);
        assertTank(tank, Fluids.LAVA, 1000);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Automated tank fill did not emit exactly one fluid-place game event");
        helper.succeed();
    }

    /**
     * No in-game form: guards that a Big Bucket reaches a block's Fabric fluid storage only through the
     * face it touches; for example, a modded tank exposing storage only on its north side gives nothing
     * to a bucket used on its top and one unit to a bucket used on its north face.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void block_capability_uses_contacted_side(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.NORTH, 4000, new StoredFluid(Fluids.WATER, 2000, null));
        ItemStack bucket = GameTestSupport.big8();

        boolean wrongSide = GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!wrongSide, "Tank capability was discovered through the wrong side");
        GameTestSupport.assertEmpty(bucket);
        assertTank(tank, Fluids.WATER, 2000);

        boolean correctSide = GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.NORTH), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(correctSide, "Tank capability was not discovered through its exposed side");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        assertTank(tank, Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * No in-game form: guards that a Big Bucket refuses a fluid-storage block (for example, any tank
     * exposing Fabric's fluid storage) that can give or take less than a full unit, leaving both sides
     * unchanged.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_block_transactions_refuse_without_mutation(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity sourceTank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 500, new StoredFluid(Fluids.WATER, 500, null));
        ItemStack emptyBucket = GameTestSupport.big8();

        boolean took = GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), emptyBucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!took, "Partial tank drain was accepted");
        GameTestSupport.assertEmpty(emptyBucket);
        assertTank(sourceTank, Fluids.WATER, 500);

        BlockPos destinationPos = TARGET.east(2);
        GameTestSupport.SidedFluidBlockEntity destinationTank = GameTestSupport.fluidTank(helper,
                destinationPos, Direction.UP, 500, StoredFluid.EMPTY);
        ItemStack filledBucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);

        boolean placed = GameTestSupport.tryBigPlaceWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, destinationPos, Direction.UP), filledBucket,
                ProtectionContext.unownedAutomation(), false);

        GameTestSupport.check(!placed, "Partial tank fill was accepted");
        GameTestSupport.assertFluid(filledBucket, Fluids.WATER, 1000);
        GameTestSupport.check(destinationTank.contents().isEmpty(),
                "Refused partial fill mutated the destination tank");
        helper.succeed();
    }

    /**
     * No in-game form: guards that a claim provider refusing interaction with a fluid-storage block
     * leaves both the block's contents and the Big Bucket unchanged.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void protection_denial_keeps_tank_and_bucket_atomic(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, new StoredFluid(Fluids.WATER, 2000, null));
        ItemStack bucket = GameTestSupport.big8();
        BlockPos absoluteTarget = helper.absolutePos(TARGET);

        boolean acted;
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, entity) ->
                        level != helper.getLevel()
                                || !absoluteTarget.equals(target)
                                || action != ProtectionAction.BLOCK_INTERACT)) {
            acted = GameTestSupport.tryBigTakeWithContext(helper.getLevel(),
                    GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                    ProtectionContext.unownedAutomation());
        }

        GameTestSupport.check(!acted, "Protected tank transaction succeeded");
        GameTestSupport.assertEmpty(bucket);
        assertTank(tank, Fluids.WATER, 2000);
        helper.succeed();
    }

    /**
     * In game: with a modded tank that exposes Fabric fluid storage on its west side, put a water
     * Source Bucket in a dispenser facing that side and power it; the tank gains one unit of water and
     * the Source Bucket stays in the dispenser, still assigned.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public void dispenser_source_bucket_fills_sided_tank_without_consumption(GameTestHelper helper) {
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
     * In game: with a modded tank holding two units of water and exposing Fabric fluid storage on its
     * west side, put a water Source Bucket in a dispenser facing that side and power it; the tank drops
     * to one unit and the Source Bucket is unchanged.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public void dispenser_assigned_source_bucket_drains_matching_sided_tank(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.WEST, 4000, new StoredFluid(Fluids.WATER, 2000, null));
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
