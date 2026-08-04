package com.github.crittscott.somebuckets.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

@FunctionalInterface
public interface ClaimProtectionProvider {
    boolean mayAct(ServerLevel level, ProtectionContext context, ProtectionAction action,
                   BlockPos target, Direction face, ItemStack stack, @Nullable Entity targetEntity);
}
