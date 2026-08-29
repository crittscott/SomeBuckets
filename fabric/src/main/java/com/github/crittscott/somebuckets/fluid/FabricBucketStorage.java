package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.BucketStackState;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

/** Transactional Fabric fluid storage backed directly by a bucket ItemStack's common NBT state. */
public abstract class FabricBucketStorage implements SingleSlotStorage<FluidVariant> {
    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / FluidBucketItem.BUCKET_VOLUME_MB;

    private final Backend backend;

    private FabricBucketStorage(Backend backend) {
        this.backend = backend;
    }

    static FabricBucketStorage finite(ContainerItemContext context, BBItem item) {
        return new Finite(new ContextBackend(context), item.getCapacityMb());
    }

    static FabricBucketStorage source(ContainerItemContext context) {
        return new Source(new ContextBackend(context));
    }

    /** A transaction participant over the exact stack used by a block interaction. */
    public static FabricBucketStorage finite(ItemStack stack, BBItem item) {
        return new Finite(new StackBackend(stack), item.getCapacityMb());
    }

    /** A transaction participant over the exact Source Bucket used by a block interaction. */
    public static FabricBucketStorage source(ItemStack stack) {
        return new Source(new StackBackend(stack));
    }

    final ItemStack stack() {
        return backend.stack();
    }

    final StoredFluid stored() {
        return NBTUtil.getStoredFluid(stack());
    }

    final FluidVariant variant() {
        return FabricFluidVariants.toVariant(stored());
    }

    final boolean replace(ItemStack updated, TransactionContext transaction) {
        return backend.replace(updated, transaction);
    }

    static long wholeMbDroplets(long droplets) {
        return droplets - droplets % DROPLETS_PER_MB;
    }

    @Override public boolean isResourceBlank() { return variant().isBlank(); }
    @Override public FluidVariant getResource() { return variant(); }

    private static final class Finite extends FabricBucketStorage {
        private final int capacityMb;

        private Finite(Backend backend, int capacityMb) {
            super(backend);
            this.capacityMb = capacityMb;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long requested = wholeMbDroplets(maxAmount);
            if (requested == 0) return 0;

            ItemStack currentStack = stack();
            NBTUtil.Mode mode = NBTUtil.getMode(currentStack);
            StoredFluid current = NBTUtil.getStoredFluid(currentStack);
            StoredFluid incoming = new StoredFluid(resource.getFluid(), 1,
                    FabricFluidVariants.variantTag(resource));
            if (mode != NBTUtil.Mode.NONE
                    && (mode != NBTUtil.Mode.FLUID || !current.isSameVariant(incoming))) return 0;

            int roomMb = capacityMb - current.amount();
            int insertedMb = (int) Math.min(roomMb, requested / DROPLETS_PER_MB);
            if (insertedMb <= 0) return 0;

            ItemStack updated = currentStack.copy();
            NBTUtil.setStoredFluid(updated, new StoredFluid(resource.getFluid(),
                    current.amount() + insertedMb, FabricFluidVariants.variantTag(resource)));
            return replace(updated, transaction) ? insertedMb * DROPLETS_PER_MB : 0;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long requested = wholeMbDroplets(maxAmount);
            StoredFluid current = stored();
            if (requested == 0 || current.isEmpty() || !resource.equals(variant())) return 0;

            int extractedMb = (int) Math.min(current.amount(), requested / DROPLETS_PER_MB);
            if (extractedMb <= 0) return 0;
            ItemStack updated = stack().copy();
            NBTUtil.drainFiniteContent(updated, extractedMb);
            return replace(updated, transaction) ? extractedMb * DROPLETS_PER_MB : 0;
        }

        @Override public long getAmount() { return stored().amount() * DROPLETS_PER_MB; }
        @Override public long getCapacity() { return capacityMb * DROPLETS_PER_MB; }
    }

    private static final class Source extends FabricBucketStorage {
        private Source(Backend backend) {
            super(backend);
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (!SBPolicy.allows(resource.getFluid())) return 0;
            long accepted = Math.min(FluidConstants.BUCKET, wholeMbDroplets(maxAmount));
            if (accepted == 0) return 0;

            ItemStack currentStack = stack();
            StoredFluid current = NBTUtil.getStoredFluid(currentStack);
            if (!current.isEmpty()) return resource.equals(variant()) ? accepted : 0;
            if (NBTUtil.getMode(currentStack) != NBTUtil.Mode.NONE) return 0;

            ItemStack updated = currentStack.copy();
            NBTUtil.setStoredFluid(updated, new StoredFluid(resource.getFluid(),
                    FluidBucketItem.BUCKET_VOLUME_MB, FabricFluidVariants.variantTag(resource)));
            return replace(updated, transaction) ? accepted : 0;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            StoredFluid current = stored();
            if (current.isEmpty() || !SBPolicy.allows(current.fluid()) || !resource.equals(variant())) return 0;
            return Math.min(FluidConstants.BUCKET, wholeMbDroplets(maxAmount));
        }

        @Override public long getAmount() { return stored().isEmpty() ? 0 : FluidConstants.BUCKET; }
        @Override public long getCapacity() { return FluidConstants.BUCKET; }
    }

    private interface Backend {
        ItemStack stack();
        boolean replace(ItemStack updated, TransactionContext transaction);
    }

    private record ContextBackend(ContainerItemContext context) implements Backend {
        @Override
        public ItemStack stack() {
            return context.getItemVariant().toStack();
        }

        @Override
        public boolean replace(ItemStack updated, TransactionContext transaction) {
            return context.exchange(ItemVariant.of(updated), 1, transaction) == 1;
        }
    }

    /** Snapshots raw stack state so it can participate beside block storage in one transaction. */
    private static final class StackBackend extends SnapshotParticipant<ItemStack> implements Backend {
        private final ItemStack stack;

        private StackBackend(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public ItemStack stack() {
            return stack;
        }

        @Override
        public boolean replace(ItemStack updated, TransactionContext transaction) {
            updateSnapshots(transaction);
            BucketStackState.copy(updated, stack);
            return true;
        }

        @Override
        protected ItemStack createSnapshot() {
            return stack.copy();
        }

        @Override
        protected void readSnapshot(ItemStack snapshot) {
            BucketStackState.copy(snapshot, stack);
        }
    }
}
