package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Objects;

/** Loader-owned fluid transactions and interaction hooks used by shared bucket items. */
public interface BucketOperations {
    final class Holder {
        private static volatile BucketOperations instance;
        private Holder() {}
    }

    static void install(BucketOperations operations) {
        synchronized (Holder.class) {
            Holder.instance = Objects.requireNonNull(operations, "operations");
        }
    }

    static BucketOperations get() {
        BucketOperations operations = Holder.instance;
        if (operations == null) throw new IllegalStateException("Bucket operations are not installed");
        return operations;
    }

    boolean tryHeldTransfer(Level level, Player player, InteractionHand bucketHand, ItemStack bucket,
                            InteractionHand otherHand, ItemStack other);

    boolean hasBlockStorage(Level level, BlockPos pos, net.minecraft.core.Direction face);

    @Nullable
    InteractionResultHolder<ItemStack> beforeWorldBucketUse(Player player, Level level, ItemStack stack,
                                                            BlockHitResult hit);

    Component fluidDisplayName(StoredFluid fluid);

    int fluidColor(StoredFluid fluid, int fallback);

    boolean canAttemptBigTake(Level level, BlockHitResult hit, ItemStack stack);

    boolean tryBigTake(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    boolean tryBigPlace(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    BlockPos resolveBigPlaceTarget(Level level, BlockHitResult hit, ItemStack stack, boolean allowFaceOffset);

    boolean canAttemptPowderTake(Level level, BlockHitResult hit, ItemStack stack);

    boolean tryPowderTake(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    boolean tryPowderPlace(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    BlockPos resolvePowderPlaceTarget(Level level, BlockHitResult hit, Player player,
                                     InteractionHand hand, boolean allowFaceOffset);

    boolean trySourceTake(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    boolean trySourcePlace(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    BlockPos resolveSourcePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                     boolean allowFaceOffset);
}
