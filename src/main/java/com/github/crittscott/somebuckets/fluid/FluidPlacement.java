package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Shared world placement for one bucket unit of fluid, following the vanilla bucket rules: a
 * block that can hold the liquid takes it in place, a replaceable block is broken with its
 * drops, water evaporates in ultra-warm dimensions, and a target that refuses the fluid falls
 * through to the neighbor along the clicked face.
 *
 * <p>The position that would actually be changed is checked as a fluid edit, so a neighbor reached
 * by fall-through is authorized in its own right rather than on the strength of the clicked block.
 *
 * <p>This owns the world transaction only. A {@code true} return means the world accepted one
 * unit; the caller decides whether to charge the bucket for it.
 */
public final class FluidPlacement {
    private static final int EVAPORATION_PARTICLE_COUNT = 8;

    private FluidPlacement() {}

    /**
     * Whether one unit of {@code fluid} can exist in the world as a block.
     *
     * <p>A fluid may be registered purely to move through pipes, tanks, and machines and have no
     * block form at all — a potion fluid is the usual example. Such a fluid still travels through
     * the fluid capability, so a bucket can hold one and be asked to pour it out. Placement of one
     * refuses here rather than consuming a unit to set air.
     */
    public static boolean isPlaceable(Fluid fluid) {
        return fluid instanceof FlowingFluid
                && !fluid.defaultFluidState().createLegacyBlock().isAir();
    }

    /**
     * Attempts to place one unit of {@code fluid} from {@code stack} using vanilla bucket target
     * and replacement rules.
     *
     * <p>If {@code mayFallThrough} is true, an invalid clicked position may resolve once to the
     * neighbor along {@link BlockHitResult#getDirection()}; it does not make an otherwise invalid
     * destination placeable. The resolved position is protected before mutation. Server success
     * places or waterlogs the fluid, destroys a replaceable non-liquid block with drops, or performs
     * ultra-warm evaporation, then emits the applicable sound and fluid-place game event. Client
     * success is prediction only. The caller remains responsible for debiting any finite container
     * and awarding item-use accounting.
     *
     * @return {@code true} when the client predicts acceptance or the server completes the world
     *         transaction; {@code false} leaves the world unchanged
     */
    public static boolean emptyContents(Level level, ProtectionContext context, ItemStack stack, BlockPos pos,
                                        BlockHitResult hit, Fluid fluid, boolean mayFallThrough) {
        return emptyContents(level, context, stack, pos, hit.getDirection(), mayFallThrough, fluid);
    }

    /**
     * The position that would actually be written by placing {@code fluid} at {@code pos} along
     * {@code face}: {@code pos} itself if it's air, replaceable, or a liquid-container block that
     * accepts the fluid; otherwise the neighbor along {@code face} if fall-through is allowed and the
     * neighbor qualifies; otherwise {@code pos} unchanged, so a caller always gets a single position
     * to report even when the eventual placement attempt will fail there.
     *
     * <p>Read-only: does not check protection or touch the world.
     */
    public static BlockPos resolveTarget(Level level, BlockPos pos, Direction face, boolean mayFallThrough,
                                         Fluid fluid) {
        BlockState state = level.getBlockState(pos);
        boolean replaceable = state.canBeReplaced(fluid);
        boolean container = state.getBlock() instanceof LiquidBlockContainer lbc
                && lbc.canPlaceLiquid(level, pos, state, fluid);

        if (!state.isAir() && !replaceable && !container) {
            if (!mayFallThrough) return pos;
            BlockPos neighbor = pos.relative(face);
            BlockState neighborState = level.getBlockState(neighbor);
            boolean neighborReplaceable = neighborState.canBeReplaced(fluid);
            boolean neighborContainer = neighborState.getBlock() instanceof LiquidBlockContainer nlbc
                    && nlbc.canPlaceLiquid(level, neighbor, neighborState, fluid);
            return neighborState.isAir() || neighborReplaceable || neighborContainer ? neighbor : pos;
        }
        return pos;
    }

    private static boolean emptyContents(Level level, ProtectionContext context, ItemStack stack, BlockPos pos,
                                         Direction face, boolean mayFallThrough, Fluid fluid) {
        if (!isPlaceable(fluid)) return false;
        FlowingFluid flowing = (FlowingFluid) fluid;

        pos = resolveTarget(level, pos, face, mayFallThrough, fluid);
        BlockState state = level.getBlockState(pos);
        boolean replaceable = state.canBeReplaced(fluid);

        LiquidBlockContainer container = null;
        if (state.getBlock() instanceof LiquidBlockContainer lbc && lbc.canPlaceLiquid(level, pos, state, fluid)) {
            container = lbc;
        }

        if (!state.isAir() && !replaceable && container == null) return false;

        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos, face, stack, null)) return false;

        if (level.dimensionType().ultraWarm() && flowing.defaultFluidState().is(FluidTags.WATER)) {
            evaporate(level, context.player(), pos);
            return true;
        }

        if (container != null) {
            if (!level.isClientSide) {
                container.placeLiquid(level, pos, state, flowing.getSource(false));
            }
            playEmpty(level, context.player(), pos, fluid);
            return true;
        }

        if (!level.isClientSide) {
            if (replaceable && !state.liquid()) {
                level.destroyBlock(pos, true);
            }
            if (!level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE)
                    && !state.getFluidState().isSource()) {
                return false;
            }
        }
        playEmpty(level, context.player(), pos, fluid);
        return true;
    }

    private static void evaporate(Level level, @Nullable Player player, BlockPos pos) {
        level.playSound(player, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
        if (level instanceof ServerLevel serverLevel) {
            for (int i = 0; i < EVAPORATION_PARTICLE_COUNT; i++) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        pos.getX() + level.random.nextDouble(),
                        pos.getY() + level.random.nextDouble(),
                        pos.getZ() + level.random.nextDouble(),
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void playEmpty(Level level, @Nullable Player player, BlockPos pos, Fluid fluid) {
        level.playSound(player, pos, Transfers.resolveEmptySound(fluid),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
    }
}
