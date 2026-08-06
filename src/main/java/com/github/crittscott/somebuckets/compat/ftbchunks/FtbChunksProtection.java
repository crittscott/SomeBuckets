package com.github.crittscott.somebuckets.compat.ftbchunks;

import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.protection.DispenserFakePlayer;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class FtbChunksProtection {
    private FtbChunksProtection() {}

    public static void register() {
        ClaimProtections.register(FtbChunksProtection::mayAct);
    }

    private static boolean mayAct(ServerLevel level, ProtectionContext context, ProtectionAction action,
                                  BlockPos target, Direction face, ItemStack stack,
                                  @Nullable Entity targetEntity) {
        FTBChunksAPI.API api = FTBChunksAPI.api();
        if (!api.isManagerLoaded()) return true;

        ServerPlayer actor;
        InteractionHand hand;
        boolean automation;
        if (context.player() instanceof ServerPlayer serverPlayer) {
            actor = serverPlayer;
            hand = context.hand() == null ? InteractionHand.MAIN_HAND : context.hand();
            automation = false;
        } else {
            actor = DispenserFakePlayer.get(level);
            hand = InteractionHand.MAIN_HAND;
            automation = true;
            if (context.automationSource() != null) {
                Vec3 source = Vec3.atCenterOf(context.automationSource());
                actor.setPos(source.x, source.y, source.z);
            }
        }

        Protection protection = switch (action) {
            case FLUID_EDIT -> Protection.EDIT_FLUID;
            case BLOCK_EDIT -> Protection.EDIT_BLOCK;
            case BLOCK_INTERACT -> Protection.INTERACT_BLOCK;
            case ENTITY_INTERACT, ENTITY_RELEASE -> Protection.INTERACT_ENTITY;
        };
        if (!automation) {
            return !api.getManager().shouldPreventInteraction(actor, hand, target, protection, targetEntity);
        }

        ItemStack previous = actor.getItemInHand(hand);
        actor.setItemInHand(hand, stack.copy());
        try {
            return !api.getManager().shouldPreventInteraction(actor, hand, target, protection, targetEntity);
        } finally {
            actor.setItemInHand(hand, previous);
        }
    }
}
