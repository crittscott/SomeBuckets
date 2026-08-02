package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.item.SBItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class Dispensers extends DefaultDispenseItemBehavior {

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        Level level = source.getLevel();
        Direction dir = source.getBlockState().getValue(DispenserBlock.FACING);
        BlockPos pos = source.getPos().relative(dir);

        /* ------------------------------ Mob Bucket ------------------------------ */
        if (stack.getItem() instanceof MBItem) {
            return handleMobBucket(level, pos, stack);
        }

        /* ------------------------------ Source Bucket ------------------------------ */
        if (stack.getItem() instanceof SBItem) {
            String mode = NBTUtil.getMode(stack);
            BlockState frontState = level.getBlockState(pos);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), dir, pos, false);

            // Handle cauldron interactions directly
            if ("fluid".equals(mode)) {
                FluidStack fluidStack = NBTUtil.getFluidStack(stack);
                if (!fluidStack.isEmpty()) {
                    // Empty cauldron - fill it
                    if (frontState.is(Blocks.CAULDRON)) {
                        if (SBFluidLogic.getInstance().tryPlace(level, hit, stack, null)) return stack;
                    }
                    // Full cauldron of same type - empty it (vanilla behavior)
                    else if (fluidStack.getFluid() == Fluids.WATER && frontState.is(Blocks.WATER_CAULDRON) &&
                            frontState.hasProperty(LayeredCauldronBlock.LEVEL) &&
                            frontState.getValue(LayeredCauldronBlock.LEVEL) == 3) {
                        if (!level.isClientSide) {
                            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                        }
                        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return stack;
                    }
                    else if (fluidStack.getFluid() == Fluids.LAVA && frontState.is(Blocks.LAVA_CAULDRON)) {
                        if (!level.isClientSide) {
                            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                        }
                        level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return stack;
                    }
                    // Other cauldron types or world placement
                    else {
                        if (SBFluidLogic.getInstance().tryPlace(level, hit, stack, null)) return stack;
                    }
                }
            } else if ("none".equals(mode)) {
                if (frontState.is(Blocks.WATER_CAULDRON) || frontState.is(Blocks.LAVA_CAULDRON)) {
                    if (SBFluidLogic.getInstance().tryTake(level, hit, stack, null)) return stack;
                }
            }

            // World fluids / milking
            if ("none".equals(mode)) {
                if (SBFluidLogic.getInstance().tryMilkDispenser(level, pos, stack)) return stack;
                SBFluidLogic.getInstance().tryTake(level, hit, stack, null);
                return stack;
            } else if ("fluid".equals(mode)) {
                SBFluidLogic.getInstance().tryPlace(level, hit, stack, null);
                return stack;
            } else { // milk: no place action
                return stack;
            }
        }

        /* ------------------------------ Big Bucket (with cauldron fixes) ------------------------------ */
        BlockState frontState = level.getBlockState(pos);
        String mode = NBTUtil.getMode(stack);
        int capMb = (stack.getItem() instanceof BBItem bb) ? bb.getCapacityMb() : 2000;
        FluidStack currentFluid = NBTUtil.getFluidStack(stack);
        int amt = currentFluid.getAmount();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), dir, pos, false);

        // Handle powder_snow mode
        if ("powder_snow".equals(mode)) {
            if (BBFluidLogic.getInstance().tryPlacePowder(level, hit, stack, null)) return stack;
        }

        // Handle cauldron interactions - simple direct logic
        if (frontState.is(Blocks.CAULDRON) && "fluid".equals(mode) && amt >= 1000) {
            // Empty cauldron - deposit fluid
            if (currentFluid.getFluid() == Fluids.WATER) {
                level.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 3);
                NBTUtil.drainFluid(stack, 1000);
                NBTUtil.normalizeEmptyState(stack);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                return stack;
            } else if (currentFluid.getFluid() == Fluids.LAVA) {
                level.setBlock(pos, Blocks.LAVA_CAULDRON.defaultBlockState(), 3);
                NBTUtil.drainFluid(stack, 1000);
                NBTUtil.normalizeEmptyState(stack);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                return stack;
            }
        } else if (frontState.is(Blocks.WATER_CAULDRON) && frontState.getValue(LayeredCauldronBlock.LEVEL) == 3) {
            // Full water cauldron - take water if we can
            if ("none".equals(mode) || ("fluid".equals(mode) &&
                    currentFluid.getFluid() == Fluids.WATER && amt + 1000 <= capMb)) {
                int newAmount = "fluid".equals(mode) ? amt + 1000 : 1000;
                NBTUtil.setFluidStack(stack, new FluidStack(Fluids.WATER, newAmount));
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                return stack;
            }
        } else if (frontState.is(Blocks.LAVA_CAULDRON)) {
            // Lava cauldron - take lava if we can
            if ("none".equals(mode) || ("fluid".equals(mode) &&
                    currentFluid.getFluid() == Fluids.LAVA && amt + 1000 <= capMb)) {
                int newAmount = "fluid".equals(mode) ? amt + 1000 : 1000;
                NBTUtil.setFluidStack(stack, new FluidStack(Fluids.LAVA, newAmount));
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                return stack;
            }
        } else if (frontState.is(Blocks.POWDER_SNOW_CAULDRON) && frontState.getValue(LayeredCauldronBlock.LEVEL) == 3) {
            // Full powder snow cauldron - take powder snow if we can
            int units = NBTUtil.getPowderUnits(stack);
            int capUnits = (stack.getItem() instanceof BBItem bb) ? bb.getCapacityUnits() : 2;
            if ("none".equals(mode) || ("powder_snow".equals(mode) && units < capUnits)) {
                NBTUtil.setPowderUnits(stack, ("powder_snow".equals(mode) ? units : 0) + 1);
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL_POWDER_SNOW, SoundSource.BLOCKS, 1.0F, 1.0F);
                return stack;
            }
        }

        // If no cauldron interaction occurred, fall back to world interactions
        if ("none".equals(mode) || ("fluid".equals(mode) && amt < capMb)) {
            if (BBFluidLogic.getInstance().tryTake(level, hit, stack, null)) return stack;
            if (BBFluidLogic.getInstance().tryTakePowder(level, hit, stack, null)) return stack;
        }
        if (amt >= 1000) {
            if (BBFluidLogic.getInstance().tryPlace(level, hit, stack, null)) return stack;
        }
        return stack;
    }

    private ItemStack handleMobBucket(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide) return stack;

        AABB searchBox = new AABB(pos);
        List<Mob> occupyingMobs = level.getEntitiesOfClass(Mob.class, searchBox, mob -> !mob.isRemoved());
        List<Mob> captureCandidates = occupyingMobs.stream()
                .filter(MBItem::canCapture)
                .filter(mob -> NBTUtil.canAcceptEntity(stack, mob.getType()))
                .toList();

        if (!captureCandidates.isEmpty()) {
            Mob target = captureCandidates.get(level.random.nextInt(captureCandidates.size()));
            if (MBItem.capture(stack, target)) {
                level.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                        SoundEvents.SLIME_ATTACK, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return stack;
        }

        if (!occupyingMobs.isEmpty()) return stack;

        if (NBTUtil.getEntityCount(stack) > 0 && MBItem.releaseOldest(level, pos, stack)) {
            level.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                    SoundEvents.SLIME_JUMP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return stack;
    }
}
