package com.github.crittscott.somebuckets.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public record ProtectionContext(@Nullable Player player, @Nullable InteractionHand hand,
                                @Nullable BlockPos automationSource) {
    public static ProtectionContext player(Player player, InteractionHand hand) {
        return new ProtectionContext(player, hand, null);
    }

    public static ProtectionContext player(Player player, ItemStack stack) {
        InteractionHand hand = player.getMainHandItem() == stack
                ? InteractionHand.MAIN_HAND
                : player.getOffhandItem() == stack ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return player(player, hand);
    }

    public static ProtectionContext dispenser(BlockPos source) {
        return new ProtectionContext(null, null, source.immutable());
    }

    public static ProtectionContext unownedAutomation() {
        return new ProtectionContext(null, null, null);
    }

    public boolean isAutomation() {
        return player == null;
    }
}
