package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.FabricBucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.register.FabricItems;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

final class GameTestSupport extends SharedGameTestSupport {
    static final String TEMPLATE = "somebuckets:empty_9x6x9";
    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / FluidBucketItem.BUCKET_VOLUME_MB;

    private GameTestSupport() {}

    /**
     * Returns the installed implementation as its Fabric type so automation tests can call the
     * overloads that accept an explicit {@code ProtectionContext}.
     */
    static FabricBucketOperations fabricOps() {
        return (FabricBucketOperations) BucketOperations.get();
    }

    static ItemStack big8() {
        return new ItemStack(FabricItems.BIG_BUCKET_8);
    }

    static ItemStack big64() {
        return new ItemStack(FabricItems.BIG_BUCKET_64);
    }

    static ItemStack source() {
        return new ItemStack(FabricItems.SOURCE_BUCKET);
    }

    static ItemStack junk() {
        return new ItemStack(FabricItems.JUNK_BUCKET);
    }

    static ItemStack trash() {
        return new ItemStack(FabricItems.TRASH_BUCKET);
    }

    static ItemStack mob() {
        return new ItemStack(FabricItems.MOB_BUCKET);
    }

    static boolean tryBigTakeWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                         ProtectionContext context) {
        return fabricOps().tryBigTakeWithContext(level, hit, stack, context);
    }

    static boolean tryBigPlaceWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                          ProtectionContext context, boolean allowFaceOffset) {
        return fabricOps().tryBigPlaceWithContext(level, hit, stack, context, allowFaceOffset);
    }

    static boolean tryPowderTakeWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        return fabricOps().tryPowderTakeWithContext(level, hit, stack, context);
    }

    static boolean tryPowderPlaceWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context, boolean allowFaceOffset) {
        return fabricOps().tryPowderPlaceWithContext(level, hit, stack, context, allowFaceOffset);
    }

    static boolean trySourceTakeWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        return fabricOps().trySourceTakeWithContext(level, hit, stack, context);
    }

    static boolean trySourcePlaceWithContext(ServerLevel level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context, boolean allowFaceOffset) {
        return fabricOps().trySourcePlaceWithContext(level, hit, stack, context, allowFaceOffset);
    }

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
                FluidVariant variant = FluidVariant.of(contents.fluid(), contents.variantTag());
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
            return new StoredFluid(variant.getFluid(), amountMb, variant.copyNbt());
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
