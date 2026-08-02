package com.github.crittscott.somebuckets.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResultHolder;
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
     * Whether {@code player} may modify the block at {@code pos} while holding {@code stack}: spawn
     * protection, the world border, and adventure-mode placement rules. A null player is an automated
     * source such as a dispenser, which vanilla does not subject to these checks.
     */
    public static boolean mayModify(Level level, @Nullable Player player, BlockPos pos, Direction face,
                                    ItemStack stack) {
        return player == null
                || (level.mayInteract(player, pos) && player.mayUseItemAt(pos, face, stack));
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
