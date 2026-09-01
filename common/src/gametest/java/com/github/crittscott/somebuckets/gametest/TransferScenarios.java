package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * Loader-neutral held-container transfer scenarios driven through the shared
 * {@link BucketOperations#tryHeldTransfer} seam. Loader GameTest trees also carry the cases that must
 * touch a loader event bus (transfer-veto priority) or a loader transaction model (multi-count
 * settlement drops).
 */
final class TransferScenarios {
    private TransferScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    static void vanilla_water_fills_empty_big_bucket(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vanilla = new ItemStack(Items.WATER_BUCKET);
        ItemStack big = GameTestSupport.big8();
        setHands(player, vanilla, big);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.OFF_HAND, big, InteractionHand.MAIN_HAND, vanilla);

        GameTestSupport.check(acted, "Vanilla water bucket did not fill Big Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Vanilla source did not become empty bucket");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    static void vanilla_milk_adds_to_compatible_big_bucket(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vanilla = new ItemStack(Items.MILK_BUCKET);
        ItemStack big = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        setHands(player, vanilla, big);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.OFF_HAND, big, InteractionHand.MAIN_HAND, vanilla);

        GameTestSupport.check(acted, "Vanilla milk bucket did not add to Big Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Milk bucket did not become empty bucket");
        GameTestSupport.assertMilk(player.getOffhandItem(), 2000);
        helper.succeed();
    }

    static void vanilla_fluid_refuses_full_big_bucket_without_mutation(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vanilla = new ItemStack(Items.WATER_BUCKET);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = big.copy();
        setHands(player, vanilla, big);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.OFF_HAND, big, InteractionHand.MAIN_HAND, vanilla);

        GameTestSupport.check(!acted, "Vanilla bucket overfilled Big Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.WATER_BUCKET), "Rejected vanilla source changed");
        GameTestSupport.assertSameStack(before, player.getOffhandItem(), "Rejected transfer mutated Big Bucket");
        helper.succeed();
    }

    static void vanilla_bucket_assigns_source_bucket(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vanilla = new ItemStack(Items.LAVA_BUCKET);
        ItemStack source = GameTestSupport.source();
        setHands(player, vanilla, source);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.OFF_HAND, source, InteractionHand.MAIN_HAND, vanilla);

        GameTestSupport.check(acted, "Vanilla lava did not assign Source Bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET), "Vanilla source did not empty");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.LAVA, 1000);
        helper.succeed();
    }

    static void big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack vanilla = new ItemStack(Items.BUCKET);
        setHands(player, big, vanilla);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, big, InteractionHand.OFF_HAND, vanilla);

        GameTestSupport.check(acted, "Big Bucket did not fill vanilla bucket");
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET), "Destination did not become water bucket");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    static void empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vanilla = new ItemStack(Items.BUCKET);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        setHands(player, vanilla, big);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.OFF_HAND, big, InteractionHand.MAIN_HAND, vanilla);

        GameTestSupport.check(acted, "Offhand Big Bucket did not fill the main-hand vanilla bucket");
        GameTestSupport.check(player.getMainHandItem().is(Items.WATER_BUCKET),
                "Main-hand destination did not become a water bucket");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    static void big_bucket_refuses_filled_vanilla_destination(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack vanilla = new ItemStack(Items.LAVA_BUCKET);
        ItemStack before = big.copy();
        setHands(player, big, vanilla);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, big, InteractionHand.OFF_HAND, vanilla);

        GameTestSupport.check(!acted, "Big Bucket filled an already-filled vanilla bucket");
        GameTestSupport.check(player.getOffhandItem().is(Items.LAVA_BUCKET), "Rejected destination changed");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Rejected transfer drained Big Bucket");
        helper.succeed();
    }

    static void big_bucket_assigns_source_and_final_unit_normalizes(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack big = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack source = GameTestSupport.source();
        setHands(player, big, source);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, big, InteractionHand.OFF_HAND, source);

        GameTestSupport.check(acted, "Big Bucket did not assign milk Source Bucket");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertMilk(player.getOffhandItem(), 1000);
        helper.succeed();
    }

    static void big_bucket_drains_into_compatible_assigned_source(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 3000);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        setHands(player, big, source);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, big, InteractionHand.OFF_HAND, source);

        GameTestSupport.check(acted, "Big Bucket did not drain into compatible Source sink");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 2000);
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.LAVA, 1000);
        helper.succeed();
    }

    static void source_bucket_fills_big_bucket_to_capacity(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 3000);
        setHands(player, source, big);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, source, InteractionHand.OFF_HAND, big);

        GameTestSupport.check(acted, "Source Bucket did not top off Big Bucket");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 8000);
        helper.succeed();
    }

    static void source_bucket_fills_vanilla_bucket_without_consumption(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack source = GameTestSupport.milk(GameTestSupport.source(), 1000);
        ItemStack vanilla = new ItemStack(Items.BUCKET);
        setHands(player, source, vanilla);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, source, InteractionHand.OFF_HAND, vanilla);

        GameTestSupport.check(acted, "Milk Source Bucket did not fill vanilla bucket");
        GameTestSupport.check(player.getOffhandItem().is(Items.MILK_BUCKET), "Destination did not become milk bucket");
        GameTestSupport.assertMilk(player.getMainHandItem(), 1000);
        helper.succeed();
    }

    static void incompatible_big_and_source_buckets_do_not_transfer(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        ItemStack bigBefore = big.copy();
        ItemStack sourceBefore = source.copy();
        setHands(player, big, source);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, big, InteractionHand.OFF_HAND, source);

        GameTestSupport.check(!acted, "Incompatible Big and Source Buckets transferred");
        GameTestSupport.assertSameStack(bigBefore, player.getMainHandItem(), "Rejected transfer mutated Big Bucket");
        GameTestSupport.assertSameStack(sourceBefore, player.getOffhandItem(), "Rejected transfer mutated Source Bucket");
        helper.succeed();
    }

    static void milk_big_bucket_refuses_incompatible_destination(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack big = GameTestSupport.milk(GameTestSupport.big8(), 8000);
        ItemStack vanilla = new ItemStack(Items.WATER_BUCKET);
        ItemStack before = big.copy();
        setHands(player, big, vanilla);

        boolean acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, big, InteractionHand.OFF_HAND, vanilla);

        GameTestSupport.check(!acted, "Milk Big Bucket transferred into a water bucket");
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET), "Rejected destination changed");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Rejected milk transfer mutated the Big Bucket");
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        return GameTestSupport.survivalPlayer(helper, TARGET);
    }

    private static void setHands(Player player, ItemStack main, ItemStack off) {
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
    }
}
