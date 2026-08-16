package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.function.Consumer;

/** Defines the ordered contents of the Some Buckets creative tab. */
public final class CreativeBucketCatalog {
    private CreativeBucketCatalog() {}

    public static void populate(Item big, Item huge, Item source, Item junk, Item mob, Item trash,
                                Consumer<ItemStack> output) {
        output.accept(new ItemStack(big));
        output.accept(new ItemStack(huge));
        addFluidPair(output, big, huge, Fluids.WATER);
        addFluidPair(output, big, huge, Fluids.LAVA);
        addMilkPair(output, big, huge);
        addPowderPair(output, big, huge);

        output.accept(new ItemStack(source));
        output.accept(fluid(source, Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB));
        output.accept(fluid(source, Fluids.LAVA, FluidBucketItem.BUCKET_VOLUME_MB));
        ItemStack sourceMilk = new ItemStack(source);
        NBTUtil.setMilkAmount(sourceMilk, FluidBucketItem.BUCKET_VOLUME_MB);
        output.accept(sourceMilk);

        output.accept(new ItemStack(junk));
        output.accept(new ItemStack(mob));
        output.accept(new ItemStack(trash));
    }

    private static void addFluidPair(Consumer<ItemStack> output, Item big, Item huge, Fluid fluid) {
        output.accept(fluid(big, fluid, capacityMb(big)));
        output.accept(fluid(huge, fluid, capacityMb(huge)));
    }

    private static void addMilkPair(Consumer<ItemStack> output, Item big, Item huge) {
        output.accept(milk(big));
        output.accept(milk(huge));
    }

    private static void addPowderPair(Consumer<ItemStack> output, Item big, Item huge) {
        output.accept(powder(big));
        output.accept(powder(huge));
    }

    private static ItemStack fluid(Item item, Fluid fluid, int amount) {
        ItemStack stack = new ItemStack(item);
        NBTUtil.setStoredFluid(stack, new StoredFluid(fluid, amount, null));
        return stack;
    }

    private static ItemStack milk(Item item) {
        ItemStack stack = new ItemStack(item);
        NBTUtil.setMilkAmount(stack, capacityMb(item));
        return stack;
    }

    private static ItemStack powder(Item item) {
        ItemStack stack = new ItemStack(item);
        NBTUtil.setPowderUnits(stack, ((BBItem) item).getCapacityUnits());
        return stack;
    }

    private static int capacityMb(Item item) {
        return ((BBItem) item).getCapacityMb();
    }
}
