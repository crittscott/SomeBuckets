package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lets a held Mob Bucket capture before the target mob's own right-click reaction, such as a
 * villager opening trades or a horse being mounted, which would otherwise consume the click.
 * Listeners that cancel the interaction, such as claim mods, run first. The automation player's
 * events are protection checks and are ignored.
 */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeMobCaptureEvents {
    private ForgeMobCaptureEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof MBItem bucket) || !(event.getTarget() instanceof LivingEntity target)
                || Protections.isAutomationActor(event.getEntity())) {
            return;
        }
        InteractionResult result = bucket.interactLivingEntity(stack, event.getEntity(), target, event.getHand());
        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
