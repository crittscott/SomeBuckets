package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

/** Fabric-native arbitrary-fluid world placement and variant-aware bucket sounds. */
public final class FabricFluidPlacement {
    private static final int EVAPORATION_PARTICLE_COUNT = 8;

    private FabricFluidPlacement() {}

    public static BlockPos resolveTarget(Level level, BlockHitResult hit, StoredFluid stored,
                                         boolean allowFaceOffset) {
        BlockPos clicked = hit.getBlockPos();
        if (canPlaceAt(level, clicked, stored.fluid()) || !allowFaceOffset) return clicked;
        BlockPos neighbor = clicked.relative(hit.getDirection());
        return canPlaceAt(level, neighbor, stored.fluid()) ? neighbor : clicked;
    }

    public static boolean place(Level level, BlockHitResult hit, ItemStack stack,
                                ProtectionContext context, StoredFluid stored,
                                boolean allowFaceOffset) {
        Fluid fluid = stored.fluid();
        if (!(fluid instanceof FlowingFluid flowing)
                || fluid.defaultFluidState().createLegacyBlock().isAir()) return false;

        BlockPos target = resolveTarget(level, hit, stored, allowFaceOffset);
        BlockState state = level.getBlockState(target);
        LiquidBlockContainer container = state.getBlock() instanceof LiquidBlockContainer candidate
                && candidate.canPlaceLiquid(level, target, state, fluid) ? candidate : null;
        if (!state.isAir() && !state.canBeReplaced(fluid) && container == null) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, target,
                hit.getDirection(), stack, null)) return false;

        if (FluidPlacement.evaporatesInUltraWarm(level, fluid)) {
            evaporate(level, target);
            return true;
        }

        if (container != null) {
            if (!level.isClientSide) {
                container.placeLiquid(level, target, state, flowing.getSource(false));
            }
        } else if (!level.isClientSide) {
            if (state.canBeReplaced(fluid) && !state.liquid()) level.destroyBlock(target, true);
            if (!level.setBlock(target, fluid.defaultFluidState().createLegacyBlock(),
                    Block.UPDATE_ALL_IMMEDIATE) && !state.getFluidState().isSource()) return false;
        }

        FluidVariant variant = FluidVariant.of(fluid, stored.variantTag());
        if (!level.isClientSide) {
            level.playSound(null, target, FluidVariantAttributes.getEmptySound(variant),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        level.gameEvent(context.player(), GameEvent.FLUID_PLACE, target);
        return true;
    }

    private static boolean canPlaceAt(Level level, BlockPos pos, Fluid fluid) {
        if (!(fluid instanceof FlowingFluid)
                || fluid.defaultFluidState().createLegacyBlock().isAir()) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced(fluid)
                || state.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(level, pos, state, fluid);
    }

    private static void evaporate(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                    FluidPlacement.hissPitch(level.random));
        }
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
}
