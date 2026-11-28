package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/**
 * Held-container transfer scenarios shared by every loader. Each right-clicks air with the main hand
 * through {@link GameTestSupport#useItem}, so a foreign container in the main hand reaches the transfer
 * through the loader's item-use event, and one of our buckets through its own {@code use}. Loader
 * GameTest trees also carry the cases that must touch a loader event bus directly.
 */
final class TransferScenarios {
    private TransferScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    /**
     * In game: hold a water bucket in the main hand and an empty Big Bucket in the off hand, look at the
     * sky, and right-click; the water bucket empties and the Big Bucket holds one unit of water.
     */
    static void vanilla_water_fills_empty_big_bucket(GameTestHelper helper) {
        Player player = airClickPlayer(helper, new ItemStack(Items.WATER_BUCKET), GameTestSupport.big8());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Vanilla water bucket did not fill Big Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Vanilla source did not become empty bucket");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a milk bucket in the main hand and a Big Bucket with one unit of milk in the off hand,
     * look at the sky, and right-click; the milk bucket empties and the Big Bucket holds two units.
     */
    static void vanilla_milk_adds_to_compatible_big_bucket(GameTestHelper helper) {
        Player player = airClickPlayer(helper, new ItemStack(Items.MILK_BUCKET),
                GameTestSupport.milk(GameTestSupport.big8(), 1000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Vanilla milk bucket did not add to Big Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Milk bucket did not become empty bucket");
        GameTestSupport.assertMilk(player.getOffhandItem(), 2000);
        helper.succeed();
    }

    /**
     * In game: hold a water bucket in the main hand and a full Big Bucket of water in the off hand, look
     * at the sky, and right-click; nothing changes.
     */
    static void vanilla_fluid_refuses_full_big_bucket_without_mutation(GameTestHelper helper) {
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = big.copy();
        Player player = airClickPlayer(helper, new ItemStack(Items.WATER_BUCKET), big);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Refused transfer consumed the use");
        GameTestSupport.check(player.getMainHandItem().is(Items.WATER_BUCKET), "Rejected vanilla source changed");
        GameTestSupport.assertSameStack(before, player.getOffhandItem(), "Rejected transfer mutated Big Bucket");
        helper.succeed();
    }

    /**
     * In game: hold a lava bucket in the main hand and an empty Source Bucket in the off hand, look at the
     * sky, and right-click; the lava bucket empties and the Source Bucket becomes a lava Source Bucket.
     */
    static void vanilla_bucket_assigns_source_bucket(GameTestHelper helper) {
        Player player = airClickPlayer(helper, new ItemStack(Items.LAVA_BUCKET), GameTestSupport.source());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Vanilla lava did not assign Source Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Vanilla source did not empty");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.LAVA, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of water in the main hand and an empty bucket in the off
     * hand, look at the sky, and right-click; the empty bucket becomes a water bucket and the Big Bucket
     * has one unit left.
     */
    static void big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000),
                new ItemStack(Items.BUCKET));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not fill vanilla bucket");
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET), "Destination did not become water bucket");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: hold an empty bucket in the main hand and a Big Bucket with two units of water in the off
     * hand, look at the sky, and right-click; the empty bucket becomes a water bucket and the Big Bucket
     * has one unit left.
     */
    static void empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(GameTestHelper helper) {
        Player player = airClickPlayer(helper, new ItemStack(Items.BUCKET),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Offhand Big Bucket did not fill the main-hand vanilla bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.WATER_BUCKET),
                "Main-hand destination did not become a water bucket");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of water in the main hand and a lava bucket in the off
     * hand, look at the sky, and right-click; nothing changes.
     */
    static void big_bucket_refuses_filled_vanilla_destination(GameTestHelper helper) {
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack before = big.copy();
        Player player = airClickPlayer(helper, big, new ItemStack(Items.LAVA_BUCKET));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Refused transfer consumed the use");
        GameTestSupport.check(player.getOffhandItem().is(Items.LAVA_BUCKET), "Rejected destination changed");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Rejected transfer drained Big Bucket");
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with one unit of milk in the main hand and an empty Source Bucket in the
     * off hand, look at the sky, and right-click; the Source Bucket becomes a milk Source Bucket and the
     * Big Bucket is empty.
     */
    static void big_bucket_assigns_source_and_final_unit_normalizes(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.milk(GameTestSupport.big8(), 1000),
                GameTestSupport.source());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not assign milk Source Bucket");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertMilk(player.getOffhandItem(), 1000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with three units of lava in the main hand and a lava Source Bucket in the
     * off hand, look at the sky, and right-click; the Big Bucket drops to two units and the Source Bucket
     * stays a lava Source Bucket.
     */
    static void big_bucket_drains_into_compatible_assigned_source(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 3000),
                GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not drain into compatible Source sink");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 2000);
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.LAVA, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a water Source Bucket in the main hand and a Big Bucket with three units of water in
     * the off hand, look at the sky, and right-click; the Big Bucket fills to eight units and the Source
     * Bucket stays a water Source Bucket.
     */
    static void source_bucket_fills_big_bucket_to_capacity(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 3000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Source Bucket did not top off Big Bucket");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 8000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with five units of water in the main hand and an empty Huge Bucket in the
     * off hand, look at the sky, and right-click; all five units move. Then hold a full Huge Bucket in the
     * main hand and an empty Big Bucket in the off hand and right-click again; the Big Bucket fills to
     * capacity and the rest stays in the Huge Bucket.
     */
    static void finite_bucket_transfers_to_finite_bucket(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 5000),
                GameTestSupport.big64());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not transfer into an empty Huge Bucket");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 5000);

        setHands(player, GameTestSupport.fluid(GameTestSupport.big64(), Fluids.WATER, 64000), GameTestSupport.big8());

        result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Huge Bucket did not transfer into an empty Big Bucket");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 56000);
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 8000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with three units of water in the main hand and one with two units in the
     * off hand, look at the sky, and right-click; the main-hand bucket empties into the off-hand one. With
     * both directions legal, the used bucket gives before it takes.
     */
    static void held_transfer_tries_bucket_to_other_first(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 3000),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Main-hand Big Bucket did not transfer into the off-hand Big Bucket");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 5000);
        helper.succeed();
    }

