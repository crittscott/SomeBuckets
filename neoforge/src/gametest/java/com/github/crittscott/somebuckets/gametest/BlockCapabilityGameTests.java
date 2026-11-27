package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class BlockCapabilityGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private BlockCapabilityGameTests() {}

    /**
     * In game: look down at the top of a modded tank (for example, any tank exposing NeoForge's
     * fluid-handler capability) holding two buckets of water and right-click it with an empty Big
     * Bucket; the bucket takes exactly one unit, the tank keeps one, and the Big Bucket use statistic rises by one. The test also checks
     * that one fluid-pickup game event is emitted at the tank.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_big_bucket_take_is_exact_observable_and_accounted(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, new FluidStack(Fluids.WATER, 2000));
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        player.setXRot(90.0F);
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
     * No in-game form: calls the Source Bucket place transaction directly as unowned automation.
     * Guards that it fills an empty tank with one unit through its fluid handler, stays assigned, and
     * emits one fluid-place game event at the tank; the in-game dispenser form is
     * {@code dispenser_source_bucket_fills_sided_tank_without_consumption}.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_bucket_place_uses_handler_infinity_and_automation_event(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, FluidStack.EMPTY);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        GameTestSupport.GameEventRecorder recorder = new GameTestSupport.GameEventRecorder(helper, TARGET);

        recorder.add(helper.getLevel());
        boolean acted;
        try {
            acted = SBFluidLogic.tryPlace(helper.getLevel(),
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
     * No in-game form: calls the Big Bucket take transaction directly as unowned automation. Guards
     * that a tank exposing its fluid handler only on its north side is not reached through its top and
     * is reached through its north face, as a player or dispenser touching that face would.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void block_capability_uses_contacted_side(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.NORTH, 4000, new FluidStack(Fluids.WATER, 2000));
        ItemStack bucket = GameTestSupport.big8();

        boolean wrongSide = BBFluidLogic.tryTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!wrongSide, "Tank capability was discovered through the wrong side");
        GameTestSupport.assertEmpty(bucket);
        assertTank(tank, Fluids.WATER, 2000);

        boolean correctSide = BBFluidLogic.tryTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.NORTH), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(correctSide, "Tank capability was not discovered through its exposed side");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        assertTank(tank, Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * No in-game form: calls the Big Bucket take and place transactions directly as unowned
     * automation. Guards that a Big Bucket neither takes from a tank holding less than one unit nor
     * places into a tank with less than one unit of room, and that both refusals leave tank and bucket
     * unchanged.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_block_transactions_refuse_without_mutation(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity sourceTank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 500, new FluidStack(Fluids.WATER, 500));
        ItemStack emptyBucket = GameTestSupport.big8();

        boolean took = BBFluidLogic.tryTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), emptyBucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!took, "Partial tank drain was accepted");
        GameTestSupport.assertEmpty(emptyBucket);
        assertTank(sourceTank, Fluids.WATER, 500);

        BlockPos destinationPos = TARGET.east(2);
        GameTestSupport.SidedFluidBlockEntity destinationTank = GameTestSupport.fluidTank(helper,
                destinationPos, Direction.UP, 500, FluidStack.EMPTY);
        ItemStack filledBucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);

        boolean placed = BBFluidLogic.tryPlace(helper.getLevel(),
                GameTestSupport.hit(helper, destinationPos, Direction.UP), filledBucket,
                ProtectionContext.unownedAutomation(), false);

        GameTestSupport.check(!placed, "Partial tank fill was accepted");
        GameTestSupport.assertFluid(filledBucket, Fluids.WATER, 1000);
        GameTestSupport.check(destinationTank.contents().isEmpty(),
                "Refused partial fill mutated the destination tank");
        helper.succeed();
    }

    /**
     * No in-game form: calls the Big Bucket take transaction directly as unowned automation while a
     * registered protection provider refuses block interaction at the tank, standing in for a claim
     * mod. Guards that the refusal leaves tank and bucket unchanged.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void protection_denial_keeps_tank_and_bucket_atomic(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, new FluidStack(Fluids.WATER, 2000));
        ItemStack bucket = GameTestSupport.big8();
        BlockPos absoluteTarget = helper.absolutePos(TARGET);

        boolean acted;
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, entity) ->
                        level != helper.getLevel()
                                || !absoluteTarget.equals(target)
                                || action != ProtectionAction.BLOCK_INTERACT)) {
            acted = BBFluidLogic.tryTakeWithContext(helper.getLevel(),
                    GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                    ProtectionContext.unownedAutomation());
        }

        GameTestSupport.check(!acted, "Protected tank transaction succeeded");
        GameTestSupport.assertEmpty(bucket);
        assertTank(tank, Fluids.WATER, 2000);
        helper.succeed();
    }

    /**
     * In game: put a water Source Bucket in a dispenser facing a modded tank (for example, any tank
     * exposing NeoForge's fluid-handler capability) and pulse it; the tank gains one bucket of water and the Source
     * Bucket stays assigned to water.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_source_bucket_fills_sided_tank_without_consumption(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.WEST, 4000, FluidStack.EMPTY);
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
     * In game: put a water Source Bucket in a dispenser facing a modded tank (for example, any tank
     * exposing NeoForge's fluid-handler capability) holding two buckets of water and pulse it; the tank loses one
     * bucket and the Source Bucket stays assigned to water.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_assigned_source_bucket_drains_matching_sided_tank(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.WEST, 4000, new FluidStack(Fluids.WATER, 2000));
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
        FluidStack contents = tank.contents();
        GameTestSupport.check(contents.getFluid() == fluid && contents.getAmount() == amount,
                "Expected tank to contain " + amount + " mB of " + fluid + ", got " + contents);
    }
}
