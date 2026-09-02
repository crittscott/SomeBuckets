package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.BucketOperations.BlockFluidOutcome;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Loader-neutral coordination of finite Big and Huge Bucket world transactions after {@link BBItem}
 * selects a gesture. Sided block storage, arbitrary world placement, per-fluid sounds, and native
 * powder-snow finalization are loader primitives on {@link BucketOperations}; this class applies
 * finite-mode admission, protection, bucket debit or credit, and player observability around them.
 */
public final class BBFluidLogic {
    private BBFluidLogic() {}

    /**
     * Tries to take one fluid unit for a real player using {@code hand}.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                  InteractionHand hand) {
        return tryTakeWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Read-only eligibility preview for taking one bucket-volume from the hit. A sided block store
     * owns the result when present; otherwise the preview checks world fluid, current mode, variant
     * compatibility, and remaining capacity. Protection is not evaluated.
     */
    public static boolean canAttemptTakeAt(Level level, BlockHitResult hit, ItemStack stack) {
        BlockFluidOutcome preview = BucketOperations.get().previewBlockTake(level, hit, stack);
        if (preview.handled()) return preview.succeeded();

        StoredFluid available = WorldFluidPickup.sourceAt(level, hit.getBlockPos());
        return !available.isEmpty() && BBItem.canAcceptFluidUnit(stack, available);
    }

