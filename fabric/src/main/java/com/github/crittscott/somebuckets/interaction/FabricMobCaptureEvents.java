package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.Identifier;

/** Lets a held Mob Bucket capture before the clicked mob's own reaction. */
public final class FabricMobCaptureEvents {
    /** Runs after the default phase, so listeners that refuse the interaction, such as claim mods, run first. */
    private static final Identifier AFTER_DEFAULT = Identifier.fromNamespaceAndPath(SomeBuckets.MODID, "mob_capture");

    private FabricMobCaptureEvents() {}

    /** Registers the capture preemption ({@link MBItem#preemptInteraction}) in a phase after the default one. */
    public static void register() {
        UseEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_DEFAULT);
        UseEntityCallback.EVENT.register(AFTER_DEFAULT, (player, level, hand, entity, hitResult) ->
                MBItem.preemptInteraction(player, hand, entity));
    }
}
