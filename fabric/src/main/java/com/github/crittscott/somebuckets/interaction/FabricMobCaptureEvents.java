package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.protection.Protections;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Lets a held Mob Bucket capture before the target mob's own right-click reaction, such as a
 * villager opening trades or a horse being mounted, which would otherwise consume the click.
 * The listener runs in a phase after the default one, so listeners that refuse the interaction,
 * such as claim mods, run first. The automation player's events are protection checks and are
 * ignored.
 */
public final class FabricMobCaptureEvents {
    private static final ResourceLocation AFTER_DEFAULT = new ResourceLocation(SomeBuckets.MODID, "mob_capture");

    private FabricMobCaptureEvents() {}

    public static void register() {
        UseEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_DEFAULT);
        UseEntityCallback.EVENT.register(AFTER_DEFAULT, (player, level, hand, entity, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (player.isSpectator() || Protections.isAutomationActor(player)
                    || !(stack.getItem() instanceof MBItem bucket)
                    || !(entity instanceof LivingEntity target)) {
                return InteractionResult.PASS;
            }
            InteractionResult result = bucket.interactLivingEntity(stack, player, target, hand);
            return result.consumesAction() ? result : InteractionResult.PASS;
        });
    }
}
