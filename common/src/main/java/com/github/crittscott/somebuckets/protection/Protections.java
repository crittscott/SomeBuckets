package com.github.crittscott.somebuckets.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The permission checks a vanilla bucket applies before it changes the world.
 *
 * <p>The block-use packet path gates on {@link Level#mayInteract} before an item ever sees it, so
 * this exists for the operations driven from {@code Item.use}, which the server receives without a
 * target position.
 */
public final class Protections {
    private Protections() {}

    /**
     * Applies vanilla player restrictions and every registered claim provider to one exact action
     * and target. The vanilla {@link Level#mayInteract} / {@link Player#mayUseItemAt} position gate
     * is applied to every action except {@link ProtectionAction#ENTITY_INTERACT}, whose target is an
     * entity rather than a block being changed; claim providers still receive every action. Providers
     * also receive automation contexts, including the dispenser's source block.
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
}
