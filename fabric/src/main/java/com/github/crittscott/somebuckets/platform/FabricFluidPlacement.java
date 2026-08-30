package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.BlockPos;
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
    private FabricFluidPlacement() {}

    public static BlockPos resolveTarget(Level level, BlockHitResult hit, StoredFluid stored,
                                         boolean allowFaceOffset) {
        return FluidPlacement.resolveTarget(level, null, hit.getBlockPos(), hit.getDirection(),
                allowFaceOffset, stored.fluid());
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
                && candidate.canPlaceLiquid(null, level, target, state, fluid) ? candidate : null;
        if (!state.isAir() && !state.canBeReplaced(fluid) && container == null) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, target,
                hit.getDirection(), stack, null)) return false;

        boolean evaporates = FluidPlacement.evaporatesInUltraWarm(level, fluid);
        boolean destroysBlock = container == null && !evaporates
                && !state.isAir() && state.canBeReplaced(fluid) && !state.liquid();
        if (destroysBlock
                && !Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, target,
                hit.getDirection(), stack, null)) return false;

        if (evaporates) {
            FluidPlacement.evaporate(level, null, target);
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

        FluidVariant variant = FabricFluidVariants.toVariant(fluid, stored.variantTag());
        if (!level.isClientSide) {
            level.playSound(null, target, FluidVariantAttributes.getEmptySound(variant),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        level.gameEvent(context.player(), GameEvent.FLUID_PLACE, target);
        return true;
    }
}
