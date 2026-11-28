package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;

import java.util.List;


/** Cross-loader cauldron scenarios; each loader wraps every method as a GameTest. */
final class CauldronScenarios {
    private static final BlockPos CAULDRON = new BlockPos(4, 2, 4);

    private CauldronScenarios() {}

    /**
     * In game: with a Big Bucket and then a Huge Bucket, collect from full water, lava, and powder snow
     * cauldrons and right-click an empty cauldron with each filled bucket; every interaction works with
     * both tiers.
     */
    static void both_big_bucket_tiers_are_registered(GameTestHelper helper) {
        BlockState fullWater = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        BlockState fullPowderSnow = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        for (ItemStack bucket : List.of(GameTestSupport.big8(), GameTestSupport.big64())) {
            for (BlockState full : List.of(fullWater, Blocks.LAVA_CAULDRON.defaultBlockState(), fullPowderSnow)) {
                helper.setBlock(CAULDRON, full);
                GameTestSupport.check(interact(helper, bucket.copy()).consumesAction(),
                        "Missing " + full.getBlock() + " interaction for " + bucket.getItem());
            }
            helper.setBlock(CAULDRON, Blocks.CAULDRON);
            ItemStack filled = GameTestSupport.fluid(bucket.copy(), Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
            GameTestSupport.check(interact(helper, filled).consumesAction(),
                    "Missing empty-cauldron interaction for " + bucket.getItem());
        }
        helper.succeed();
    }

    /** In game: use an empty Big Bucket on a full water cauldron; the cauldron empties and the bucket gains one unit. */
    static void full_water_cauldron_fills_empty_big_bucket(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, bucket);

        GameTestSupport.check(result.consumesAction(), "Full water cauldron interaction did not succeed");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: use a partly water-filled Big Bucket on a full water cauldron; it collects before
     * considering placement.
     */
    static void partial_big_bucket_takes_from_full_water_cauldron_first(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(
                GameTestSupport.big8(), Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, bucket);

        GameTestSupport.check(result.consumesAction(), "Partial Big Bucket did not take cauldron water");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB * 2);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /** In game: use an empty Big Bucket on a partially filled water cauldron; neither cauldron nor bucket changes. */
    static void partial_water_cauldron_is_not_collected(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, bucket);

        GameTestSupport.check(!result.consumesAction(), "Partial water cauldron was collected");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Rejected interaction changed cauldron");
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket of water and right-click a partly filled water cauldron; nothing is
     * poured.
     */
    static void big_bucket_use_on_partial_water_cauldron_places_nothing(GameTestHelper helper) {
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);
        helper.setBlock(CAULDRON.above(), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, CAULDRON.above());
        ItemStack bucket = GameTestSupport.fluid(
                GameTestSupport.big8(), Fluids.WATER, 2 * FluidBucketItem.BUCKET_VOLUME_MB);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult result = bucket.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Big Bucket use on a partial water cauldron acted");
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Big Bucket use changed the cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON.above(), Blocks.AIR);
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 2 * FluidBucketItem.BUCKET_VOLUME_MB);
        helper.succeed();
    }

    /**
     * In game: sneak while holding a Big Bucket of water and right-click a partly filled water
     * cauldron; one unit is poured on top of it, as a vanilla water bucket does.
     */
    static void sneaking_big_bucket_use_on_partial_water_cauldron_places_above(GameTestHelper helper) {
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);
        helper.setBlock(CAULDRON.above(), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, CAULDRON.above());
        player.setShiftKeyDown(true);
        ItemStack bucket = GameTestSupport.fluid(
                GameTestSupport.big8(), Fluids.WATER, 2 * FluidBucketItem.BUCKET_VOLUME_MB);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult result = bucket.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneaking Big Bucket use beside a cauldron did not pour");
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Sneaking Big Bucket use changed the cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON.above(), Blocks.WATER);
        GameTestSupport.assertFluid(bucket, Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket of powder snow and right-click a full water cauldron; no powder snow
     * is placed on top of it.
     */
    static void powder_big_bucket_on_water_cauldron_places_nothing(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        helper.setBlock(CAULDRON.above(), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayer(helper, CAULDRON.north(2));
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult result = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CAULDRON, Direction.UP)));

        GameTestSupport.check(!result.consumesAction(), "Powder Big Bucket use on a water cauldron acted");
        GameTestSupport.assertBlock(helper, CAULDRON.above(), Blocks.AIR);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.assertPowder(bucket, 2);
        helper.succeed();
    }

    /** In game: use a water-filled Big Bucket on an empty cauldron; it becomes full and the bucket spends one unit. */
    static void big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(
                GameTestSupport.big8(), Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB * 2);
        BlockState state = Blocks.CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, bucket);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not fill empty water cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(CAULDRON).getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL,
                "Water cauldron was not filled to level 3");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
        helper.succeed();
    }

    /**
     * In game: right-click a lava cauldron with an empty Big Bucket, then right-click the now-empty
     * cauldron; the cauldron is full of lava again and the bucket is empty and stacks with fresh empty
     * Big Buckets.
     */
    static void lava_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState full = Blocks.LAVA_CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, full);

        InteractionResult collected = interact(helper, bucket);
        InteractionResult placed = interact(helper, bucket);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect lava cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill lava cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: right-click a full powder snow cauldron with an empty Big Bucket, then right-click the
     * now-empty cauldron; the cauldron is full of powder snow again and the bucket is empty and stacks
     * with fresh empty Big Buckets.
     */
    static void powder_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState full = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, full);

        InteractionResult collected = interact(helper, bucket);
        InteractionResult placed = interact(helper, bucket);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect powder cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill powder cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.POWDER_SNOW_CAULDRON);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /** In game: use a milk-filled Big Bucket on an empty cauldron; neither cauldron nor bucket changes. */
    static void milk_does_not_fill_empty_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), FluidBucketItem.BUCKET_VOLUME_MB);
        ItemStack before = bucket.copy();
        BlockState state = Blocks.CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, bucket);

        GameTestSupport.check(!result.consumesAction(), "Milk filled vanilla cauldron");
        GameTestSupport.assertSameStack(before, bucket, "Rejected milk-cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: right-click a full water cauldron with an empty Big Bucket, then right-click the now-
     * empty cauldron; the cauldron empties and refills, the Big Bucket's "used" statistic rises by two,
     * the cauldron-use and cauldron-fill statistics each rise by one, a nearby sculk sensor hears both,
     * and no filled-bucket advancement fires, as with vanilla buckets.
     */
    static void player_big_bucket_cauldron_round_trip_has_vanilla_accounting(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, CAULDRON.north(2));
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        BlockState full = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, full);

        var filled = new GameTestSupport.CriterionProbe(player, "filled_bucket");
        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, CAULDRON);
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        int cauldronUsesBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON));
        int cauldronFillsBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.FILL_CAULDRON));

        boolean roundTrip = filled.during(() -> recorder.during(() -> {
            InteractionResult pickup = interact(helper, bucket, player);
            InteractionResult placement = interact(helper, bucket, player);
            return pickup.consumesAction() && placement.consumesAction();
        }));

        GameTestSupport.check(roundTrip,
                "Player Big Bucket cauldron round trip failed");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == itemUsesBefore + 2,
                "Cauldron round trip did not award exactly two item-use statistics");
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON))
                        == cauldronUsesBefore + 1,
                "Cauldron round trip did not award exactly one cauldron-use statistic");
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.FILL_CAULDRON))
                        == cauldronFillsBefore + 1,
                "Cauldron round trip did not award exactly one cauldron-fill statistic");
        GameTestSupport.check(!filled.fired(),
                "Cauldron pickup fired the filled-bucket criterion, which vanilla cauldrons do not");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Cauldron pickup did not emit exactly one fluid-pickup game event");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Cauldron placement did not emit exactly one fluid-place game event");
        helper.succeed();
    }

    /**
     * In game: right-click a lava cauldron with an empty Source Bucket, then right-click the now-empty
     * cauldron; the bucket is assigned to lava and stays assigned after refilling the cauldron, its
     * "used" statistic rises by two, and the cauldron-use and cauldron-fill statistics each rise by one.
     */
    static void player_source_cauldron_round_trip_assigns_and_remains_infinite(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, CAULDRON.north(2));
        ItemStack bucket = GameTestSupport.source();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(CAULDRON, Blocks.LAVA_CAULDRON);
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        int cauldronUsesBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON));
        int cauldronFillsBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.FILL_CAULDRON));

        boolean pickedUp = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CAULDRON, Direction.UP))).consumesAction();
        boolean placed = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CAULDRON, Direction.UP))).consumesAction();

        GameTestSupport.check(pickedUp && placed, "Player Source Bucket cauldron round trip failed");
        GameTestSupport.assertFluid(bucket, Fluids.LAVA, FluidBucketItem.BUCKET_VOLUME_MB);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == itemUsesBefore + 2,
                "Source cauldron round trip did not award exactly two item-use statistics");
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON))
                        == cauldronUsesBefore + 1,
                "Source cauldron round trip did not award exactly one cauldron-use statistic");
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.FILL_CAULDRON))
                        == cauldronFillsBefore + 1,
                "Source cauldron round trip did not award exactly one cauldron-fill statistic");
        helper.succeed();
    }

    private static InteractionResult interact(GameTestHelper helper, ItemStack stack) {
        Player player = GameTestSupport.survivalPlayer(helper, CAULDRON.north(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return interact(helper, stack, player);
    }

    private static InteractionResult interact(GameTestHelper helper, ItemStack stack, Player player) {
        return helper.getBlockState(CAULDRON).useItemOn(stack, helper.getLevel(), player,
                InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CAULDRON, Direction.UP));
    }
}
