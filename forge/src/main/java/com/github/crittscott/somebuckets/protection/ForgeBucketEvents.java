package com.github.crittscott.somebuckets.protection;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;

/**
 * Runs Forge's bucket-use event and its standard result-stack settlement before a player bucket
 * operation, letting other Forge mods handle the interaction ahead of Some Buckets' own mutation.
 */
public final class ForgeBucketEvents {
    private ForgeBucketEvents() {}

    /**
     * Cancellation fails the interaction; {@code DEFAULT} and non-canceling {@code DENY} leave it to
     * the caller. An {@code ALLOW} listener supplies the processed result, which Forge settles
     * against the held stack and player inventory using the same rules as a vanilla bucket.
     */
    @Nullable
    public static InteractionResultHolder<ItemStack> onBucketUse(Player player, Level level, ItemStack stack,
                                                                 HitResult hit) {
        return ForgeEventFactory.onBucketUse(player, level, stack, hit);
    }
}
