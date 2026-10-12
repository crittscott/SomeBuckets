package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/** Held-container transfer scenarios shared by both loaders. */
final class TransferScenarios {
    private static final BlockPos PLAYER_POS = new BlockPos(4, 3, 4);
    private static final double SURVIVAL_BLOCK_REACH = 4.5D;
    private static final int FREE_INVENTORY_SLOTS = 5;

    private TransferScenarios() {}

    /**
     * In game: hold an assigned water Source Bucket and, in the other hand, sixteen empty buckets,
     * with all but five inventory slots full, and use the Source Bucket on air. One water bucket
     * stays in hand, five go into the free slots, and the other ten drop at your feet.
     */
    static void settlement_overflow_fills_inventory_before_dropping(GameTestHelper helper) {
        // A server player: only ServerPlayer adds the item entities Player#drop creates to the level.
        Player player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack vanilla = new ItemStack(Items.BUCKET, 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, source);
        player.setItemInHand(InteractionHand.OFF_HAND, vanilla);
        List<ItemStack> slots = player.getInventory().items;
        for (int slot = slots.size() - 1, filled = 0; slot >= 0 && filled < slots.size() - 1 - FREE_INVENTORY_SLOTS;
             slot--) {
            if (slot == player.getInventory().selected) continue;
            slots.set(slot, new ItemStack(Items.DIRT));
            filled++;
        }

        boolean acted = HeldTransfers.tryTransfer(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, source, InteractionHand.OFF_HAND, vanilla);

        GameTestSupport.check(acted, "Source Bucket did not fill the stacked destination");
        GameTestSupport.assertFluid(source, Fluids.WATER, 1000);
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
     * In game: hold an empty bucket in the main hand and a Big Bucket of water in the off hand, and
     * use the main hand on air; the Big Bucket fills the empty bucket instead of the main hand using
     * itself.
     */
    static void offhand_bucket_transfer_takes_priority_over_main_hand_use(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setXRot(-90.0F);
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        player.setItemInHand(InteractionHand.OFF_HAND, big);

        boolean acted = HeldTransfers.tryOffHandPriority(player, InteractionHand.MAIN_HAND, SURVIVAL_BLOCK_REACH);

        GameTestSupport.check(acted, "Off-hand Big Bucket did not take transfer priority");
        GameTestSupport.check(player.getMainHandItem().is(Items.WATER_BUCKET),
                "Main-hand bucket was not filled from the off hand");
        GameTestSupport.assertFluid(big, Fluids.WATER, 1000);
        helper.succeed();
    }
}
