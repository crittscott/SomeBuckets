package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.SomeBucketItem;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives an off-hand Some Buckets container priority when the main hand holds another container. */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeHeldTransferEvents {
    private ForgeHeldTransferEvents() {}

    /**
     * On a main-hand right-click against air with a foreign container in the main hand and a Some
     * Buckets container in the off hand, runs the held transfer and consumes the interaction.
     *
     * @param event the Forge right-click-item event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (HeldTransfers.tryOffHandPriority(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(SomeBucketItem.success(event.getEntity().level()));
        }
    }
}