    /**
     * In game: hold a milk Source Bucket in the main hand and an empty bucket in the off hand, look at the
     * sky, and right-click; the empty bucket becomes a milk bucket and the Source Bucket keeps its milk.
     */
    static void source_bucket_fills_vanilla_bucket_without_consumption(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.milk(GameTestSupport.source(), 1000),
                new ItemStack(Items.BUCKET));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Milk Source Bucket did not fill vanilla bucket");
        GameTestSupport.check(player.getOffhandItem().is(Items.MILK_BUCKET), "Destination did not become milk bucket");
        GameTestSupport.assertMilk(player.getMainHandItem(), 1000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of water in the main hand and a lava Source Bucket in the
     * off hand, look at the sky, and right-click; neither bucket changes.
     */
    static void incompatible_big_and_source_buckets_do_not_transfer(GameTestHelper helper) {
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        ItemStack bigBefore = big.copy();
        ItemStack sourceBefore = source.copy();
        Player player = airClickPlayer(helper, big, source);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Refused transfer consumed the use");
        GameTestSupport.assertSameStack(bigBefore, player.getMainHandItem(), "Rejected transfer mutated Big Bucket");
        GameTestSupport.assertSameStack(sourceBefore, player.getOffhandItem(), "Rejected transfer mutated Source Bucket");
        helper.succeed();
    }

    /**
     * In game: hold a full Big Bucket of milk in the main hand and a water bucket in the off hand, look at
     * the sky, and right-click; no milk moves, the water bucket stays, and you start drinking from the Big
     * Bucket instead.
     */
    static void milk_big_bucket_refuses_incompatible_destination(GameTestHelper helper) {
        ItemStack big = GameTestSupport.milk(GameTestSupport.big8(), 8000);
        ItemStack before = big.copy();
        Player player = airClickPlayer(helper, big, new ItemStack(Items.WATER_BUCKET));

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(player.isUsingItem(), "Refused milk transfer did not fall through to drinking");
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET), "Rejected destination changed");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Rejected milk transfer mutated the Big Bucket");
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of milk in the main hand and a stack of four empty Big
     * Buckets in the off hand, look at the sky, and right-click; one empty bucket fills with milk and goes
     * to the inventory, three stay empty in the off hand, and the main-hand bucket is empty.
     */
    static void milk_into_stacked_empty_big_buckets_fills_one(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.milk(GameTestSupport.big8(), 2000),
                GameTestSupport.big8().copyWithCount(4));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Milk Big Bucket did not pour into the stacked empty Big Buckets");
        GameTestSupport.check(player.getOffhandItem().getCount() == 3,
                "Expected three empty Big Buckets left in hand, got " + player.getOffhandItem());
        GameTestSupport.assertEmpty(player.getOffhandItem());
        List<ItemStack> filled = GameTestSupport.inventoryStacks(player,
                stack -> BucketState.getMode(stack) == BucketState.Mode.MILK);
        GameTestSupport.check(filled.size() == 1 && filled.get(0).getCount() == 1,
                "Expected one milk Big Bucket in the inventory, got " + filled);
        GameTestSupport.assertMilk(filled.get(0), 2000);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: with milk allowed in Source Buckets, hold a milk Source Bucket in the main hand and a stack
     * of four empty Source Buckets in the off hand, look at the sky, and right-click; one empty bucket is
     * assigned to milk and goes to the inventory, three stay empty in the off hand, and the main-hand
     * bucket keeps its milk.
     */
    static void milk_source_into_stacked_empty_source_buckets_fills_one(GameTestHelper helper) {
        Runnable restorePolicy = GameTestSupport.sourcePolicyRestorer();
        try {
            SBPolicy.replaceFromServer(SBPolicy.resolvedFluidIds(), true);
            Player player = airClickPlayer(helper, GameTestSupport.milk(GameTestSupport.source(), 1000),
                    GameTestSupport.source().copyWithCount(4));

            InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

            GameTestSupport.check(result.consumesAction(),
                    "Milk Source Bucket did not pour into the stacked empty Source Buckets");
            GameTestSupport.check(player.getOffhandItem().getCount() == 3,
                    "Expected three empty Source Buckets left in hand, got " + player.getOffhandItem());
            GameTestSupport.assertEmpty(player.getOffhandItem());
            List<ItemStack> assigned = GameTestSupport.inventoryStacks(player,
                    stack -> stack != player.getMainHandItem() && BucketState.getMode(stack) == BucketState.Mode.MILK);
            GameTestSupport.check(assigned.size() == 1 && assigned.get(0).getCount() == 1,
                    "Expected one milk Source Bucket in the inventory, got " + assigned);
            GameTestSupport.assertMilk(player.getMainHandItem(), 1000);
            helper.succeed();
        } finally {
            restorePolicy.run();
        }
    }

