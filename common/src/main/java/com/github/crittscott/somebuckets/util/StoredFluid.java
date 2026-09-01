package com.github.crittscott.somebuckets.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * Immutable loader-neutral fluid identity, amount in millibuckets, and optional variant payload.
 * Variant NBT is defensively copied on construction; the accessor returns that detached copy
 * directly, so callers persisting it back into storage must copy it again.
 */
public record StoredFluid(Fluid fluid, int amount, @Nullable CompoundTag variantTag) {
    public static final StoredFluid EMPTY = new StoredFluid(Fluids.EMPTY, 0, null);

    /**
     * Creates a stored-fluid value and detaches its variant payload from the caller.
     *
     * @param fluid loader-neutral fluid identity
     * @param amount amount in millibuckets
     * @param variantTag optional variant NBT, copied on the way in
     * @throws IllegalArgumentException if {@code amount} is negative
     */
    public StoredFluid {
        if (amount < 0) throw new IllegalArgumentException("Fluid amount must be nonnegative: " + amount);
        variantTag = variantTag == null ? null : variantTag.copy();
    }

    /**
     * Returns whether this value has no usable fluid identity or positive amount.
     *
     * @return {@code true} when the fluid is {@link Fluids#EMPTY} or the amount is not positive
     */
    public boolean isEmpty() {
        return fluid == Fluids.EMPTY || amount <= 0;
    }

    /**
     * Compares fluid identity and variant NBT while ignoring amount. Fluid aliases recognized by
     * {@link Fluid#isSame} compare as the same fluid.
     *
     * @param other value to compare against
     * @return {@code true} when both fluids are the same and their variant NBT is equal
     */
    public boolean isSameVariant(StoredFluid other) {
        return fluid.isSame(other.fluid) && java.util.Objects.equals(variantTag, other.variantTag);
    }

    /**
     * Returns this fluid identity with a replacement amount.
     *
     * @param newAmount replacement amount in millibuckets
     * @return {@link #EMPTY} for zero or negative amounts; otherwise a new defensively copied value
     */
    public StoredFluid withAmount(int newAmount) {
        return newAmount <= 0 ? EMPTY : new StoredFluid(fluid, newAmount, variantTag);
    }
}
