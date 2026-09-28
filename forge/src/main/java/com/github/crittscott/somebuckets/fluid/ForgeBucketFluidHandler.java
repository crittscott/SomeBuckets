package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * Single-tank item fluid capability for both Some Buckets fluid items. Exposes content only in fluid
 * mode and converts between Forge fluid stacks and the item's own {@link FluidBucketItem} container
 * rules, which decide capacity, admission, and whether a transfer depletes the bucket. Simulated
 * actions never mutate the stack.
 */
public final class ForgeBucketFluidHandler implements IFluidHandlerItem {
    private final ItemStack container;
    private final FluidBucketItem item;

    /** Creates a stack-bound handler for a finite or Source Bucket item. */
    public ForgeBucketFluidHandler(ItemStack container) {
        this.container = container;
        this.item = (FluidBucketItem) container.getItem();
    }

    @Override
    public ItemStack getContainer() {
        return container;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? item.getCapacityMb() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && !stack.isEmpty() && item.acceptsFluid(stack.getFluid());
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0) return FluidStack.EMPTY;
        if (BucketState.getMode(container) != BucketState.Mode.FLUID) return FluidStack.EMPTY;
        return ForgeFluidStacks.get(container);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        StoredFluid offered = ForgeFluidStacks.stored(resource);
        int accepted = item.acceptable(container, offered);
        if (accepted > 0 && action.execute()) item.insert(container, offered, accepted);
        return accepted;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        FluidStack current = getFluidInTank(0);
        if (current.isEmpty() || !current.isFluidEqual(resource)) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        StoredFluid yielded = item.extractable(container, maxDrain);
        if (yielded.isEmpty()) return FluidStack.EMPTY;
        if (action.execute()) item.extract(container, yielded.amount());
        return ForgeFluidStacks.of(yielded);
    }
}
