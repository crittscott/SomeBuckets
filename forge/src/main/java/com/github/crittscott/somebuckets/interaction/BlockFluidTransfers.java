package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;

/**
 * One-bucket-volume transfer between a Some Buckets item handler and a sided block fluid capability.
 *
 * <p>A present block handler owns dispatch even when it refuses the transaction, so callers fall back
 * to world-fluid handling only for {@link BlockTransferResult#NO_HANDLER}. Each mutating method
 * simulates, checks {@link ProtectionAction#BLOCK_INTERACT}, then executes on the server; the client
 * path stops after the preview.
 */
public final class BlockFluidTransfers {

    /**
     * Result of dispatching a fluid operation to a block capability. A present capability owns the
     * interaction even when it refuses, so callers may fall back to world behavior only for
     * {@link #NO_HANDLER}.
     */
    public enum BlockTransferResult {
        /** The clicked face exposes no fluid handler; world fallback is permitted. */
        NO_HANDLER,
        /** A fluid handler exists but cannot complete the requested operation. */
        REFUSED,
        /** The handler accepted the preview or completed the server transaction. */
        SUCCESS;

        /** Returns whether a block handler, rather than world fallback, owns this operation. */
        public boolean handled() {
            return this != NO_HANDLER;
        }

        /** Returns whether the block handler accepted the operation. */
        public boolean succeeded() {
            return this == SUCCESS;
        }
    }

    private BlockFluidTransfers() {}

    /**
     * Returns the mod bucket's own fluid handler, which is an invariant rather than an optional
     * dispatch signal.
     *
     * @return the stack's fluid-handler-item capability
     * @throws IllegalStateException if the stack does not expose one
     */
    public static IFluidHandlerItem requireBucketHandler(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(
                () -> new IllegalStateException("Some Buckets item is missing its fluid capability"));
    }

    /** Read-only preview of an exact one-bucket-volume block drain. */
    public static BlockTransferResult previewTakeFromBlock(Level level, BlockPos pos, Direction face,
                                                           IFluidHandlerItem bucketHandler) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BlockTransferResult.NO_HANDLER;

        FluidStack available = blockHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (!isBucketVolume(available)) return BlockTransferResult.REFUSED;

