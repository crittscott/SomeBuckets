package com.github.crittscott.somebuckets.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Extension point for claim systems that authorize Some Buckets interactions before mutation.
 * Providers are composed by unanimous consent: returning {@code false} vetoes the action and
 * prevents later providers and the mod-owned mutation from running.
 */
@FunctionalInterface
public interface ClaimProtectionProvider {
    /**
     * Decides whether one exact action may proceed.
     *
     * <p>For block, fluid, and capability actions, {@code target} is the block that will be changed
     * or interacted with and {@code face} is the contacted or placement face supplied to that
     * operation. For entity interaction, {@code target} is the entity's block position and
     * {@code face} is the interaction direction chosen by the caller. For entity release,
     * {@code target} is the destination and {@code face} is the release direction.
     *
     * @param level server level in which the action applies
     * @param context acting player and hand, dispenser source, or explicit unowned automation
     * @param action kind of mutation or interaction being authorized
     * @param target exact block position associated with the action
     * @param face non-null face or direction associated with the action
     * @param stack bucket stack driving the action
     * @param targetEntity entity being interacted with or released, including an unspawned probe;
     *                     {@code null} when the action has no entity target
     * @return {@code true} to allow evaluation to continue, or {@code false} to veto the action
     */
    boolean mayAct(ServerLevel level, ProtectionContext context, ProtectionAction action,
                   BlockPos target, Direction face, ItemStack stack, @Nullable Entity targetEntity);
}
