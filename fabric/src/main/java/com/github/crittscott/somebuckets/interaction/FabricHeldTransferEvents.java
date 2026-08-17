package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/** Gives an off-hand Some Buckets container priority when the main hand holds another container. */
public final class FabricHeldTransferEvents {
    private static final double CREATIVE_BLOCK_REACH = 5.0D;
    private static final double SURVIVAL_BLOCK_REACH = 4.5D;

    private FabricHeldTransferEvents() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack used = player.getItemInHand(hand);
            if (player.isSpectator() || hand != InteractionHand.MAIN_HAND || used.isEmpty()
                    || used.getItem() instanceof FluidBucketItem) {
                return InteractionResultHolder.pass(used);
            }
            ItemStack bucket = player.getOffhandItem();
            if (bucket.isEmpty() || !(bucket.getItem() instanceof FluidBucketItem)) {
                return InteractionResultHolder.pass(used);
            }
            HitResult hit = player.pick(blockReach(player), 1.0F, false);
            if (hit.getType() != HitResult.Type.MISS) return InteractionResultHolder.pass(used);

            if (BucketOperations.get().tryHeldTransfer(level, player,
                    InteractionHand.OFF_HAND, bucket, InteractionHand.MAIN_HAND, used)) {
                return InteractionResultHolder.sidedSuccess(player.getMainHandItem(), level.isClientSide);
            }
            return InteractionResultHolder.pass(used);
        });
    }

    /** Vanilla's own block-interaction range: 1.20.1 has no reach attribute to read it from directly. */
    private static double blockReach(Player player) {
        return player.isCreative() ? CREATIVE_BLOCK_REACH : SURVIVAL_BLOCK_REACH;
    }
}
