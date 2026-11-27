package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.GameEventRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Vanilla cauldron scenarios shared by both loaders. A right-click is dispatched as vanilla does for
 * a player: the cauldron's own block use (its interaction map) unless the player is sneaking, then
 * the held item. The loader's block-use event is not posted.
 */
final class CauldronScenarios {
    private static final BlockPos CAULDRON = new BlockPos(4, 2, 4);

    private CauldronScenarios() {}

    /**
     * In game: hold a Big Bucket with two units of water and right-click a partly filled water
     * cauldron; nothing happens: the cauldron keeps its level, no water is poured on top, and the
     * bucket keeps both units.
     */
    static void big_bucket_use_on_partial_water_cauldron_places_nothing(GameTestHelper helper) {
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = rightClick(helper, player);

        GameTestSupport.check(!result.consumesAction(), "Big Bucket use on a partial cauldron consumed the action");
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Big Bucket use changed the cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON.above(), Blocks.AIR);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 2000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of water and right-click an empty cauldron; it fills
     * to the brim with water and the bucket keeps one unit.
     */
    static void big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.CAULDRON);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = rightClick(helper, player);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not fill empty water cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(CAULDRON).getValue(LayeredCauldronBlock.LEVEL) == 3,
                "Water cauldron was not filled to level 3");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * No in-game form: guards that the Big and Huge Bucket each have an entry in vanilla's empty,
     * water, lava and powder-snow cauldron interaction maps, which the other cauldron scenarios
     * exercise only for the Big Bucket.
     */
    static void both_big_bucket_tiers_are_registered(GameTestHelper helper) {
        assertRegistered(GameTestSupport.big8().getItem());
        assertRegistered(GameTestSupport.big64().getItem());
        helper.succeed();
    }

    /**
     * In game: hold an empty Big Bucket and right-click a full water cauldron; the cauldron empties
     * and the bucket holds one unit of water.
     */
    static void full_water_cauldron_fills_empty_big_bucket(GameTestHelper helper) {
        helper.setBlock(CAULDRON, fullWater());
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult result = rightClick(helper, player);

        GameTestSupport.check(result.consumesAction(), "Full water cauldron interaction did not succeed");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: hold an empty Big Bucket, right-click a lava cauldron, then right-click the emptied
     * cauldron; the lava goes into the bucket and back, leaving a lava cauldron and an empty bucket
     * with no leftover content.
     */
    static void lava_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.LAVA_CAULDRON);
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult collected = rightClick(helper, player);
        InteractionResult placed = rightClick(helper, player);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect lava cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill lava cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with one unit of milk and right-click an empty cauldron; you start
     * drinking the milk instead, and the cauldron stays empty.
     */
    static void milk_does_not_fill_empty_cauldron(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.CAULDRON);
        Player player = playerAbove(helper, GameTestSupport.milk(GameTestSupport.big8(), 1000));
        ItemStack before = player.getMainHandItem().copy();

        rightClick(helper, player);

        GameTestSupport.check(player.isUsingItem(), "Milk Big Bucket did not start drinking");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Milk-cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with one unit of water and right-click a full water cauldron; the
     * bucket takes the cauldron's water (two units) rather than pouring, and the cauldron empties.
     */
    static void partial_big_bucket_takes_from_full_water_cauldron_first(GameTestHelper helper) {
        helper.setBlock(CAULDRON, fullWater());
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));

        InteractionResult result = rightClick(helper, player);

        GameTestSupport.check(result.consumesAction(), "Partial Big Bucket did not take cauldron water");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 2000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: hold an empty Big Bucket and right-click a water cauldron that is two-thirds full;
     * nothing happens.
     */
    static void partial_water_cauldron_is_not_collected(GameTestHelper helper) {
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult result = rightClick(helper, player);

        GameTestSupport.check(!result.consumesAction(), "Partial water cauldron was collected");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Rejected interaction changed cauldron");
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket of water and record your statistics; right-click a full water
     * cauldron, then the emptied cauldron. The Big Bucket's "times used" statistic rises by two, "Cauldrons
     * used" and "Cauldrons filled" by one each, and a nearby sculk sensor detects one fluid pickup and one
     * fluid placement.
     */
    static void player_big_bucket_cauldron_round_trip_has_vanilla_accounting(GameTestHelper helper) {
        helper.setBlock(CAULDRON, fullWater());
        ServerPlayer player = GameTestSupport.serverPlayer(helper, CAULDRON.above());
        player.setXRot(90.0F);
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        GameEventRecorder recorder = new GameEventRecorder(helper, CAULDRON);
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        int cauldronUsesBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON));
        int cauldronFillsBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.FILL_CAULDRON));

        InteractionResult pickup;
        InteractionResult placement;
        recorder.add(helper.getLevel());
        try {
            pickup = rightClick(helper, player);
            placement = rightClick(helper, player);
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
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON))
                        == cauldronUsesBefore + 1,
                "Cauldron pickup did not award exactly one cauldron-use statistic");
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.FILL_CAULDRON))
                        == cauldronFillsBefore + 1,
                "Cauldron placement did not award exactly one cauldron-fill statistic");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Cauldron pickup did not emit exactly one fluid-pickup game event");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Cauldron placement did not emit exactly one fluid-place game event");
        helper.succeed();
    }

    /**
     * In game: hold an empty Source Bucket, right-click a lava cauldron, then right-click the emptied
     * cauldron; the bucket is assigned to lava, the cauldron is refilled, and the bucket stays
     * assigned. The Source Bucket's "times used" statistic rises by two, "Cauldrons used" and
     * "Cauldrons filled" by one each.
     */
    static void player_source_cauldron_round_trip_assigns_and_remains_infinite(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.LAVA_CAULDRON);
        ServerPlayer player = GameTestSupport.serverPlayer(helper, CAULDRON.above());
        player.setXRot(90.0F);
        ItemStack bucket = GameTestSupport.source();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        int cauldronUsesBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON));
        int cauldronFillsBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.FILL_CAULDRON));

        InteractionResult pickedUp = rightClick(helper, player);
        InteractionResult placed = rightClick(helper, player);

        GameTestSupport.check(pickedUp.consumesAction() && placed.consumesAction(),
                "Player Source Bucket cauldron round trip failed");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == itemUsesBefore + 2,
                "Source cauldron round trip did not award exactly two item-use statistics");
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON))
                        == cauldronUsesBefore + 1,
                "Source cauldron pickup did not award exactly one cauldron-use statistic");
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.FILL_CAULDRON))
                        == cauldronFillsBefore + 1,
                "Source cauldron placement did not award exactly one cauldron-fill statistic");
        helper.succeed();
    }

    /**
     * In game: hold an empty Big Bucket, right-click a full powder-snow cauldron, then right-click the
     * emptied cauldron; the powder snow goes into the bucket and back, leaving a powder-snow cauldron
     * and an empty bucket with no leftover content.
     */
    static void powder_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult collected = rightClick(helper, player);
        InteractionResult placed = rightClick(helper, player);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect powder cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill powder cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.POWDER_SNOW_CAULDRON);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of powder snow and right-click a full water cauldron;
     * nothing happens: no powder snow appears on top and the cauldron keeps its water.
     */
    static void powder_big_bucket_use_on_water_cauldron_places_nothing(GameTestHelper helper) {
        BlockState state = fullWater();
        helper.setBlock(CAULDRON, state);
        Player player = playerAbove(helper, GameTestSupport.powder(GameTestSupport.big8(), 2));

        InteractionResult result = rightClick(helper, player);

        GameTestSupport.check(!result.consumesAction(), "Powder Big Bucket acted on a water cauldron");
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Powder use changed the cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON.above(), Blocks.AIR);
        GameTestSupport.assertPowder(player.getMainHandItem(), 2);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of water, sneak, and right-click a partly filled water
     * cauldron; as with a vanilla water bucket, water is poured on top of the cauldron, the cauldron
     * keeps its level, and the bucket keeps one unit.
     */
    static void sneaking_big_bucket_use_on_partial_water_cauldron_pours_beside_it(GameTestHelper helper) {
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));
        player.setShiftKeyDown(true);

        InteractionResult result = rightClick(helper, player);

        GameTestSupport.check(result.consumesAction(), "Sneaking Big Bucket use did not pour");
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Sneaking use changed the cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON.above(), Blocks.WATER);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    private static void assertRegistered(Item item) {
        GameTestSupport.check(CauldronInteraction.EMPTY.containsKey(item), "Missing empty-cauldron registration for " + item);
        GameTestSupport.check(CauldronInteraction.WATER.containsKey(item), "Missing water-cauldron registration for " + item);
        GameTestSupport.check(CauldronInteraction.LAVA.containsKey(item), "Missing lava-cauldron registration for " + item);
        GameTestSupport.check(CauldronInteraction.POWDER_SNOW.containsKey(item),
                "Missing powder-cauldron registration for " + item);
    }

    private static BlockState fullWater() {
        return Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
    }

    /* A survival player directly above the cauldron, looking straight down at it, holding bucket. */
    private static Player playerAbove(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, CAULDRON.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }

    /*
     * A right-click on the cauldron's top in vanilla's server order: the block's own use unless the
     * player is sneaking, then, if that did not consume the click, the held item.
     */
    private static InteractionResult rightClick(GameTestHelper helper, Player player) {
        BlockHitResult hit = GameTestSupport.hit(helper, CAULDRON, Direction.UP);
        if (!player.isSecondaryUseActive()) {
            InteractionResult blockResult = helper.getBlockState(CAULDRON)
                    .use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            if (blockResult.consumesAction()) return blockResult;
        }
        return GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND, hit);
    }
}