        int accepted = bucketHandler.fill(available, IFluidHandler.FluidAction.SIMULATE);
        return accepted == FluidType.BUCKET_VOLUME
                ? BlockTransferResult.SUCCESS
                : BlockTransferResult.REFUSED;
    }

    /** Classifies the contents of a present sided block handler for Source Bucket dispatch. */
    public static BucketOperations.SourceTarget classifySourceTarget(Level level, BlockPos pos,
                                                                      Direction face,
                                                                      IFluidHandlerItem bucketHandler) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BucketOperations.SourceTarget.NO_FLUID;

        FluidStack available = blockHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (isBucketVolume(available)
                && bucketHandler.fill(available, IFluidHandler.FluidAction.SIMULATE)
                == FluidType.BUCKET_VOLUME) {
            return BucketOperations.SourceTarget.MATCHING_FLUID;
        }

        FluidStack any = blockHandler.drain(1, IFluidHandler.FluidAction.SIMULATE);
        return any.isEmpty() ? BucketOperations.SourceTarget.NO_FLUID
                : BucketOperations.SourceTarget.BLOCKING_FLUID;
    }

    /**
     * Takes exactly one bucket volume from the sided block capability into the supplied BB/SB item
     * handler. A present handler owns dispatch even when it refuses the transaction.
     */
    public static BlockTransferResult tryTakeFromBlock(Level level, BlockPos pos, Direction face,
                                                       ItemStack bucketStack,
                                                       IFluidHandlerItem bucketHandler,
                                                       ProtectionContext context) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BlockTransferResult.NO_HANDLER;

        FluidStack available = blockHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (!isBucketVolume(available)) return BlockTransferResult.REFUSED;
        if (bucketHandler.fill(available, IFluidHandler.FluidAction.SIMULATE)
                != FluidType.BUCKET_VOLUME) return BlockTransferResult.REFUSED;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face,
                bucketStack, null)) return BlockTransferResult.REFUSED;

        if (!level.isClientSide) {
            FluidStack removed = blockHandler.drain(
                    ForgeFluidStacks.resized(available, FluidType.BUCKET_VOLUME),
                    IFluidHandler.FluidAction.EXECUTE);
            if (!isBucketVolume(removed) || !ForgeFluidStacks.sameFluid(removed, available)) {
                reportFluidContractViolation(level, pos, context, "block drain", blockHandler,
                        available, removed);
                return BlockTransferResult.REFUSED;
            }
            bucketHandler.fill(removed, IFluidHandler.FluidAction.EXECUTE);
            if (context.player() != null) {
                context.player().awardStat(Stats.ITEM_USED.get(bucketStack.getItem()));
            }
            level.gameEvent(context.player(), GameEvent.FLUID_PICKUP, pos);
        }

        BucketSounds.playBucketSound(level, context, pos, BucketSounds.resolveFillSound(available.getFluid()));
        return BlockTransferResult.SUCCESS;
    }

    /**
     * Places exactly one bucket volume from the supplied BB/SB item handler into the sided block
     * capability. Finite versus infinite consumption is expressed by that item handler's drain.
     */
    public static BlockTransferResult tryPlaceIntoBlock(Level level, BlockPos pos, Direction face,
                                                        ItemStack bucketStack,
                                                        IFluidHandlerItem bucketHandler,
                                                        ProtectionContext context) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BlockTransferResult.NO_HANDLER;

        FluidStack available = bucketHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (!isBucketVolume(available)) return BlockTransferResult.REFUSED;
        if (blockHandler.fill(available, IFluidHandler.FluidAction.SIMULATE)
                != FluidType.BUCKET_VOLUME) return BlockTransferResult.REFUSED;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face,
                bucketStack, null)) return BlockTransferResult.REFUSED;

        if (!level.isClientSide) {
            int accepted = blockHandler.fill(available, IFluidHandler.FluidAction.EXECUTE);
            if (accepted != FluidType.BUCKET_VOLUME) {
                reportFluidContractViolation(level, pos, context, "block fill", blockHandler,
                        FluidType.BUCKET_VOLUME, accepted);
                return BlockTransferResult.REFUSED;
            }
            bucketHandler.drain(
                    ForgeFluidStacks.resized(available, FluidType.BUCKET_VOLUME),
                    IFluidHandler.FluidAction.EXECUTE);
            if (context.player() != null) {
                context.player().awardStat(Stats.ITEM_USED.get(bucketStack.getItem()));
            }
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, pos);
        }

        BucketSounds.playBucketSound(level, context, pos, BucketSounds.resolveEmptySound(available.getFluid()));
        return BlockTransferResult.SUCCESS;
    }

    /** Whether the block at {@code pos} exposes a fluid handler on {@code face}. */
    public static boolean hasBlockHandler(Level level, BlockPos pos, Direction face) {
        return blockHandler(level, pos, face) != null;
    }

    @Nullable
    private static IFluidHandler blockHandler(Level level, BlockPos pos, Direction face) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null
                ? null
                : blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
    }

    static void reportFluidContractViolation(Level level, BlockPos pos, ProtectionContext context,
                                             String operation, Object handler,
                                             Object expected, Object actual) {
        SomeBuckets.LOGGER.error(
                "Fluid handler contract violation during {} at {} in {} (block {}, handler {}): expected {}, got {}",
                operation, pos, level.dimension().location(), level.getBlockState(pos),
                handler.getClass().getName(), expected, actual);
        if (context.player() != null) {
            context.player().displayClientMessage(
                    Component.translatable("message.somebuckets.fluid_transfer_inconsistent"), false);
        }
    }

    private static boolean isBucketVolume(FluidStack stack) {
        return !stack.isEmpty() && stack.getAmount() == FluidType.BUCKET_VOLUME;
    }
}
