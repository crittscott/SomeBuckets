package com.github.crittscott.somebuckets.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Loader-neutral fluid identity, amount in millibuckets, and variant components. The components use
 * the same immutable {@link DataComponentPatch} form as NeoForge fluid stacks and Fabric fluid
 * variants, so conversion at those loader boundaries is lossless.
 */
public record StoredFluid(Fluid fluid, int amount, DataComponentPatch components) {
    /** Canonical empty fluid value. */
    public static final StoredFluid EMPTY = new StoredFluid(Fluids.EMPTY, 0);

    /**
     * Creates a stored-fluid value.
     *
     * @param fluid loader-neutral fluid identity
     * @param amount amount in millibuckets
     * @param components variant components; {@link DataComponentPatch#EMPTY} for a plain fluid
     * @throws IllegalArgumentException if {@code amount} is negative
     */
    public StoredFluid {
        if (amount < 0) throw new IllegalArgumentException("Fluid amount must be nonnegative: " + amount);
    }

    /**
     * Creates a stored-fluid value with no variant components.
     *
     * @param fluid loader-neutral fluid identity
     * @param amount amount in millibuckets
     */
    public StoredFluid(Fluid fluid, int amount) {
        this(fluid, amount, DataComponentPatch.EMPTY);
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
     * Compares fluid identity and variant components while ignoring amount. Fluid aliases recognized
     * by {@link Fluid#isSame} compare as the same fluid.
     *
     * @param other value to compare against
     * @return {@code true} when both fluids are the same and their components are equal
     */
    public boolean isSameVariant(StoredFluid other) {
        return fluid.isSame(other.fluid) && components.equals(other.components);
    }

    /**
     * Returns this fluid identity with a replacement amount.
     *
     * @param newAmount replacement amount in millibuckets
     * @return {@link #EMPTY} for zero or negative amounts; otherwise a new value with the same
     *         components
     */
    public StoredFluid withAmount(int newAmount) {
        return newAmount <= 0 ? EMPTY : new StoredFluid(fluid, newAmount, components);
    }
}
