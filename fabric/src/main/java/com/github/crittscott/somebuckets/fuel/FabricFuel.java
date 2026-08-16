package com.github.crittscott.somebuckets.fuel;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

/** NBT-sensitive furnace-fuel predicate shared by Fabric furnace mixin hooks. */
public final class FabricFuel {
    private FabricFuel() {}

    public static boolean isLavaFuel(ItemStack stack) {
        if (!(stack.getItem() instanceof FluidBucketItem)
                || NBTUtil.getMode(stack) != NBTUtil.Mode.FLUID) return false;
        StoredFluid fluid = NBTUtil.getStoredFluid(stack);
        if (stack.getItem() instanceof SBItem && !SBPolicy.allows(fluid.fluid())) return false;
        return fluid.fluid() == Fluids.LAVA
                && fluid.amount() >= FluidBucketItem.BUCKET_VOLUME_MB;
    }
}
