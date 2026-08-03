package com.github.crittscott.somebuckets.event;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.item.SBItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NBEvents {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        Level level = player.level();
        ItemStack mainHandStack = player.getMainHandItem();
        ItemStack offHandStack = player.getOffhandItem();

        // The off hand carries one of ours; the main hand carries whatever it is being poured
        // into or out of. Our own buckets drive this from their use(), so skip them here.
        if (mainHandStack.isEmpty() || offHandStack.isEmpty()) return;
        if (mainHandStack.getItem() instanceof BBItem || mainHandStack.getItem() instanceof SBItem) return;
        if (!(offHandStack.getItem() instanceof BBItem || offHandStack.getItem() instanceof SBItem)) return;

        // Only intercept when right-clicking air (like the existing BB/SB logic)
        HitResult hitResult = player.pick(player.getBlockReach(), 0.0F, false);
        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
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
