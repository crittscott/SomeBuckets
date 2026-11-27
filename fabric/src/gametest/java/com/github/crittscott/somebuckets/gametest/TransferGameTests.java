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
import net.minecraft.world.phys.HitResult;

public final class TransferGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(4, 3, 4);
    private static final double SURVIVAL_BLOCK_REACH = 4.5D;

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_assigns_source_and_final_unit_normalizes(GameTestHelper helper) {
        TransferScenarios.big_bucket_assigns_source_and_final_unit_normalizes(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_drains_into_compatible_assigned_source(GameTestHelper helper) {
        TransferScenarios.big_bucket_drains_into_compatible_assigned_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(GameTestHelper helper) {
        TransferScenarios.big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_refuses_filled_vanilla_destination(GameTestHelper helper) {
        TransferScenarios.big_bucket_refuses_filled_vanilla_destination(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(GameTestHelper helper) {
        TransferScenarios.empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(helper);
    }

    /**
     * No in-game form: invokes Fabric's item-use callback for a player holding an empty bucket in the
     * main hand and a Big Bucket of water in the off hand, looking down at a block within reach.
     * Guards that a targeted block leaves the use to the main-hand item: the callback passes and
     * neither bucket changes.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void foreign_main_hand_use_at_block_leaves_offhand_big_bucket_alone(GameTestHelper helper) {
        helper.setBlock(PLAYER_POS.below(), Blocks.STONE);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, PLAYER_POS);
        GameTestSupport.check(player.pick(SURVIVAL_BLOCK_REACH, 1.0F, false).getType() == HitResult.Type.BLOCK,
                "Block-click fixture found no block in reach");
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack bigBefore = big.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        player.setItemInHand(InteractionHand.OFF_HAND, big);

        InteractionResult result = UseItemCallback.EVENT.invoker()
                .interact(player, player.level(), InteractionHand.MAIN_HAND).getResult();

        GameTestSupport.check(result == InteractionResult.PASS, "Item-use callback acted with a block in reach");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET) && player.getMainHandItem().getCount() == 1,
                "Main-hand bucket changed with a block in reach");
        GameTestSupport.assertSameStack(bigBefore, player.getOffhandItem(), "Off-hand Big Bucket changed");
        helper.succeed();
    }

    /**
     * No in-game form: invokes Fabric's item-use callback for a player holding an empty bucket in the
     * main hand and a Big Bucket with two units of water in the off hand, looking at the sky. Guards
     * that the mod's callback is registered and consumes the use with the transfer: the empty bucket
     * becomes a water bucket and the Big Bucket keeps one unit. That Fabric API fires the callback
     * before the main-hand item's own use is Fabric API's contract, not checked here.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void foreign_main_hand_use_gives_offhand_big_bucket_priority(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setXRot(-90.0F);
        GameTestSupport.check(player.pick(SURVIVAL_BLOCK_REACH, 1.0F, false).getType() == HitResult.Type.MISS,
                "Air-click fixture found a block in reach");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        player.setItemInHand(InteractionHand.OFF_HAND, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = UseItemCallback.EVENT.invoker()
                .interact(player, player.level(), InteractionHand.MAIN_HAND).getResult();

        GameTestSupport.check(result.consumesAction(), "Item-use callback did not consume the transfer");
        GameTestSupport.check(player.getMainHandItem().is(Items.WATER_BUCKET),
                "Main-hand bucket was not filled from the off hand");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void incompatible_big_and_source_buckets_do_not_transfer(GameTestHelper helper) {
        TransferScenarios.incompatible_big_and_source_buckets_do_not_transfer(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void settlement_overflow_fills_inventory_before_dropping(GameTestHelper helper) {
        TransferScenarios.settlement_overflow_fills_inventory_before_dropping(helper);
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
    public void vanilla_bucket_assigns_source_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_bucket_assigns_source_bucket(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_fluid_refuses_full_big_bucket_without_mutation(GameTestHelper helper) {
        TransferScenarios.vanilla_fluid_refuses_full_big_bucket_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_milk_adds_to_compatible_big_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_milk_adds_to_compatible_big_bucket(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_water_fills_empty_big_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_water_fills_empty_big_bucket(helper);
    }
}
