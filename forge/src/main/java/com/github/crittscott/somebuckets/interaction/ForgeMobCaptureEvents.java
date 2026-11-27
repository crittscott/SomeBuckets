package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lets a held Mob Bucket capture before the clicked mob's own reaction. */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeMobCaptureEvents {
    private ForgeMobCaptureEvents() {}

    /**
     * Runs the capture preemption ({@link MBItem#preemptInteraction}) at lowest priority, after
     * listeners that refuse the interaction, such as claim mods, and consumes the click on capture.
     *
     * @param event the Forge entity-interaction event
     * @return whether the capture consumed the click, cancelling the event
     */
    @SubscribeEvent(priority = Priority.LOWEST)
    public static boolean onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = MBItem.preemptInteraction(event.getEntity(), event.getHand(), event.getTarget());
        if (!result.consumesAction()) return false;
        event.setCancellationResult(result);
        return true;
    }
}
