package com.github.crittscott.somebuckets.event;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles held-container transfers where the driving bucket is in the off hand rather than the
 * main hand, so vanilla and modded items in the main hand can still be filled from or drained
 * into a Big, Huge, or Source Bucket.
 */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NBEvents {

    /**
     * Intercepts right-clicking air with a non-bucket item in the main hand and a Big, Huge, or
     * Source Bucket in the off hand, transferring between them. Own-bucket main-hand cases are
     * left to that bucket's {@code use()}.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        Level level = player.level();
        ItemStack mainHandStack = player.getMainHandItem();
        ItemStack offHandStack = player.getOffhandItem();

        // The off hand carries one of ours; the main hand carries whatever it is being poured
        // into or out of. Our own buckets drive this from their use(), so skip them here.
        if (mainHandStack.isEmpty() || offHandStack.isEmpty()) return;
        if (mainHandStack.getItem() instanceof FluidBucketItem) return;
        if (!(offHandStack.getItem() instanceof FluidBucketItem)) return;

        // Only intercept when right-clicking air (like the existing BB/SB logic)
        HitResult hitResult = player.pick(player.getBlockReach(), 1.0F, false);
        if (hitResult.getType() != HitResult.Type.MISS) {
            return;
        }

        boolean transferOccurred = Transfers.tryTransferEither(level, player,
                InteractionHand.MAIN_HAND, mainHandStack,
                InteractionHand.OFF_HAND, offHandStack);
        if (transferOccurred) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        }
    }
}
