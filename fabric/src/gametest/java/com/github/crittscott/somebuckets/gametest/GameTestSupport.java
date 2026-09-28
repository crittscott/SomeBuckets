package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric adapter for shared GameTest fixtures, adding sided Transfer API storage used by the common
 * block-capability scenarios.
 */
final class GameTestSupport extends SharedGameTestSupport {
    static final String TEMPLATE = "somebuckets:empty_9x6x9";
    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / FluidBucketItem.BUCKET_VOLUME_MB;

    private GameTestSupport() {}

    static SidedFluidBlockEntity fluidTank(GameTestHelper helper, BlockPos relative,
                                           Direction exposedFace, int capacityMb, StoredFluid contents) {
        helper.setBlock(relative, Blocks.STRUCTURE_BLOCK);
        BlockPos absolute = helper.absolutePos(relative);
        SidedFluidBlockEntity blockEntity = new SidedFluidBlockEntity(
                absolute, helper.getBlockState(relative), exposedFace, capacityMb, contents);
        helper.getLevel().setBlockEntity(blockEntity);
        check(helper.getLevel().getBlockEntity(absolute) == blockEntity,
                "Test fluid block entity was not installed");
        return blockEntity;
    }

    /**
     * A single-slot container holding one bucket stack, standing in for a player inventory slot so a
     * bare {@link ItemStack} can expose its {@code FluidStorage.ITEM} the way production code only ever
     * does through a real container context.
     */
    static SimpleContainer containerOf(ItemStack stack) {
        return new SimpleContainer(stack);
    }

    static Storage<FluidVariant> fluidStorage(SimpleContainer container) {
        ContainerItemContext context = ContainerItemContext.ofSingleSlot(
                InventoryStorage.of(container, null).getSlot(0));
        return FluidStorage.ITEM.find(container.getItem(0), context);
    }

    static long insert(Storage<FluidVariant> storage, FluidVariant variant, long amountDroplets, boolean execute) {
        try (Transaction transaction = Transaction.openOuter()) {
            long moved = storage.insert(variant, amountDroplets, transaction);
            if (execute) transaction.commit();
            return moved;
        }
    }

    static long extract(Storage<FluidVariant> storage, FluidVariant variant,
                        long amountDroplets, boolean execute) {
        try (Transaction transaction = Transaction.openOuter()) {
            long moved = storage.extract(variant, amountDroplets, transaction);
            if (execute) transaction.commit();
            return moved;
        }
    }

    static boolean isEmpty(Storage<FluidVariant> storage) {
        for (var view : storage) {
            if (!view.isResourceBlank() && view.getAmount() > 0) return false;
        }
        return true;
    }

    static final class SidedFluidBlockEntity extends BlockEntity {
        private static volatile boolean registered = false;

        private final Direction exposedFace;
        private final SingleFluidStorage storage;

        private SidedFluidBlockEntity(BlockPos pos, BlockState state, Direction exposedFace,
                                      int capacityMb, StoredFluid contents) {
            super(BlockEntityType.STRUCTURE_BLOCK, pos, state);
            ensureRegistered();
            this.exposedFace = exposedFace;
            long capacityDroplets = (long) capacityMb * DROPLETS_PER_MB;
            this.storage = SingleFluidStorage.withFixedCapacity(capacityDroplets, () -> {});
            if (!contents.isEmpty()) {
                FluidVariant variant = FabricFluidVariants.toVariant(contents);
                long amountDroplets = (long) contents.amount() * DROPLETS_PER_MB;
                try (Transaction transaction = Transaction.openOuter()) {
                    storage.insert(variant, amountDroplets, transaction);
                    transaction.commit();
                }
            }
        }

        StoredFluid contents() {
            FluidVariant variant = storage.getResource();
            if (variant.isBlank() || storage.getAmount() == 0) return StoredFluid.EMPTY;
            int amountMb = (int) (storage.getAmount() / DROPLETS_PER_MB);
            return new StoredFluid(variant.getFluid(), amountMb, variant.getComponents());
        }

        /**
         * {@code registerForBlockEntity} binds its provider's parameter type to the passed
         * {@link BlockEntityType}'s own declared Java type ({@code StructureBlockEntity} for
         * {@link BlockEntityType#STRUCTURE_BLOCK}), which this fixture doesn't extend. A fallback
         * provider takes a plain {@link BlockEntity} instead, matching how Fabric API itself wires
         * {@code SidedStorageBlockEntity} support in {@code FluidStorage}'s own static initializer.
         */
        private static synchronized void ensureRegistered() {
            if (registered) return;
            registered = true;
            FluidStorage.SIDED.registerFallback((level, pos, state, blockEntity, direction) -> {
                if (blockEntity instanceof SidedFluidBlockEntity sided && direction == sided.exposedFace) {
                    return sided.storage;
                }
                return null;
            });
        }
    }
}
