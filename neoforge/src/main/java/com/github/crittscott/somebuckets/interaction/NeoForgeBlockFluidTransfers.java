package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.BucketOperations.BlockFluidOutcome;
import com.github.crittscott.somebuckets.platform.BucketOperations.BlockFluidResult;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-bucket-volume transfer between a Some Buckets item handler and a sided block fluid capability.
 *
 * <p>A present block handler owns dispatch even when it refuses the transaction, so callers fall back
 * to world-fluid handling only for {@link BlockFluidOutcome#NO_STORE}. Each mutating method
 * simulates, checks {@link Protections#mayModify}, then executes on the server; the client
 * path stops after the preview.
 */
public final class NeoForgeBlockFluidTransfers {
    private static final Set<Class<?>> REPORTED_CONTRACT_VIOLATIONS = ConcurrentHashMap.newKeySet();

    private NeoForgeBlockFluidTransfers() {}

    /**
     * Returns the mod bucket's own fluid handler, which is an invariant rather than an optional
     * dispatch signal.
     *
     * @return the stack's fluid-handler-item capability
     * @throws IllegalStateException if the stack does not expose one
     */
    public static IFluidHandlerItem requireBucketHandler(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            throw new IllegalStateException("Some Buckets item is missing its fluid capability");
        }
        return handler;
    }

    /** Read-only preview of an exact one-bucket-volume block drain. */
    public static BlockFluidOutcome previewTakeFromBlock(Level level, BlockPos pos, Direction face,
                                                           IFluidHandlerItem bucketHandler) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BlockFluidOutcome.NO_STORE;

        FluidStack available = blockHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (!isBucketVolume(available)) return BlockFluidOutcome.REFUSED;

        int accepted = bucketHandler.fill(available, IFluidHandler.FluidAction.SIMULATE);
        return accepted == FluidType.BUCKET_VOLUME
                ? BlockFluidOutcome.SUCCESS
                : BlockFluidOutcome.REFUSED;
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
    public static BlockFluidResult tryTakeFromBlock(Level level, BlockPos pos, Direction face,
                                                     ItemStack bucketStack,
                                                     IFluidHandlerItem bucketHandler,
                                                     ProtectionContext context) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BlockFluidResult.noStore();

        FluidStack available = blockHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (!isBucketVolume(available)) return BlockFluidResult.refused();
        if (bucketHandler.fill(available, IFluidHandler.FluidAction.SIMULATE)
                != FluidType.BUCKET_VOLUME) return BlockFluidResult.refused();
        if (!Protections.mayModify(level, context, pos, face, bucketStack)) return BlockFluidResult.refused();

        if (!level.isClientSide) {
            FluidStack removed = blockHandler.drain(
                    available.copyWithAmount(FluidType.BUCKET_VOLUME),
                    IFluidHandler.FluidAction.EXECUTE);
            if (!isBucketVolume(removed) || !FluidStack.isSameFluidSameComponents(removed, available)) {
                reportFluidContractViolation(level, pos, context, "block drain", blockHandler,
                        available, removed);
                return BlockFluidResult.refused();
            }
            bucketHandler.fill(removed, IFluidHandler.FluidAction.EXECUTE);
        }
        return BlockFluidResult.success(NeoForgeFluidStacks.stored(available));
    }

    /**
     * Places exactly one bucket volume from the supplied BB/SB item handler into the sided block
     * capability. Finite versus infinite consumption is expressed by that item handler's drain.
     */
    public static BlockFluidResult tryPlaceIntoBlock(Level level, BlockPos pos, Direction face,
                                                      ItemStack bucketStack,
                                                      IFluidHandlerItem bucketHandler,
                                                      ProtectionContext context) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BlockFluidResult.noStore();

        FluidStack available = bucketHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (!isBucketVolume(available)) return BlockFluidResult.refused();
        if (blockHandler.fill(available, IFluidHandler.FluidAction.SIMULATE)
                != FluidType.BUCKET_VOLUME) return BlockFluidResult.refused();
        if (!Protections.mayModify(level, context, pos, face, bucketStack)) return BlockFluidResult.refused();

        if (!level.isClientSide) {
            int accepted = blockHandler.fill(available, IFluidHandler.FluidAction.EXECUTE);
            if (accepted != FluidType.BUCKET_VOLUME) {
                reportFluidContractViolation(level, pos, context, "block fill", blockHandler,
                        FluidType.BUCKET_VOLUME, accepted);
                return BlockFluidResult.refused();
            }
            bucketHandler.drain(
                    available.copyWithAmount(FluidType.BUCKET_VOLUME),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        return BlockFluidResult.success(NeoForgeFluidStacks.stored(available));
    }

    /** Whether the block at {@code pos} exposes a fluid handler on {@code face}. */
    public static boolean hasBlockHandler(Level level, BlockPos pos, Direction face) {
        return blockHandler(level, pos, face) != null;
    }

    @Nullable
    private static IFluidHandler blockHandler(Level level, BlockPos pos, Direction face) {
        // NeoForge exposes a fluid handler for vanilla cauldrons, but Some Buckets routes vanilla
        // cauldron interactions through the dedicated Cauldrons path so they award the cauldron
        // statistics and emit the cauldron game events, matching Forge, which has no such
        // capability. Modded cauldron blocks keep their capability.
        BlockState state = level.getBlockState(pos);
        if (Cauldrons.isVanillaCauldron(state)) return null;
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
    }

    /**
     * Reports a fluid handler that contradicted its simulation. Logs only the first violation from
     * each handler class, while every occurrence notifies the real player when one initiated the move.
     *
     * @param level level containing the handler
     * @param pos handler block position
     * @param context authorization identity for the move
     * @param operation operation that violated its simulated result
     * @param handler offending fluid handler
     * @param expected simulated result
     * @param actual executed result
     */
    static void reportFluidContractViolation(Level level, BlockPos pos, ProtectionContext context,
                                             String operation, Object handler,
                                             Object expected, Object actual) {
        if (REPORTED_CONTRACT_VIOLATIONS.add(handler.getClass())) {
            SomeBuckets.LOGGER.error(
                    "Fluid handler contract violation during {} at {} in {} (block {}, handler {}): expected {}, got {}; further violations from this handler class will not be logged",
                    operation, pos, level.dimension().location(), level.getBlockState(pos),
                    handler.getClass().getName(), expected, actual);
        }
        if (context.player() != null) {
            context.player().displayClientMessage(
                    Component.translatable(BucketOperations.FLUID_TRANSFER_INCONSISTENT_KEY), false);
        }
    }

    private static boolean isBucketVolume(FluidStack stack) {
        return !stack.isEmpty() && stack.getAmount() == FluidType.BUCKET_VOLUME;
    }
}
