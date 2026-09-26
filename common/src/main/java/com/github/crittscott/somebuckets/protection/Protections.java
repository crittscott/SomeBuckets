package com.github.crittscott.somebuckets.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The permission checks a vanilla bucket applies before it changes the world, applied to the
 * context's actor: the real player or a dispenser's automation player. Unowned automation has no
 * actor and is always permitted.
 *
 * <p>The block-use packet path gates on {@link Level#mayInteract} before an item ever sees it, so
 * this exists for the operations driven from {@code Item.use}, which the server receives without a
 * target position, and for automation.
 */
public final class Protections {
    private Protections() {}

    /**
     * Applies vanilla's spawn-protection, world-border, and block-placement gates to an edit of the
     * world at {@code pos}: fluid or block placement and removal, cauldron and block-storage
     * transfers, and entity or item release.
     *
     * @param level level the action applies in
     * @param context acting player and hand, dispenser, or unowned automation
     * @param pos exact block position the action changes
     * @param face face associated with the action
     * @param stack bucket stack driving the action
     * @return {@code true} when the actor may modify {@code pos}
     */
    public static boolean mayModify(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                    ItemStack stack) {
        Player actor = context.actor();
        return actor == null || (level.mayInteract(actor, pos) && actor.mayUseItemAt(pos, face, stack));
    }

    /**
     * Applies vanilla's spawn-protection and world-border gate to an interaction with an entity at
     * {@code pos}. The block-placement gate is skipped, since interacting with a mob neither places
     * nor breaks a block.
     *
     * @param level level the action applies in
     * @param context acting player and hand, dispenser, or unowned automation
     * @param pos position of the target entity
     * @return {@code true} when the actor may interact at {@code pos}
     */
    public static boolean mayInteract(Level level, ProtectionContext context, BlockPos pos) {
        Player actor = context.actor();
        return actor == null || level.mayInteract(actor, pos);
    }
}
