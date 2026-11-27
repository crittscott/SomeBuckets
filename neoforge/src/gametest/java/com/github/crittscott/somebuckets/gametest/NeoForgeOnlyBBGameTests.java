package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Consumer;

/**
 * NeoForge-only Big Bucket coverage: powder-snow block output posts NeoForge's
 * {@link BlockEvent.EntityPlaceEvent} and honors its cancellation atomically, and the item fluid
 * handler refuses to fill a stack of empties. NeoForge has no
 * fill-bucket event, so the Forge {@code FillBucketEventGameTests} suite has no counterpart here.
 */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class NeoForgeOnlyBBGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private NeoForgeOnlyBBGameTests() {}

    /** See {@link BBScenarios#powder_snow_place_event_cancellation_is_atomic}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_place_event_cancellation_is_atomic(GameTestHelper helper) {
        BBScenarios.powder_snow_place_event_cancellation_is_atomic(
                helper, (target, onEvent, action) -> {
                    Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
                        if (event.getPos().equals(target)) {
                            onEvent.run();
                            event.setCanceled(true);
                        }
                    };
                    NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
                    try {
                        action.run();
                    } finally {
                        NeoForge.EVENT_BUS.unregister(listener);
                    }
                });
    }

    /**
     * In game: with a protection mod that logs block placements, right-click the top of a stone block
     * with a Big Bucket holding two powder snow; one placement is recorded and the bucket keeps one
     * unit.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_place_event_fires_once_on_success(GameTestHelper helper) {
        helper.setBlock(TARGET.below(), Blocks.STONE);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.powder(GameTestSupport.big8(), 2));
        int[] eventCalls = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getPos().equals(helper.absolutePos(TARGET))) eventCalls[0]++;
        };

        InteractionResult result;
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET.below(), Direction.UP));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(result.consumesAction(), "Powder placement did not succeed");
        GameTestSupport.check(eventCalls[0] == 1, "Powder placement did not post exactly one place event");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertPowder(player.getMainHandItem(), 1);
        helper.succeed();
    }

    /**
     * No in-game form: a dispenser placing powder snow from a Big Bucket does not post a player
     * place event, so protection mods do not see a placement by a player who is not there.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_automation_posts_no_player_place_event(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, dispenserPos, Direction.EAST,
                GameTestSupport.powder(GameTestSupport.big8(), 1));
        int[] eventCalls = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getPos().equals(helper.absolutePos(TARGET))) eventCalls[0]++;
        };

        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            GameTestSupport.dispenseNow(helper, dispenserPos);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(eventCalls[0] == 0, "Automation powder placement posted a player place event");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertEmpty(dispenser.getItem(0));
        helper.succeed();
    }

    /**
     * No in-game form: needs a machine mod that fills buckets in a slot. Guards such a machine from
     * turning one bucket of fluid into a stack of filled buckets: filling NeoForge's item fluid handler
     * on a stack of 16 empty Big or Source Buckets accepts nothing, while a single empty Big Bucket
     * still fills.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void item_fluid_handler_refuses_a_stack_of_empties(GameTestHelper helper) {
        for (ItemStack stack : List.of(GameTestSupport.big8().copyWithCount(16),
                GameTestSupport.source().copyWithCount(16))) {
            IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
            int filled = handler.fill(new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME),
                    IFluidHandler.FluidAction.EXECUTE);
            GameTestSupport.check(filled == 0, "A stack of empty buckets accepted " + filled + " mB");
            GameTestSupport.check(stack.getCount() == 16, "The stack of empties changed size: " + stack);
            GameTestSupport.assertEmpty(stack);
        }

        ItemStack stack = GameTestSupport.big8();
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        int filled = handler.fill(new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME),
                IFluidHandler.FluidAction.EXECUTE);
        GameTestSupport.check(filled == FluidType.BUCKET_VOLUME, "A single empty Big Bucket accepted " + filled + " mB");
        GameTestSupport.assertFluid(stack, Fluids.WATER, FluidType.BUCKET_VOLUME);
        helper.succeed();
    }
}
