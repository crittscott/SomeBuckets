package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/**
 * NeoForge counterpart of the Forge {@code GameTestSupport}. The vanilla and loader-neutral helpers
 * come from {@link SharedGameTestSupport}; this class adds a NeoForge
 * {@link Capabilities.Fluid#BLOCK}-backed sided tank fixture.
 */
final class GameTestSupport extends SharedGameTestSupport {

    private GameTestSupport() {}

    /**
     * Attaches the sided-tank fixture's fluid handler to every {@link Blocks#STRUCTURE_BLOCK}
     * position; the provider returns {@code null} unless a {@link SidedFluidBlockEntity} is installed
     * there and the queried side matches its exposed face. Wired from {@link SomeBucketsGameTestMod}.
     */
    static void registerTestCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Fluid.BLOCK,
                (level, pos, state, blockEntity, side) ->
                        blockEntity instanceof SidedFluidBlockEntity sided && side == sided.exposedFace
                                ? sided.handler
                                : null,
                Blocks.STRUCTURE_BLOCK);
    }

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

    static ResourceHandler<FluidResource> fluidHandler(ItemStack stack) {
        ResourceHandler<FluidResource> handler = ItemAccess.forStack(stack).getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) throw new IllegalStateException("Bucket exposed no fluid capability");
        return handler;
    }

    static int fill(ItemStack stack, StoredFluid offered, boolean execute) {
        try (Transaction transaction = Transaction.open(null)) {
            int moved = fluidHandler(stack).insert(FluidResource.of(NeoForgeFluidStacks.of(offered)),
                    offered.amount(), transaction);
            if (execute) transaction.commit();
            return moved;
        }
    }

    static StoredFluid drain(ItemStack stack, int amount, boolean execute) {
        ResourceHandler<FluidResource> handler = fluidHandler(stack);
        for (int index = 0; index < handler.size(); index++) {
            FluidResource resource = handler.getResource(index);
            if (resource.isEmpty()) continue;
            try (Transaction transaction = Transaction.open(null)) {
                int moved = handler.extract(resource, amount, transaction);
                if (execute) transaction.commit();
                return NeoForgeFluidStacks.stored(resource.toStack(moved));
            }
        }
        return StoredFluid.EMPTY;
    }

    /** Structure-block fixture exposing a native transactional tank through one face. */
    static final class SidedFluidBlockEntity extends BlockEntity {
        final Direction exposedFace;
        final Tank handler;

        private SidedFluidBlockEntity(BlockPos pos, BlockState state, Direction exposedFace,
                                      int capacity, StoredFluid contents, int executeLimitMb) {
            super(BlockEntityTypes.STRUCTURE_BLOCK, pos, state);
            this.exposedFace = exposedFace;
            this.handler = new Tank(capacity, contents, executeLimitMb);
        }

        StoredFluid contents() {
            return handler.contents;
        }
    }

    /* The first operation promises full throughput; subsequent calls model a short transfer. */
    private static final class Tank extends SnapshotJournal<StoredFluid> implements ResourceHandler<FluidResource> {
        private final int capacity;
        private final int limit;
        private StoredFluid contents;
        private boolean promised;

        private Tank(int capacity, StoredFluid contents, int limit) {
            this.capacity = capacity;
            this.contents = contents;
            this.limit = limit;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            Objects.checkIndex(index, size());
            return FluidResource.of(NeoForgeFluidStacks.of(contents));
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, size());
            return contents.amount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            Objects.checkIndex(index, size());
            return capacity;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            Objects.checkIndex(index, size());
            return !resource.isEmpty();
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            StoredFluid offered = NeoForgeFluidStacks.stored(resource.toStack(amount));
            if (offered.isEmpty() || !contents.isEmpty() && !contents.isSameVariant(offered)) return 0;
            int moved = Math.min(limited(amount), capacity - contents.amount());
            if (moved > 0) {
                updateSnapshots(transaction);
                contents = offered.withAmount(contents.amount() + moved);
            }
            return moved;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            if (contents.isEmpty() || !getResource(index).equals(resource)) return 0;
            int moved = Math.min(limited(amount), contents.amount());
            if (moved > 0) {
                updateSnapshots(transaction);
                contents = contents.withAmount(contents.amount() - moved);
            }
            return moved;
        }

        private int limited(int requested) {
            if (!promised) {
                promised = true;
                return requested;
            }
            return Math.min(requested, limit);
        }

        @Override
        protected StoredFluid createSnapshot() {
            return contents;
        }

        @Override
        protected void revertToSnapshot(StoredFluid snapshot) {
            contents = snapshot;
        }
    }
}
