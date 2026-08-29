package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BlockCapabilityGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_big_bucket_take_is_exact_observable_and_accounted(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, new StoredFluid(Fluids.WATER, 2000, null));
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        EventRecorder recorder = new EventRecorder(helper, TARGET);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        recorder.add(helper.getLevel());
        boolean acted;
        try {
            acted = GameTestSupport.fabricOps().tryBigTakeWithContext(helper.getLevel(),
                    GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                    ProtectionContext.player(player, InteractionHand.MAIN_HAND));
        } finally {
            recorder.remove(helper.getLevel());
        }

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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_place_uses_handler_infinity_and_automation_event(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, StoredFluid.EMPTY);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        EventRecorder recorder = new EventRecorder(helper, TARGET);

        recorder.add(helper.getLevel());
        boolean acted;
        try {
            acted = GameTestSupport.fabricOps().trySourcePlaceWithContext(helper.getLevel(),
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void block_capability_uses_contacted_side(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.NORTH, 4000, new StoredFluid(Fluids.WATER, 2000, null));
        ItemStack bucket = GameTestSupport.big8();

        boolean wrongSide = GameTestSupport.fabricOps().tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!wrongSide, "Tank capability was discovered through the wrong side");
        GameTestSupport.assertEmpty(bucket);
        assertTank(tank, Fluids.WATER, 2000);

        boolean correctSide = GameTestSupport.fabricOps().tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.NORTH), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(correctSide, "Tank capability was not discovered through its exposed side");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        assertTank(tank, Fluids.WATER, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_block_transactions_refuse_without_mutation(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity sourceTank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 500, new StoredFluid(Fluids.WATER, 500, null));
        ItemStack emptyBucket = GameTestSupport.big8();

        boolean took = GameTestSupport.fabricOps().tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), emptyBucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!took, "Partial tank drain was accepted");
        GameTestSupport.assertEmpty(emptyBucket);
        assertTank(sourceTank, Fluids.WATER, 500);

        BlockPos destinationPos = TARGET.east(2);
        GameTestSupport.SidedFluidBlockEntity destinationTank = GameTestSupport.fluidTank(helper,
                destinationPos, Direction.UP, 500, StoredFluid.EMPTY);
        ItemStack filledBucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);

        boolean placed = GameTestSupport.fabricOps().tryBigPlaceWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, destinationPos, Direction.UP), filledBucket,
                ProtectionContext.unownedAutomation(), false);

        GameTestSupport.check(!placed, "Partial tank fill was accepted");
        GameTestSupport.assertFluid(filledBucket, Fluids.WATER, 1000);
        GameTestSupport.check(destinationTank.contents().isEmpty(),
                "Refused partial fill mutated the destination tank");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void protection_denial_keeps_tank_and_bucket_atomic(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, new StoredFluid(Fluids.WATER, 2000, null));
        ItemStack bucket = GameTestSupport.big8();
        BlockPos absoluteTarget = helper.absolutePos(TARGET);

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) ->
                        level != helper.getLevel()
                                || !absoluteTarget.equals(target)
                                || action != ProtectionAction.BLOCK_INTERACT)) {
            acted = GameTestSupport.fabricOps().tryBigTakeWithContext(helper.getLevel(),
                    GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                    ProtectionContext.unownedAutomation());
        }

        GameTestSupport.check(!acted, "Protected tank transaction succeeded");
        GameTestSupport.assertEmpty(bucket);
        assertTank(tank, Fluids.WATER, 2000);
        helper.succeed();
    }

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
                                   net.minecraft.world.level.material.Fluid fluid, int amount) {
        StoredFluid contents = tank.contents();
        GameTestSupport.check(contents.fluid() == fluid && contents.amount() == amount,
                "Expected tank to contain " + amount + " mB of " + fluid + ", got " + contents);
    }

    private static final class EventRecorder implements GameEventListener {
        private final BlockPos absoluteTarget;
        private final List<Holder<GameEvent>> events = new ArrayList<>();
        private final DynamicGameEventListener<EventRecorder> dynamicListener;

        private EventRecorder(GameTestHelper helper, BlockPos relativeTarget) {
            this.absoluteTarget = helper.absolutePos(relativeTarget);
            this.dynamicListener = new DynamicGameEventListener<>(this);
        }

        private void add(ServerLevel level) {
            dynamicListener.add(level);
        }

        private void remove(ServerLevel level) {
            dynamicListener.remove(level);
        }

        private long count(Holder<GameEvent> event) {
            return events.stream().filter(observed -> observed == event).count();
        }

        @Override
        public BlockPositionSource getListenerSource() {
            return new BlockPositionSource(absoluteTarget);
        }

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event,
                                       GameEvent.Context context, Vec3 position) {
            if (BlockPos.containing(position).equals(absoluteTarget)) events.add(event);
            return true;
        }
    }
}
