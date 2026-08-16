package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.register.FabricItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CauldronGameTests {
    private static final BlockPos CAULDRON = new BlockPos(4, 2, 4);

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void both_big_bucket_tiers_register_powder_cauldron_interactions(GameTestHelper helper) {
        assertRegistered(FabricItems.BIG_BUCKET_8);
        assertRegistered(FabricItems.BIG_BUCKET_64);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void full_water_cauldron_fills_empty_big_bucket(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, state);

        boolean acted = GameTestSupport.fabricOps().tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Full water cauldron interaction did not succeed");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_big_bucket_takes_from_full_water_cauldron_first(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, state);

        boolean acted = GameTestSupport.fabricOps().tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Partial Big Bucket did not take cauldron water");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 2000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_water_cauldron_is_not_collected(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);

        boolean acted = GameTestSupport.fabricOps().tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!acted, "Partial water cauldron was collected");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Rejected interaction changed cauldron");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        BlockState state = Blocks.CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, state);

        boolean acted = GameTestSupport.fabricOps().tryBigPlaceWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(acted, "Big Bucket did not fill empty water cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(CAULDRON).getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL,
                "Water cauldron was not filled to level 3");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void lava_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState full = Blocks.LAVA_CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, full);

        boolean collected = GameTestSupport.fabricOps().tryBigTakeWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());
        boolean placed = GameTestSupport.fabricOps().tryBigPlaceWithContext(helper.getLevel(),
                GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(collected, "Big Bucket did not collect lava cauldron");
        GameTestSupport.check(placed, "Big Bucket did not refill lava cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void powder_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState full = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, full);

        InteractionResult collected = interact(helper, CauldronInteraction.POWDER_SNOW, full, bucket);
        BlockState empty = helper.getBlockState(CAULDRON);
        InteractionResult placed = interact(helper, CauldronInteraction.EMPTY, empty, bucket);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect powder cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill powder cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.POWDER_SNOW_CAULDRON);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void milk_does_not_fill_empty_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack before = bucket.copy();
        BlockState state = Blocks.CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, CauldronInteraction.EMPTY, state, bucket);

        GameTestSupport.check(!result.consumesAction(), "Milk filled vanilla cauldron");
        GameTestSupport.assertSameStack(before, bucket, "Rejected milk-cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_big_bucket_cauldron_round_trip_awards_item_use_and_emits_fluid_events(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, CAULDRON.north(2));
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        BlockState full = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, full);

        EventRecorder recorder = new EventRecorder(helper, CAULDRON);
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        boolean pickup;
        boolean placement;
        recorder.add(helper.getLevel());
        try {
            pickup = BucketOperations.get().tryBigTake(helper.getLevel(),
                    GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket, player, InteractionHand.MAIN_HAND);
            placement = BucketOperations.get().tryBigPlace(helper.getLevel(),
                    GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket, player, InteractionHand.MAIN_HAND);
        } finally {
            recorder.remove(helper.getLevel());
        }

        GameTestSupport.check(pickup && placement,
                "Player Big Bucket cauldron round trip failed");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == itemUsesBefore + 2,
                "Cauldron round trip did not award exactly two item-use statistics");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Cauldron pickup did not emit exactly one fluid-pickup game event");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Cauldron placement did not emit exactly one fluid-place game event");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_source_cauldron_round_trip_assigns_and_remains_infinite(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, CAULDRON.north(2));
        ItemStack bucket = GameTestSupport.source();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(CAULDRON, Blocks.LAVA_CAULDRON);
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        boolean pickedUp = BucketOperations.get().trySourceTake(
                helper.getLevel(), GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket,
                player, InteractionHand.MAIN_HAND);
        boolean placed = BucketOperations.get().trySourcePlace(
                helper.getLevel(), GameTestSupport.hit(helper, CAULDRON, Direction.UP), bucket,
                player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(pickedUp && placed, "Player Source Bucket cauldron round trip failed");
        GameTestSupport.assertFluid(bucket, Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == itemUsesBefore + 2,
                "Source cauldron round trip did not award exactly two item-use statistics");
        helper.succeed();
    }

    private static void assertRegistered(Item item) {
        GameTestSupport.check(CauldronInteraction.EMPTY.containsKey(item),
                "Missing empty-cauldron (powder placement) registration for " + item);
        GameTestSupport.check(CauldronInteraction.POWDER_SNOW.containsKey(item),
                "Missing powder-cauldron registration for " + item);
    }

    private static InteractionResult interact(GameTestHelper helper, Map<Item, CauldronInteraction> map,
                                              BlockState state, ItemStack stack) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 2, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        CauldronInteraction interaction = map.get(stack.getItem());
        GameTestSupport.check(interaction != null, "No cauldron interaction registered for " + stack.getItem());
        return interaction.interact(state, helper.getLevel(), helper.absolutePos(CAULDRON), player,
                InteractionHand.MAIN_HAND, stack);
    }

    private static final class EventRecorder implements GameEventListener {
        private final BlockPos absoluteTarget;
        private final List<GameEvent> events = new ArrayList<>();
        private final DynamicGameEventListener<EventRecorder> dynamicListener;

        private EventRecorder(GameTestHelper helper, BlockPos relativeTarget) {
            absoluteTarget = helper.absolutePos(relativeTarget);
            dynamicListener = new DynamicGameEventListener<>(this);
        }

        private void add(ServerLevel level) {
            dynamicListener.add(level);
        }

        private void remove(ServerLevel level) {
            dynamicListener.remove(level);
        }

        private long count(GameEvent event) {
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
        public boolean handleGameEvent(ServerLevel level, GameEvent event,
                                       GameEvent.Context context, Vec3 position) {
            if (BlockPos.containing(position).equals(absoluteTarget)) events.add(event);
            return true;
        }
    }
}
