package com.github.crittscott.somebuckets.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public interface IFluidLogic {
    boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player);
    boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player);
    boolean tryTakePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player);
    boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player);
    boolean releaseOneEntity(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player);
    boolean tryMilkDispenser(Level level, BlockPos front, ItemStack stack);
}
