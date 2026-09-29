package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Cauldrons.CauldronFluid;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.BucketOperations.BlockFluidStore;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Loader-neutral fluid transactions for Big, Huge, and Source Buckets after an item or dispenser
 * selects a gesture, plus vanilla-contract world pickup and fixed-water placement.
 *
 * <p>Sided block fluid stores, arbitrary-fluid world placement, per-fluid sounds, and the checked
 * world placement are loader primitives on {@link BucketOperations}; this class applies admission,
 * the {@link SBPolicy} allowlist, protection, bucket debit or credit, and player observability
 * around them. A block store is moved one bucket volume at a time through the bucket's own
 * {@link FluidBucketItem} container rules. Each operation previews, authorizes the exact target, and
 * only then mutates; a world placement completes the loader's place check against the placed block
 * before the bucket is debited. A {@code true} result means an accepted client prediction or a
 * completed server transaction.
 *
 * <ul>
 *   <li>Finite Big and Huge Bucket transactions take and place one unit, crediting or debiting the
 *       bucket; powder snow moves one block.</li>
 *   <li>Source Bucket transactions assign an empty bucket, sink matching input, and place without
 *       depleting; the assignment never changes on a take or place.</li>
 *   <li>World pickup removes one bucket volume through the block's own {@link BucketPickup}
 *       contract: a source block is removed, a waterlogged block keeps itself and loses only its
 *       fluid, and a block that refuses pickup keeps its fluid.</li>
 *   <li>Vanilla-rule world placement serves aquatic Mob Bucket release and Fabric's arbitrary-fluid
 *       output; Forge and NeoForge place through their own fluid types so that metadata remains
 *       authoritative. The position that would actually be changed is authorized, so a neighbor
 *       reached by fall-through is authorized in its own right.</li>
 * </ul>
 */
public final class FluidTransactions {
    /** One bucket volume of plain water, the only fluid aquatic Mob Bucket capture removes. */
    public static final StoredFluid WATER_UNIT =
            new StoredFluid(Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);

    private static final int EVAPORATION_PARTICLE_COUNT = 8;

    private static final float HISS_PITCH_BASE = 2.6F;
    private static final float HISS_PITCH_VARIANCE = 0.8F;

    /**
     * Translation key shown to the acting player when a block fluid store reports a transfer result
     * that contradicts its own simulation.
     */
    private static final String FLUID_TRANSFER_INCONSISTENT_KEY = "message.somebuckets.fluid_transfer_inconsistent";
    private static final Set<Block> REPORTED_CONTRACT_VIOLATIONS = ConcurrentHashMap.newKeySet();

    /** Read-only classification of the exact block targeted by an assigned Source Bucket. */
    public enum SourceTarget {
        /** One bucket-volume of the assigned fluid can be removed from the target. */
        MATCHING_FLUID,
        /** Fluid is present, but it is different or cannot be collected as one bucket-volume. */
        BLOCKING_FLUID,
        /** The target contains no fluid, so normal Source Bucket placement may be attempted. */
        NO_FLUID
    }

    /** Outcome of a sided block-store transfer; a present store owns dispatch even when it refuses. */
    private enum StoreOutcome {
        NO_STORE, REFUSED, SUCCESS
    }

    private FluidTransactions() {}

    // ---- Finite Big and Huge Bucket transactions ----

    /**
     * Tries to take one fluid unit for a real player using {@code hand}.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryTakeFinite(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                        InteractionHand hand) {
        return tryTakeFinite(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Read-only eligibility preview for taking one bucket-volume from the hit. A sided block store
     * owns the result when present; otherwise the preview checks world fluid, current mode, variant
     * compatibility, and remaining capacity. Protection is not evaluated.
     */
    public static boolean canTakeFiniteAt(Level level, BlockHitResult hit, ItemStack stack) {
        BlockFluidStore store = blockStore(level, hit.getBlockPos(), hit.getDirection());
        if (store != null) return takeableUnit(store, stack) != null;

        StoredFluid available = sourceAt(level, hit.getBlockPos());
        return !available.isEmpty() && BBItem.canAcceptFluidUnit(stack, available);
    }

