package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
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

/** Gives an off-hand Some Buckets container priority when the main hand holds another container. */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeHeldTransferEvents {
    private ForgeHeldTransferEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        Level level = player.level();
        ItemStack mainHandStack = player.getMainHandItem();
        ItemStack offHandStack = player.getOffhandItem();
        if (mainHandStack.isEmpty() || offHandStack.isEmpty()) return;
        if (mainHandStack.getItem() instanceof FluidBucketItem) return;
        if (!(offHandStack.getItem() instanceof FluidBucketItem)) return;

        HitResult hitResult = player.pick(player.getBlockReach(), 1.0F, false);
        if (hitResult.getType() != HitResult.Type.MISS) return;

        if (Transfers.tryTransferEither(level, player,
                InteractionHand.MAIN_HAND, mainHandStack,
                InteractionHand.OFF_HAND, offHandStack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        }
    }
}
