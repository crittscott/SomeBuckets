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
 * adds the Fabric-specific paths: the Transfer API transaction settling multi-count overflow, and the
 * callback that yields a held transfer to a targeted block.
 */
public final class TransferGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    /** See {@link TransferScenarios#vanilla_water_fills_empty_big_bucket}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_water_fills_empty_big_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_water_fills_empty_big_bucket(helper);
    }

    /** See {@link TransferScenarios#vanilla_milk_adds_to_compatible_big_bucket}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_milk_adds_to_compatible_big_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_milk_adds_to_compatible_big_bucket(helper);
    }

    /** See {@link TransferScenarios#vanilla_fluid_refuses_full_big_bucket_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_fluid_refuses_full_big_bucket_without_mutation(GameTestHelper helper) {
        TransferScenarios.vanilla_fluid_refuses_full_big_bucket_without_mutation(helper);
    }

    /** See {@link TransferScenarios#vanilla_bucket_assigns_source_bucket}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_bucket_assigns_source_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_bucket_assigns_source_bucket(helper);
    }

    /** See {@link TransferScenarios#big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(GameTestHelper helper) {
        TransferScenarios.big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(helper);
    }

    /** See {@link TransferScenarios#empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(GameTestHelper helper) {
        TransferScenarios.empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(helper);
    }

    /** See {@link TransferScenarios#big_bucket_refuses_filled_vanilla_destination}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_refuses_filled_vanilla_destination(GameTestHelper helper) {
        TransferScenarios.big_bucket_refuses_filled_vanilla_destination(helper);
    }

    /** See {@link TransferScenarios#big_bucket_assigns_source_and_final_unit_normalizes}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_assigns_source_and_final_unit_normalizes(GameTestHelper helper) {
        TransferScenarios.big_bucket_assigns_source_and_final_unit_normalizes(helper);
    }

    /** See {@link TransferScenarios#big_bucket_drains_into_compatible_assigned_source}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_drains_into_compatible_assigned_source(GameTestHelper helper) {
        TransferScenarios.big_bucket_drains_into_compatible_assigned_source(helper);
    }

    /** See {@link TransferScenarios#source_bucket_fills_big_bucket_to_capacity}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_fills_big_bucket_to_capacity(GameTestHelper helper) {
        TransferScenarios.source_bucket_fills_big_bucket_to_capacity(helper);
    }

    /** See {@link TransferScenarios#finite_bucket_transfers_to_finite_bucket}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void finite_bucket_transfers_to_finite_bucket(GameTestHelper helper) {
        TransferScenarios.finite_bucket_transfers_to_finite_bucket(helper);
    }

    /** See {@link TransferScenarios#held_transfer_tries_bucket_to_other_first}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void held_transfer_tries_bucket_to_other_first(GameTestHelper helper) {
        TransferScenarios.held_transfer_tries_bucket_to_other_first(helper);
    }

    /** See {@link TransferScenarios#source_bucket_fills_vanilla_bucket_without_consumption}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_fills_vanilla_bucket_without_consumption(GameTestHelper helper) {
        TransferScenarios.source_bucket_fills_vanilla_bucket_without_consumption(helper);
    }

    /** See {@link TransferScenarios#incompatible_big_and_source_buckets_do_not_transfer}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void incompatible_big_and_source_buckets_do_not_transfer(GameTestHelper helper) {
        TransferScenarios.incompatible_big_and_source_buckets_do_not_transfer(helper);
    }

    /** See {@link TransferScenarios#milk_big_bucket_refuses_incompatible_destination}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void milk_big_bucket_refuses_incompatible_destination(GameTestHelper helper) {
        TransferScenarios.milk_big_bucket_refuses_incompatible_destination(helper);
    }

    /**
     * Manual: transfer a stacked foreign container with ten free inventory slots; ten results go into the
     * inventory and the rest appear as ordinary item drops.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void settlement_overflow_fills_inventory_before_dropping(GameTestHelper helper) {
        TransferScenarios.settlement_overflow_fills_inventory_before_dropping(helper);
    }

    /**
     * Manual: hold a Some Buckets container offhand and use the main hand on a fluid-capable block; the
     * targeted block handles the use before held transfer.
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
                .interact(blocked, helper.getLevel(), InteractionHand.MAIN_HAND);

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
                .interact(airPlayer, helper.getLevel(), InteractionHand.MAIN_HAND);

        GameTestSupport.check(airResult.consumesAction(),
                "Air-targeted held transfer did not consume the interaction");
        GameTestSupport.check(airPlayer.getMainHandItem().is(Items.WATER_BUCKET),
                "Air-targeted held transfer did not fill the foreign bucket");
        GameTestSupport.assertFluid(airPlayer.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    private static void setHands(Player player, ItemStack main, ItemStack off) {
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
    }
}
