package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Lets a held Mob Bucket capture before the clicked mob's own reaction. */
@EventBusSubscriber(modid = SomeBuckets.MODID)
public final class NeoForgeMobCaptureEvents {
    private NeoForgeMobCaptureEvents() {}

    /**
     * Runs the capture preemption ({@link MBItem#preemptInteraction}) at lowest priority, after
     * listeners that refuse the interaction, such as claim mods, and consumes the click on capture.
     *
     * @param event the NeoForge entity-interaction event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = MBItem.preemptInteraction(event.getEntity(), event.getHand(), event.getTarget());
        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
