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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * NeoForge counterpart of the Forge {@code GameTestSupport}. The vanilla and loader-neutral helpers
 * come from {@link SharedGameTestSupport}; this class adds NeoForge item accessors, the common
 * fluid-logic context entry points, and a NeoForge {@link Capabilities.FluidHandler#BLOCK}-backed
 * sided tank fixture.
 */
final class GameTestSupport extends SharedGameTestSupport {
    /**
     * Bare structure name. The NeoForge GameTest registry prefixes this with the
     * {@code @GameTestHolder} namespace (and, unless {@code @PrefixGameTestTemplate(false)} is
     * present, the class name), so the effective template is {@code somebuckets:empty_9x6x9}.
     */
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
        return BBFluidLogic.tryTakeWithContext(level, hit, stack, context);
    }

    static boolean tryBigPlaceWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                          ProtectionContext context, boolean allowFaceOffset) {
        return BBFluidLogic.tryPlace(level, hit, stack, context, allowFaceOffset);
    }

    static boolean tryPowderTakeWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        return BBFluidLogic.tryTakePowderWithContext(level, hit, stack, context);
    }

    static boolean tryPowderPlaceWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context, boolean allowFaceOffset) {
        return BBFluidLogic.tryPlacePowder(
                level, hit, stack, context, allowFaceOffset);
    }

    static boolean trySourceTakeWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        return SBFluidLogic.tryTakeWithContext(level, hit, stack, context);
    }

    static boolean trySourcePlaceWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context, boolean allowFaceOffset) {
        return SBFluidLogic.tryPlace(level, hit, stack, context, allowFaceOffset);
    }

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
                                      int capacity, FluidStack contents) {
            super(BlockEntityType.STRUCTURE_BLOCK, pos, state);
            this.exposedFace = exposedFace;
            this.tank = new FluidTank(capacity);
            this.tank.setFluid(contents.copy());
            this.handler = tank;
        }

        FluidStack contents() {
            return tank.getFluid().copy();
        }
    }
}
