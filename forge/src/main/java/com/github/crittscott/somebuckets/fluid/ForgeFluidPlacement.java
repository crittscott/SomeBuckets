package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.interaction.Transfers;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;

/** Forge-native arbitrary-fluid world placement for Some Buckets containers. */
public final class ForgeFluidPlacement {
    private ForgeFluidPlacement() {}

    /** Resolves the same direct-or-neighbor target later passed to {@link FluidUtil}. */
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
     * Places and drains exactly one bucket-volume through Forge's fluid type and placement helper.
     * The item handler determines whether that drain is finite or infinite.
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

        if (level.isClientSide) return true;
        boolean vaporizes = unit.getFluid().getFluidType().isVaporizedOnPlacement(
                level, target, unit);
        if (!FluidUtil.tryPlaceFluid(player, level, hand, target, source, unit)) return false;
        if (!vaporizes) {
            Transfers.notifyActor(player, Transfers.resolveEmptySound(unit.getFluid()));
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

    /** Target selection stays bucket-like; FluidUtil performs its broader final admission check. */
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
        return new FluidStack(stored.getFluid(), FluidType.BUCKET_VOLUME, stored.getTag());
    }
}
