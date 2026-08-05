package com.github.crittscott.somebuckets.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.wrappers.BucketPickupHandlerWrapper;
import net.minecraftforge.fluids.capability.wrappers.FluidBlockWrapper;

import javax.annotation.Nullable;

/**
 * Shared world pickup of one bucket unit, following the vanilla bucket rules: the block that owns
 * the fluid gives it up through its own pickup contract, so a plain source block is removed, a
 * waterlogged block keeps itself and loses only its water, and a block that refuses bucket pickup
 * keeps its fluid.
 *
 * <p>Forge's block wrappers present that contract as an {@link IFluidHandler}, so {@link #available}
 * reports what one unit of pickup would yield without changing anything. A caller decides whether
 * that content is acceptable, checks protection, and only then calls {@link #take}.
 *
 * <p>This owns the world transaction only. A non-empty {@link #take} result means the world gave up
 * one unit; the caller decides where to record it.
 */
public final class FluidPickup {
    private FluidPickup() {}

    /**
     * The pickup contract for the block at {@code pos}, or null if a bucket cannot take a unit from
     * it. Flowing fluid is excluded here: a {@link BucketPickup} block reports its whole fluid state
     * when asked what it holds, but only a source is actually collectable.
     */
    @Nullable
    private static IFluidHandler handlerFor(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof IFluidBlock fluidBlock) {
            return new FluidBlockWrapper(fluidBlock, level, pos);
        }
        if (block instanceof BucketPickup pickup && state.getFluidState().isSource()) {
            return new BucketPickupHandlerWrapper(pickup, level, pos);
        }
        return null;
    }

    /**
     * One unit of whatever the block at {@code pos} would hand over, or empty if it would not hand
     * over a whole unit. Nothing is changed.
     */
    public static FluidStack available(Level level, BlockPos pos) {
        IFluidHandler handler = handlerFor(level, pos, level.getBlockState(pos));
        if (handler == null) return FluidStack.EMPTY;
        FluidStack drained = handler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
        return drained.getAmount() < FluidType.BUCKET_VOLUME ? FluidStack.EMPTY : drained;
    }

    /**
     * Takes {@code expected}, as reported by {@link #available}, out of the block at {@code pos} and
     * returns what was removed, or empty if the block declined. Asking for an exact content lets the
     * wrapper reject a block that hands back something other than what it advertised, rather than
     * leaving the caller to store the wrong fluid.
     *
     * <p>The client is only told what it would get; the world is changed on the server alone.
     */
    public static FluidStack take(Level level, BlockPos pos, FluidStack expected, @Nullable Player player) {
        BlockState state = level.getBlockState(pos);
        IFluidHandler handler = handlerFor(level, pos, state);
        if (handler == null) return FluidStack.EMPTY;

        FluidStack taken = handler.drain(expected, level.isClientSide
                ? IFluidHandler.FluidAction.SIMULATE
                : IFluidHandler.FluidAction.EXECUTE);
        if (taken.getAmount() < FluidType.BUCKET_VOLUME) return FluidStack.EMPTY;

        playFill(level, player, pos, state, taken.getFluid());
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        return taken;
    }

    private static void playFill(Level level, @Nullable Player player, BlockPos pos, BlockState state,
                                 Fluid fluid) {
        SoundEvent sound = state.getBlock() instanceof BucketPickup pickup
                ? pickup.getPickupSound(state).orElse(null)
                : null;
        if (sound == null) sound = fluid.getFluidType().getSound(SoundActions.BUCKET_FILL);
        if (sound == null) {
            sound = fluid.defaultFluidState().is(FluidTags.LAVA)
                    ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL;
        }
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
