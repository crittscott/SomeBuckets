package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.interaction.BucketSounds;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;

/** NeoForge-native arbitrary-fluid world placement for Some Buckets containers. */
public final class NeoForgeFluidPlacement {
    private NeoForgeFluidPlacement() {}

    /**
     * Resolves the same direct-or-neighbor target later passed to {@link FluidUtil}.
     *
     * @param allowFaceOffset whether an unusable clicked position may resolve to the neighbor along
     *                        the hit face
     * @return the clicked position, or the neighbor when it is allowed and the clicked position
     *         cannot be targeted
     */
    public static BlockPos resolveTarget(Level level, BlockHitResult hit, ItemStack stack,
                                         @Nullable Player player, InteractionHand hand,
                                         FluidStack stored, boolean allowFaceOffset) {
        FluidStack unit = unit(stored);
        BlockPos clicked = hit.getBlockPos();
        if (canTargetAt(level, clicked, stack, player, hand, unit) || !allowFaceOffset) return clicked;

        BlockPos neighbor = clicked.relative(hit.getDirection());
        return canTargetAt(level, neighbor, stack, player, hand, unit) ? neighbor : clicked;
    }

    /**
     * Places and drains exactly one bucket-volume through NeoForge's fluid type and placement helper.
     * The item handler determines whether that drain is finite or infinite.
     *
     * @param stack the bucket stack driving the placement
     * @param source item fluid handler drained for the placed unit
     * @param context authorization identity
     * @param stored the bucket's current fluid; only a full bucket-volume is placed
     * @param allowFaceOffset whether an unusable clicked position may resolve to the neighbor
     * @return {@code true} for an accepted client prediction or a completed server placement;
     *         {@code false} leaves the world unchanged
     */
    public static boolean place(Level level, BlockHitResult hit, ItemStack stack,
                                IFluidHandlerItem source, ProtectionContext context,
                                FluidStack stored, boolean allowFaceOffset) {
        FluidStack unit = unit(stored);
        if (unit.isEmpty()) return false;

        Player player = context.player();
        InteractionHand hand = context.hand() == null ? InteractionHand.MAIN_HAND : context.hand();
        BlockPos target = resolveTarget(level, hit, stack, player, hand, unit, allowFaceOffset);
        if (!canPlaceAt(level, target, stack, player, hand, unit)) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, target,
                hit.getDirection(), stack, null)) return false;

        boolean vaporizes = unit.getFluid().getFluidType().isVaporizedOnPlacement(level, target, unit);
        BlockState targetState = level.getBlockState(target);
        boolean container = targetState.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(player, level, target, targetState, unit.getFluid());
        boolean destroysBlock = !container && !vaporizes && !targetState.isAir()
                && targetState.canBeReplaced(unit.getFluid()) && !targetState.liquid();
        if (destroysBlock && !Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, target,
                hit.getDirection(), stack, null)) return false;

        if (level.isClientSide) {
            if (vaporizes) {
                unit.getFluid().getFluidType().onVaporize(player, level, target, unit);
            }
            return true;
        }
        if (!FluidUtil.tryPlaceFluid(player, level, hand, target, source, unit)) return false;
        if (!vaporizes) {
            BucketSounds.notifyActor(player, BucketSounds.resolveEmptySound(unit.getFluid()));
        }
        level.gameEvent(player, GameEvent.FLUID_PLACE, target);
        return true;
    }

    private static boolean canPlaceAt(Level level, BlockPos pos, ItemStack stack,
                                      @Nullable Player player, InteractionHand hand,
                                      FluidStack resource) {
        Fluid fluid = resource.getFluid();
        if (fluid == Fluids.EMPTY
                || !fluid.getFluidType().canBePlacedInLevel(level, pos, resource)) return false;

        ItemStack held = player == null ? ItemStack.EMPTY : stack;
        BlockPlaceContext context = new BlockPlaceContext(level, player, hand, held,
                new BlockHitResult(Vec3.ZERO, Direction.UP, pos, false));
        BlockState state = level.getBlockState(pos);
        boolean container = state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(player, level, pos, state, fluid);
        return level.isEmptyBlock(pos) || !state.isSolid() || state.canBeReplaced(context) || container;
    }

    /**
     * Reports whether {@code pos} is a bucket-like placement target. {@link FluidUtil} performs its
     * broader final admission check.
     *
     * @return {@code true} when the position is empty, replaceable, or a compatible liquid container
     */
    private static boolean canTargetAt(Level level, BlockPos pos, ItemStack stack,
                                       @Nullable Player player, InteractionHand hand,
                                       FluidStack resource) {
        Fluid fluid = resource.getFluid();
        if (fluid == Fluids.EMPTY
                || !fluid.getFluidType().canBePlacedInLevel(level, pos, resource)) return false;

        ItemStack held = player == null ? ItemStack.EMPTY : stack;
        BlockPlaceContext context = new BlockPlaceContext(level, player, hand, held,
                new BlockHitResult(Vec3.ZERO, Direction.UP, pos, false));
        BlockState state = level.getBlockState(pos);
        return level.isEmptyBlock(pos) || state.canBeReplaced(context)
                || state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(player, level, pos, state, fluid);
    }

    private static FluidStack unit(FluidStack stored) {
        if (stored.isEmpty() || stored.getAmount() < FluidType.BUCKET_VOLUME) return FluidStack.EMPTY;
        return NeoForgeFluidStacks.resized(stored, FluidType.BUCKET_VOLUME);
    }
}
