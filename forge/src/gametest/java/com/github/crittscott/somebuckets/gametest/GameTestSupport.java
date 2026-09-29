package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Forge adapter for the shared GameTest fixtures, adding a sided test tank that exposes Forge's
 * block fluid capability.
 */
final class GameTestSupport extends SharedGameTestSupport {
    /** Fully namespaced empty structure used by every Forge test in this package. */
    static final String TEMPLATE = SomeBuckets.MODID + ":empty_9x6x9";

    private GameTestSupport() {}

    static SidedFluidBlockEntity fluidTank(GameTestHelper helper, BlockPos relative,
                                           Direction exposedFace, int capacity, StoredFluid contents) {
        return fluidTank(helper, relative, exposedFace, capacity, contents, Integer.MAX_VALUE);
    }

    /**
     * A sided tank whose executed fills and drains move at most {@code executeLimitMb}, while its
     * simulations promise the full amount.
     */
    static SidedFluidBlockEntity fluidTank(GameTestHelper helper, BlockPos relative, Direction exposedFace,
                                           int capacity, StoredFluid contents, int executeLimitMb) {
        helper.setBlock(relative, Blocks.STRUCTURE_BLOCK);
        BlockPos absolute = helper.absolutePos(relative);
        SidedFluidBlockEntity blockEntity = new SidedFluidBlockEntity(
                absolute, helper.getBlockState(relative), exposedFace, capacity, contents, executeLimitMb);
        helper.getLevel().setBlockEntity(blockEntity);
        check(helper.getLevel().getBlockEntity(absolute) == blockEntity,
                "Test fluid block entity was not installed");
        return blockEntity;
    }

    /** Structure-block fixture exposing one fluid tank only through {@link #exposedFace}. */
    static final class SidedFluidBlockEntity extends BlockEntity {
        private final Direction exposedFace;
        private final FluidTank tank;
        private final LazyOptional<IFluidHandler> capability;

        private SidedFluidBlockEntity(BlockPos pos, BlockState state, Direction exposedFace,
                                      int capacity, StoredFluid contents, int executeLimitMb) {
            super(BlockEntityType.STRUCTURE_BLOCK, pos, state);
            this.exposedFace = exposedFace;
            this.tank = new FluidTank(capacity) {
                @Override
                public int fill(FluidStack resource, FluidAction action) {
                    return super.fill(action.execute() && resource.getAmount() > executeLimitMb
                            ? ForgeFluidStacks.resized(resource, executeLimitMb) : resource, action);
                }

                @Override
                public FluidStack drain(int maxDrain, FluidAction action) {
                    return super.drain(action.execute() ? Math.min(maxDrain, executeLimitMb) : maxDrain, action);
                }
            };
            this.tank.setFluid(ForgeFluidStacks.of(contents));
            this.capability = LazyOptional.of(() -> tank);
        }

        StoredFluid contents() {
            return ForgeFluidStacks.stored(tank.getFluid());
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
