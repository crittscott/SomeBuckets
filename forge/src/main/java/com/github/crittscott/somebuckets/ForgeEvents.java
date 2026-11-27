package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.item.MBItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge game-bus listeners that translate interaction events into the shared item rules. Each runs
 * at the lowest priority and skips canceled events, so listeners that refuse the interaction, such
 * as claim mods, decide first.
 */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEvents {
    private ForgeEvents() {}

    /** Lets a held Mob Bucket capture before the target's own reaction ({@link MBItem#preemptInteraction}). */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = MBItem.preemptInteraction(event.getEntity(), event.getHand(), event.getTarget());
        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /**
     * On a main-hand right-click against air with a foreign container in the main hand and a Some
     * Buckets container in the off hand, runs the held transfer and consumes the interaction.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (HeldTransfers.tryOffHandPriority(player, event.getHand(), player.getBlockReach())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        }
    }
}
