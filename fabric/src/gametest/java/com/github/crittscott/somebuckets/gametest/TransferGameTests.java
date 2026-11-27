package com.github.crittscott.somebuckets.gametest;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

/**
 * Held-item transfer coverage. The loader-neutral cases live in {@link TransferScenarios}; this class
 * adds the {@link UseItemCallback} case that yields a held transfer to a targeted block.
 */
public final class TransferGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_water_fills_empty_big_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_water_fills_empty_big_bucket(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_milk_adds_to_compatible_big_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_milk_adds_to_compatible_big_bucket(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_fluid_refuses_full_big_bucket_without_mutation(GameTestHelper helper) {
        TransferScenarios.vanilla_fluid_refuses_full_big_bucket_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_bucket_assigns_source_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_bucket_assigns_source_bucket(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(GameTestHelper helper) {
        TransferScenarios.big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(GameTestHelper helper) {
        TransferScenarios.empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_refuses_filled_vanilla_destination(GameTestHelper helper) {
        TransferScenarios.big_bucket_refuses_filled_vanilla_destination(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_assigns_source_and_final_unit_normalizes(GameTestHelper helper) {
        TransferScenarios.big_bucket_assigns_source_and_final_unit_normalizes(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_drains_into_compatible_assigned_source(GameTestHelper helper) {
        TransferScenarios.big_bucket_drains_into_compatible_assigned_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_fills_big_bucket_to_capacity(GameTestHelper helper) {
        TransferScenarios.source_bucket_fills_big_bucket_to_capacity(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_fills_vanilla_bucket_without_consumption(GameTestHelper helper) {
        TransferScenarios.source_bucket_fills_vanilla_bucket_without_consumption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void incompatible_big_and_source_buckets_do_not_transfer(GameTestHelper helper) {
        TransferScenarios.incompatible_big_and_source_buckets_do_not_transfer(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void milk_big_bucket_refuses_incompatible_destination(GameTestHelper helper) {
        TransferScenarios.milk_big_bucket_refuses_incompatible_destination(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void settlement_overflow_is_an_ordinary_player_drop(GameTestHelper helper) {
        TransferScenarios.settlement_overflow_is_an_ordinary_player_drop(helper);
    }

    /**
     * In game: hold an empty bucket in the main hand and a Big Bucket with two units of water in the
     * off hand, look at a stone block within reach, and right-click; nothing transfers. Look at the
     * sky instead and right-click; the empty bucket becomes a water bucket and the Big Bucket has one
     * unit left.
     */
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void milk_source_fills_one_of_stacked_empty_big_buckets(GameTestHelper helper) {
        TransferScenarios.milk_source_fills_one_of_stacked_empty_big_buckets(helper);
    }

    private static void setHands(Player player, ItemStack main, ItemStack off) {
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
    }
}
