package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.advancements.criterion.FilledBucketTrigger;
import net.minecraft.advancements.criterion.ItemPredicate;
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
     * Manual: with both Big and Huge Buckets, collect from full water, lava, and powder-snow cauldrons,
     * then use filled buckets on empty cauldrons; each tier performs every applicable interaction.
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

    /** Manual: use an empty Big Bucket on a full water cauldron; the cauldron empties and the bucket gains one unit. */
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
     * Manual: use a partly water-filled Big Bucket on a full water cauldron; it collects before
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

    /** Manual: use an empty Big Bucket on a partially filled water cauldron; neither cauldron nor bucket changes. */
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

    /** Manual: use a water-filled Big Bucket on an empty cauldron; it becomes full and the bucket spends one unit. */
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
     * Manual: move the final lava unit into an empty cauldron and collect it again; the finite bucket
     * empties and refills canonically.
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
     * Manual: move the final powder-snow unit into an empty cauldron and collect it again; bucket state
     * normalizes at each boundary.
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

    /** Manual: use a milk-filled Big Bucket on an empty cauldron; neither cauldron nor bucket changes. */
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
     * Automation-only: performs a player Big Bucket cauldron round trip and verifies vanilla statistics,
     * criteria, sounds, and game events.
     */
    static void player_big_bucket_cauldron_round_trip_has_vanilla_accounting(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, CAULDRON.north(2));
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        BlockState full = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, full);

        var filled = new GameTestSupport.CriterionProbe<>(player,
                FilledBucketTrigger.TriggerInstance.filledBucket(ItemPredicate.Builder.item()));
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
     * Automation-only: performs a player Source Bucket cauldron round trip through item use and
     * verifies assignment, infinity, and that each use is counted once.
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
