package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/** Gives an off-hand Some Buckets container priority when the main hand holds another container. */
public final class FabricHeldTransferEvents {
    private FabricHeldTransferEvents() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack used = player.getItemInHand(hand);
            if (!FluidBucketItem.offHandTransferHasPriority(player, hand)) return InteractionResultHolder.pass(used);
            ItemStack bucket = player.getOffhandItem();
            if (BucketOperations.get().tryHeldTransfer(level, player,
                    InteractionHand.OFF_HAND, bucket, InteractionHand.MAIN_HAND, used)) {
                return InteractionResultHolder.sidedSuccess(player.getMainHandItem(), level.isClientSide);
            }
            return InteractionResultHolder.pass(used);
        });
    }
}
