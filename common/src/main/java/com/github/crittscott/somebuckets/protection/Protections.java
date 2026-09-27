package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The permission checks vanilla applies before a bucket changes the world, applied to the context's
 * actor.
 *
 * <p>A real player gets vanilla's block-use gates: spawn protection, the world border, and the
 * block-placement ability. Automation gets only the world border, as a vanilla dispenser ignores
 * spawn protection. Entity interactions get only the world border, as vanilla's entity-interaction
 * handler does. A real player's removal or placement of a world block additionally posts the
 * loader's block-break or block-place check, which claim and protection mods observe; automation
 * posts no check of its own, as vanilla dispensers post none.
 *
 * <p>Interactions with a clicked block (fluid tanks, cauldrons) run from {@code useOn}, where
 * vanilla dispatch has already posted the loader's block-interaction event.
 */
public final class Protections {
    private Protections() {}

    /**
     * Applies vanilla's block-use gates to an edit of the world at {@code pos}: cauldron and
     * block-storage transfers, native block placement that posts its own place event, and entity or
     * item release.
     *
     * @param level level the action applies in
     * @param context acting player and hand, or dispenser
     * @param pos exact block position the action changes
     * @param face face associated with the action
     * @param stack bucket stack driving the action
     * @return {@code true} when the actor may modify {@code pos}
     */
    public static boolean mayModify(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                    ItemStack stack) {
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (context.isAutomation()) return true;
        Player player = context.actor();
        return level.mayInteract(player, pos) && player.mayUseItemAt(pos, face, stack);
    }

    /**
     * Applies {@link #mayModify} and, for a real player on the server, the loader's block-break check
     * to removing the fluid or block at {@code pos}.
     *
     * @return {@code true} when the actor may remove the block at {@code pos}
     */
    public static boolean mayRemove(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                    ItemStack stack) {
        if (!mayModify(level, context, pos, face, stack)) return false;
        return level.isClientSide || !(context.player() instanceof ServerPlayer player)
                || BucketOperations.get().permitsBlockBreak((ServerLevel) level, player, pos);
    }

    /**
     * Applies {@link #mayModify} and, for a real player on the server, the loader's block-place check
     * to placing fluid at {@code pos}.
     *
     * @param face clicked face the placement is made against
     * @return {@code true} when the actor may place at {@code pos}
     */
    public static boolean mayPlace(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                   ItemStack stack) {
        if (!mayModify(level, context, pos, face, stack)) return false;
        return level.isClientSide || !(context.player() instanceof ServerPlayer player)
                || BucketOperations.get().permitsBlockPlace((ServerLevel) level, player, pos, face);
    }

    /**
     * Applies vanilla's entity-interaction gate, the world border, to an interaction with an entity
     * at {@code pos}: milking, feeding, capture, and item intake.
     *
     * @param level level the action applies in
     * @param pos position of the target entity
     * @return {@code true} when {@code pos} is inside the world border
     */
    public static boolean mayInteract(Level level, BlockPos pos) {
        return level.getWorldBorder().isWithinBounds(pos);
    }
}
