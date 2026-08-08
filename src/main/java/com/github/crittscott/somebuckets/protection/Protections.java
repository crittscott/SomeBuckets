package com.github.crittscott.somebuckets.protection;

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
     * Fires {@link FillBucketEvent} before a player bucket operation. Cancellation fails the
     * interaction; {@code DEFAULT} and non-canceling {@code DENY} leave it to the caller.
     *
     * <p>An {@code ALLOW} listener has handled the operation and supplies the held result. In
     * survival, that result is compatible only when it is one instance of the exact input item, so
     * the listener can update the multi-unit bucket without replacing its tier or losing the bucket.
     * Creative mode follows Forge's bucket helper and retains the original stack. An incompatible
     * survival result fails without running the caller's mutation logic.
     */
    @Nullable
    public static InteractionResultHolder<ItemStack> onBucketUse(Player player, Level level, ItemStack stack,
                                                                 HitResult hit) {
        FillBucketEvent event = new FillBucketEvent(player, stack, level, hit);
        if (MinecraftForge.EVENT_BUS.post(event)) return InteractionResultHolder.fail(stack);
        if (event.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW) {
            if (player.getAbilities().instabuild) return InteractionResultHolder.success(stack);

            ItemStack result = event.getFilledBucket();
            if (result == null || result.isEmpty() || result.getCount() != 1
                    || result.getItem() != stack.getItem()) {
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.success(result);
        }
        return null;
    }
}