    /**
     * In game: with only ten free inventory slots, hold a water Source Bucket in the main hand and sixteen
     * empty buckets in the off hand, look at the sky, and right-click; one water bucket stays in the off
     * hand, ten go into the free slots, and the other five drop at your feet as separate water buckets.
     */
    static void settlement_overflow_fills_inventory_before_dropping(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000),
                new ItemStack(Items.BUCKET, 16));
        for (int slot = 11; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Source Bucket did not fill buckets from the stacked destination");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET),
                "The useful transfer result did not remain in hand");

        helper.runAfterDelay(1L, () -> {
            int stored = 0;
            for (int slot = 1; slot <= 10; slot++) {
                if (player.getInventory().getItem(slot).is(Items.WATER_BUCKET)) stored++;
            }
            GameTestSupport.check(stored == 10,
                    "Expected ten settlement results in the inventory, got " + stored);
            List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class, TARGET, 3.0D);
            GameTestSupport.check(drops.size() == 5, "Expected five settlement drops, got " + drops.size());
            for (ItemEntity drop : drops) {
                GameTestSupport.check(drop.getItem().is(Items.WATER_BUCKET) && drop.getItem().getCount() == 1,
                        "Settlement did not drop five individually filled buckets");
            }
            helper.succeed();
        });
    }

    /** A survival player at {@link #TARGET} holding {@code main} and {@code off}, looking at the sky. */
    private static Player airClickPlayer(GameTestHelper helper, ItemStack main, ItemStack off) {
        Player player = GameTestSupport.survivalPlayerLookingAtAir(helper, TARGET);
        setHands(player, main, off);
        return player;
    }

    private static void setHands(Player player, ItemStack main, ItemStack off) {
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
    }
}