    /**
     * Resolves the position a finite-fluid placement would target without checking protection or
     * changing state. A sided block store selects the clicked block; otherwise generic world
     * placement may select the neighboring block.
     */
    public static BlockPos resolveFinitePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                    Player player, InteractionHand hand,
                                                    boolean allowFaceOffset) {
        if (hasBlockStore(level, hit.getBlockPos(), hit.getDirection())) return hit.getBlockPos();
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
    public static boolean tryTakeFinite(Level level, BlockHitResult hit, ItemStack stack,
                                        ProtectionContext context) {
        StoreOutcome blockTransfer = takeFromStore(level, hit, stack, context);
        if (blockTransfer != StoreOutcome.NO_STORE) return blockTransfer == StoreOutcome.SUCCESS;

        BlockPos pos = hit.getBlockPos();
        StoredFluid available = sourceAt(level, pos);
        if (available.isEmpty() || !BBItem.canAcceptFluidUnit(stack, available)) return false;
        if (!Protections.mayRemove(level, context, pos, hit.getDirection(), stack)) return false;

        if (!takeWorldFluid(level, pos, available, context.player())) return false;

        if (!level.isClientSide) {
            ((BBItem) stack.getItem()).insert(stack, available, FluidBucketItem.BUCKET_VOLUME_MB);
            completePlayerPickup(level, context.player(), stack);
        }
        return true;
    }

    /**
     * Tries to place one fluid unit for a real player, allowing vanilla face-offset target selection.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryPlaceFinite(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                         InteractionHand hand) {
        return tryPlaceFinite(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Tries to place one fluid unit using an explicit player or automation context.
     *
     * <p>A sided block store has priority. Otherwise placement uses the loader's arbitrary-fluid
     * world rules; {@code allowFaceOffset} permits a blocked clicked position to resolve to its
     * neighbor along the hit face. Protection, the finite debit, sound, and the fluid-place game
     * event belong to the selected primitive; a successful world placement additionally awards a
     * player the item-use statistic and the placed-block criterion.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryPlaceFinite(Level level, BlockHitResult hit, ItemStack stack,
                                         ProtectionContext context, boolean allowFaceOffset) {
        if (BucketState.getMode(stack) != BucketState.Mode.FLUID) return false;
        StoredFluid stored = BucketState.getStoredFluid(stack);
        if (stored.amount() < FluidBucketItem.BUCKET_VOLUME_MB) return false;

        StoreOutcome blockTransfer = placeIntoStore(level, hit, stack, context);
        if (blockTransfer != StoreOutcome.NO_STORE) return blockTransfer == StoreOutcome.SUCCESS;

        BlockPos target = BucketOperations.get().resolveArbitraryPlaceTarget(level, hit, stack,
                context.actor(), context.hand() == null ? InteractionHand.MAIN_HAND : context.hand(), stored,
                allowFaceOffset);
        if (!BucketOperations.get().placeArbitraryFluid(
                level, hit, stack, context, stored, allowFaceOffset)) return false;
        completePlayerPlacement(level, context.player(), target, stack);
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
     * Read-only eligibility preview for collecting the target. Checks that the target is powder snow
     * and that the bucket's mode and remaining capacity permit one more unit.
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
     * and protection before the server stores one unit and removes the block through the
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
        if (!Protections.mayRemove(level, context, pos, hit.getDirection(), stack)) return false;

        if (!takeWorldBlock(level, pos, context.player())) return false;
        if (!level.isClientSide) {
            BucketState.setPowderUnits(stack, (mode == BucketState.Mode.POWDER_SNOW ? units : 0) + 1);
            completePlayerPickup(level, context.player(), stack);
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
     * Tries native powder-snow placement with explicit authorization identity. Resolves the target
     * through a vanilla {@link BlockPlaceContext}, checks protection at the resolved position, places
     * a vanilla powder-snow bucket's block under {@link BucketOperations#placeChecked}, and on server
     * success debits one unit.
     *
     * @param allowFaceOffset whether an unusable clicked position may resolve to the neighbor
     * @return {@code true} for an accepted client prediction or a committed server placement
     */
    public static boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack,
                                         ProtectionContext context, boolean allowFaceOffset) {
        if (BucketState.getMode(stack) != BucketState.Mode.POWDER_SNOW) return false;
        int units = BucketState.getPowderUnits(stack);
        InteractionHand hand = context.hand() == null ? InteractionHand.MAIN_HAND : context.hand();
        BlockPlaceContext placement = placementContext(
                level, context.actor(), hand, stack.copyWithCount(1), hit);
        if (!allowFaceOffset && !placement.replacingClickedOnBlock()) return false;
        if (!Protections.mayModify(level, context, placement.getClickedPos(), hit.getDirection(), stack)) {
            return false;
        }

        BlockItem powderSnow = (BlockItem) Items.POWDER_SNOW_BUCKET;
        if (!BucketOperations.get().placeChecked(level, context, placement.getClickedPos(), hit.getDirection(),
                () -> powderSnow.place(placement).consumesAction())) return false;
        if (!level.isClientSide) BucketState.setPowderUnits(stack, units - 1);
        return true;
    }

    /**
     * Creates a placement context with an explicit level, actor, hand, and stack. Vanilla exposes
     * this constructor to subclasses, so the mod does not need to widen it globally merely to build
     * the synthetic contexts used by bucket and dispenser transactions.
     */
    public static BlockPlaceContext placementContext(Level level, Player player, InteractionHand hand,
                                                      ItemStack stack, BlockHitResult hit) {
        return new ExplicitBlockPlaceContext(level, player, hand, stack, hit);
    }

    private static final class ExplicitBlockPlaceContext extends BlockPlaceContext {
        private ExplicitBlockPlaceContext(Level level, Player player, InteractionHand hand,
                                          ItemStack stack, BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }

    // ---- Source Bucket transactions ----

    /**
     * Tries to assign an empty Source Bucket or sink matching fluid for a real player.
     *
     * @return {@code true} for an accepted client prediction or completed server intake
     */
    public static boolean tryTakeSource(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                        InteractionHand hand) {
        return tryTakeSource(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Tries to assign an empty Source Bucket or sink matching fluid using explicit authorization.
     *
     * <p>A sided block store has priority, followed by vanilla cauldrons and the world block's
     * pickup contract. Every acquired content is allowlist-checked and the exact target is protected
     * before mutation. An empty bucket records the acquired identity; an assigned bucket accepts only
     * matching input and retains its identity.
     *
     * @return {@code true} for an accepted client prediction or completed server intake
     */
    public static boolean tryTakeSource(Level level, BlockHitResult hit, ItemStack stack,
                                        ProtectionContext context) {
        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode != BucketState.Mode.NONE && mode != BucketState.Mode.FLUID) return false;
        boolean assigning = mode == BucketState.Mode.NONE;
        StoredFluid assigned = assigning ? StoredFluid.EMPTY : BucketState.getStoredFluid(stack);
        if (!assigning && !SBPolicy.allows(assigned.fluid())) return false;

        BlockPos pos = hit.getBlockPos();

        StoreOutcome blockTransfer = takeFromStore(level, hit, stack, context);
        if (blockTransfer != StoreOutcome.NO_STORE) return blockTransfer == StoreOutcome.SUCCESS;

        if (Cauldrons.isVanillaCauldron(level.getBlockState(pos))) {
            CauldronFluid full = Cauldrons.fullFluidAt(level.getBlockState(pos));
            if (full == null || !SBPolicy.allows(full.fluid())
                    || !Cauldrons.take(level, pos, hit.getDirection(), stack, full, context)) return false;
            assignIfEmpty(level, stack, assigning, full.fluid());
            return true;
        }

        // Generic world fluid, taken through the block's own pickup contract
        StoredFluid available = sourceAt(level, pos);
        if (available.isEmpty() || !SBPolicy.allows(available.fluid())
                || (!assigning && !available.fluid().isSame(assigned.fluid()))) return false;
        if (!Protections.mayRemove(level, context, pos, hit.getDirection(), stack)) return false;

        if (!takeWorldFluid(level, pos, available, context.player())) return false;

        if (!level.isClientSide) {
            if (assigning) {
                BucketState.setStoredFluid(stack, available.withAmount(FluidBucketItem.BUCKET_VOLUME_MB));
                completePlayerPickup(level, context.player(), stack);
            } else if (context.player() != null) {
                context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            }
        }
        return true;
    }

    /**
     * Classifies the exact target for an assigned Source Bucket's take-or-place decision.
     *
     * @return whether the target holds the matching fluid, a blocking fluid, or no fluid
     */
    public static SourceTarget classifySourceTarget(Level level, BlockHitResult hit, ItemStack stack) {
        if (BucketState.getMode(stack) != BucketState.Mode.FLUID) return SourceTarget.BLOCKING_FLUID;
        StoredFluid assigned = BucketState.getStoredFluid(stack);
        if (!SBPolicy.allows(assigned.fluid())) return SourceTarget.BLOCKING_FLUID;

        BlockPos pos = hit.getBlockPos();
        BlockFluidStore store = blockStore(level, pos, hit.getDirection());
        if (store != null) {
            if (takeableUnit(store, stack) != null) return SourceTarget.MATCHING_FLUID;
            return store.offered().isEmpty() ? SourceTarget.NO_FLUID : SourceTarget.BLOCKING_FLUID;
        }

        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.LAVA_CAULDRON)) {
            CauldronFluid full = Cauldrons.fullFluidAt(state);
            return full != null && assigned.fluid().isSame(full.fluid())
                    ? SourceTarget.MATCHING_FLUID : SourceTarget.BLOCKING_FLUID;
        }
        if (!state.getFluidState().isEmpty()) {
            StoredFluid available = sourceAt(level, pos);
            return !available.isEmpty() && available.fluid().isSame(assigned.fluid())
                    ? SourceTarget.MATCHING_FLUID : SourceTarget.BLOCKING_FLUID;
        }
        return SourceTarget.NO_FLUID;
    }

    /**
     * Tries infinite output from an assigned Source Bucket for a real player, allowing vanilla
     * face-offset target selection.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryPlaceSource(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                         InteractionHand hand) {
        return tryPlaceSource(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Tries infinite output from an assigned Source Bucket with explicit authorization identity.
     *
     * <p>A sided store has priority; a present store that refuses is authoritative and blocks world
     * fall-through. A vanilla cauldron is served by {@link Cauldrons#place}. Otherwise the loader's
     * arbitrary-fluid world placement runs. The bucket is never debited.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryPlaceSource(Level level, BlockHitResult hit, ItemStack stack,
                                         ProtectionContext context, boolean allowFaceOffset) {
        if (BucketState.getMode(stack) != BucketState.Mode.FLUID) return false;
        StoredFluid stored = BucketState.getStoredFluid(stack);
        if (!SBPolicy.allows(stored.fluid())) return false;

        BlockPos pos = hit.getBlockPos();
        StoreOutcome blockTransfer = placeIntoStore(level, hit, stack, context);
        if (blockTransfer != StoreOutcome.NO_STORE) return blockTransfer == StoreOutcome.SUCCESS;

        if (Cauldrons.isVanillaCauldron(level.getBlockState(pos))) {
            CauldronFluid fluid = CauldronFluid.of(stored.fluid());
            // Cauldrons.place owns its cauldron stat, sound, and game event.
            return fluid != null
                    && Cauldrons.place(level, pos, hit.getDirection(), stack, fluid, context);
        }

        BlockPos target = BucketOperations.get().resolveArbitraryPlaceTarget(level, hit, stack,
                context.actor(), context.hand() == null ? InteractionHand.MAIN_HAND : context.hand(), stored,
                allowFaceOffset);
        if (!BucketOperations.get().placeArbitraryFluid(
                level, hit, stack, context, stored, allowFaceOffset)) return false;
        completePlayerPlacement(level, context.player(), target, stack);
        return true;
    }

    /**
     * Resolves the position a Source Bucket placement would target without checking protection or
     * changing state.
     *
     * @param allowFaceOffset whether world placement may target the neighbor along the clicked face
     * @return the candidate target; placement is not guaranteed to succeed there
     */
    public static BlockPos resolveSourcePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                    Player player, InteractionHand hand, boolean allowFaceOffset) {
        BlockPos clicked = hit.getBlockPos();
        if (hasBlockStore(level, clicked, hit.getDirection())) return clicked;
        BlockState state = level.getBlockState(clicked);
        if (Cauldrons.isEmptyCauldron(state)
                && CauldronFluid.of(BucketState.getStoredFluid(stack).fluid()) != null) return clicked;
        return BucketOperations.get().resolveArbitraryPlaceTarget(
                level, hit, stack, player, hand, BucketState.getStoredFluid(stack), allowFaceOffset);
    }

    private static void assignIfEmpty(Level level, ItemStack stack, boolean assigning, Fluid fluid) {
        if (!level.isClientSide && assigning) {
            BucketState.setStoredFluid(stack, new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB));
        }
    }

    // ---- World pickup ----

    /**
     * Reports what one bucket volume of world pickup at {@code pos} would yield, without changing
     * anything. No variant payload is carried: a vanilla {@code BucketPickup} block never exposes
     * one.
     *
     * @param level level to query
     * @param pos block position to inspect
     * @return one bucket volume of the source fluid at {@code pos}, or {@link StoredFluid#EMPTY} when
     *         the block is not a {@link BucketPickup} source
     */
    private static StoredFluid sourceAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup) || !state.getFluidState().isSource()) {
            return StoredFluid.EMPTY;
        }
        return new StoredFluid(state.getFluidState().getType(), FluidBucketItem.BUCKET_VOLUME_MB);
    }

    /**
     * Removes one bucket volume of {@code expected} from the block at {@code pos} through its own
     * {@link BucketPickup#pickupBlock} contract, then plays the block's pickup sound and emits the
     * fluid-pickup game event. The world changes on the server only; the client predicts acceptance.
     *
     * @param level acting level
     * @param pos block position to draw from
     * @param expected fluid the caller requires; a different world fluid is rejected
     * @param player acting player, or {@code null} for automation
     * @return {@code true} when the block gave up a unit, or the client predicted it; {@code false}
     *         leaves the world unchanged
     */
    public static boolean takeWorldFluid(Level level, BlockPos pos, StoredFluid expected, @Nullable Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup pickup) || !state.getFluidState().isSource()
                || !state.getFluidState().getType().isSame(expected.fluid())) return false;
        if (!level.isClientSide && pickup.pickupBlock(player, level, pos, state).isEmpty()) return false;
        if (!level.isClientSide) {
            BucketOperations.get().pickupSound(pickup, state).ifPresent(sound ->
                    playBucketSound(level, pos, sound));
        }
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        return true;
    }

    /**
     * Removes the {@link BucketPickup} block at {@code pos} through its own
     * {@link BucketPickup#pickupBlock} contract, then plays the block's pickup sound and emits the
     * fluid-pickup game event. This is the non-fluid counterpart of {@link #takeWorldFluid}: powder snow is a
     * {@code BucketPickup} block with no fluid state. The block is removed on the server only; the
     * client predicts acceptance and still plays the predicted sound and game event.
     *
     * @param level acting level
     * @param pos block position to remove
     * @param player acting player, or {@code null} for automation
     * @return {@code true} when the block gave up its pickup stack, or the client predicted it;
     *         {@code false} leaves the world unchanged
     */
    private static boolean takeWorldBlock(Level level, BlockPos pos, @Nullable Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup pickup)) return false;
        if (!level.isClientSide && pickup.pickupBlock(player, level, pos, state).isEmpty()) return false;
        BucketOperations.get().pickupSound(pickup, state).ifPresent(sound ->
                level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F));
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        return true;
    }

    /**
     * Records vanilla bucket-pickup observability after the caller has stored the acquired content:
     * the item-use statistic and the filled-bucket criterion.
     *
     * @param level acting level; client prediction is a no-op
     * @param player acting player; a {@code null} player (automation) is a no-op
     * @param filledStack the now-filled bucket stack, used to key the statistic and criterion
     */
    private static void completePlayerPickup(Level level, @Nullable Player player, ItemStack filledStack) {
        if (level.isClientSide || player == null) return;
        player.awardStat(Stats.ITEM_USED.get(filledStack.getItem()));
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, filledStack);
        }
    }

    // ---- Sided block fluid stores ----

    /**
     * Whether the block at {@code pos} exposes a loader fluid store on {@code face}, whether or not
     * it can take part in the current operation.
     */
    public static boolean hasBlockStore(Level level, BlockPos pos, Direction face) {
        return blockStore(level, pos, face) != null;
    }

    @Nullable
    private static BlockFluidStore blockStore(Level level, BlockPos pos, Direction face) {
        return BucketOperations.get().blockFluidStore(level, pos, face);
    }

    /*
     * The first bucket volume the store offers that it would give up whole and the bucket would take
     * whole, or null when there is none.
     */
    @Nullable
    private static StoredFluid takeableUnit(BlockFluidStore store, ItemStack stack) {
        FluidBucketItem item = (FluidBucketItem) stack.getItem();
        for (StoredFluid candidate : store.offered()) {
            StoredFluid unit = candidate.withAmount(FluidBucketItem.BUCKET_VOLUME_MB);
            if (unit.isEmpty()) continue;
            if (isUnitOf(store.drain(unit, true), unit)
                    && item.acceptable(stack, unit) == FluidBucketItem.BUCKET_VOLUME_MB) {
                return unit;
            }
        }
        return null;
    }

    /*
     * Takes one bucket volume from the clicked face's store into the bucket: a finite bucket is
     * credited, an empty Source Bucket assigned, and an assigned one left unchanged.
     */
    private static StoreOutcome takeFromStore(Level level, BlockHitResult hit, ItemStack stack,
                                              ProtectionContext context) {
        BlockPos pos = hit.getBlockPos();
        BlockFluidStore store = blockStore(level, pos, hit.getDirection());
        if (store == null) return StoreOutcome.NO_STORE;
        StoredFluid unit = takeableUnit(store, stack);
        if (unit == null) return StoreOutcome.REFUSED;
        if (!Protections.mayModify(level, context, pos, hit.getDirection(), stack)) return StoreOutcome.REFUSED;

        if (!level.isClientSide) {
            StoredFluid removed = store.drain(unit, false);
            if (!isUnitOf(removed, unit)) {
                reportContractViolation(level, pos, context, "block drain", unit, removed);
                return StoreOutcome.REFUSED;
            }
            ((FluidBucketItem) stack.getItem()).insert(stack, removed, FluidBucketItem.BUCKET_VOLUME_MB);
        }
        completeStoreTransfer(level, pos, context, unit, true);
        return StoreOutcome.SUCCESS;
    }

    /*
     * Places one bucket volume from the bucket into the clicked face's store. The bucket's own
     * extract rule decides whether it is debited, so a Source Bucket is left unchanged.
     */
    private static StoreOutcome placeIntoStore(Level level, BlockHitResult hit, ItemStack stack,
                                               ProtectionContext context) {
        BlockPos pos = hit.getBlockPos();
        BlockFluidStore store = blockStore(level, pos, hit.getDirection());
        if (store == null) return StoreOutcome.NO_STORE;
        FluidBucketItem item = (FluidBucketItem) stack.getItem();
        StoredFluid unit = item.extractable(stack, FluidBucketItem.BUCKET_VOLUME_MB);
        if (unit.amount() != FluidBucketItem.BUCKET_VOLUME_MB) return StoreOutcome.REFUSED;
        if (store.fill(unit, true) != FluidBucketItem.BUCKET_VOLUME_MB) return StoreOutcome.REFUSED;
        if (!Protections.mayModify(level, context, pos, hit.getDirection(), stack)) return StoreOutcome.REFUSED;

        if (!level.isClientSide) {
            int accepted = store.fill(unit, false);
            if (accepted != FluidBucketItem.BUCKET_VOLUME_MB) {
                reportContractViolation(level, pos, context, "block fill",
                        FluidBucketItem.BUCKET_VOLUME_MB, accepted);
                return StoreOutcome.REFUSED;
            }
            item.extract(stack, FluidBucketItem.BUCKET_VOLUME_MB);
        }
        completeStoreTransfer(level, pos, context, unit, false);
        return StoreOutcome.SUCCESS;
    }

    private static boolean isUnitOf(StoredFluid fluid, StoredFluid unit) {
        return fluid.amount() == FluidBucketItem.BUCKET_VOLUME_MB && fluid.isSameVariant(unit);
    }

    /*
     * Emits the fluid game event and plays the bucket sound. A player reaches a store through the
     * item's useOn, whose success vanilla counts as an item use.
     */
    private static void completeStoreTransfer(Level level, BlockPos pos,
                                              ProtectionContext context, StoredFluid unit, boolean pickup) {
        if (!level.isClientSide) {
            level.gameEvent(context.player(), pickup ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, pos);
        }
        (pickup ? BucketOperations.get().fillSound(unit) : BucketOperations.get().emptySound(unit))
                .ifPresent(sound -> playBucketSound(level, pos, sound));
    }

    /*
     * Reports a block fluid store whose executed transfer contradicted its simulation. Logs only the
     * first violation from each block, while every occurrence tells the acting real player.
     */
    private static void reportContractViolation(Level level, BlockPos pos, ProtectionContext context,
                                                String operation, Object expected, Object actual) {
        BlockState state = level.getBlockState(pos);
        if (REPORTED_CONTRACT_VIOLATIONS.add(state.getBlock())) {
            SomeBuckets.LOGGER.error(
                    "Fluid store contract violation during {} at {} in {} (block {}): expected {}, got {}; further violations from this block will not be logged",
                    operation, pos, level.dimension().location(), state, expected, actual);
        }
        if (context.player() != null) {
            context.player().displayClientMessage(Component.translatable(FLUID_TRANSFER_INCONSISTENT_KEY), false);
        }
    }

    // ---- World placement, evaporation, and sounds ----

    /**
     * Reports whether placing {@code fluid} in {@code level} evaporates instead of forming a block,
     * matching vanilla's ultra-warm-dimension water rule. Loaders with a fluid-specific vaporization
     * policy (Forge's {@code FluidType#isVaporizedOnPlacement}) should defer to it instead of this
     * fallback.
     *
     * @param level level the placement would occur in
     * @param fluid fluid being placed
     * @return {@code true} when the dimension is ultra-warm and the fluid is water
     */
    public static boolean evaporatesInUltraWarm(Level level, Fluid fluid) {
        return level.dimensionType().ultraWarm() && fluid.defaultFluidState().is(FluidTags.WATER);
    }

    /**
     * Resolves the position that would actually be written by placing {@code fluid} at {@code pos}
     * along {@code face}.
     *
     * <p>Read-only: does not check protection or touch the world.
     *
     * @param level level to inspect
     * @param player player used for liquid-container placement checks, or {@code null}
     * @param pos clicked position
     * @param face clicked face, used for fall-through to the neighbor
     * @param mayFallThrough whether an invalid clicked position may resolve to the neighbor
     * @param fluid fluid being placed
     * @return {@code pos} itself when it is air, replaceable, or a liquid-container block that accepts
     *         the fluid; the neighbor along {@code face} when fall-through is allowed and the neighbor
     *         qualifies; otherwise {@code pos} unchanged, so the caller always gets a single position
     *         to report even when the eventual placement attempt will fail there
     */
    public static BlockPos resolveWorldTarget(Level level, @Nullable Player player, BlockPos pos,
                                              Direction face, boolean mayFallThrough, Fluid fluid) {
        BlockState state = level.getBlockState(pos);
        if (!canHoldPlacedFluid(level, player, pos, state, fluid)) {
            if (!mayFallThrough) return pos;
            BlockPos neighbor = pos.relative(face);
            BlockState neighborState = level.getBlockState(neighbor);
            return canHoldPlacedFluid(level, player, neighbor, neighborState, fluid) ? neighbor : pos;
        }
        return pos;
    }

    /** Whether {@code state} at {@code pos} can receive {@code fluid} under vanilla bucket rules. */
    public static boolean canHoldPlacedFluid(Level level, @Nullable Player player, BlockPos pos,
                                             BlockState state, Fluid fluid) {
        return state.isAir() || state.canBeReplaced(fluid)
                || state.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(player, level, pos, state, fluid);
    }

    /**
     * Places one bucket volume of {@code stored} at {@code pos} along {@code face} using vanilla
     * bucket target and replacement rules.
     *
     * <p>If {@code mayFallThrough} is true, an invalid clicked position may resolve once to the
     * neighbor along {@code face}; it does not make an otherwise invalid destination placeable. The
     * resolved position is authorized, then {@code beforeMutation} runs; returning {@code false}
     * aborts before the world changes. Ultra-warm evaporation is handled here. Otherwise the fluid is
     * placed through {@link #placeFluidChecked}. A fluid with a {@link BucketItem} is placed by that
     * item's own {@code emptyContents}, so waterlogging, the empty sound, the fluid-place game event,
     * and any modded override apply; any other flowing fluid is placed directly. The caller remains
     * responsible for item-use accounting, and commits a debit prepared by {@code beforeMutation} only
     * on success.
     *
     * @param level acting level
     * @param context authorization identity
     * @param stack the bucket stack driving the placement
     * @param pos clicked position
     * @param face clicked face
     * @param mayFallThrough whether an invalid clicked position may resolve once to the neighbor
     * @param stored the fluid to place; its amount is ignored
     * @param beforeMutation prepares the caller's debit after authorization; {@code false} aborts
     * @return {@code true} for an accepted client prediction or a completed server placement;
     *         {@code false} leaves the world unchanged
     */
    public static boolean emptyFluid(Level level, ProtectionContext context, ItemStack stack, BlockPos pos,
                                     Direction face, boolean mayFallThrough, StoredFluid stored,
                                     BooleanSupplier beforeMutation) {
        Fluid fluid = stored.fluid();
        if (fluid.defaultFluidState().createLegacyBlock().isAir()) return false;
        Player actor = context.actor();
        BlockPos target = resolveWorldTarget(level, actor, pos, face, mayFallThrough, fluid);
        BlockState state = level.getBlockState(target);
        if (!canHoldPlacedFluid(level, actor, target, state, fluid)) return false;
        if (!Protections.mayModify(level, context, target, face, stack)) return false;
        if (!beforeMutation.getAsBoolean()) return false;

        if (evaporatesInUltraWarm(level, fluid)) {
            evaporate(level, target);
            return true;
        }
        if (level.isClientSide) return true;

        Optional<SoundEvent> emptySound = BucketOperations.get().emptySound(stored);
        return placeFluidChecked(level, context, target, face, fluid, () -> {
            if (fluid.getBucket() instanceof BucketItem bucketItem) {
                // As in vanilla, automation passes no entity, so the fluid-place game event has no
                // source. A real player is excluded from emptyContents' sound, having predicted nothing.
                if (!bucketItem.emptyContents(context.player(), level, target, null)) return false;
                emptySound.ifPresent(sound -> notifyActor(context.player(), sound));
                return true;
            }

            if (!(fluid instanceof FlowingFluid flowing)) return false;
            BlockState current = level.getBlockState(target);
            if (current.getBlock() instanceof LiquidBlockContainer container
                    && container.canPlaceLiquid(actor, level, target, current, fluid)) {
                container.placeLiquid(level, target, current, flowing.getSource(false));
            } else if (!level.setBlock(target, fluid.defaultFluidState().createLegacyBlock(),
                    Block.UPDATE_ALL_IMMEDIATE) && !state.getFluidState().isSource()) {
                return false;
            }
            emptySound.ifPresent(sound -> playBucketSound(level, target, sound));
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, target);
            return true;
        });
    }

    /**
     * Runs {@code place}, a server-side placement of {@code fluid} at {@code target}, under
     * {@link BucketOperations#placeChecked}. A block the fluid replaces, which bucket placement
     * destroys with drops, is first cleared without drops, so a refused placement restores it and
     * drops nothing; its drops, destruction particles, and game event follow only once it is gone
     * for good. A block that takes the fluid by waterlogging is not cleared.
     *
     * @param level acting server level
     * @param context authorization identity
     * @param target exact position the fluid is placed at
     * @param face clicked face the placement is made against
     * @param fluid fluid being placed
     * @param place performs the placement, returning {@code false} when it did not happen
     * @return {@code true} when the placement happened and stands
     */
    public static boolean placeFluidChecked(Level level, ProtectionContext context, BlockPos target,
                                            Direction face, Fluid fluid, BooleanSupplier place) {
        BlockState replaced = level.getBlockState(target);
        boolean clears = !replaced.isAir() && !replaced.liquid()
                && !(replaced.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(context.actor(), level, target, replaced, fluid));
        BlockEntity blockEntity = clears ? level.getBlockEntity(target) : null;
        boolean placed = BucketOperations.get().placeChecked(level, context, target, face, () -> {
            if (clears) level.setBlock(target, replaced.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            return place.getAsBoolean();
        });
        // A placement the check refused, or that failed under the loader's recording, was restored.
        if (clears && level.getBlockState(target) != replaced) {
            if (!(replaced.getBlock() instanceof BaseFireBlock)) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, target, Block.getId(replaced));
            }
            Block.dropResources(replaced, level, target, blockEntity);
            level.gameEvent(GameEvent.BLOCK_DESTROY, target, GameEvent.Context.of(replaced));
        }
        return placed;
    }

    /** Broadcasts one server-authoritative bucket sound, including the acting player. */
    public static void playBucketSound(Level level, BlockPos pos, SoundEvent sound) {
        if (!level.isClientSide) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /** Sends an acting server player a sound excluded from a loader utility's broadcast. */
    public static void notifyActor(@Nullable Player player, SoundEvent sound) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.playNotifySound(sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * Records vanilla bucket-placement observability for a player after a successful world placement:
     * the item-use statistic and, as {@code BucketItem#use} does, the placed-block criterion at the
     * placement target.
     *
     * @param level acting level; client prediction is a no-op
     * @param player acting real player, or {@code null} for automation
     * @param target position the placement resolved to, computed before the world changed
     * @param stack the bucket stack that placed the fluid
     */
    private static void completePlayerPlacement(Level level, @Nullable Player player, BlockPos target,
                                                ItemStack stack) {
        if (level.isClientSide || player == null) return;
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, target, stack);
        }
    }

    /**
     * Plays vanilla's ultra-warm evaporation feedback at {@code pos}: the extinguish hiss (server
     * authoritative, so a client-predicting caller stays silent) and a burst of large smoke from
     * {@link ServerLevel}.
     *
     * @param level acting level
     * @param pos position the feedback plays at
     */
    public static void evaporate(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                    hissPitch(level.random));
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    EVAPORATION_PARTICLE_COUNT, 0.5D, 0.5D, 0.5D, 0.0D);
        }
    }

    /**
     * Computes the shared "raspy hiss" pitch for the vanilla evaporation sound and its Trash Bucket
     * reuse.
     *
     * @param random randomness source for the pitch variance
     * @return a randomized pitch around the evaporation-hiss base
     */
    public static float hissPitch(RandomSource random) {
        return HISS_PITCH_BASE + (random.nextFloat() - random.nextFloat()) * HISS_PITCH_VARIANCE;
    }
}
