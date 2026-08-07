package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.config.SourceBucketPolicy;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import com.github.crittscott.somebuckets.item.SBItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
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
        // The target's face adjacent to the dispenser is the opposite of the direction the dispenser fires in.
        Direction hitFace = dir.getOpposite();
        ProtectionContext protectionContext = ProtectionContext.dispenser(source.getPos());

        /* ------------------------------ Mob Bucket ------------------------------ */
        if (stack.getItem() instanceof MBItem) {
            return handleMobBucket(level, pos, stack, protectionContext);
        }

        /* ------------------------------ Source Bucket ------------------------------ */
        if (stack.getItem() instanceof SBItem) {
            NBTUtil.Mode mode = NBTUtil.getMode(stack);
            BlockState frontState = level.getBlockState(pos);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), hitFace, pos, false);

            if (mode == NBTUtil.Mode.FLUID) {
                FluidStack fluidStack = NBTUtil.getFluidStack(stack);
                if (!SourceBucketPolicy.allows(fluidStack)) return stack;
                if (!fluidStack.isEmpty()) {
                    // Full cauldron of same type - empty it (vanilla behavior)
                    if (fluidStack.getFluid() == Fluids.WATER && frontState.is(Blocks.WATER_CAULDRON) &&
                            frontState.hasProperty(LayeredCauldronBlock.LEVEL) &&
                            frontState.getValue(LayeredCauldronBlock.LEVEL) == 3) {
                        if (!mayInteract(level, protectionContext, pos, hitFace, stack)) return stack;
                        if (!level.isClientSide) {
                            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                        }
                        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return stack;
                    }
                    if (fluidStack.getFluid() == Fluids.LAVA && frontState.is(Blocks.LAVA_CAULDRON)) {
                        if (!mayInteract(level, protectionContext, pos, hitFace, stack)) return stack;
                        if (!level.isClientSide) {
                            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                        }
                        level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return stack;
                    }
                }
                SBFluidLogic.getInstance().tryPlace(level, hit, stack, protectionContext, false);
                return stack;
            }
            if (mode == NBTUtil.Mode.NONE) {
                if (SBFluidLogic.getInstance().tryMilkDispenser(level, pos, stack, protectionContext)) return stack;
                SBFluidLogic.getInstance().tryTakeWithContext(level, hit, stack, protectionContext);
                return stack;
            }
            return stack; // Milk has no dispenser place action.
        }

        /* ------------------------------ Big Bucket (with cauldron fixes) ------------------------------ */
        BlockState frontState = level.getBlockState(pos);
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int capMb = ((BBItem) stack.getItem()).getCapacityMb();
        FluidStack currentFluid = NBTUtil.getFluidStack(stack);
        int amt = currentFluid.getAmount();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), hitFace, pos, false);

        // Handle powder_snow mode
        if (mode == NBTUtil.Mode.POWDER_SNOW) {
            if (BBFluidLogic.getInstance().tryPlacePowder(level, hit, stack, protectionContext, false)) return stack;
        }

        // Handle cauldron interactions - simple direct logic
        if (frontState.is(Blocks.CAULDRON) && mode == NBTUtil.Mode.FLUID && amt >= 1000) {
            // Empty cauldron - deposit fluid
            if (currentFluid.getFluid() == Fluids.WATER) {
                if (!mayInteract(level, protectionContext, pos, hitFace, stack)) return stack;
                level.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 3);
                NBTUtil.drainFluid(stack, 1000);
                NBTUtil.normalizeEmptyState(stack);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                return stack;
            } else if (currentFluid.getFluid() == Fluids.LAVA) {
                if (!mayInteract(level, protectionContext, pos, hitFace, stack)) return stack;
                level.setBlock(pos, Blocks.LAVA_CAULDRON.defaultBlockState(), 3);
                NBTUtil.drainFluid(stack, 1000);
                NBTUtil.normalizeEmptyState(stack);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                return stack;
            }
        } else if (frontState.is(Blocks.WATER_CAULDRON) && frontState.getValue(LayeredCauldronBlock.LEVEL) == 3) {
            // Full water cauldron - take water if we can
            if (mode == NBTUtil.Mode.NONE || (mode == NBTUtil.Mode.FLUID &&
                    currentFluid.getFluid() == Fluids.WATER && amt + 1000 <= capMb)) {
                if (!mayInteract(level, protectionContext, pos, hitFace, stack)) return stack;
                int newAmount = mode == NBTUtil.Mode.FLUID ? amt + 1000 : 1000;
                NBTUtil.setFluidStack(stack, new FluidStack(Fluids.WATER, newAmount));
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                return stack;
            }
        } else if (frontState.is(Blocks.LAVA_CAULDRON)) {
            // Lava cauldron - take lava if we can
            if (mode == NBTUtil.Mode.NONE || (mode == NBTUtil.Mode.FLUID &&
                    currentFluid.getFluid() == Fluids.LAVA && amt + 1000 <= capMb)) {
                if (!mayInteract(level, protectionContext, pos, hitFace, stack)) return stack;
                int newAmount = mode == NBTUtil.Mode.FLUID ? amt + 1000 : 1000;
                NBTUtil.setFluidStack(stack, new FluidStack(Fluids.LAVA, newAmount));
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                return stack;
            }
        } else if (frontState.is(Blocks.POWDER_SNOW_CAULDRON) && frontState.getValue(LayeredCauldronBlock.LEVEL) == 3) {
            // Full powder snow cauldron - take powder snow if we can
            int units = NBTUtil.getPowderUnits(stack);
            int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();
            if (mode == NBTUtil.Mode.NONE || (mode == NBTUtil.Mode.POWDER_SNOW && units < capUnits)) {
                if (!mayInteract(level, protectionContext, pos, hitFace, stack)) return stack;
                NBTUtil.setPowderUnits(stack, (mode == NBTUtil.Mode.POWDER_SNOW ? units : 0) + 1);
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL_POWDER_SNOW, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                return stack;
            }
        }

        // If no cauldron interaction occurred, fall back to world interactions
        if (mode == NBTUtil.Mode.NONE || (mode == NBTUtil.Mode.FLUID && amt < capMb)) {
            if (BBFluidLogic.getInstance().tryTakeWithContext(level, hit, stack, protectionContext)) return stack;
            if (BBFluidLogic.getInstance().tryTakePowderWithContext(level, hit, stack, protectionContext)) return stack;
        }
        if (amt >= 1000) {
            if (BBFluidLogic.getInstance().tryPlace(level, hit, stack, protectionContext, false)) return stack;
        }
        return stack;
    }

    private ItemStack handleMobBucket(Level level, BlockPos pos, ItemStack stack,
                                     ProtectionContext protectionContext) {
        if (level.isClientSide) return stack;

        AABB searchBox = new AABB(pos);
        List<Mob> occupyingMobs = level.getEntitiesOfClass(Mob.class, searchBox, mob -> !mob.isRemoved());
        List<Mob> captureCandidates = occupyingMobs.stream()
                .filter(MBItem::canCapture)
                .filter(mob -> NBTUtil.canAcceptEntity(stack, mob.getType()))
                .toList();

        if (!captureCandidates.isEmpty()) {
            Mob target = captureCandidates.get(level.random.nextInt(captureCandidates.size()));
            if (MBItem.capture(stack, target, protectionContext)) {
                level.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                        SoundEvents.SLIME_ATTACK, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return stack;
        }

        if (!occupyingMobs.isEmpty()) return stack;

        if (NBTUtil.getEntityCount(stack) > 0
                && MBItem.releaseOldest((ServerLevel) level, pos, stack, protectionContext, Direction.UP)) {
            level.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                    SoundEvents.SLIME_JUMP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return stack;
    }

    private static boolean mayInteract(Level level, ProtectionContext context, BlockPos pos,
                                       Direction face, ItemStack stack) {
        return Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT,
                pos, face, stack, null);
    }
}
