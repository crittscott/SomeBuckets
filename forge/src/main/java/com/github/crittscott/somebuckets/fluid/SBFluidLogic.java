package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.List;

/**
 * Coordinates Source Bucket assignment and infinite output after {@code SBItem} selects a gesture.
 * Shared capability, cauldron, pickup, and placement primitives own physical transactions; this
 * class enforces the Source Bucket allowlist and its unassigned-versus-assigned dispatch policy.
 */
public class SBFluidLogic {
    private static final SBFluidLogic INSTANCE = new SBFluidLogic();

    private SBFluidLogic() {}

    public static SBFluidLogic getInstance() {
        return INSTANCE;
    }

    /**
     * Tries to assign an empty Source Bucket from one fluid unit for a real player.
     *
     * @return {@code true} for an accepted client prediction or a completed server assignment;
     *         {@code false} leaves the source and bucket unchanged
     */
    public boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, Player player,
                           InteractionHand hand) {
        return tryTakeWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Tries to assign an empty Source Bucket using explicit authorization identity.
     *
     * <p>A sided block capability has priority, followed by supported cauldrons and the world
     * block's pickup contract. Every acquired content is allowlist-checked, and the exact target is
     * protected before mutation. A server success assigns the bucket and emits the operation's
     * sound and fluid-pickup game event. Player pickups receive item-use accounting; genuine world
     * or cauldron pickups also fire the filled-bucket criterion. Client calls only predict.
     *
     * @return {@code true} for an accepted client prediction or a completed server assignment;
     *         {@code false} leaves the source and bucket unchanged
     */
    public boolean tryTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                      ProtectionContext context) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) return false;
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
        if (available.isEmpty() || !SBPolicy.allows(available.getFluid())) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        FluidStack taken = FluidPickup.take(level, pos, available, context.player());
        if (taken.isEmpty()) return false;

        if (!level.isClientSide) {
            ForgeFluidStacks.set(stack, new FluidStack(taken.getFluid(), FluidType.BUCKET_VOLUME, taken.getTag()));
            FluidPickup.completePlayerPickup(level, context.player(), stack);
        }
        return true;
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

        FluidStack fluidStack = ForgeFluidStacks.get(stack);
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
     * The position {@link #tryPlace} would actually act on: the clicked block if it exposes a
     * compatible fluid-handler capability, otherwise wherever {@link #resolvePlaceTargetInWorld}
     * resolves for cauldron or generic world placement. Read-only: does not check protection or touch
     * the world. Used to pick the correct {@code FillBucketEvent} target before dispatch.
     */
    public static BlockPos resolvePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                              boolean allowFaceOffset) {
        Transfers.requireBucketHandler(stack);
        BlockPos clicked = hit.getBlockPos();
        if (Transfers.hasBlockHandler(level, clicked, hit.getDirection())) return clicked;
        return resolvePlaceTargetInWorld(level, hit, ForgeFluidStacks.get(stack), allowFaceOffset);
    }

    /**
     * The position placing {@code fluidStack} would actually change: the clicked block for an empty
     * cauldron this fluid can fill, otherwise whatever {@link FluidPlacement#resolveTarget} resolves
     * for generic world placement. Read-only: does not check protection or touch the world. Used to
     * pick the correct {@code FillBucketEvent} target before dispatch.
     */
    public static BlockPos resolvePlaceTargetInWorld(Level level, BlockHitResult hit, FluidStack fluidStack,
                                                      boolean allowFaceOffset) {
        BlockPos clicked = hit.getBlockPos();
        BlockState clickedState = level.getBlockState(clicked);
        Fluid fluid = fluidStack.getFluid();
        if (clickedState.is(Blocks.CAULDRON) && (fluid == Fluids.WATER || fluid == Fluids.LAVA)) {
            return clicked;
        }
        return FluidPlacement.resolveTarget(level, clicked, hit.getDirection(), allowFaceOffset, fluid);
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

        // Check if trying to fill already-full cauldron of same type - do nothing
        if (fluid == Fluids.WATER && clickedState.is(Blocks.WATER_CAULDRON) &&
                clickedState.hasProperty(LayeredCauldronBlock.LEVEL) &&
                clickedState.getValue(LayeredCauldronBlock.LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL) {
            return false;
        }
        if (fluid == Fluids.LAVA && clickedState.is(Blocks.LAVA_CAULDRON)) {
            return false;
        }

        // World placement; the Source Bucket is infinite, so nothing is drained
        if (!FluidPlacement.emptyContents(level, context, stack, clicked, hit, fluid, allowFaceOffset)) return false;

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
