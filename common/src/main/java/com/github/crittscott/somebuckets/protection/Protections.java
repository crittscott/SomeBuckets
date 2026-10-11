package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The permission checks applied before a bucket changes the world, expressed as the vanilla gates
 * and loader events a player doing the same thing by hand would pass.
 *
 * <p>A real player gets vanilla's block-use gates (spawn protection, the world border, and the
 * block-placement ability); automation gets only the world border. On the server, each check then
 * posts the loader event for the action through {@link BucketOperations}: block break for removal,
 * block place for placement, block interaction for cauldrons, tanks, and dispenser releases, entity
 * interaction for dispenser capture, milking, and feeding, and item pickup for storage intake.
 * Claim and protection mods observe those events. Automation posts them as the stable
 * {@link AutomationPlayers} fake player, standing at the dispenser and holding a copy of the bucket.
 *
 * <p>A player's own entity interactions reach the item through vanilla entity dispatch, which has
 * already posted the loader's entity-interaction event, so only automation posts it here. The
 * client applies the vanilla gates only; the server is authoritative.
 */
public final class Protections {
    private Protections() {}

    /**
     * Applies vanilla's block-use gates to an edit at {@code pos} without posting a loader event.
     * Callers use this directly only where the loader itself posts the matching event, such as a
     * player's native block-item placement.
     *
     * @return {@code true} when the actor may modify {@code pos}
     */
    public static boolean mayModify(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                    ItemStack stack) {
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        Player player = context.player();
        return player == null || level.mayInteract(player, pos) && player.mayUseItemAt(pos, face, stack);
    }

    /**
     * Applies {@link #mayModify} and the loader's block-break event to removing the fluid or block at
     * {@code pos}.
     */
    public static boolean mayRemove(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                    ItemStack stack) {
        return mayModify(level, context, pos, face, stack)
                && permits(level, context, stack, (operations, server, actor, hand) ->
                operations.permitsBlockBreak(server, actor, pos));
    }

    /**
     * Applies {@link #mayModify} and the loader's block-place event to placing {@code placed} at
     * {@code pos}. The event is posted before the world changes.
     */
    public static boolean mayPlace(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                   ItemStack stack, BlockState placed) {
        return mayModify(level, context, pos, face, stack)
                && permits(level, context, stack, (operations, server, actor, hand) ->
                operations.permitsBlockPlace(server, actor, pos, face, placed));
    }

    /**
     * Applies {@link #mayModify} and the loader's block-interaction event to using the bucket on the
     * cauldron or fluid storage at {@code pos}.
     */
    public static boolean mayInteractWithBlock(Level level, ProtectionContext context, BlockPos pos,
                                               Direction face, ItemStack stack) {
        return mayModify(level, context, pos, face, stack)
                && permits(level, context, stack, (operations, server, actor, hand) ->
                operations.permitsBlockInteract(server, actor, hand,
                        new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
    }

    /**
     * Authorizes releasing a stored mob or item into {@code pos}. A player's release is a use of the
     * clicked block or a throw, which vanilla dispatch has already announced, so it gets the vanilla
     * gates only; automation also posts the loader's block-interaction event at the destination.
     */
    public static boolean mayRelease(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                     ItemStack stack) {
        if (!context.isAutomation()) return mayModify(level, context, pos, face, stack);
        return mayInteractWithBlock(level, context, pos, face, stack);
    }

    /**
     * Authorizes capturing, milking, or feeding {@code target}: the world border for every actor,
     * plus the loader's entity-interaction event for automation.
     */
    public static boolean mayInteractWithEntity(Level level, ProtectionContext context, Entity target,
                                                ItemStack stack) {
        if (!level.getWorldBorder().isWithinBounds(target.blockPosition())) return false;
        return !context.isAutomation() || permits(level, context, stack, (operations, server, actor, hand) ->
                operations.permitsEntityInteract(server, actor, hand, target));
    }

    /**
     * Authorizes taking {@code item} into storage: the world border plus the loader's item-pickup
     * event. Pickup listeners may take part of the stack themselves, so the caller reads the item
     * entity again after a {@code true} result.
     */
    public static boolean mayPickUp(Level level, ProtectionContext context, ItemEntity item, ItemStack stack) {
        if (!level.getWorldBorder().isWithinBounds(item.blockPosition())) return false;
        return permits(level, context, stack, (operations, server, actor, hand) ->
                operations.permitsItemPickup(actor, item));
    }

    /** Runs one loader-event check on the server as the context's actor; the client is not checked. */
    private static boolean permits(Level level, ProtectionContext context, ItemStack stack, Check check) {
        if (!(level instanceof ServerLevel server)) return true;
        BucketOperations operations = BucketOperations.get();
        if (!context.isAutomation()) {
            return check.permits(operations, server, context.player(), context.hand());
        }

        ServerPlayer actor = AutomationPlayers.get(server);
        if (context.automationSource() != null) {
            Vec3 source = Vec3.atCenterOf(context.automationSource());
            actor.setPos(source.x, source.y, source.z);
        }
        ItemStack previous = actor.getMainHandItem();
        actor.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        try {
            return check.permits(operations, server, actor, InteractionHand.MAIN_HAND);
        } finally {
            actor.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean permits(BucketOperations operations, ServerLevel level, Player actor, InteractionHand hand);
    }
}
