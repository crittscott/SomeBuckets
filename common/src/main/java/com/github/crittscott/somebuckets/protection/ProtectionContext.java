package com.github.crittscott.somebuckets.protection;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Identity attached to an authorized mutation.
 *
 * <p>The {@link #actor()} is the entity presented to vanilla permission checks, loader events, and
 * native world operations: the real player, or the stable automation player a dispenser acts as. The
 * {@link #player()} is the real user who earns statistics and criteria and receives direct feedback;
 * it is {@code null} for every automation context, so an automation player never collects them.
 *
 * <p>A player context has a hand. A dispenser context has the automation player as its actor and no
 * hand. Callers should use the factories rather than constructing contexts directly.
 *
 * @param actor real player or automation player performing the action
 * @param hand player's actual interaction hand, present exactly for a real player
 */
public record ProtectionContext(Player actor, @Nullable InteractionHand hand) {
    /** Creates a context for a real player using the specified hand. */
    public static ProtectionContext player(Player player, InteractionHand hand) {
        return new ProtectionContext(player, hand);
    }

    /** Creates an automation context for a dispenser acting as {@code automationPlayer}. */
    public static ProtectionContext dispenser(Player automationPlayer) {
        return new ProtectionContext(automationPlayer, null);
    }

    /** Returns whether this context represents automation rather than a real player. */
    public boolean isAutomation() {
        return hand == null;
    }

    /**
     * Returns the real user eligible for statistics, criteria, and direct feedback.
     *
     * @return the acting real player, or {@code null} for automation
     */
    @Nullable
    public Player player() {
        return isAutomation() ? null : actor;
    }
}
