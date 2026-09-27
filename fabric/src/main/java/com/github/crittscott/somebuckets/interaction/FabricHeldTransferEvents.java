package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.SomeBucketItem;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;

/** Gives an off-hand Some Buckets container priority when the main hand holds another container. */
public final class FabricHeldTransferEvents {
    private FabricHeldTransferEvents() {}

    /** Registers the main-hand callback that gives an off-hand fluid bucket first transfer priority. */
    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) ->
                HeldTransfers.tryOffHandPriority(player, hand) ? SomeBucketItem.success(level) : InteractionResult.PASS);
    }
}
