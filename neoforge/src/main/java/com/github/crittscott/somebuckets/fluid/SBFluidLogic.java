package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import java.util.List;

/**
 * Coordinates Source Bucket assignment and infinite output after {@code SBItem} selects a gesture.
 * Shared capability, cauldron, pickup, and placement primitives own physical transactions; this
 * class enforces the Source Bucket allowlist and its assignment, matching-intake, and output policy.
 */
public class SBFluidLogic {
    private static final SBFluidLogic INSTANCE = new SBFluidLogic();

    private SBFluidLogic() {}

    public static SBFluidLogic getInstance() {
        return INSTANCE;
    }

    /**
     * Tries to assign an empty Source Bucket or sink matching fluid for a real player.
     *
     * @return {@code true} for an accepted client prediction or completed server intake;
     *         {@code false} leaves the source and bucket unchanged
     */
    public boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, Player player,
                           InteractionHand hand) {
        return tryTakeWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Tries to assign an empty Source Bucket or sink matching fluid using explicit authorization.
     *
     * <p>A sided block capability has priority, followed by supported cauldrons and the world
     * block's pickup contract. Every acquired content is allowlist-checked, and the exact target is
     * protected before mutation. An empty bucket records the acquired identity; an assigned bucket
     * accepts only matching input and retains its identity. A success emits the operation's sound
     * and fluid-pickup game event. Player pickups receive item-use accounting. Client calls only
     * predict.
     *
     * @return {@code true} for an accepted client prediction or completed server intake;
     *         {@code false} leaves the source and bucket unchanged
     */
    public boolean tryTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                      ProtectionContext context) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode != NBTUtil.Mode.NONE && mode != NBTUtil.Mode.FLUID) return false;
        boolean assigning = mode == NBTUtil.Mode.NONE;
        FluidStack assigned = assigning ? FluidStack.EMPTY : NeoForgeFluidStacks.get(stack);
        if (!assigning && (assigned.isEmpty() || !SBPolicy.allows(assigned.getFluid()))) return false;
        IFluidHandlerItem itemHandler = Transfers.requireBucketHandler(stack);

        BlockPos pos = hit.getBlockPos();

        Transfers.BlockTransferResult blockTransfer = Transfers.tryTakeFromBlock(
                level, pos, hit.getDirection(), stack, itemHandler, context);
        if (blockTransfer.handled()) {
            return blockTransfer.succeeded();
        }

        if (SBPolicy.allows(Fluids.WATER)
                && Cauldrons.takeWater(level, pos, hit.getDirection(), stack, itemHandler, context)) {
            return true;
        }
        if (SBPolicy.allows(Fluids.LAVA)
                && Cauldrons.takeLava(level, pos, hit.getDirection(), stack, itemHandler, context)) {
            return true;
        }

        // Generic world fluid, taken through the block's own pickup contract
        FluidStack available = FluidPickup.available(level, pos);
        if (available.isEmpty() || !SBPolicy.allows(available.getFluid())
                || !assigning && !available.getFluid().isSame(assigned.getFluid())) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        FluidStack taken = FluidPickup.take(level, pos, available, context.player());
        if (taken.isEmpty()) return false;

        if (!level.isClientSide) {
            if (assigning) {
                NeoForgeFluidStacks.set(stack,
                        NeoForgeFluidStacks.resized(taken, FluidType.BUCKET_VOLUME));
                FluidPickup.completePlayerPickup(level, context.player(), stack);
            } else if (context.player() != null) {
                context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            }
        }
        return true;
    }

    /** Classifies the exact target for an assigned Source Bucket's take-or-place decision. */
    public BucketOperations.SourceTarget classifyTarget(Level level, BlockHitResult hit,
                                                         ItemStack stack) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.FLUID) {
            return BucketOperations.SourceTarget.BLOCKING_FLUID;
        }
        FluidStack assigned = NeoForgeFluidStacks.get(stack);
        if (assigned.isEmpty() || !SBPolicy.allows(assigned.getFluid())) {
            return BucketOperations.SourceTarget.BLOCKING_FLUID;
        }

        BlockPos pos = hit.getBlockPos();
        IFluidHandlerItem itemHandler = Transfers.requireBucketHandler(stack);
        if (Transfers.hasBlockHandler(level, pos, hit.getDirection())) {
            return Transfers.classifySourceTarget(
                    level, pos, hit.getDirection(), itemHandler);
        }

        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON)) {
            boolean full = state.hasProperty(LayeredCauldronBlock.LEVEL)
                    && state.getValue(LayeredCauldronBlock.LEVEL)
                    == LayeredCauldronBlock.MAX_FILL_LEVEL;
            return full && assigned.getFluid().isSame(Fluids.WATER)
                    ? BucketOperations.SourceTarget.MATCHING_FLUID
                    : BucketOperations.SourceTarget.BLOCKING_FLUID;
        }
        if (state.is(Blocks.LAVA_CAULDRON)) {
            return assigned.getFluid().isSame(Fluids.LAVA)
                    ? BucketOperations.SourceTarget.MATCHING_FLUID
                    : BucketOperations.SourceTarget.BLOCKING_FLUID;
        }

        if (!state.getFluidState().isEmpty()) {
            FluidStack available = FluidPickup.available(level, pos);
            return !available.isEmpty() && available.getFluid().isSame(assigned.getFluid())
                    ? BucketOperations.SourceTarget.MATCHING_FLUID
                    : BucketOperations.SourceTarget.BLOCKING_FLUID;
        }
        return BucketOperations.SourceTarget.NO_FLUID;
    }

    /**
     * Tries infinite output from an assigned Source Bucket for a real player, allowing vanilla
     * face-offset target selection.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction;
     *         {@code false} leaves the target and bucket unchanged
     */
    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, Player player,
                            InteractionHand hand) {
        return tryPlace(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Tries infinite output from an assigned Source Bucket with explicit authorization identity.
     *
     * <p>The assignment is rechecked against the allowlist. A sided capability has priority,
     * followed by a supported cauldron and vanilla-style world placement. {@code allowFaceOffset}
     * permits a blocked clicked position to resolve to its neighbor but does not bypass placement
     * validity. Protection, sound, game events, and player statistics belong to the selected shared
     * transaction. The bucket is never debited. Client calls predict without world mutation.
     *
     * @return {@code true} for an accepted client prediction or a completed server transaction;
     *         {@code false} leaves the target and bucket unchanged
     */
    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                            boolean allowFaceOffset) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.FLUID) return false;
        IFluidHandlerItem itemHandler = Transfers.requireBucketHandler(stack);

        FluidStack fluidStack = NeoForgeFluidStacks.get(stack);
        if (!SBPolicy.allows(fluidStack.getFluid())) return false;

        BlockPos clicked = hit.getBlockPos();

        Transfers.BlockTransferResult blockTransfer = Transfers.tryPlaceIntoBlock(
                level, clicked, hit.getDirection(), stack, itemHandler, context);
        if (blockTransfer.handled()) {
            return blockTransfer.succeeded();
        }

        // Fall back to cauldron and world placement
        return tryPlaceInWorld(level, hit, stack, itemHandler, context, fluidStack, allowFaceOffset);
    }

    /**
     * Resolves the position a Source Bucket placement would target without checking protection or
     * changing state. A block fluid capability selects the clicked block; otherwise cauldron or
     * generic world targeting applies.
     *
     * @param allowFaceOffset whether world placement may target the neighbor along the clicked face
     * @return the candidate target; this does not guarantee that placement will succeed
     */
    public static BlockPos resolvePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                              Player player, InteractionHand hand,
                                              boolean allowFaceOffset) {
        Transfers.requireBucketHandler(stack);
        BlockPos clicked = hit.getBlockPos();
        if (Transfers.hasBlockHandler(level, clicked, hit.getDirection())) return clicked;
        return resolvePlaceTargetInWorld(
                level, hit, stack, player, hand, NeoForgeFluidStacks.get(stack), allowFaceOffset);
    }

    /**
     * Resolves a Source Bucket's world-placement target without checking protection or changing
     * state. A fillable empty cauldron selects the clicked block; otherwise generic placement applies.
     *
     * @param allowFaceOffset whether generic placement may target the neighbor along the clicked face
     * @return the candidate target; this does not guarantee that placement will succeed
     */
    public static BlockPos resolvePlaceTargetInWorld(Level level, BlockHitResult hit, ItemStack stack,
                                                      Player player, InteractionHand hand,
                                                      FluidStack fluidStack, boolean allowFaceOffset) {
        BlockPos clicked = hit.getBlockPos();
        BlockState clickedState = level.getBlockState(clicked);
        Fluid fluid = fluidStack.getFluid();
        if (clickedState.is(Blocks.CAULDRON) && (fluid == Fluids.WATER || fluid == Fluids.LAVA)) {
            return clicked;
        }
        return NeoForgeFluidPlacement.resolveTarget(
                level, hit, stack, player, hand, fluidStack, allowFaceOffset);
    }

    private boolean tryPlaceInWorld(Level level, BlockHitResult hit, ItemStack stack,
                                    IFluidHandlerItem itemHandler, ProtectionContext context,
                                    FluidStack fluidStack,
                                    boolean allowFaceOffset) {
        BlockPos clicked = hit.getBlockPos();
        BlockState clickedState = level.getBlockState(clicked);
        Fluid fluid = fluidStack.getFluid();

        if (clickedState.is(Blocks.CAULDRON)) {
            if (fluid == Fluids.WATER) {
                return Cauldrons.placeWater(
                        level, clicked, hit.getDirection(), stack, itemHandler, context);
            }
            if (fluid == Fluids.LAVA) {
                return Cauldrons.placeLava(
                        level, clicked, hit.getDirection(), stack, itemHandler, context);
            }
        }

        // A full cauldron of the assigned fluid accepts nothing, but a normal place gesture still
        // reports success with the empty sound, matching placement onto an existing source block.
        boolean fullWaterCauldron = fluid == Fluids.WATER && clickedState.is(Blocks.WATER_CAULDRON)
                && clickedState.hasProperty(LayeredCauldronBlock.LEVEL)
                && clickedState.getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL;
        if (fullWaterCauldron || fluid == Fluids.LAVA && clickedState.is(Blocks.LAVA_CAULDRON)) {
            if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, clicked,
                    hit.getDirection(), stack, null)) return false;
            if (!level.isClientSide) {
                Transfers.playBucketSound(level, context, clicked, Transfers.resolveEmptySound(fluid));
                level.gameEvent(context.player(), GameEvent.FLUID_PLACE, clicked);
                if (context.player() != null) {
                    context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
                }
            }
            return true;
        }

        // World placement; the Source Bucket is infinite, so nothing is drained
        if (!NeoForgeFluidPlacement.place(
                level, hit, stack, itemHandler, context, fluidStack, allowFaceOffset)) return false;

        if (!level.isClientSide && context.player() != null) {
            context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    /**
     * Assigns an empty Source Bucket to allowed milk from the first adult cow in the dispenser's
     * front block.
     *
     * <p>This server-only operation checks entity-interaction protection and plays the automated
     * milking sound after assignment. It has no player statistics or criterion.
     *
     * @return {@code true} only when the bucket was assigned; {@code false} leaves cow and bucket
     *         unchanged
     */
    public boolean tryMilkDispenser(ServerLevel level, BlockPos front, Direction face, ItemStack stack,
                                      ProtectionContext context) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) return false;
        if (!SBPolicy.allowsMilk()) return false;
        AABB box = new AABB(front);
        List<Cow> cows = level.getEntitiesOfClass(Cow.class, box, cow -> !cow.isBaby());
        if (cows.isEmpty()) return false;
        Cow cow = cows.get(0);
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_INTERACT, cow.blockPosition(),
                face, stack, cow)) return false;

        NBTUtil.setMilkAmount(stack, FluidType.BUCKET_VOLUME);
        level.playSound(context.player(), front, Transfers.automatedMilkingSound(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

}
