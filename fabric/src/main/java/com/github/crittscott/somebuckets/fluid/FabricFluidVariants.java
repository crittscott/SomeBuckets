package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;

/**
 * Converts between the loader-neutral {@link StoredFluid} value and Fabric's {@link FluidVariant}.
 *
 * <p>{@link StoredFluid} carries an optional variant payload as a {@link CompoundTag}; a
 * {@link FluidVariant} carries a {@link DataComponentPatch}. The two are bridged with
 * {@link DataComponentPatch#CODEC} over {@link NbtOps}, the same codec Fabric itself uses for
 * variant persistence. Without registry context a component that needs it to serialize is dropped,
 * yielding a blank patch; water, lava, milk, and virtually every registered modded fluid carry no
 * variant components and are unaffected.
 */
public final class FabricFluidVariants {
    private FabricFluidVariants() {}

    /**
     * Builds a variant for a stored fluid, decoding any stored component payload.
     *
     * @param stored the loader-neutral fluid value
     * @return the matching {@link FluidVariant}, or {@link FluidVariant#blank()} when empty
     */
    public static FluidVariant toVariant(StoredFluid stored) {
        return stored.isEmpty() ? FluidVariant.blank()
                : FluidVariant.of(stored.fluid(), toPatch(stored.variantTag()));
    }

    /**
     * Builds a variant for a bare fluid plus an optional stored component payload.
     *
     * @param fluid the fluid identity
     * @param variantTag optional variant NBT to decode into components, or {@code null}
     * @return the assembled {@link FluidVariant}
     */
    public static FluidVariant toVariant(Fluid fluid, @Nullable CompoundTag variantTag) {
        return FluidVariant.of(fluid, toPatch(variantTag));
    }

    /**
     * Serializes a variant's component patch to detached NBT.
     *
     * @param variant the variant to read
     * @return the encoded component patch, or {@code null} when it has none
     */
    @Nullable
    public static CompoundTag variantTag(FluidVariant variant) {
        return fromPatch(variant.getComponents());
    }

    private static DataComponentPatch toPatch(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return DataComponentPatch.EMPTY;
        return DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(DataComponentPatch.EMPTY);
    }

    @Nullable
    private static CompoundTag fromPatch(DataComponentPatch patch) {
        if (patch.isEmpty()) return null;
        Tag encoded = DataComponentPatch.CODEC.encodeStart(NbtOps.INSTANCE, patch).result().orElse(null);
        return encoded instanceof CompoundTag compound ? compound : null;
    }
}
