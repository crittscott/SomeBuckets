package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.BucketState;
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

/**
 * Transactional Fabric fluid storage backed directly by a bucket stack's component state. Converts
 * between droplets and whole millibuckets and applies the item's own {@link FluidBucketItem}
 * container rules, which decide capacity, admission, and whether a transfer depletes the bucket.
 */
public final class FabricBucketStorage implements SingleSlotStorage<FluidVariant> {
    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / FluidBucketItem.BUCKET_VOLUME_MB;

    private final Backend backend;
    private final FluidBucketItem item;

    private FabricBucketStorage(Backend backend, FluidBucketItem item) {
        this.backend = backend;
        this.item = item;
    }

    /**
     * Creates a transaction participant over the stack exposed by a context.
     *
     * @param context the container-item context whose stack is edited
     * @param item the Big, Huge, or Source Bucket item the context holds
     * @return the storage participant
     */
    static FabricBucketStorage of(ContainerItemContext context, FluidBucketItem item) {
        return new FabricBucketStorage(new ContextBackend(context), item);
    }

    /**
     * Creates a transaction participant over the exact stack used by a block interaction.
     *
     * @param stack the Big, Huge, or Source Bucket stack edited in place
     * @return the storage participant
     */
    public static FabricBucketStorage of(ItemStack stack) {
        return new FabricBucketStorage(new StackBackend(stack), (FluidBucketItem) stack.getItem());
    }

    private StoredFluid stored() {
        return BucketState.getStoredFluid(backend.stack());
    }

    private FluidVariant variant() {
        return FabricFluidVariants.toVariant(stored());
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        int requestedMb = wholeMb(maxAmount);
        if (requestedMb == 0) return 0;

        ItemStack current = backend.stack();
        StoredFluid offered = new StoredFluid(resource.getFluid(), requestedMb, resource.getComponents());
        int acceptedMb = item.acceptable(current, offered);
        if (acceptedMb <= 0) return 0;

        ItemStack updated = current.copy();
        item.insert(updated, offered, acceptedMb);
        return commit(current, updated, transaction) ? acceptedMb * DROPLETS_PER_MB : 0;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        int requestedMb = wholeMb(maxAmount);
        if (requestedMb == 0 || !resource.equals(variant())) return 0;

        ItemStack current = backend.stack();
        StoredFluid yielded = item.extractable(current, requestedMb);
        if (yielded.isEmpty()) return 0;

        ItemStack updated = current.copy();
        item.extract(updated, yielded.amount());
        return commit(current, updated, transaction) ? yielded.amount() * DROPLETS_PER_MB : 0;
    }

    @Override public boolean isResourceBlank() { return variant().isBlank(); }
    @Override public FluidVariant getResource() { return variant(); }
    @Override public long getAmount() { return stored().amount() * DROPLETS_PER_MB; }
    @Override public long getCapacity() { return item.getCapacityMb() * DROPLETS_PER_MB; }

    /* Writes the updated stack back, skipping the write when the rules left the state unchanged. */
    private boolean commit(ItemStack current, ItemStack updated, TransactionContext transaction) {
        return ItemStack.isSameItemSameComponents(current, updated) || backend.replace(updated, transaction);
    }

    /* Whole millibuckets in a droplet amount, capped to the int range the item rules work in. */
    private static int wholeMb(long droplets) {
        return (int) Math.min(Integer.MAX_VALUE, droplets / DROPLETS_PER_MB);
    }

    private interface Backend {
        ItemStack stack();
        boolean replace(ItemStack updated, TransactionContext transaction);
    }

    /*
     * Rebuilds the stack from the context's current ItemVariant only when that variant
     * changes, so repeated resource/amount/blank probes on an unchanged slot reuse one instance.
     */
    private static final class ContextBackend implements Backend {
        private final ContainerItemContext context;
        private ItemVariant cachedVariant;
        private ItemStack cachedStack;

        private ContextBackend(ContainerItemContext context) {
            this.context = context;
        }

        @Override
        public ItemStack stack() {
            ItemVariant current = context.getItemVariant();
            if (!current.equals(cachedVariant)) {
                cachedVariant = current;
                cachedStack = current.toStack();
            }
            return cachedStack;
        }

        @Override
        public boolean replace(ItemStack updated, TransactionContext transaction) {
            return context.exchange(ItemVariant.of(updated), 1, transaction) == 1;
        }
    }

    /* Snapshots raw stack state so it can participate beside block storage in one transaction. */
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
            BucketState.copyState(updated, stack);
            return true;
        }

        @Override
        protected ItemStack createSnapshot() {
            return stack.copy();
        }

        @Override
        protected void readSnapshot(ItemStack snapshot) {
            BucketState.copyState(snapshot, stack);
        }
    }
}
