package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;

/** Single-index transactional fluid storage backed by the capability's native item access. */
public final class NeoForgeBucketFluidHandler implements ResourceHandler<FluidResource> {
    private final ItemAccess access;

    /** Uses the caller's inventory access so container changes participate in its transaction. */
    public NeoForgeBucketFluidHandler(ItemAccess access) {
        this.access = access;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        Objects.checkIndex(index, size());
        return FluidResource.of(NeoForgeFluidStacks.of(FluidBucketItem.exposedFluid(stack())));
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, size());
        return FluidBucketItem.exposedFluid(stack()).amount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        Objects.checkIndex(index, size());
        ItemStack stack = stack();
        return stack.getItem() instanceof FluidBucketItem item ? item.getCapacityMb() : 0;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        Objects.checkIndex(index, size());
        ItemStack stack = stack();
        return !resource.isEmpty() && stack.getItem() instanceof FluidBucketItem item
                && item.acceptsFluid(resource.toStack(1).getFluid());
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        return insert(0, resource, amount, transaction);
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        return extract(0, resource, amount, transaction);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkTransfer(index, resource, amount);
        if (amount == 0 || access.getAmount() != 1) return 0;
        ItemStack stack = stack();
        if (!(stack.getItem() instanceof FluidBucketItem item)) return 0;
        StoredFluid offered = NeoForgeFluidStacks.stored(resource.toStack(amount));
        int accepted = item.acceptable(stack, offered);
        if (accepted <= 0) return 0;
        item.insert(stack, offered, accepted);
        return replace(stack, transaction) ? accepted : 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkTransfer(index, resource, amount);
        if (amount == 0 || access.getAmount() != 1) return 0;
        ItemStack stack = stack();
        if (!(stack.getItem() instanceof FluidBucketItem item)) return 0;
        StoredFluid yielded = item.extractable(stack, amount);
        if (yielded.isEmpty() || !FluidResource.of(NeoForgeFluidStacks.of(yielded)).equals(resource)) return 0;
        item.extract(stack, yielded.amount());
        return replace(stack, transaction) ? yielded.amount() : 0;
    }

    private ItemStack stack() {
        return access.getResource().toStack();
    }

    private boolean replace(ItemStack replacement, TransactionContext parent) {
        ItemResource previous = access.getResource();
        ItemResource updated = ItemResource.of(replacement);
        return updated.equals(previous) || access.exchange(updated, 1, parent) == 1;
    }

    private void checkTransfer(int index, FluidResource resource, int amount) {
        Objects.checkIndex(index, size());
        if (resource.isEmpty() || amount < 0) throw new IllegalArgumentException("Invalid fluid transfer");
    }
}
