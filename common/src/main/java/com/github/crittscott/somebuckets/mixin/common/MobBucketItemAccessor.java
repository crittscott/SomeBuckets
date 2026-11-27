package com.github.crittscott.somebuckets.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

/**
 * Plays a mob bucket's own empty sound, which a released Mob Bucket mob uses. The method is
 * protected, and common code has no access widener, so it is reached through this invoker on both
 * loaders.
 */
@Mixin(MobBucketItem.class)
public interface MobBucketItemAccessor {
    @Invoker("playEmptySound")
    void somebuckets$playEmptySound(@Nullable Player player, LevelAccessor level, BlockPos pos);
}
