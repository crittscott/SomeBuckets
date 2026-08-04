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
import net.minecraftforge.eventbus.api.Event;

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
     * see a vanilla one. Returns the result to return from {@code use} when a listener claimed the
     * interaction, or null to carry on.
     *
     * <p>An allowing listener is told it handled the interaction, but the bucket is not exchanged for
     * {@link FillBucketEvent#getFilledBucket()}: these hold many units and are not interchangeable
     * with a one-unit vanilla bucket.
     */
    @Nullable
    public static InteractionResultHolder<ItemStack> onBucketUse(Player player, Level level, ItemStack stack,
                                                                 HitResult hit) {
        FillBucketEvent event = new FillBucketEvent(player, stack, level, hit);
        if (MinecraftForge.EVENT_BUS.post(event)) return InteractionResultHolder.fail(stack);
        if (event.getResult() == Event.Result.ALLOW) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return null;
    }
}
