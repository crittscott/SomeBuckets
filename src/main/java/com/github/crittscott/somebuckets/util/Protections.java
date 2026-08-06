package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.FillBucketEvent;

import javax.annotation.Nullable;

/**
 * The permission checks and bucket-use hook a vanilla bucket applies before it changes the world.
 *
 * <p>The block-use packet path gates on {@link Level#mayInteract} before an item ever sees it, so
 * these exist for the operations driven from {@code Item.use}, which the server receives without a
 * target position.
 */
public final class Protections {
    private Protections() {}

    /**
     * Applies vanilla player restrictions and every registered claim provider to one exact action
     * and target. Providers also receive automation contexts, including the dispenser's source block.
     */
    public static boolean mayAct(Level level, ProtectionContext context, ProtectionAction action,
                                 BlockPos pos, Direction face, ItemStack stack,
                                 @Nullable Entity targetEntity) {
        Player player = context.player();
        if (player != null && action != ProtectionAction.ENTITY_INTERACT
                && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, face, stack))) {
            return false;
        }
        return !(level instanceof ServerLevel serverLevel)
                || ClaimProtections.mayAct(serverLevel, context, action, pos, face, stack, targetEntity);
    }

    /**
     * Fires {@link FillBucketEvent} so protection and automation mods see these buckets the way they
     * see a vanilla one. Returns the result to return from {@code use} when a listener vetoed the
     * interaction, or null to let the caller proceed with its own take/place logic.
     *
     * <p>Cancellation fails the interaction outright. {@link net.minecraftforge.eventbus.api.Event.Result#ALLOW}
     * does not short-circuit:
     * a listener setting {@code ALLOW} normally substitutes {@link FillBucketEvent#getFilledBucket()}
     * for the vanilla bucket item, but these buckets hold many units in NBT and are not interchangeable
     * with a one-unit vanilla bucket, so that substitution can't be honored. {@code ALLOW} is instead
     * treated the same as the default result: permission granted, proceed with this bucket's own
     * take/place logic.
     */
    @Nullable
    public static InteractionResultHolder<ItemStack> onBucketUse(Player player, Level level, ItemStack stack,
                                                                 HitResult hit) {
        FillBucketEvent event = new FillBucketEvent(player, stack, level, hit);
        if (MinecraftForge.EVENT_BUS.post(event)) return InteractionResultHolder.fail(stack);
        return null;
    }
}
