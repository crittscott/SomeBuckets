package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.client.SidedFluidColors;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.FluidPickup;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.FluidStack;

/** Forge adapters for the shared bucket item interaction flow. */
public final class ForgeBucketOperations implements BucketOperations {
    @Override
    public boolean tryHeldTransfer(Level level, Player player, InteractionHand bucketHand, ItemStack bucket,
                                   InteractionHand otherHand, ItemStack other) {
        return Transfers.tryTransferEither(level, player, bucketHand, bucket, otherHand, other);
    }

    @Override
    public boolean hasBlockStorage(Level level, BlockPos pos, Direction face) {
        return Transfers.hasBlockHandler(level, pos, face);
    }

    @Override
    public InteractionResultHolder<ItemStack> beforeWorldBucketUse(Player player, Level level,
                                                                   ItemStack stack, BlockHitResult hit) {
        return ForgeEventFactory.onBucketUse(player, level, stack, hit);
    }

    @Override
    public Component fluidDisplayName(StoredFluid fluid) {
        return new FluidStack(fluid.fluid(), fluid.amount(), fluid.variantTag()).getDisplayName();
    }

    @Override
    public int fluidColor(StoredFluid fluid, int fallback) {
        return SidedFluidColors.getColorRgb(
                new FluidStack(fluid.fluid(), fluid.amount(), fluid.variantTag()), fallback);
    }

    @Override
    public boolean takeAquaticSourceWater(Level level, BlockPos pos, StoredFluid expected,
                                          Player player) {
        FluidStack available = FluidPickup.available(level, pos);
        return !available.isEmpty() && available.getFluid().isSame(expected.fluid())
                && !FluidPickup.take(level, pos, available, player).isEmpty();
    }

    @Override public boolean canAttemptBigTake(Level level, BlockHitResult hit, ItemStack stack) {
        return BBFluidLogic.canAttemptTakeAt(level, hit, stack);
    }

    @Override public boolean tryBigTake(Level level, BlockHitResult hit, ItemStack stack,
                                        Player player, InteractionHand hand) {
        return BBFluidLogic.getInstance().tryTake(level, hit, stack, player, hand);
    }

    @Override public boolean tryBigPlace(Level level, BlockHitResult hit, ItemStack stack,
                                         Player player, InteractionHand hand) {
        return BBFluidLogic.getInstance().tryPlace(level, hit, stack, player, hand);
    }

    @Override public BlockPos resolveBigPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                    Player player, InteractionHand hand,
                                                    boolean allowFaceOffset) {
        return BBFluidLogic.resolvePlaceTarget(level, hit, stack, player, hand, allowFaceOffset);
    }

    @Override public boolean canAttemptPowderTake(Level level, BlockHitResult hit, ItemStack stack) {
        return BBFluidLogic.canAttemptTakePowderAt(level, hit, stack);
    }

    @Override public boolean tryPowderTake(Level level, BlockHitResult hit, ItemStack stack,
                                           Player player, InteractionHand hand) {
        return BBFluidLogic.getInstance().tryTakePowder(level, hit, stack, player, hand);
    }

    @Override public boolean tryPowderPlace(Level level, BlockHitResult hit, ItemStack stack,
                                            Player player, InteractionHand hand) {
        return BBFluidLogic.getInstance().tryPlacePowder(level, hit, stack, player, hand);
    }

    @Override public boolean trySourceTake(Level level, BlockHitResult hit, ItemStack stack,
                                           Player player, InteractionHand hand) {
        return SBFluidLogic.getInstance().tryTake(level, hit, stack, player, hand);
    }

    @Override public SourceTarget classifySourceTarget(Level level, BlockHitResult hit,
                                                        ItemStack stack) {
        return SBFluidLogic.getInstance().classifyTarget(level, hit, stack);
    }

    @Override public boolean trySourcePlace(Level level, BlockHitResult hit, ItemStack stack,
                                            Player player, InteractionHand hand) {
        return SBFluidLogic.getInstance().tryPlace(level, hit, stack, player, hand);
    }

    @Override public BlockPos resolveSourcePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                       Player player, InteractionHand hand,
                                                       boolean allowFaceOffset) {
        return SBFluidLogic.resolvePlaceTarget(level, hit, stack, player, hand, allowFaceOffset);
    }
}
