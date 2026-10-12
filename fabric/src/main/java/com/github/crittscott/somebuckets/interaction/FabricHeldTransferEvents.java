package com.github.crittscott.somebuckets.interaction;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;

/** Gives an off-hand Some Buckets container priority when the main hand holds another container. */
public final class FabricHeldTransferEvents {
    private static final double CREATIVE_BLOCK_REACH = 5.0D;
    private static final double SURVIVAL_BLOCK_REACH = 4.5D;

    private FabricHeldTransferEvents() {}

    /** Registers the main-hand callback that gives an off-hand fluid bucket first transfer priority. */
    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) ->
                HeldTransfers.tryOffHandPriority(player, hand, blockReach(player))
                        ? InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide)
                        : InteractionResultHolder.pass(player.getItemInHand(hand)));
    }

    /** Vanilla's own block-interaction range: 1.20.1 has no reach attribute to read it from directly. */
    private static double blockReach(Player player) {
        return player.isCreative() ? CREATIVE_BLOCK_REACH : SURVIVAL_BLOCK_REACH;
    }
}
