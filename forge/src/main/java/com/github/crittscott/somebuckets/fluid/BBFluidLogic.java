package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates finite Big and Huge Bucket world transactions after {@link BBItem} selects a player
 * gesture. Capability transfer, world pickup, world placement, and native powder-snow placement
 * remain owned by their shared primitives; this class applies finite-mode admission, protection,
 * bucket debit or credit, and player observability around those operations.
 */
public class BBFluidLogic {
    /** Mirrors vanilla {@code Level#setBlock}'s own internal update-recursion default. */
    private static final int MAX_UPDATE_RECURSION = 512;

    private static final BBFluidLogic INSTANCE = new BBFluidLogic();

    private BBFluidLogic() {}

    public static BBFluidLogic getInstance() {
        return INSTANCE;
    }

    /**
     * Tries to take one fluid unit for a real player using {@code hand}.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction;
     *         {@code false} leaves the world and bucket unchanged
     */
    public boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, Player player,
                           InteractionHand hand) {
        return tryTakeWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Whether {@link #tryTakeWithContext} would attempt a take at {@code hit}: a compatible block
     * capability transfer, or a world pickup compatible with the stack's current mode and capacity.
     * Read-only: does not check protection or touch the world. Used to pick the correct
     * {@code FillBucketEvent} target before dispatch; the real attempt re-derives this independently.
     */
    public static boolean canAttemptTakeAt(Level level, BlockHitResult hit, ItemStack stack) {
        BlockPos pos = hit.getBlockPos();
        IFluidHandlerItem itemHandler = Transfers.requireBucketHandler(stack);
        Transfers.BlockTransferResult blockPreview = Transfers.previewTakeFromBlock(
                level, pos, hit.getDirection(), itemHandler);
        if (blockPreview.handled()) {
            return blockPreview.succeeded();
        }

        FluidStack available = FluidPickup.available(level, pos);
        if (available.isEmpty()) return false;

        int capMb = ((BBItem) stack.getItem()).getCapacityMb();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        FluidStack current = ForgeFluidStacks.get(stack);
        return mode == NBTUtil.Mode.NONE ||
                (mode == NBTUtil.Mode.FLUID && (current.isEmpty() ||
                        (current.isFluidEqual(available) && current.getAmount() + FluidType.BUCKET_VOLUME <= capMb)));
    }

    /**
     * The position {@link #tryPlace} would actually act on: the clicked block if it exposes a
     * compatible fluid-handler capability, otherwise wherever {@link FluidPlacement#resolveTarget}
     * resolves for generic world placement (which may fall through to the neighbor along the clicked
     * face). Read-only: does not check protection or touch the world. Used to pick the correct
     * {@code FillBucketEvent} target before dispatch.
     */
    public static BlockPos resolvePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                              boolean allowFaceOffset) {
        Transfers.requireBucketHandler(stack);
        BlockPos clickedPos = hit.getBlockPos();
        if (Transfers.hasBlockHandler(level, clickedPos, hit.getDirection())) return clickedPos;
        FluidStack fluidStack = ForgeFluidStacks.get(stack);
        return FluidPlacement.resolveTarget(level, clickedPos, hit.getDirection(), allowFaceOffset,
                fluidStack.getFluid());
    }

    /**
     * Tries to take one fluid unit using an explicit player or automation context.
     *
     * <p>A sided block capability has priority and owns dispatch even when it refuses. Otherwise
     * the world block's bucket-pickup contract is used. The exact target is protected before
     * mutation. The server credits the finite bucket and emits the matching sound and fluid game
     * event; player world pickup also awards the item-use statistic and filled-bucket criterion,
     * while a capability pickup awards only the statistic. The client performs prediction without
     * changing either state.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction;
     *         {@code false} leaves the world and bucket unchanged
     */
    public boolean tryTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                      ProtectionContext context) {
        IFluidHandlerItem itemHandler = Transfers.requireBucketHandler(stack);
        BlockPos pos = hit.getBlockPos();

        Transfers.BlockTransferResult blockTransfer = Transfers.tryTakeFromBlock(
                level, pos, hit.getDirection(), stack, itemHandler, context);
        if (blockTransfer.handled()) {
            return blockTransfer.succeeded();
        }

        // Fall back to the world block's own pickup contract
        FluidStack available = FluidPickup.available(level, pos);
        if (available.isEmpty()) return false;

        int capMb = ((BBItem) stack.getItem()).getCapacityMb();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        FluidStack current = ForgeFluidStacks.get(stack);

        boolean canTake = mode == NBTUtil.Mode.NONE ||
                (mode == NBTUtil.Mode.FLUID && (current.isEmpty() ||
                        (current.isFluidEqual(available) && current.getAmount() + FluidType.BUCKET_VOLUME <= capMb)));
        if (!canTake) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        FluidStack taken = FluidPickup.take(level, pos, available, context.player());
        if (taken.isEmpty()) return false;

        if (!level.isClientSide) {
            boolean merging = mode == NBTUtil.Mode.FLUID && !current.isEmpty();
            ForgeFluidStacks.set(stack, merging
                    ? new FluidStack(current.getFluid(), current.getAmount() + FluidType.BUCKET_VOLUME, current.getTag())
                    : new FluidStack(taken.getFluid(), FluidType.BUCKET_VOLUME, taken.getTag()));
            FluidPickup.completePlayerPickup(level, context.player(), stack);
        }
        return true;
    }

    /**
     * Tries to place one fluid unit for a real player, allowing vanilla face-offset target
     * selection.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction;
     *         {@code false} leaves the world and bucket unchanged
     */
    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, Player player,
                            InteractionHand hand) {
        return tryPlace(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Tries to place one fluid unit using an explicit player or automation context.
     *
     * <p>A sided block capability has priority. Otherwise placement uses vanilla-style world
     * rules; {@code allowFaceOffset} permits a blocked clicked position to resolve to its neighbor
     * along the hit face but does not bypass placement validity. Protection is checked at the
     * actual target. A server success debits one finite unit, emits sound and a fluid-place game
     * event, and awards the item-use statistic to a player. The client predicts without debit or
     * world mutation.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction;
     *         {@code false} leaves the world and bucket unchanged
     */
    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                            boolean allowFaceOffset) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.FLUID) return false;
        IFluidHandlerItem itemHandler = Transfers.requireBucketHandler(stack);

        FluidStack fluidStack = ForgeFluidStacks.get(stack);
        if (fluidStack.isEmpty() || fluidStack.getAmount() < FluidType.BUCKET_VOLUME) return false;

        BlockPos clickedPos = hit.getBlockPos();

        Transfers.BlockTransferResult blockTransfer = Transfers.tryPlaceIntoBlock(
                level, clickedPos, hit.getDirection(), stack, itemHandler, context);
        if (blockTransfer.handled()) {
            return blockTransfer.succeeded();
        }

        // Fall back to world placement
        return tryPlaceInWorld(level, hit, stack, context, fluidStack, allowFaceOffset);
    }

    private boolean tryPlaceInWorld(Level level, BlockHitResult hit, ItemStack stack,
                                    ProtectionContext context, FluidStack fluidStack,
                                    boolean allowFaceOffset) {
        if (!FluidPlacement.emptyContents(level, context, stack, hit.getBlockPos(), hit,
                fluidStack.getFluid(), allowFaceOffset)) return false;

        if (!level.isClientSide) {
            NBTUtil.drainFiniteContent(stack, FluidType.BUCKET_VOLUME);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    /**
     * Tries to collect one powder-snow block for a real player.
     *
     * @return {@code true} for an accepted client prediction or a completed server pickup;
     *         {@code false} leaves the block and bucket unchanged
     */
    public boolean tryTakePowder(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                 InteractionHand hand) {
        return tryTakePowderWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Whether {@link #tryTakePowderWithContext} would attempt a take at {@code hit}: the block is
     * powder snow and the stack's mode/capacity accepts another unit. Read-only: does not check
     * protection or touch the world. Used to pick the correct {@code FillBucketEvent} target before
     * dispatch; the real attempt re-derives this independently.
     */
    public static boolean canAttemptTakePowderAt(Level level, BlockHitResult hit, ItemStack stack) {
        if (!level.getBlockState(hit.getBlockPos()).is(Blocks.POWDER_SNOW)) return false;
        int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int units = NBTUtil.getPowderUnits(stack);
        return mode == NBTUtil.Mode.NONE || (mode == NBTUtil.Mode.POWDER_SNOW && units < capUnits);
    }

    /**
     * Tries to collect one powder-snow block with explicit authorization identity.
     *
     * <p>The method checks capacity and block-edit protection before the server stores one unit and
     * removes the block. Success emits pickup sound and a fluid-pickup game event; a player also
     * receives the item-use statistic and filled-bucket criterion. Client calls predict without
     * changing the block or bucket.
     *
     * @return {@code true} for an accepted client prediction or a completed server pickup;
     *         {@code false} leaves the block and bucket unchanged
     */
    public boolean tryTakePowderWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        if (!canAttemptTakePowderAt(level, hit, stack)) return false;

        BlockPos pos = hit.getBlockPos();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int units = NBTUtil.getPowderUnits(stack);
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, (mode == NBTUtil.Mode.POWDER_SNOW ? units : 0) + 1);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            FluidPickup.completePlayerPickup(level, context.player(), stack);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_FILL_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(context.player(), GameEvent.FLUID_PICKUP, pos);
        return true;
    }

    /**
     * Tries native powder-snow placement for a real player, allowing face-offset target selection.
     *
     * @return {@code true} for an accepted client prediction or a committed server placement;
     *         {@code false} leaves the block and bucket unchanged
     */
    public boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                  InteractionHand hand) {
        return tryPlacePowder(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /** The target selected by the same native block-place context used for powder output. */
    public static BlockPos resolvePowderPlaceTarget(Level level, BlockHitResult hit, Player player,
                                                    InteractionHand hand, boolean allowFaceOffset) {
        BlockPlaceContext placement = powderPlacementContext(level, player, hand, hit);
        return !allowFaceOffset && !placement.replacingClickedOnBlock()
                ? hit.getBlockPos() : placement.getClickedPos();
    }

    /**
     * Tries native powder-snow placement with explicit authorization identity.
     *
     * <p>{@code allowFaceOffset} controls whether the native placement context may select the
     * adjacent block; it does not relax native placement checks. The exact destination is protected
     * before Forge's place-event transaction. A server success commits the block first, then debits
     * one stored unit and awards player accounting; cancellation or failure preserves both states.
     * The client only predicts the native placement result.
     *
     * @return {@code true} for an accepted client prediction or a committed server placement;
     *         {@code false} leaves the block and bucket unchanged
     */
    public boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack,
                                  ProtectionContext context, boolean allowFaceOffset) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.POWDER_SNOW) return false;
        int units = NBTUtil.getPowderUnits(stack);
        if (units <= 0) return false;

        Player player = context.player();
        InteractionHand hand = player == null ? InteractionHand.MAIN_HAND : context.hand();
        BlockPlaceContext placement = powderPlacementContext(level, player, hand, hit);
        if (!allowFaceOffset && !placement.replacingClickedOnBlock()) return false;

        BlockPos placePos = placement.getClickedPos();
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, placePos,
                hit.getDirection(), stack, null)) return false;

        if (!placePowderBlock(level, placement, player)) return false;

        if (!level.isClientSide) {
            int newUnits = units - 1;
            NBTUtil.setPowderUnits(stack, newUnits);
            if (newUnits <= 0) NBTUtil.normalizeEmptyState(stack);
            if (player != null) player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    private static BlockPlaceContext powderPlacementContext(Level level, @Nullable Player player,
                                                             InteractionHand hand, BlockHitResult hit) {
        return new BlockPlaceContext(level, player, hand,
                new ItemStack(Items.POWDER_SNOW_BUCKET), hit);
    }

    /**
     * Runs vanilla's powder-snow block-item placement inside Forge's snapshot/event transaction.
     * The temporary vanilla stack is transaction-local; the caller debits the BB only after this
     * method reports that placement committed.
     */
    private static boolean placePowderBlock(Level level, BlockPlaceContext placement, @Nullable Player player) {
        BlockItem powderSnow = (BlockItem) Items.POWDER_SNOW_BUCKET;
        if (level.isClientSide) return powderSnow.place(placement).consumesAction();

        int firstSnapshot = level.capturedBlockSnapshots.size();
        boolean wasCapturing = level.captureBlockSnapshots;
        level.captureBlockSnapshots = true;
        InteractionResult result;
        try {
            result = powderSnow.place(placement);
        } finally {
            level.captureBlockSnapshots = wasCapturing;
        }

        List<BlockSnapshot> snapshots = new ArrayList<>(
                level.capturedBlockSnapshots.subList(firstSnapshot, level.capturedBlockSnapshots.size()));
        level.capturedBlockSnapshots.subList(firstSnapshot, level.capturedBlockSnapshots.size()).clear();
        if (!result.consumesAction()) {
            restoreSnapshots(level, snapshots);
            return false;
        }
        if (snapshots.isEmpty()) return false;

        boolean canceled = snapshots.size() > 1
                ? ForgeEventFactory.onMultiBlockPlace(player, snapshots, placement.getClickedFace())
                : ForgeEventFactory.onBlockPlace(player, snapshots.get(0), placement.getClickedFace());
        if (canceled) {
            restoreSnapshots(level, snapshots);
            return false;
        }

        for (BlockSnapshot snapshot : snapshots) {
            BlockState oldState = snapshot.getReplacedBlock();
            BlockState newState = level.getBlockState(snapshot.getPos());
            newState.onPlace(level, snapshot.getPos(), oldState, false);
            level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()),
                    oldState, newState, snapshot.getFlag(), MAX_UPDATE_RECURSION);
        }
        return true;
    }

    private static void restoreSnapshots(Level level, List<BlockSnapshot> snapshots) {
        boolean wasRestoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
            for (int i = snapshots.size() - 1; i >= 0; i--) {
                snapshots.get(i).restore(true, false);
            }
        } finally {
            level.restoringBlockSnapshots = wasRestoring;
        }
    }

}
