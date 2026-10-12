package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.SomeBucketItem;
import net.minecraft.world.entity.player.Player;
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
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (HeldTransfers.tryOffHandPriority(player, event.getHand(), player.getBlockReach())) {
            event.setCanceled(true);
            event.setCancellationResult(SomeBucketItem.success(player.level()));
        }
    }
}
