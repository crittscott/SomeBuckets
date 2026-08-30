package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/** Exercises bidirectional held-item transfers through the Fabric Transfer API implementation. */
public final class TransferGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_water_fills_empty_big_bucket(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_milk_adds_to_compatible_big_bucket(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_fluid_refuses_full_big_bucket_without_mutation(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_bucket_assigns_source_bucket(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_refuses_filled_vanilla_destination(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_assigns_source_and_final_unit_normalizes(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_drains_into_compatible_assigned_source(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_fills_big_bucket_to_capacity(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_fills_vanilla_bucket_without_consumption(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void settlement_overflow_is_an_ordinary_player_drop(GameTestHelper helper) {
        Player player = GameTestSupport.serverPlayer(helper, TARGET);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack vanilla = new ItemStack(Items.BUCKET, 16);
        setHands(player, source, vanilla);

        boolean acted;
        boolean[] entityReleaseChecked = {false};
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action == ProtectionAction.ENTITY_RELEASE) {
                        entityReleaseChecked[0] = true;
                        return false;
                    }
                    return true;
                })) {
            acted = BucketOperations.get().tryHeldTransfer(helper.getLevel(), player,
                    InteractionHand.MAIN_HAND, source, InteractionHand.OFF_HAND, vanilla);
        }

        GameTestSupport.check(!entityReleaseChecked[0],
                "Transfer settlement entered the internal entity-release protection layer");
        GameTestSupport.check(acted, "Source Bucket did not fill a bucket from the stacked destination");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET),
                "The useful transfer result did not remain in hand");

        helper.runAfterDelay(1L, () -> {
            List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class,
                    new BlockPos(4, 2, 4), 3.0D);
            GameTestSupport.check(drops.size() == 15, "Expected fifteen settlement drops, got " + drops.size());
            for (ItemEntity drop : drops) {
                GameTestSupport.check(drop.getItem().is(Items.WATER_BUCKET) && drop.getItem().getCount() == 1,
                        "Settlement did not drop fifteen individually filled buckets");
            }
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void incompatible_big_and_source_buckets_do_not_transfer(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void milk_big_bucket_refuses_incompatible_destination(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void offhand_held_transfer_yields_to_targeted_block(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(TARGET.north(), Blocks.AIR);
        helper.setBlock(TARGET.north(2), Blocks.AIR);
        Player blocked = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.north(3), TARGET);
        ItemStack blockedOff = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack blockedOffBefore = blockedOff.copy();
        setHands(blocked, new ItemStack(Items.BUCKET), blockedOff);

        InteractionResult blockedResult = UseItemCallback.EVENT.invoker()
                .interact(blocked, helper.getLevel(), InteractionHand.MAIN_HAND).getResult();

        GameTestSupport.check(blockedResult == InteractionResult.PASS,
                "Held transfer did not yield to the targeted block");
        GameTestSupport.check(blocked.getMainHandItem().is(Items.BUCKET),
                "Yielded interaction still emptied the foreign bucket");
        GameTestSupport.assertSameStack(blockedOffBefore, blocked.getOffhandItem(),
                "Yielded interaction still drained the offhand Big Bucket");

        Player airPlayer = GameTestSupport.survivalPlayerLookingAtAir(helper, new BlockPos(2, 3, 4));
        setHands(airPlayer, new ItemStack(Items.BUCKET),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult airResult = UseItemCallback.EVENT.invoker()
                .interact(airPlayer, helper.getLevel(), InteractionHand.MAIN_HAND).getResult();

        GameTestSupport.check(airResult.consumesAction(),
                "Air-targeted held transfer did not consume the interaction");
        GameTestSupport.check(airPlayer.getMainHandItem().is(Items.WATER_BUCKET),
                "Air-targeted held transfer did not fill the foreign bucket");
        GameTestSupport.assertFluid(airPlayer.getOffhandItem(), Fluids.WATER, 1000);
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
