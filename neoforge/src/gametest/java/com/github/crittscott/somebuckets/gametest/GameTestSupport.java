package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * NeoForge counterpart of the Forge {@code GameTestSupport}. The vanilla and loader-neutral helpers
 * come from {@link SharedGameTestSupport}; this class adds a NeoForge
 * {@link Capabilities.FluidHandler#BLOCK}-backed sided tank fixture.
 */
final class GameTestSupport extends SharedGameTestSupport {
    /**
     * Bare structure name. The NeoForge GameTest registry prefixes this with the
     * {@code @GameTestHolder} namespace (and, unless {@code @PrefixGameTestTemplate(false)} is
     * present, the class name), so the effective template is {@code somebuckets:empty_9x6x9}.
     */
    static final String TEMPLATE = "empty_9x6x9";

    private GameTestSupport() {}

    /**
     * Attaches the sided-tank fixture's fluid handler to every {@link Blocks#STRUCTURE_BLOCK}
     * position; the provider returns {@code null} unless a {@link SidedFluidBlockEntity} is installed
     * there and the queried side matches its exposed face. Wired from {@link SomeBucketsGameTestMod}.
     */
    static void registerTestCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) ->
                        blockEntity instanceof SidedFluidBlockEntity sided && side == sided.exposedFace
                                ? sided.handler
                                : null,
                Blocks.STRUCTURE_BLOCK);
    }

    static SidedFluidBlockEntity fluidTank(GameTestHelper helper, BlockPos relative,
                                           Direction exposedFace, int capacity, StoredFluid contents) {
        helper.setBlock(relative, Blocks.STRUCTURE_BLOCK);
        BlockPos absolute = helper.absolutePos(relative);
        SidedFluidBlockEntity blockEntity = new SidedFluidBlockEntity(
                absolute, helper.getBlockState(relative), exposedFace, capacity, contents);
        helper.getLevel().setBlockEntity(blockEntity);
        check(helper.getLevel().getBlockEntity(absolute) == blockEntity,
                "Test fluid block entity was not installed");
        return blockEntity;
    }

    /**
     * A structure-block-typed block entity exposing a single {@link FluidTank} through one face. The
     * NeoForge fluid-handler block capability is bound to it by {@link #registerTestCapabilities}
     * rather than a per-instance override.
     */
    static final class SidedFluidBlockEntity extends BlockEntity {
        final Direction exposedFace;
        final IFluidHandler handler;
        private final FluidTank tank;

        private SidedFluidBlockEntity(BlockPos pos, BlockState state, Direction exposedFace,
                                      int capacity, StoredFluid contents) {
            super(BlockEntityType.STRUCTURE_BLOCK, pos, state);
            this.exposedFace = exposedFace;
            this.tank = new FluidTank(capacity);
            this.tank.setFluid(NeoForgeFluidStacks.of(contents));
            this.handler = tank;
        }

        StoredFluid contents() {
            return NeoForgeFluidStacks.stored(tank.getFluid());
        }
    }
}
