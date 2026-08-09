package com.github.crittscott.somebuckets.protection;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.FillBucketEvent;

import javax.annotation.Nullable;

/**
 * Fires Forge's {@link FillBucketEvent} before a player bucket operation, letting other Forge mods
 * hook vanilla bucket use ahead of Some Buckets' own mutation. Fabric has no equivalent event; this
 * class has no Fabric counterpart.
 */
public final class ForgeBucketEvents {
    private ForgeBucketEvents() {}

    /**
     * Cancellation fails the interaction; {@code DEFAULT} and non-canceling {@code DENY} leave it to
     * the caller.
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
