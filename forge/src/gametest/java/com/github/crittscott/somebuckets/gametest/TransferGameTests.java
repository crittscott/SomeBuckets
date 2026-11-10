package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

import java.util.function.Predicate;

/**
 * Held-item transfer coverage. The loader-neutral cases live in {@link TransferScenarios}; this class
 * adds the Forge-specific paths: {@code PlayerInteractEvent} priority around a foreign main-hand
 * transfer, and the synchronous {@link HeldTransfers} settlement of multi-count overflow.
 */
public final class TransferGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private TransferGameTests() {}

    /** See {@link TransferScenarios#vanilla_water_fills_empty_big_bucket}. */
    public static void vanilla_water_fills_empty_big_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_water_fills_empty_big_bucket(helper);
    }

    /** See {@link TransferScenarios#vanilla_milk_adds_to_compatible_big_bucket}. */
    public static void vanilla_milk_adds_to_compatible_big_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_milk_adds_to_compatible_big_bucket(helper);
    }

    /** See {@link TransferScenarios#vanilla_fluid_refuses_full_big_bucket_without_mutation}. */
    public static void vanilla_fluid_refuses_full_big_bucket_without_mutation(GameTestHelper helper) {
        TransferScenarios.vanilla_fluid_refuses_full_big_bucket_without_mutation(helper);
    }

    /** See {@link TransferScenarios#vanilla_bucket_assigns_source_bucket}. */
    public static void vanilla_bucket_assigns_source_bucket(GameTestHelper helper) {
        TransferScenarios.vanilla_bucket_assigns_source_bucket(helper);
    }

    /** See {@link TransferScenarios#big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit}. */
    public static void big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(GameTestHelper helper) {
        TransferScenarios.big_bucket_fills_empty_vanilla_bucket_and_loses_one_unit(helper);
    }

    /** See {@link TransferScenarios#empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer}. */
    public static void empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(GameTestHelper helper) {
        TransferScenarios.empty_vanilla_main_hand_accepts_big_bucket_offhand_transfer(helper);
    }

    /** See {@link TransferScenarios#big_bucket_refuses_filled_vanilla_destination}. */
    public static void big_bucket_refuses_filled_vanilla_destination(GameTestHelper helper) {
        TransferScenarios.big_bucket_refuses_filled_vanilla_destination(helper);
    }

    /** See {@link TransferScenarios#big_bucket_assigns_source_and_final_unit_normalizes}. */
    public static void big_bucket_assigns_source_and_final_unit_normalizes(GameTestHelper helper) {
        TransferScenarios.big_bucket_assigns_source_and_final_unit_normalizes(helper);
    }

    /** See {@link TransferScenarios#big_bucket_drains_into_compatible_assigned_source}. */
    public static void big_bucket_drains_into_compatible_assigned_source(GameTestHelper helper) {
        TransferScenarios.big_bucket_drains_into_compatible_assigned_source(helper);
    }

    /** See {@link TransferScenarios#source_bucket_fills_big_bucket_to_capacity}. */
    public static void source_bucket_fills_big_bucket_to_capacity(GameTestHelper helper) {
        TransferScenarios.source_bucket_fills_big_bucket_to_capacity(helper);
    }

    /** See {@link TransferScenarios#finite_bucket_transfers_to_finite_bucket}. */
    public static void finite_bucket_transfers_to_finite_bucket(GameTestHelper helper) {
        TransferScenarios.finite_bucket_transfers_to_finite_bucket(helper);
    }

    /** See {@link TransferScenarios#held_transfer_tries_bucket_to_other_first}. */
    public static void held_transfer_tries_bucket_to_other_first(GameTestHelper helper) {
        TransferScenarios.held_transfer_tries_bucket_to_other_first(helper);
    }

    /** See {@link TransferScenarios#source_bucket_fills_vanilla_bucket_without_consumption}. */
    public static void source_bucket_fills_vanilla_bucket_without_consumption(GameTestHelper helper) {
        TransferScenarios.source_bucket_fills_vanilla_bucket_without_consumption(helper);
    }

    /** See {@link TransferScenarios#incompatible_big_and_source_buckets_do_not_transfer}. */
    public static void incompatible_big_and_source_buckets_do_not_transfer(GameTestHelper helper) {
        TransferScenarios.incompatible_big_and_source_buckets_do_not_transfer(helper);
    }

    /** See {@link TransferScenarios#milk_big_bucket_refuses_incompatible_destination}. */
    public static void milk_big_bucket_refuses_incompatible_destination(GameTestHelper helper) {
        TransferScenarios.milk_big_bucket_refuses_incompatible_destination(helper);
    }

    /**
     * Automation-only: registers an earlier interaction listener and verifies its veto prevents the
     * foreign-main-hand held transfer.
     */
    public static void earlier_listener_can_veto_foreign_main_hand_transfer(GameTestHelper helper) {
        Player player = airClickPlayer(helper);
        ItemStack vanilla = new ItemStack(Items.BUCKET);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack vanillaBefore = vanilla.copy();
        ItemStack bigBefore = big.copy();
        BlockState blockBefore = helper.getBlockState(TARGET);
        setHands(player, vanilla, big);

        Predicate<PlayerInteractEvent.RightClickItem> listener = event -> {
            if (event.getEntity() == player) {
                event.setCancellationResult(InteractionResult.FAIL);
                return true;
            }
            return false;
        };
        PlayerInteractEvent.RightClickItem event =
                new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        var registration = PlayerInteractEvent.RightClickItem.BUS.addListener(Priority.HIGH, listener);
        boolean cancelled;
        try {
            cancelled = PlayerInteractEvent.RightClickItem.BUS.post(event);
        } finally {
            PlayerInteractEvent.RightClickItem.BUS.removeListener(registration);
        }

        GameTestSupport.check(cancelled, "Earlier listener did not cancel the interaction");
        GameTestSupport.check(event.getCancellationResult() == InteractionResult.FAIL,
                "Later transfer handling replaced the earlier cancellation result");
        GameTestSupport.assertSameStack(vanillaBefore, player.getMainHandItem(),
                "Canceled interaction mutated the foreign main-hand bucket");
        GameTestSupport.assertSameStack(bigBefore, player.getOffhandItem(),
                "Canceled interaction mutated the offhand Big Bucket");
        GameTestSupport.check(helper.getBlockState(TARGET).equals(blockBefore),
                "Canceled interaction changed the world block");
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, TARGET, 3.0D).isEmpty(),
                "Canceled interaction dropped an item into the world");
        helper.succeed();
    }

    /**
     * Automation-only: lets interaction dispatch reach lowest priority and verifies the foreign-main-hand
     * held transfer still completes.
     */
    public static void lowest_priority_foreign_main_hand_transfer_still_succeeds(GameTestHelper helper) {
        Player player = airClickPlayer(helper);
        ItemStack vanilla = new ItemStack(Items.BUCKET);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        setHands(player, vanilla, big);

        PlayerInteractEvent.RightClickItem event =
                new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        boolean cancelled = PlayerInteractEvent.RightClickItem.BUS.post(event);

        GameTestSupport.check(cancelled, "Successful foreign-main-hand transfer did not consume the event");
        GameTestSupport.check(event.getCancellationResult().consumesAction(),
                "Successful foreign-main-hand transfer did not report a consuming result");
        GameTestSupport.check(player.getMainHandItem().is(Items.WATER_BUCKET),
                "Event-bus transfer did not fill the foreign main-hand bucket");
        GameTestSupport.assertFluid(player.getOffhandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * Manual: hold a Some Buckets container offhand and use a foreign main-hand container while looking at
     * a block; the targeted block handles the use and no held transfer occurs.
     */
    public static void offhand_held_transfer_yields_to_targeted_block(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(TARGET.north(), Blocks.AIR);
        helper.setBlock(TARGET.north(2), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.north(3), TARGET);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack bigBefore = big.copy();
        setHands(player, new ItemStack(Items.BUCKET), big);

        PlayerInteractEvent.RightClickItem event =
                new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        boolean cancelled = PlayerInteractEvent.RightClickItem.BUS.post(event);

        GameTestSupport.check(!cancelled, "Held transfer did not yield to the targeted block");
        GameTestSupport.check(player.getMainHandItem().is(Items.BUCKET),
                "Yielded interaction still emptied the foreign bucket");
        GameTestSupport.assertSameStack(bigBefore, player.getOffhandItem(),
                "Yielded interaction still drained the offhand Big Bucket");
        helper.succeed();
    }

    /**
     * Manual: transfer a stacked foreign container with ten free inventory slots; ten results go into the
     * inventory and the rest appear as ordinary item drops.
     */
    public static void settlement_overflow_fills_inventory_before_dropping(GameTestHelper helper) {
        TransferScenarios.settlement_overflow_fills_inventory_before_dropping(helper);
    }

    private static Player player(GameTestHelper helper) {
        return GameTestSupport.survivalPlayer(helper, TARGET);
    }

    private static Player airClickPlayer(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        for (int z = 3; z <= 7; z++) {
            helper.setBlock(new BlockPos(2, 3, z), Blocks.AIR);
            helper.setBlock(new BlockPos(2, 4, z), Blocks.AIR);
        }
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        GameTestSupport.check(player.pick(player.blockInteractionRange(), 1.0F, false).getType()
                        == HitResult.Type.MISS,
                "Air-click transfer fixture raytrace did not miss");
        return player;
    }

    private static void setHands(Player player, ItemStack main, ItemStack off) {
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
    }
}
