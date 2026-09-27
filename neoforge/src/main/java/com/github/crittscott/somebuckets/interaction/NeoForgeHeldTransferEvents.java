package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.SomeBucketItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Gives an off-hand Some Buckets container priority when the main hand holds another container. */
@EventBusSubscriber(modid = SomeBuckets.MODID)
public final class NeoForgeHeldTransferEvents {
    private NeoForgeHeldTransferEvents() {}

    /**
     * On a main-hand right-click against air with a foreign container in the main hand and a Some
     * Buckets container in the off hand, runs the held transfer and consumes the interaction.
     *
     * @param event the NeoForge right-click-item event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (HeldTransfers.tryOffHandPriority(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(SomeBucketItem.success(event.getEntity().level()));
        }
    }
}
