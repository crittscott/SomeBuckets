package com.github.crittscott.somebuckets.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Identity attached to an authorized mutation.
 *
 * <p>A player context has non-null {@link #player()} and {@link #hand()} values and no automation
 * source. An automation context has neither player nor hand; a dispenser context names its source
 * block, while unowned automation has no source. Callers should use the factories rather than
 * constructing malformed combinations directly.
 *
 * @param player acting player, or {@code null} for automation
 * @param hand player's actual interaction hand, required exactly when {@code player} is non-null
 * @param automationSource dispenser or other owned automation source, or {@code null} for a player
 *                         or deliberately unowned automation
 */
public record ProtectionContext(@Nullable Player player, @Nullable InteractionHand hand,
                                @Nullable BlockPos automationSource) {
    /** Creates a context for a real player using the specified hand. */
    public static ProtectionContext player(Player player, InteractionHand hand) {
        return new ProtectionContext(Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(hand, "hand"), null);
    }

    /** Creates an automation context attributed to the dispenser at {@code source}. */
    public static ProtectionContext dispenser(BlockPos source) {
        return new ProtectionContext(null, null, source.immutable());
    }

    /** Creates an explicitly unattributed automation context. */
    public static ProtectionContext unownedAutomation() {
        return new ProtectionContext(null, null, null);
    }

    /** Returns whether this context represents automation rather than a real player. */
    public boolean isAutomation() {
        return player == null;
    }
}
