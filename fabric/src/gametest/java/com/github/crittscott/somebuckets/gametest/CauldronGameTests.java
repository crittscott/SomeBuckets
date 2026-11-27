package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.register.FabricItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;

/**
 * Fabric cauldron coverage. Water and lava cauldrons reach a Big or Source Bucket through Fabric's
 * fluid Transfer API on the bucket's own use; powder snow cauldrons through the vanilla cauldron
 * interaction maps.
 */
public final class CauldronGameTests {
    private static final BlockPos CAULDRON = new BlockPos(4, 2, 4);

    /**
     * No in-game form: guards that both Big Bucket sizes are registered in the empty and powder-snow
     * cauldron interaction maps, through which a click on those cauldrons reaches the bucket.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void both_big_bucket_tiers_register_powder_cauldron_interactions(GameTestHelper helper) {
        assertRegistered(FabricItems.BIG_BUCKET_8);
        assertRegistered(FabricItems.BIG_BUCKET_64);
        helper.succeed();
    }

    /**
     * In game: right-click a full water cauldron with an empty Big Bucket; the bucket holds one unit
     * of water and the cauldron is empty.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void full_water_cauldron_fills_empty_big_bucket(GameTestHelper helper) {
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, state);
        Player player = playerAtCauldron(helper, GameTestSupport.big8());

        InteractionResult result = click(helper, player);

        GameTestSupport.check(result.consumesAction(), "Full water cauldron interaction did not succeed");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: right-click a full water cauldron with a Big Bucket holding one unit of water; the
     * bucket takes the cauldron's water (two units) instead of pouring into it.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_big_bucket_takes_from_full_water_cauldron_first(GameTestHelper helper) {
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, state);
        Player player = playerAtCauldron(helper,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));

        InteractionResult result = click(helper, player);

        GameTestSupport.check(result.consumesAction(), "Partial Big Bucket did not take cauldron water");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 2000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /** In game: right-click a two-thirds full water cauldron with an empty Big Bucket; nothing happens. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_water_cauldron_is_not_collected(GameTestHelper helper) {
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);
        Player player = playerAtCauldron(helper, GameTestSupport.big8());

        InteractionResult result = click(helper, player);

        GameTestSupport.check(!result.consumesAction(), "Partial water cauldron was collected");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Rejected interaction changed cauldron");
        helper.succeed();
    }

    /**
     * In game: right-click an empty cauldron with a Big Bucket holding two units of water; the
     * cauldron fills to the brim and the bucket has one unit left.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.CAULDRON.defaultBlockState());
        Player player = playerAtCauldron(helper,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = click(helper, player);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not fill empty water cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(CAULDRON).getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL,
                "Water cauldron was not filled to level 3");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: right-click a lava cauldron with an empty Big Bucket, then right-click the now empty
     * cauldron again; the cauldron holds lava again and the bucket is empty.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void lava_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.LAVA_CAULDRON.defaultBlockState());
        Player player = playerAtCauldron(helper, GameTestSupport.big8());

        InteractionResult collected = click(helper, player);
        InteractionResult placed = click(helper, player);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect lava cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill lava cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: right-click a full powder snow cauldron with an empty Big Bucket, then right-click the
     * now empty cauldron again; the cauldron is full of powder snow again and the bucket is empty.
     * Drives the cauldron's registered interactions, which the cauldron block runs for the click.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void powder_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState full = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, full);

        ItemInteractionResult collected = interact(helper, CauldronInteraction.POWDER_SNOW, full, bucket);
        BlockState empty = helper.getBlockState(CAULDRON);
        ItemInteractionResult placed = interact(helper, CauldronInteraction.EMPTY, empty, bucket);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect powder cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill powder cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.POWDER_SNOW_CAULDRON);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: right-click an empty cauldron with a Big Bucket of milk; the cauldron stays empty and
     * the bucket keeps its milk. Drives the empty cauldron's registered interaction, which the
     * cauldron block runs for the click.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void milk_does_not_fill_empty_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack before = bucket.copy();
        BlockState state = Blocks.CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, state);

        ItemInteractionResult result = interact(helper, CauldronInteraction.EMPTY, state, bucket);

        GameTestSupport.check(!result.consumesAction(), "Milk filled vanilla cauldron");
        GameTestSupport.assertSameStack(before, bucket, "Rejected milk-cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: with a sculk sensor nearby, right-click a full water cauldron with an empty Big Bucket
     * and then right-click it again; the sensor reacts to a fluid pickup and then a fluid placement,
     * the cauldron is full again, and the bucket's "times used" statistic rises by two.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_big_bucket_cauldron_round_trip_awards_item_use_and_emits_fluid_events(
            GameTestHelper helper) {
        BlockState full = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, full);
        ItemStack bucket = GameTestSupport.big8();
        // survivalPlayer creates a ServerPlayer, which owns the statistics read below.
        ServerPlayer player = (ServerPlayer) playerAtCauldron(helper, bucket);

        GameTestSupport.GameEventRecorder recorder = new GameTestSupport.GameEventRecorder(helper, CAULDRON);
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult pickup;
        InteractionResult placement;
        recorder.add(helper.getLevel());
        try {
            pickup = click(helper, player);
            placement = click(helper, player);
        } finally {
            recorder.remove(helper.getLevel());
        }

        GameTestSupport.check(pickup.consumesAction() && placement.consumesAction(),
                "Player Big Bucket cauldron round trip failed");
        GameTestSupport.assertEmpty(player.getMainHandItem());
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

    /**
     * In game: right-click a lava cauldron with an empty Source Bucket, then right-click the now empty
     * cauldron again; the Source Bucket becomes and stays a lava Source Bucket, the cauldron holds
     * lava again, and the bucket's "times used" statistic rises by two.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_source_cauldron_round_trip_assigns_and_remains_infinite(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.LAVA_CAULDRON);
        ItemStack bucket = GameTestSupport.source();
        // survivalPlayer creates a ServerPlayer, which owns the statistics read below.
        ServerPlayer player = (ServerPlayer) playerAtCauldron(helper, bucket);
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult pickedUp = click(helper, player);
        InteractionResult placed = click(helper, player);

        GameTestSupport.check(pickedUp.consumesAction() && placed.consumesAction(),
                "Player Source Bucket cauldron round trip failed");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == itemUsesBefore + 2,
                "Source cauldron round trip did not award exactly two item-use statistics");
        helper.succeed();
    }

    private static void assertRegistered(Item item) {
        GameTestSupport.check(CauldronInteraction.EMPTY.map().containsKey(item),
                "Missing empty-cauldron (powder placement) registration for " + item);
        GameTestSupport.check(CauldronInteraction.POWDER_SNOW.map().containsKey(item),
                "Missing powder-cauldron registration for " + item);
    }

    private static ItemInteractionResult interact(GameTestHelper helper,
                                                  CauldronInteraction.InteractionMap interactions,
                                                  BlockState state, ItemStack stack) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 2, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        CauldronInteraction interaction = interactions.map().get(stack.getItem());
        GameTestSupport.check(interaction != null, "No cauldron interaction registered for " + stack.getItem());
        return interaction.interact(state, helper.getLevel(), helper.absolutePos(CAULDRON), player,
                InteractionHand.MAIN_HAND, stack);
    }

    /** A survival player above the cauldron, looking straight down at it and holding {@code bucket}. */
    private static Player playerAtCauldron(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, CAULDRON.above(2), CAULDRON);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }

    private static InteractionResult click(GameTestHelper helper, Player player) {
        return GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CAULDRON, Direction.UP));
    }
}
