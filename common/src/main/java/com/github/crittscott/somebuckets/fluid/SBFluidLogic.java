package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.BucketOperations.BlockFluidOutcome;
import com.github.crittscott.somebuckets.platform.BucketOperations.SourceTarget;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Loader-neutral coordination of Source Bucket assignment and infinite output after {@code SBItem}
 * selects a gesture. Sided block storage, vanilla water/lava cauldron transitions, arbitrary world
 * placement, and per-fluid sounds are loader primitives on {@link BucketOperations}; this class
 * enforces the {@link SBPolicy} allowlist and the assignment, matching-intake, and output policy.
 */
public final class SBFluidLogic {
    private SBFluidLogic() {}

    /**
     * Tries to assign an empty Source Bucket or sink matching fluid for a real player.
     *
     * @return {@code true} for an accepted client prediction or completed server intake
     */
    public static boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                  InteractionHand hand) {
        return tryTakeWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Tries to assign an empty Source Bucket or sink matching fluid using explicit authorization.
     *
     * <p>A sided block store has priority, followed by supported cauldrons and the world block's
     * pickup contract. Every acquired content is allowlist-checked and the exact target is protected
     * before mutation. An empty bucket records the acquired identity; an assigned bucket accepts only
     * matching input and retains its identity.
     *
     * @return {@code true} for an accepted client prediction or completed server intake
     */
    public static boolean tryTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context) {
        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode != BucketState.Mode.NONE && mode != BucketState.Mode.FLUID) return false;
        boolean assigning = mode == BucketState.Mode.NONE;
        StoredFluid assigned = assigning ? StoredFluid.EMPTY : BucketState.getStoredFluid(stack);
        if (!assigning && (assigned.isEmpty() || !SBPolicy.allows(assigned.fluid()))) return false;

        BlockPos pos = hit.getBlockPos();

        BlockFluidOutcome blockTransfer = BucketOperations.get().blockTake(level, hit, stack, context, true);
        if (blockTransfer.handled()) return blockTransfer.succeeded();

        boolean clickedCauldron = level.getBlockState(pos).getBlock() instanceof AbstractCauldronBlock;
        if (clickedCauldron) {
            if (SBPolicy.allows(Fluids.WATER)
                    && BucketOperations.get().cauldronTake(level, pos, hit.getDirection(), stack, Fluids.WATER, context)) {
                assignIfEmpty(level, stack, assigning, Fluids.WATER);
                return true;
            }
            if (SBPolicy.allows(Fluids.LAVA)
                    && BucketOperations.get().cauldronTake(level, pos, hit.getDirection(), stack, Fluids.LAVA, context)) {
                assignIfEmpty(level, stack, assigning, Fluids.LAVA);
                return true;
            }
            return false;
        }

        // Generic world fluid, taken through the block's own pickup contract
        StoredFluid available = WorldFluidPickup.sourceAt(level, pos);
        if (available.isEmpty() || !SBPolicy.allows(available.fluid())
                || (!assigning && !available.fluid().isSame(assigned.fluid()))) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        if (!WorldFluidPickup.take(level, pos, available, context.player(),
                BucketOperations.get().fillSound(available))) return false;

        if (!level.isClientSide) {
            if (assigning) {
                BucketState.setStoredFluid(stack, available.withAmount(FluidBucketItem.BUCKET_VOLUME_MB));
                WorldFluidPickup.completePlayerPickup(level, context.player(), stack);
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
    public static SourceTarget classifyTarget(Level level, BlockHitResult hit, ItemStack stack) {
        if (BucketState.getMode(stack) != BucketState.Mode.FLUID) return SourceTarget.BLOCKING_FLUID;
        StoredFluid assigned = BucketState.getStoredFluid(stack);
        if (assigned.isEmpty() || !SBPolicy.allows(assigned.fluid())) return SourceTarget.BLOCKING_FLUID;

        SourceTarget fromStore = BucketOperations.get().classifyBlockTarget(level, hit, stack);
        if (fromStore != null) return fromStore;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON)) {
            boolean full = state.hasProperty(LayeredCauldronBlock.LEVEL)
                    && state.getValue(LayeredCauldronBlock.LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL;
            return full && assigned.fluid().isSame(Fluids.WATER)
                    ? SourceTarget.MATCHING_FLUID : SourceTarget.BLOCKING_FLUID;
        }
        if (state.is(Blocks.LAVA_CAULDRON)) {
            return assigned.fluid().isSame(Fluids.LAVA)
                    ? SourceTarget.MATCHING_FLUID : SourceTarget.BLOCKING_FLUID;
        }
        if (!state.getFluidState().isEmpty()) {
            StoredFluid available = WorldFluidPickup.sourceAt(level, pos);
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
    public static boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                   InteractionHand hand) {
        return tryPlace(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Tries infinite output from an assigned Source Bucket with explicit authorization identity.
     *
     * <p>A sided store has priority; a present non-cauldron store that refuses is authoritative and
     * blocks world fall-through. A cauldron is served by {@link BucketOperations#cauldronPlace}.
     * Otherwise the loader's arbitrary-fluid world placement runs. The bucket is never debited.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction
     */
    public static boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack,
                                   ProtectionContext context, boolean allowFaceOffset) {
        if (BucketState.getMode(stack) != BucketState.Mode.FLUID) return false;
        StoredFluid stored = BucketState.getStoredFluid(stack);
        if (stored.isEmpty() || !SBPolicy.allows(stored.fluid())) return false;

        BlockPos pos = hit.getBlockPos();
        boolean clickedCauldron = level.getBlockState(pos).getBlock() instanceof AbstractCauldronBlock;

        BlockFluidOutcome outcome = BucketOperations.get().blockPlace(level, hit, stack, context, true);
        if (outcome == BlockFluidOutcome.SUCCESS) return true;
        if (outcome == BlockFluidOutcome.REFUSED && !clickedCauldron) return false;

        if (clickedCauldron) {
            Fluid fluid = stored.fluid();
            // cauldronPlace owns its own stats, criterion, sound, and game event.
            return (fluid == Fluids.WATER || fluid == Fluids.LAVA)
                    && BucketOperations.get().cauldronPlace(level, pos, hit.getDirection(), stack, fluid, context);
        }

        if (!BucketOperations.get().placeArbitraryFluid(
                level, hit, stack, context, stored, true, allowFaceOffset)) return false;
        awardPlaceStat(level, context, stack);
        return true;
    }

    /**
     * Resolves the position a Source Bucket placement would target without checking protection or
     * changing state.
     *
     * @param allowFaceOffset whether world placement may target the neighbor along the clicked face
     * @return the candidate target; placement is not guaranteed to succeed there
     */
    public static BlockPos resolvePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                              Player player, InteractionHand hand, boolean allowFaceOffset) {
        BlockPos clicked = hit.getBlockPos();
        if (BucketOperations.get().hasBlockStorage(level, clicked, hit.getDirection())) return clicked;
        BlockState state = level.getBlockState(clicked);
        Fluid fluid = BucketState.getStoredFluid(stack).fluid();
        if (state.is(Blocks.CAULDRON) && (fluid == Fluids.WATER || fluid == Fluids.LAVA)) return clicked;
        return BucketOperations.get().resolveArbitraryPlaceTarget(
                level, hit, stack, player, hand, BucketState.getStoredFluid(stack), allowFaceOffset);
    }

    /**
     * Assigns an empty Source Bucket to allowed milk from the first adult cow in the dispenser's
     * front block. Server-only; checks entity-interaction protection and plays the automated milking
     * sound after assignment.
     *
     * @return {@code true} only when the bucket was assigned
     */
    public static boolean tryMilkDispenser(ServerLevel level, BlockPos front, Direction face, ItemStack stack,
                                           ProtectionContext context) {
        if (BucketState.getMode(stack) != BucketState.Mode.NONE) return false;
        if (!SBPolicy.allowsMilk()) return false;
        List<Cow> cows = level.getEntitiesOfClass(Cow.class, new AABB(front), cow -> !cow.isBaby());
        if (cows.isEmpty()) return false;
        Cow cow = cows.get(0);
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_INTERACT, cow.blockPosition(),
                face, stack, cow)) return false;

        BucketState.setMilkAmount(stack, FluidBucketItem.BUCKET_VOLUME_MB);
        level.playSound(context.player(), front, SoundEvents.COW_MILK, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private static void assignIfEmpty(Level level, ItemStack stack, boolean assigning, Fluid fluid) {
        if (!level.isClientSide && assigning) {
            BucketState.setStoredFluid(stack, new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB, null));
        }
    }

    private static void awardPlaceStat(Level level, ProtectionContext context, ItemStack stack) {
        if (!level.isClientSide && context.player() != null) {
            context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
    }
}
