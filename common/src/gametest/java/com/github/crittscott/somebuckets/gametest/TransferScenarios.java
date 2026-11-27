package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.Protections;
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
 * through {@link GameTestSupport#useItem}, so a foreign container in the main hand reaches the
 * transfer through the loader's item-use event, and one of our buckets through its own {@code use}.
 */
final class TransferScenarios {
    private static final BlockPos PLAYER_POS = new BlockPos(4, 2, 4);

    private TransferScenarios() {}

    /**
     * In game: hold a water bucket in the main hand and an empty Big Bucket in the off hand, look at
     * the sky, and right-click; the water bucket empties and the Big Bucket holds one unit of water.
     */
    static void vanilla_water_fills_empty_big_bucket(GameTestHelper helper) {
        ItemStack vanilla = new ItemStack(Items.WATER_BUCKET);
        ItemStack big = GameTestSupport.big8();
        Player player = airClickPlayer(helper, vanilla, big);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Vanilla water bucket did not fill Big Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Vanilla source did not become empty bucket");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a milk bucket in the main hand and a Big Bucket with one unit of milk in the off
     * hand, look at the sky, and right-click; the milk bucket empties and the Big Bucket holds two
     * units of milk.
     */
    static void vanilla_milk_adds_to_compatible_big_bucket(GameTestHelper helper) {
        ItemStack vanilla = new ItemStack(Items.MILK_BUCKET);
        ItemStack big = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        Player player = airClickPlayer(helper, vanilla, big);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Vanilla milk bucket did not add to Big Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Milk bucket did not become empty bucket");
        GameTestSupport.assertMilk(player.getOffhandItem(), 2000);
        helper.succeed();
    }

    /**
     * In game: hold a water bucket in the main hand and a full Big Bucket of water in the off hand,
     * look at the sky, and right-click; nothing changes.
     */
    static void vanilla_fluid_refuses_full_big_bucket_without_mutation(GameTestHelper helper) {
        ItemStack vanilla = new ItemStack(Items.WATER_BUCKET);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = big.copy();
        Player player = airClickPlayer(helper, vanilla, big);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Refused transfer consumed the use");
        GameTestSupport.check(player.getMainHandItem().is(Items.WATER_BUCKET), "Rejected vanilla source changed");
        GameTestSupport.assertSameStack(before, player.getOffhandItem(), "Rejected transfer mutated Big Bucket");
        helper.succeed();
    }

    /**
     * In game: hold a lava bucket in the main hand and an empty Source Bucket in the off hand, look at
     * the sky, and right-click; the lava bucket empties and the Source Bucket becomes a lava Source
     * Bucket.
     */
    static void vanilla_bucket_assigns_source_bucket(GameTestHelper helper) {
        ItemStack vanilla = new ItemStack(Items.LAVA_BUCKET);
        ItemStack source = GameTestSupport.source();
        Player player = airClickPlayer(helper, vanilla, source);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Vanilla lava did not assign Source Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Vanilla source did not empty");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.LAVA, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of water in the main hand and an empty bucket in the
     * off hand, look at the sky, and right-click; the empty bucket becomes a water bucket and the Big
     * Bucket has one unit left.
     */
    static void big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(GameTestHelper helper) {
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack vanilla = new ItemStack(Items.BUCKET);
        Player player = airClickPlayer(helper, big, vanilla);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not fill vanilla bucket");
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET), "Destination did not become water bucket");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: hold an empty bucket in the main hand and a Big Bucket with two units of water in the
     * off hand, look at the sky, and right-click; the empty bucket becomes a water bucket and the Big
     * Bucket has one unit left.
     */
    static void empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(GameTestHelper helper) {
        ItemStack vanilla = new ItemStack(Items.BUCKET);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        Player player = airClickPlayer(helper, vanilla, big);

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
        ItemStack vanilla = new ItemStack(Items.LAVA_BUCKET);
        ItemStack before = big.copy();
        Player player = airClickPlayer(helper, big, vanilla);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Refused transfer consumed the use");
        GameTestSupport.check(player.getOffhandItem().is(Items.LAVA_BUCKET), "Rejected destination changed");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Rejected transfer drained Big Bucket");
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with one unit of milk in the main hand and an empty Source Bucket in
     * the off hand, look at the sky, and right-click; the Source Bucket becomes a milk Source Bucket
     * and the Big Bucket is empty.
     */
    static void big_bucket_assigns_source_and_final_unit_normalizes(GameTestHelper helper) {
        ItemStack big = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack source = GameTestSupport.source();
        Player player = airClickPlayer(helper, big, source);

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
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 3000);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        Player player = airClickPlayer(helper, big, source);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not drain into compatible Source sink");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 2000);
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.LAVA, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a water Source Bucket in the main hand and a Big Bucket with three units of water
     * in the off hand, look at the sky, and right-click; the Big Bucket fills to eight units and the
     * Source Bucket stays a water Source Bucket.
     */
    static void source_bucket_fills_big_bucket_to_capacity(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 3000);
        Player player = airClickPlayer(helper, source, big);

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
        ItemStack source = GameTestSupport.milk(GameTestSupport.source(), 1000);
        ItemStack vanilla = new ItemStack(Items.BUCKET);
        Player player = airClickPlayer(helper, source, vanilla);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Milk Source Bucket did not fill vanilla bucket");
        GameTestSupport.check(player.getOffhandItem().is(Items.MILK_BUCKET), "Destination did not become milk bucket");
        GameTestSupport.assertMilk(player.getMainHandItem(), 1000);
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
        GameTestSupport.assertSameStack(sourceBefore, player.getOffhandItem(), "Rejected transfer mutated Source Bucket");
        helper.succeed();
    }

    /**
     * In game: hold a full Big Bucket of milk in the main hand and a water bucket in the off hand, look
     * at the sky, and right-click; no milk moves, the water bucket stays, and you start drinking from
     * the Big Bucket instead.
     */
    static void milk_big_bucket_refuses_incompatible_destination(GameTestHelper helper) {
        ItemStack big = GameTestSupport.milk(GameTestSupport.big8(), 8000);
        ItemStack vanilla = new ItemStack(Items.WATER_BUCKET);
        ItemStack before = big.copy();
        Player player = airClickPlayer(helper, big, vanilla);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(player.isUsingItem(), "Refused milk transfer did not fall through to drinking");
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET), "Rejected destination changed");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Rejected milk transfer mutated the Big Bucket");
        helper.succeed();
    }

    /**
     * In game: hold an assigned water Source Bucket in the main hand and sixteen empty buckets in the
     * off hand, look at the sky, and right-click; one water bucket stays in the off hand and the other
     * fifteen drop at your feet as separate water buckets.
     */
    static void settlement_overflow_is_an_ordinary_player_drop(GameTestHelper helper) {
        Player player = airClickPlayer(helper, GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000),
                new ItemStack(Items.BUCKET, 16));

        InteractionResult result;
        boolean[] entityReleaseChecked = {false};
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action == ProtectionAction.ENTITY_RELEASE) {
                        entityReleaseChecked[0] = true;
                        return false;
                    }
                    return true;
                })) {
            result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!entityReleaseChecked[0],
                "Transfer settlement entered the internal entity-release protection layer");
        GameTestSupport.check(result.consumesAction(), "Source Bucket did not fill a bucket from the stacked destination");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET),
                "The useful transfer result did not remain in hand");

        // Drops are counted a tick later, after any entity the settlement added has joined the level.
        helper.runAfterDelay(1L, () -> {
            List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class, PLAYER_POS, 3.0D);
            GameTestSupport.check(drops.size() == 15, "Expected fifteen settlement drops, got " + drops.size());
            for (ItemEntity drop : drops) {
                GameTestSupport.check(drop.getItem().is(Items.WATER_BUCKET) && drop.getItem().getCount() == 1,
                        "Settlement did not drop fifteen individually filled buckets");
            }
            helper.succeed();
        });
    }

    /**
     * A server player (only a ServerPlayer adds the item entities its drops create to the level)
     * holding {@code main} and {@code off}, looking at the sky with no block in reach.
     */
    /**
     * In game: hold a milk Source Bucket in the main hand and a stack of 16 empty Big Buckets in the
     * off hand, look at the sky, and right-click; 15 empty Big Buckets stay in the off hand, one Big
     * Bucket full of milk appears in the inventory, and the Source Bucket keeps its milk.
     */
    static void milk_source_fills_one_of_stacked_empty_big_buckets(GameTestHelper helper) {
        ItemStack source = GameTestSupport.milk(GameTestSupport.source(), 1000);
        Player player = airClickPlayer(helper, source,
                GameTestSupport.big8().copyWithCount(GameTestSupport.STACKED_EMPTIES));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Milk Source Bucket did not fill a stacked empty Big Bucket");
        GameTestSupport.assertMilk(GameTestSupport.assertOneFilledFromStack(
                player, InteractionHand.OFF_HAND, GameTestSupport.big8().getItem()), 8000);
        GameTestSupport.assertMilk(player.getMainHandItem(), 1000);
        helper.succeed();
    }

    private static Player airClickPlayer(GameTestHelper helper, ItemStack main, ItemStack off) {
        Player player = GameTestSupport.survivalPlayerLookingAtAir(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        return player;
    }
}
