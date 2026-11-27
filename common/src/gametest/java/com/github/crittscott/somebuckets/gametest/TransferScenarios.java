package com.github.crittscott.somebuckets.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * Held-container transfer scenarios shared by both loaders. Each right-clicks air with the main hand
 * through {@link GameTestSupport#useItem}, so a foreign container in the main hand reaches the
 * transfer through the loader's item-use event, and one of our buckets through its own {@code use}.
 */
final class TransferScenarios {
    private static final int FREE_INVENTORY_SLOTS = 5;
    private static final BlockPos PLAYER_POS = new BlockPos(4, 3, 4);
    private static final double SURVIVAL_BLOCK_REACH = 4.5D;

    private TransferScenarios() {}

    /**
     * In game: hold a Big Bucket of milk with one unit in the main hand and an empty Source Bucket in
     * the off hand, look at the sky, and right-click; the Source Bucket becomes a milk Source Bucket
     * and the Big Bucket is empty.
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
     * In game: hold a Big Bucket with three units of lava in the main hand and a lava Source Bucket in
     * the off hand, look at the sky, and right-click; the Big Bucket drops to two units and the Source
     * Bucket stays a lava Source Bucket.
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
     * In game: hold a Big Bucket with two units of water in the main hand and an empty bucket in the
     * off hand, look at the sky, and right-click; the empty bucket becomes a water bucket and the Big
     * Bucket has one unit left.
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
     * In game: hold a Big Bucket of water in the main hand and a lava bucket in the off hand, look at
     * the sky, and right-click; nothing changes.
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
     * In game: hold an empty bucket in the main hand and a Big Bucket with two units of water in the
     * off hand, look at the sky, and right-click; the empty bucket becomes a water bucket and the Big
     * Bucket has one unit left, instead of the empty bucket using itself.
     */
    static void empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(GameTestHelper helper) {
        Player player = airClickPlayer(helper, new ItemStack(Items.BUCKET),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Off-hand Big Bucket did not take transfer priority");
        GameTestSupport.check(player.getMainHandItem().is(Items.WATER_BUCKET),
                "Main-hand bucket was not filled from the off hand");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket of water in the main hand and a lava Source Bucket in the off hand,
     * look at the sky, and right-click; neither bucket changes.
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
        GameTestSupport.assertSameStack(sourceBefore, player.getOffhandItem(),
                "Rejected transfer mutated Source Bucket");
        helper.succeed();
    }

    /**
     * In game: hold an assigned water Source Bucket and, in the other hand, sixteen empty buckets,
     * with all but five inventory slots full, and use the Source Bucket on air. One water bucket
     * stays in hand, five go into the free slots, and the other ten drop at your feet.
     */
    static void settlement_overflow_fills_inventory_before_dropping(GameTestHelper helper) {
        // A server player: only ServerPlayer adds the item entities Player#drop creates to the level.
        Player player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        lookAtSky(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BUCKET, 16));
        List<ItemStack> slots = player.getInventory().items;
        for (int slot = slots.size() - 1, filled = 0; slot >= 0 && filled < slots.size() - 1 - FREE_INVENTORY_SLOTS;
             slot--) {
            if (slot == player.getInventory().selected) continue;
            slots.set(slot, new ItemStack(Items.DIRT));
            filled++;
        }

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Source Bucket did not fill the stacked destination");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET),
                "The useful transfer result did not remain in hand");
        int stored = 0;
        for (ItemStack stack : slots) {
            if (stack.is(Items.WATER_BUCKET)) stored += stack.getCount();
        }
        GameTestSupport.check(stored == FREE_INVENTORY_SLOTS,
                "Expected five filled buckets in the inventory, got " + stored);
        List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class, PLAYER_POS, 3.0D);
        GameTestSupport.check(drops.size() == 10, "Expected ten settlement drops, got " + drops.size());
        for (ItemEntity drop : drops) {
            GameTestSupport.check(drop.getItem().is(Items.WATER_BUCKET) && drop.getItem().getCount() == 1,
                    "Settlement dropped something other than single filled buckets");
        }
        helper.succeed();
    }

    /**
     * In game: hold a water Source Bucket in the main hand and a Big Bucket with three units of water
     * in the off hand, look at the sky, and right-click; the Big Bucket fills to eight units and the
     * Source Bucket stays a water Source Bucket.
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
     * In game: hold a milk Source Bucket in the main hand and an empty bucket in the off hand, look at
     * the sky, and right-click; the empty bucket becomes a milk bucket and the Source Bucket keeps its
     * milk.
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
     * In game: hold a lava bucket in the main hand and an empty Source Bucket in the off hand, look at
     * the sky, and right-click; the lava bucket empties and the Source Bucket becomes a lava Source
     * Bucket.
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
     * In game: hold a water bucket in the main hand and a full Big Bucket of water in the off hand,
     * look at the sky, and right-click; nothing changes.
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
     * In game: hold a milk bucket in the main hand and a Big Bucket with one unit of milk in the off
     * hand, look at the sky, and right-click; the milk bucket empties and the Big Bucket holds two
     * units of milk.
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
     * In game: hold a water bucket in the main hand and an empty Big Bucket in the off hand, look at
     * the sky, and right-click; the water bucket empties and the Big Bucket holds one unit of water.
     */
    static void vanilla_water_fills_empty_big_bucket(GameTestHelper helper) {
        Player player = airClickPlayer(helper, new ItemStack(Items.WATER_BUCKET), GameTestSupport.big8());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Vanilla water bucket did not fill Big Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Vanilla source did not become empty bucket");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /** A survival player holding {@code main} and {@code off}, looking at the sky with no block in reach. */
    private static Player airClickPlayer(GameTestHelper helper, ItemStack main, ItemStack off) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        lookAtSky(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        return player;
    }

    private static void lookAtSky(Player player) {
        player.setXRot(-90.0F);
        GameTestSupport.check(player.pick(SURVIVAL_BLOCK_REACH, 1.0F, false).getType() == HitResult.Type.MISS,
                "Air-click transfer fixture found a block in reach");
    }
}
