package com.github.crittscott.somebuckets.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Boundary conversions between NeoForge's fluid value and the loader-neutral persisted
 * representation.
 *
 * <p>NeoForge's {@link FluidStack} is component-based: it carries a {@link DataComponentPatch}, not a
 * {@link CompoundTag}. {@link StoredFluid} carries an optional variant payload as a
 * {@link CompoundTag}. The two are bridged with {@link DataComponentPatch#CODEC} over
 * {@link NbtOps}, mirroring {@code forge/.../util/ForgeFluidStacks} and
 * {@code fabric/.../fluid/FabricFluidVariants}. Without registry context a component that needs it to
 * serialize is dropped, yielding a blank patch; water, lava, milk, and virtually every registered
 * modded fluid carry no variant components and are unaffected.
 */
public final class NeoForgeFluidStacks {
    private NeoForgeFluidStacks() {}

    /**
     * Reads the container's persisted fluid as a NeoForge {@link FluidStack}.
     *
     * @param stack the bucket stack to read
     * @return the persisted fluid, or {@link FluidStack#EMPTY} when none is stored
     */
    public static FluidStack get(ItemStack stack) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        return stored.isEmpty() ? FluidStack.EMPTY
                : of(stored.fluid(), stored.amount(), stored.variantTag());
    }

    /**
     * Writes a NeoForge {@link FluidStack} into the container's persisted fluid schema.
     *
     * @param stack the bucket stack to mutate in place
     * @param fluidStack the fluid to store; an empty stack clears the persisted fluid
     */
    public static void set(ItemStack stack, FluidStack fluidStack) {
        NBTUtil.setStoredFluid(stack, fluidStack.isEmpty() ? StoredFluid.EMPTY
                : new StoredFluid(fluidStack.getFluid(), fluidStack.getAmount(), variantTag(fluidStack)));
    }

    /**
     * Builds a stack for a bare fluid plus an optional stored component payload.
     *
     * @param fluid the fluid identity
     * @param amount amount in millibuckets
     * @param variantTag optional variant NBT to apply as components, or {@code null}
     * @return the assembled fluid stack
     */
    public static FluidStack of(Fluid fluid, int amount, @Nullable CompoundTag variantTag) {
        FluidStack fluidStack = new FluidStack(fluid, amount);
        DataComponentPatch patch = toPatch(variantTag);
        if (!patch.isEmpty()) fluidStack.applyComponents(patch);
        return fluidStack;
    }

    /**
     * Copies {@code src} with a new amount, preserving fluid identity and components.
     *
     * @param src the source stack
     * @param amount the new amount in millibuckets
     * @return the resized copy, or {@link FluidStack#EMPTY} when {@code src} is empty
     */
    public static FluidStack resized(FluidStack src, int amount) {
        if (src.isEmpty()) return FluidStack.EMPTY;
        FluidStack copy = src.copy();
        copy.setAmount(amount);
        return copy;
    }

    /**
     * Tests whether two stacks hold the same fluid with equal components.
     *
     * @param a first stack
     * @param b second stack
     * @return {@code true} when fluid identity and components match
     */
    public static boolean sameFluid(FluidStack a, FluidStack b) {
        return FluidStack.isSameFluidSameComponents(a, b);
    }

    /**
     * Serializes a stack's component patch to detached NBT.
     *
     * @param fluidStack the stack to read
     * @return the encoded component patch, or {@code null} when it has none
     */
    @Nullable
    public static CompoundTag variantTag(FluidStack fluidStack) {
        return fromPatch(fluidStack.getComponentsPatch());
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
