package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.item.MBItem;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;

/** Fabric interaction callbacks that translate player interactions into the shared item rules. */
public final class FabricEvents {
    private static final ResourceLocation AFTER_DEFAULT = new ResourceLocation(SomeBuckets.MODID, "mob_capture");
    private static final double CREATIVE_BLOCK_REACH = 5.0D;
    private static final double SURVIVAL_BLOCK_REACH = 4.5D;

    private FabricEvents() {}

    /**
     * Registers the Mob Bucket capture preemption ({@link MBItem#preemptInteraction}) in a phase after
     * the default one, so listeners that refuse the interaction, such as claim mods, run first; and
     * the main-hand use callback that gives an off-hand Some Buckets container transfer priority when
     * the main hand holds another container.
     */
    public static void register() {
        UseEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_DEFAULT);
        UseEntityCallback.EVENT.register(AFTER_DEFAULT, (player, level, hand, entity, hitResult) ->
                MBItem.preemptInteraction(player, hand, entity));

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
