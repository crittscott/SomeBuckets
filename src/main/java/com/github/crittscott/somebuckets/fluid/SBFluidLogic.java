package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.List;

public class SBFluidLogic implements IFluidLogic {
    private static final SBFluidLogic INSTANCE = new SBFluidLogic();

    private SBFluidLogic() {}

    public static SBFluidLogic getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) return false;

        BlockPos pos = hit.getBlockPos();
        if (!Protections.mayModify(level, player, pos, hit.getDirection(), stack)) return false;

        // First try block entity capability
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            IFluidHandler blockHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, hit.getDirection()).orElse(null);
            if (blockHandler != null) {
                IFluidHandlerItem itemHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (itemHandler != null) {
                    return tryTakeFromBlock(level, pos, blockHandler, itemHandler, player, stack);
                }
            }
        }

        // Fall back to cauldron and world interactions
        BlockState state = level.getBlockState(pos);

        // Full water cauldron -> empty cauldron, SB becomes water
        if (state.is(Blocks.WATER_CAULDRON) && state.hasProperty(LayeredCauldronBlock.LEVEL)
                && state.getValue(LayeredCauldronBlock.LEVEL) == 3) {
            if (!level.isClientSide) {
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                NBTUtil.setFluidStack(stack, new FluidStack(Fluids.WATER, 1000));
                if (player != null) player.awardStat(Stats.USE_CAULDRON);
            }
            level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }

        // Lava cauldron -> empty cauldron, SB becomes lava
        if (state.is(Blocks.LAVA_CAULDRON)) {
            if (!level.isClientSide) {
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                NBTUtil.setFluidStack(stack, new FluidStack(Fluids.LAVA, 1000));
                if (player != null) player.awardStat(Stats.USE_CAULDRON);
            }
            level.playSound(player, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }

        // Generic world fluid source blocks
        FluidState fs = level.getFluidState(pos);
        Fluid fluid = fs.getType();

        if (fluid != Fluids.EMPTY && fs.isSource()) {
            if (!level.isClientSide) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                NBTUtil.setFluidStack(stack, new FluidStack(fluid, 1000));
                if (player != null) player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            }

            boolean isLava = fluid == Fluids.LAVA;
            level.playSound(player, pos,
                    isLava ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }

        return false;
    }

    @Override
    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.FLUID) return false;

        FluidStack fluidStack = NBTUtil.getFluidStack(stack);
        if (fluidStack.isEmpty()) return false;

        BlockPos clicked = hit.getBlockPos();
        if (!Protections.mayModify(level, player, clicked, hit.getDirection(), stack)) return false;

        // First try block entity capability
        BlockEntity blockEntity = level.getBlockEntity(clicked);
        if (blockEntity != null) {
            IFluidHandler blockHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, hit.getDirection()).orElse(null);
            if (blockHandler != null) {
                IFluidHandlerItem itemHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (itemHandler != null) {
                    return tryPlaceToBlock(level, clicked, blockHandler, itemHandler, player, stack, fluidStack);
                }
            }
        }

        // Fall back to cauldron and world placement
        return tryPlaceInWorld(level, hit, stack, player, fluidStack);
    }

    private boolean tryTakeFromBlock(Level level, BlockPos pos, IFluidHandler blockHandler,
                                     IFluidHandlerItem itemHandler, @Nullable Player player, ItemStack stack) {
        // Try to drain 1000mB from block
        FluidStack drained = blockHandler.drain(1000, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty() || drained.getAmount() < 1000) return false;

        int filled = itemHandler.fill(drained, IFluidHandler.FluidAction.SIMULATE);
        if (filled < 1000) return false;

        if (!level.isClientSide) {
            blockHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            itemHandler.fill(new FluidStack(drained.getFluid(), 1000, drained.getTag()), IFluidHandler.FluidAction.EXECUTE);
            if (player != null) player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }

        boolean isLava = drained.getFluid() == Fluids.LAVA;
        level.playSound(player, pos,
                isLava ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private boolean tryPlaceToBlock(Level level, BlockPos pos, IFluidHandler blockHandler,
                                    IFluidHandlerItem itemHandler, @Nullable Player player,
                                    ItemStack stack, FluidStack fluidStack) {
        // SB is infinite source, so always try to fill 1000mB
        FluidStack toTransfer = new FluidStack(fluidStack.getFluid(), 1000, fluidStack.getTag());
        int filled = blockHandler.fill(toTransfer, IFluidHandler.FluidAction.SIMULATE);
        if (filled < 1000) return false;

        if (!level.isClientSide) {
            blockHandler.fill(toTransfer, IFluidHandler.FluidAction.EXECUTE);
            if (player != null) player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }

        boolean isLava = fluidStack.getFluid() == Fluids.LAVA;
        level.playSound(player, pos,
                isLava ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private boolean tryPlaceInWorld(Level level, BlockHitResult hit, ItemStack stack,
                                    @Nullable Player player, FluidStack fluidStack) {
        BlockPos clicked = hit.getBlockPos();
        BlockState clickedState = level.getBlockState(clicked);
        Fluid fluid = fluidStack.getFluid();

        // Cauldron interactions: fill empty cauldron if this fluid has a cauldron block
        if (clickedState.is(Blocks.CAULDRON)) {
            if (fluid == Fluids.WATER) {
                if (!level.isClientSide) {
                    level.setBlock(clicked, Blocks.WATER_CAULDRON.defaultBlockState()
                            .setValue(LayeredCauldronBlock.LEVEL, 3), 3);
                    if (player != null) player.awardStat(Stats.USE_CAULDRON);
                }
                level.playSound(player, clicked, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                return true;
            } else if (fluid == Fluids.LAVA) {
                if (!level.isClientSide) {
                    level.setBlock(clicked, Blocks.LAVA_CAULDRON.defaultBlockState(), 3);
                    if (player != null) player.awardStat(Stats.USE_CAULDRON);
                }
                level.playSound(player, clicked, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                return true;
            }
        }

        // Check if trying to fill already-full cauldron of same type - do nothing
        if (fluid == Fluids.WATER && clickedState.is(Blocks.WATER_CAULDRON) &&
                clickedState.hasProperty(LayeredCauldronBlock.LEVEL) &&
                clickedState.getValue(LayeredCauldronBlock.LEVEL) == 3) {
            return false;
        }
        if (fluid == Fluids.LAVA && clickedState.is(Blocks.LAVA_CAULDRON)) {
            return false;
        }

        // World placement; the Source Bucket is infinite, so nothing is drained
        if (!FluidPlacement.emptyContents(level, player, stack, clicked, hit, fluid)) return false;

        if (!level.isClientSide && player != null) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    @Override
    public boolean tryTakePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        // SB does not support powder snow
        return false;
    }

    @Override
    public boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        // SB does not support powder snow
        return false;
    }

    @Override
    public boolean tryMilkDispenser(Level level, BlockPos front, ItemStack stack) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) return false;
        AABB box = new AABB(front);
        List<Cow> cows = level.getEntitiesOfClass(Cow.class, box, cow -> !cow.isBaby());
        if (cows.isEmpty()) return false;

        if (!level.isClientSide) {
            NBTUtil.setMilkAmount(stack, 1000);
        }
        level.playSound(null, front, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }
}
