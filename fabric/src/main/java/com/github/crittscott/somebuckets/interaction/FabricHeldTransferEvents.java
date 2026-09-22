package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

/** Gives an off-hand Some Buckets container priority when the main hand holds another container. */
public final class FabricHeldTransferEvents {
    private FabricHeldTransferEvents() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack used = player.getItemInHand(hand);
            if (player.isSpectator() || hand != InteractionHand.MAIN_HAND || used.isEmpty()
                    || used.getItem() instanceof FluidBucketItem) {
                return InteractionResult.PASS;
            }
            ItemStack bucket = player.getOffhandItem();
            if (bucket.isEmpty() || !(bucket.getItem() instanceof FluidBucketItem)) {
                return InteractionResult.PASS;
            }
            HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
            if (hit.getType() != HitResult.Type.MISS) return InteractionResult.PASS;

            if (BucketOperations.get().tryHeldTransfer(level, player,
                    InteractionHand.OFF_HAND, bucket, InteractionHand.MAIN_HAND, used)) {
                return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        });
    }
}
