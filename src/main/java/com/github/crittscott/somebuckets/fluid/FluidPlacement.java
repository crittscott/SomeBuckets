package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.SoundActions;

import javax.annotation.Nullable;

/**
 * Shared world placement for one bucket unit of fluid, following the vanilla bucket rules: a
 * block that can hold the liquid takes it in place, a replaceable block is broken with its
 * drops, water evaporates in ultra-warm dimensions, and a target that refuses the fluid falls
 * through to the neighbor along the clicked face.
 *
 * <p>Every position considered is checked against {@link Protections#mayModify}, so the neighbor
 * reached by a fall-through is authorized in its own right rather than on the strength of the
 * clicked block.
 *
 * <p>This owns the world transaction only. A {@code true} return means the world accepted one
 * unit; the caller decides whether to charge the bucket for it.
 */
public final class FluidPlacement {
    private FluidPlacement() {}

    /**
     * Places one unit of {@code fluid} at {@code pos}, or at the neighbor along the clicked face
     * when {@code pos} cannot hold it. Pass a null {@code hit} to place at {@code pos} only.
     */
    public static boolean emptyContents(Level level, @Nullable Player player, ItemStack stack, BlockPos pos,
                                        @Nullable BlockHitResult hit, Fluid fluid) {
        Direction face = hit != null ? hit.getDirection() : Direction.UP;
        return emptyContents(level, player, stack, pos, face, hit != null, fluid);
    }

    private static boolean emptyContents(Level level, @Nullable Player player, ItemStack stack, BlockPos pos,
                                         Direction face, boolean mayFallThrough, Fluid fluid) {
        if (!(fluid instanceof FlowingFluid flowing)) return false;
        if (!Protections.mayModify(level, player, pos, face, stack)) return false;

        BlockState state = level.getBlockState(pos);
        boolean replaceable = state.canBeReplaced(fluid);

        LiquidBlockContainer container = null;
        if (state.getBlock() instanceof LiquidBlockContainer lbc && lbc.canPlaceLiquid(level, pos, state, fluid)) {
            container = lbc;
        }

        if (!state.isAir() && !replaceable && container == null) {
            return mayFallThrough
                    && emptyContents(level, player, stack, pos.relative(face), face, false, fluid);
        }

        if (level.dimensionType().ultraWarm() && flowing.defaultFluidState().is(FluidTags.WATER)) {
            evaporate(level, player, pos);
            return true;
        }

        if (container != null) {
            if (!level.isClientSide) {
                container.placeLiquid(level, pos, state, flowing.getSource(false));
            }
            playEmpty(level, player, pos, fluid);
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
        playEmpty(level, player, pos, fluid);
        return true;
    }

    private static void evaporate(Level level, @Nullable Player player, BlockPos pos) {
        level.playSound(player, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
        if (level instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 8; i++) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        pos.getX() + level.random.nextDouble(),
                        pos.getY() + level.random.nextDouble(),
                        pos.getZ() + level.random.nextDouble(),
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void playEmpty(Level level, @Nullable Player player, BlockPos pos, Fluid fluid) {
        SoundEvent sound = fluid.getFluidType().getSound(SoundActions.BUCKET_EMPTY);
        if (sound == null) {
            sound = fluid.defaultFluidState().is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
        }
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
