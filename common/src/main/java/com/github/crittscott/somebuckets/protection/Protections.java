package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/**
 * The permission checks applied before a bucket changes the world, expressed as the vanilla gates
 * and loader events a player doing the same thing by hand would pass.
 *
 * <p>A real player gets vanilla's block-use gates (spawn protection, the world border, and the
 * block-placement ability); automation gets only the world border. On the server, each check then
 * posts the loader event for the action through {@link BucketOperations}: block break for removal,
 * block place for placement, judged against the block actually placed through
 * {@link BucketOperations#placeChecked}, and item pickup for storage intake. Claim and protection
 * mods observe those events.
 *
 * <p>A player's use of a clicked block (tanks, cauldrons, release against a block) or of an entity
 * (capture, milking, feeding) reaches the item through vanilla dispatch, which has already posted
 * the loader's block- or entity-interaction event, so only automation posts those here. Automation
 * posts every event as the context's automation player, positioned at the dispenser and holding a
 * copy of the bucket. The client applies the vanilla gates only; the server is authoritative.
 */
public final class Protections {
    private Protections() {}

    /**
     * Applies vanilla's block-use gates to an edit at {@code pos} without posting a loader event.
     *
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
     * Applies {@link #mayModify} to using the bucket on the block at {@code pos}, or releasing a mob
     * or item into it, plus the loader's block-interaction event for automation.
     */
    public static boolean mayInteractWithBlock(Level level, ProtectionContext context, BlockPos pos,
                                               Direction face, ItemStack stack) {
        if (!mayModify(level, context, pos, face, stack)) return false;
        return !context.isAutomation() || permits(level, context, stack, (operations, server, actor, hand) ->
                operations.permitsBlockInteract(server, actor, hand,
                        new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
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
     * event. Pickup listeners may change the item entity, so the caller reads it again after a
     * {@code true} result.
     */
    public static boolean mayPickUp(Level level, ProtectionContext context, ItemEntity item, ItemStack stack) {
        if (!level.getWorldBorder().isWithinBounds(item.blockPosition())) return false;
        return permits(level, context, stack, (operations, server, actor, hand) ->
                operations.allowsItemPickup(item, actor));
    }

    /**
     * Runs {@code place}, one world placement of {@code placed} at {@code pos}, under the loader's
     * block-place check for the context's actor. The caller has already applied {@link #mayModify}
     * and debits the bucket only after a {@code true} result.
     *
     * @return {@code true} when the placement happened and stands
     */
    public static boolean placeChecked(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                       ItemStack stack, BlockState placed, BooleanSupplier place) {
        if (level.isClientSide || !context.isAutomation()) {
            return BucketOperations.get().placeChecked(level, context, pos, face, placed, place);
        }
        return holding(context, stack, () ->
                BucketOperations.get().placeChecked(level, context, pos, face, placed, place));
    }

    /** Runs one loader-event check on the server as the context's actor; the client is not checked. */
    private static boolean permits(Level level, ProtectionContext context, ItemStack stack, Check check) {
        if (!(level instanceof ServerLevel server)) return true;
        BucketOperations operations = BucketOperations.get();
        if (!context.isAutomation()) {
            return check.permits(operations, server, context.actor(), context.hand());
        }
        return holding(context, stack, () ->
                check.permits(operations, server, context.actor(), InteractionHand.MAIN_HAND));
    }

    /**
     * Whether {@code player} is the automation player dispensers act as. The loader interaction
     * events it posts are protection checks, not uses, so the mod's own interaction listeners
     * ignore them.
     */
    public static boolean isAutomationActor(Player player) {
        return player.level() instanceof ServerLevel server
                && player == BucketOperations.get().automationPlayer(server);
    }

    /* Runs action while the automation actor holds a copy of the bucket in its main hand. */
    private static boolean holding(ProtectionContext context, ItemStack stack, BooleanSupplier action) {
        Player actor = context.actor();
        ItemStack previous = actor.getMainHandItem();
        actor.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        try {
            return action.getAsBoolean();
        } finally {
            actor.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean permits(BucketOperations operations, ServerLevel level, Player actor, InteractionHand hand);
    }
}
