package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.register.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class GameTestSupport extends SharedGameTestSupport {
    static final String TEMPLATE = "empty_9x6x9";

    private GameTestSupport() {}

    static ItemStack big8() {
        return new ItemStack(ModItems.BIG_BUCKET_8.get());
    }

    static ItemStack big64() {
        return new ItemStack(ModItems.BIG_BUCKET_64.get());
    }

    static ItemStack source() {
        return new ItemStack(ModItems.SOURCE_BUCKET.get());
    }

    static ItemStack junk() {
        return new ItemStack(ModItems.JUNK_BUCKET.get());
    }

    static ItemStack trash() {
        return new ItemStack(ModItems.TRASH_BUCKET.get());
    }

    static ItemStack mob() {
        return new ItemStack(ModItems.MOB_BUCKET.get());
    }

    static boolean tryBigTakeWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                         ProtectionContext context) {
        return BBFluidLogic.getInstance().tryTakeWithContext(level, hit, stack, context);
    }

    static boolean tryBigPlaceWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                          ProtectionContext context, boolean allowFaceOffset) {
        return BBFluidLogic.getInstance().tryPlace(level, hit, stack, context, allowFaceOffset);
    }

    static boolean tryPowderTakeWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        return BBFluidLogic.getInstance().tryTakePowderWithContext(level, hit, stack, context);
    }

    static boolean tryPowderPlaceWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context, boolean allowFaceOffset) {
        return BBFluidLogic.getInstance().tryPlacePowder(
                level, hit, stack, context, allowFaceOffset);
    }

    static boolean trySourceTakeWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        return SBFluidLogic.getInstance().tryTakeWithContext(level, hit, stack, context);
    }

    static boolean trySourcePlaceWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context, boolean allowFaceOffset) {
        return SBFluidLogic.getInstance().tryPlace(level, hit, stack, context, allowFaceOffset);
    }

    static SidedFluidBlockEntity fluidTank(GameTestHelper helper, BlockPos relative,
                                           Direction exposedFace, int capacity, FluidStack contents) {
        helper.setBlock(relative, Blocks.STRUCTURE_BLOCK);
        BlockPos absolute = helper.absolutePos(relative);
        SidedFluidBlockEntity blockEntity = new SidedFluidBlockEntity(
                absolute, helper.getBlockState(relative), exposedFace, capacity, contents);
        helper.getLevel().setBlockEntity(blockEntity);
        check(helper.getLevel().getBlockEntity(absolute) == blockEntity,
                "Test fluid block entity was not installed");
        return blockEntity;
    }

    static final class SidedFluidBlockEntity extends BlockEntity {
        private final Direction exposedFace;
        private final FluidTank tank;
        private final LazyOptional<IFluidHandler> capability;

        private SidedFluidBlockEntity(BlockPos pos, BlockState state, Direction exposedFace,
                                      int capacity, FluidStack contents) {
            super(BlockEntityType.STRUCTURE_BLOCK, pos, state);
            this.exposedFace = exposedFace;
            this.tank = new FluidTank(capacity);
            this.tank.setFluid(contents.copy());
            this.capability = LazyOptional.of(() -> tank);
        }

        FluidStack contents() {
            return tank.getFluid().copy();
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> requested,
                                                 @Nullable Direction side) {
            if (requested == ForgeCapabilities.FLUID_HANDLER && side == exposedFace) {
                return capability.cast();
            }
            return super.getCapability(requested, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            capability.invalidate();
        }
    }
}
