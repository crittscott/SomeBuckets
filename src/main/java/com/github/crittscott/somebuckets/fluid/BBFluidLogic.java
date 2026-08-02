package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;

public class BBFluidLogic implements IFluidLogic {
    private static final BBFluidLogic INSTANCE = new BBFluidLogic();

    private BBFluidLogic() {}

    public static BBFluidLogic getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        BlockPos pos = hit.getBlockPos();

        // First try block entity capability
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            IFluidHandler blockHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, hit.getDirection()).orElse(null);
            if (blockHandler != null) {
                IFluidHandlerItem itemHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (itemHandler != null) {
                    return tryTransferFromBlock(level, pos, blockHandler, itemHandler, player, stack);
                }
            }
        }

        // Fall back to world fluid pickup
        BlockState state = level.getBlockState(pos);
        Fluid fluid = state.getFluidState().getType();

        // Generic source fluid detection
        if (fluid != Fluids.EMPTY && state.getFluidState().isSource()) {
            int capMb = (stack.getItem() instanceof BBItem bb) ? bb.getCapacityMb() : 2000;
            String mode = NBTUtil.getMode(stack);
            FluidStack current = NBTUtil.getFluidStack(stack);

            boolean canTake = "none".equals(mode) ||
                    ("fluid".equals(mode) && (current.isEmpty() ||
                            (current.getFluid() == fluid && current.getAmount() < capMb)));

            if (canTake) {
                if (!level.isClientSide) {
                    int newAmount = "fluid".equals(mode) ? current.getAmount() + 1000 : 1000;
                    NBTUtil.setFluidStack(stack, new FluidStack(fluid, newAmount));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    if (player != null) player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                }

                // Use appropriate sound for fluid type
                boolean isLava = fluid == Fluids.LAVA;
                level.playSound(player, pos,
                        isLava ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        if (!"fluid".equals(NBTUtil.getMode(stack))) return false;

        FluidStack fluidStack = NBTUtil.getFluidStack(stack);
        if (fluidStack.isEmpty() || fluidStack.getAmount() < 1000) return false;

        BlockPos clickedPos = hit.getBlockPos();

        // First try block entity capability
        BlockEntity blockEntity = level.getBlockEntity(clickedPos);
        if (blockEntity != null) {
            IFluidHandler blockHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, hit.getDirection()).orElse(null);
            if (blockHandler != null) {
                IFluidHandlerItem itemHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (itemHandler != null) {
                    return tryTransferToBlock(level, clickedPos, blockHandler, itemHandler, player, stack);
                }
            }
        }

        // Fall back to world placement
        return tryPlaceInWorld(level, hit, stack, player, fluidStack);
    }

    private boolean tryTransferFromBlock(Level level, BlockPos pos, IFluidHandler blockHandler,
                                         IFluidHandlerItem itemHandler, @Nullable Player player, ItemStack stack) {
        // Try to drain 1000mB from the block into our item
        FluidStack drained = blockHandler.drain(1000, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty()) return false;

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

    private boolean tryTransferToBlock(Level level, BlockPos pos, IFluidHandler blockHandler,
                                       IFluidHandlerItem itemHandler, @Nullable Player player, ItemStack stack) {
        // Try to fill 1000mB from our item into the block
        FluidStack current = itemHandler.getFluidInTank(0);
        if (current.isEmpty() || current.getAmount() < 1000) return false;

        FluidStack toTransfer = new FluidStack(current.getFluid(), 1000, current.getTag());
        int filled = blockHandler.fill(toTransfer, IFluidHandler.FluidAction.SIMULATE);
        if (filled < 1000) return false;

        if (!level.isClientSide) {
            blockHandler.fill(toTransfer, IFluidHandler.FluidAction.EXECUTE);
            itemHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            if (player != null) player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }

        boolean isLava = current.getFluid() == Fluids.LAVA;
        level.playSound(player, pos,
                isLava ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private boolean tryPlaceInWorld(Level level, BlockHitResult hit, ItemStack stack,
                                    @Nullable Player player, FluidStack fluidStack) {
        if (!FluidPlacement.emptyContents(level, player, hit.getBlockPos(), hit, fluidStack.getFluid())) return false;

        if (!level.isClientSide) {
            NBTUtil.drainFluid(stack, 1000);
            NBTUtil.normalizeEmptyState(stack);
            if (player != null) player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    @Override
    public boolean tryTakePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.POWDER_SNOW)) return false;

        int capUnits = (stack.getItem() instanceof BBItem bb) ? bb.getCapacityUnits() : 2;
        String mode = NBTUtil.getMode(stack);
        int units = NBTUtil.getPowderUnits(stack);
        boolean can = "none".equals(mode) || ("powder_snow".equals(mode) && units < capUnits);
        if (!can) return false;

        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, ("powder_snow".equals(mode) ? units : 0) + 1);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.playSound(player, pos, SoundEvents.BUCKET_FILL_POWDER_SNOW, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    @Override
    public boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        if (!"powder_snow".equals(NBTUtil.getMode(stack))) return false;
        int units = NBTUtil.getPowderUnits(stack);
        if (units <= 0) return false;

        BlockPos clickedPos = hit.getBlockPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockPos placePos = clickedState.canBeReplaced() ? clickedPos : clickedPos.relative(hit.getDirection());
        BlockState placeState = level.getBlockState(placePos);
        if (!placeState.canBeReplaced()) return false;

        if (!level.isClientSide) {
            int newUnits = units - 1;
            level.setBlock(placePos, Blocks.POWDER_SNOW.defaultBlockState(), Block.UPDATE_ALL);
            NBTUtil.setPowderUnits(stack, newUnits);
            if (newUnits <= 0) NBTUtil.normalizeEmptyState(stack);
        }
        level.playSound(player, placePos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    @Override
    public boolean releaseOneEntity(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        if (!"entity".equals(NBTUtil.getMode(stack))) return false;
        if (NBTUtil.getEntityCount(stack) <= 0) return false;

        BlockPos clickedPos = hit.getBlockPos();
        BlockState clickedState = level.getBlockState(clickedPos);

        boolean placedWater = false;
        if (clickedState.hasProperty(BlockStateProperties.WATERLOGGED) &&
                !clickedState.getValue(BlockStateProperties.WATERLOGGED)) {
            if (!level.isClientSide) level.setBlock(clickedPos, clickedState.setValue(BlockStateProperties.WATERLOGGED,
                    true), Block.UPDATE_ALL);
            placedWater = true;
        } else {
            BlockPos placePos = clickedState.canOcclude() ? clickedPos.relative(hit.getDirection()) : clickedPos;
            BlockState placeState = level.getBlockState(placePos);
            if (placeState.canBeReplaced()) {
                if (!level.isClientSide) level.setBlock(placePos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                placedWater = true;
            }
        }

        CompoundTag tag = NBTUtil.removeFirstEntitySnapshot(stack);
        String typeId = stack.getOrCreateTag().getString(NBTUtil.ENTITY_TYPE);
        EntityType<?> type = EntityType.byString(typeId).orElse(null);
        if (type == null) return false;

        if (!level.isClientSide) {
            BlockPos spawnPos = clickedPos.relative(hit.getDirection());
            Entity e = type.create(level);
            if (e == null) return false;
            e.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 0.1, spawnPos.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            if (e instanceof Bucketable b) {
                b.loadFromBucketTag(tag.copy());
                b.setFromBucket(true);
            }
            level.addFreshEntity(e);
            if (player != null) player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        level.playSound(player, clickedPos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);

        if (NBTUtil.getEntityCount(stack) == 0) {
            stack.getOrCreateTag().remove(NBTUtil.ENTITY_TYPE);
            stack.getOrCreateTag().remove(NBTUtil.ENTITIES);
            NBTUtil.setMode(stack, "none");
        }
        return true;
    }

    @Override
    public boolean tryMilkDispenser(Level level, BlockPos front, ItemStack stack) {
        // BB does not support dispenser milking
        return false;
    }
}