    /**
     * Resolves the position a finite-fluid placement would target without checking protection or
     * changing state. A sided block store selects the clicked block; otherwise generic world
     * placement may select the neighboring block.
     */
    public static BlockPos resolvePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                              Player player, InteractionHand hand,
                                              boolean allowFaceOffset) {
        if (BucketOperations.get().hasBlockStorage(level, hit.getBlockPos(), hit.getDirection())) {
            return hit.getBlockPos();
        }
        return BucketOperations.get().resolveArbitraryPlaceTarget(
                level, hit, stack, player, hand, BucketState.getStoredFluid(stack), allowFaceOffset);
    }

    /**
     * Tries to take one fluid unit using an explicit player or automation context.
     *
     * <p>A sided block store has priority and owns dispatch even when it refuses. Otherwise the world
     * block's bucket-pickup contract is used. The exact target is protected before mutation. On a
     * world pickup the server credits the finite bucket and emits sound and the fluid game event;
     * player world pickup also awards the item-use statistic and filled-bucket criterion.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context) {
        BlockFluidOutcome blockTransfer = BucketOperations.get().blockTake(level, hit, stack, context, false);
        if (blockTransfer.handled()) return blockTransfer.succeeded();

        BlockPos pos = hit.getBlockPos();
        StoredFluid available = WorldFluidPickup.sourceAt(level, pos);
        if (available.isEmpty() || !BBItem.canAcceptFluidUnit(stack, available)) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        if (!WorldFluidPickup.take(level, pos, available, context.player(),
                BucketOperations.get().fillSound(available))) return false;

        if (!level.isClientSide) {
            StoredFluid current = BucketState.getStoredFluid(stack);
            boolean merging = BucketState.getMode(stack) == BucketState.Mode.FLUID && !current.isEmpty();
            BucketState.setStoredFluid(stack, merging
                    ? current.withAmount(current.amount() + FluidBucketItem.BUCKET_VOLUME_MB)
                    : available.withAmount(FluidBucketItem.BUCKET_VOLUME_MB));
            WorldFluidPickup.completePlayerPickup(level, context.player(), stack);
        }
        return true;
    }

    /**
     * Tries to place one fluid unit for a real player, allowing vanilla face-offset target selection.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                   InteractionHand hand) {
        return tryPlace(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Tries to place one fluid unit using an explicit player or automation context.
     *
     * <p>A sided block store has priority. Otherwise placement uses the loader's arbitrary-fluid
     * world rules; {@code allowFaceOffset} permits a blocked clicked position to resolve to its
     * neighbor along the hit face. Protection, the finite debit, sound, and the fluid-place game
     * event belong to the selected primitive; a successful world placement additionally awards the
     * item-use statistic to a player.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack,
                                   ProtectionContext context, boolean allowFaceOffset) {
        if (BucketState.getMode(stack) != BucketState.Mode.FLUID) return false;
        StoredFluid stored = BucketState.getStoredFluid(stack);
        if (stored.isEmpty() || stored.amount() < FluidBucketItem.BUCKET_VOLUME_MB) return false;

        BlockFluidOutcome blockTransfer = BucketOperations.get().blockPlace(level, hit, stack, context, false);
        if (blockTransfer.handled()) return blockTransfer.succeeded();

        if (!BucketOperations.get().placeArbitraryFluid(
                level, hit, stack, context, stored, false, allowFaceOffset)) return false;
        if (!level.isClientSide && context.player() != null) {
            context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    /**
     * Tries to collect one powder-snow block for a real player.
     *
     * @return {@code true} for an accepted client prediction or a completed server pickup
     */
    public static boolean tryTakePowder(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                        InteractionHand hand) {
        return tryTakePowderWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Read-only eligibility preview for collecting the targeted powder-snow block. Checks mode and
     * remaining capacity only.
     */
    public static boolean canAttemptTakePowderAt(Level level, BlockHitResult hit, ItemStack stack) {
        if (!level.getBlockState(hit.getBlockPos()).is(Blocks.POWDER_SNOW)) return false;
        int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();
        BucketState.Mode mode = BucketState.getMode(stack);
        int units = BucketState.getPowderUnits(stack);
        return mode == BucketState.Mode.NONE || (mode == BucketState.Mode.POWDER_SNOW && units < capUnits);
    }

    /**
     * Tries to collect one powder-snow block with explicit authorization identity. Checks capacity
     * and block-edit protection before the server stores one unit and removes the block through the
     * vanilla {@code BucketPickup} contract.
     *
     * @return {@code true} for an accepted client prediction or a completed server pickup
     */
    public static boolean tryTakePowderWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                                   ProtectionContext context) {
        if (!canAttemptTakePowderAt(level, hit, stack)) return false;

        BlockPos pos = hit.getBlockPos();
        BucketState.Mode mode = BucketState.getMode(stack);
        int units = BucketState.getPowderUnits(stack);
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        if (!WorldFluidPickup.takeBlock(level, pos, context.player(),
                SoundEvents.BUCKET_FILL_POWDER_SNOW)) return false;
        if (!level.isClientSide) {
            BucketState.setPowderUnits(stack, (mode == BucketState.Mode.POWDER_SNOW ? units : 0) + 1);
            WorldFluidPickup.completePlayerPickup(level, context.player(), stack);
        }
        return true;
    }

    /**
     * Tries native powder-snow placement for a real player, allowing face-offset target selection.
     *
     * @return {@code true} for an accepted client prediction or a committed server placement
     */
    public static boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                         InteractionHand hand) {
        return tryPlacePowder(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Tries native powder-snow placement with explicit authorization identity. Guards mode and unit
     * count, then hands the placement to {@link BucketOperations#placeStoredPowder}, which resolves
     * the target through a loader-built {@code BlockPlaceContext} (whose constructor is loader-only),
     * checks block-edit protection at the resolved position, runs the placement, and debits one unit
     * with the loader's own place-event and rollback behavior.
     *
     * @return {@code true} for an accepted client prediction or a committed server placement
     */
    public static boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack,
                                         ProtectionContext context, boolean allowFaceOffset) {
        if (BucketState.getMode(stack) != BucketState.Mode.POWDER_SNOW) return false;
        if (BucketState.getPowderUnits(stack) <= 0) return false;
        return BucketOperations.get().placeStoredPowder(level, hit, stack, context, allowFaceOffset);
    }
}
