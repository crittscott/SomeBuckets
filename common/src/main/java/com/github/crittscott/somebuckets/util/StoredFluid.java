package com.github.crittscott.somebuckets.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/** Loader-neutral fluid identity, amount in mB, and variant payload stored by a bucket. */
public record StoredFluid(Fluid fluid, int amount, @Nullable CompoundTag variantTag) {
    public static final StoredFluid EMPTY = new StoredFluid(Fluids.EMPTY, 0, null);

    public StoredFluid {
        if (amount < 0) throw new IllegalArgumentException("Fluid amount must be nonnegative: " + amount);
        variantTag = variantTag == null ? null : variantTag.copy();
    }

    @Override
    public CompoundTag variantTag() {
        return variantTag == null ? null : variantTag.copy();
    }

    public boolean isEmpty() {
        return fluid == Fluids.EMPTY || amount <= 0;
    }

    public boolean isSameVariant(StoredFluid other) {
        return fluid.isSame(other.fluid) && java.util.Objects.equals(variantTag, other.variantTag);
    }

    public StoredFluid withAmount(int newAmount) {
        return newAmount <= 0 ? EMPTY : new StoredFluid(fluid, newAmount, variantTag);
    }
}
