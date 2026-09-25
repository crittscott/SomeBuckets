package com.github.crittscott.somebuckets.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Boundary conversions between Forge's fluid value and the loader-neutral persisted representation.
 *
 * <p>Forge's {@link FluidStack} carries its variant payload as a {@link CompoundTag}; the
 * loader-neutral value carries a {@link DataComponentPatch}. The tag travels as the patch's
 * {@link DataComponents#CUSTOM_DATA} component, the same place vanilla keeps free-form item NBT. The
 * helper surface mirrors {@code neoforge/.../util/NeoForgeFluidStacks} so fluid-logic changes stay
 * diff-clean between the two loaders.
 */
public final class ForgeFluidStacks {
    private ForgeFluidStacks() {}

    /**
     * Reads the container's persisted fluid as a Forge {@link FluidStack}.
     *
     * @param stack the bucket stack to read
     * @return the persisted fluid, or {@link FluidStack#EMPTY} when none is stored
     */
    public static FluidStack get(ItemStack stack) {
        return of(BucketState.getStoredFluid(stack));
    }

    /**
     * Writes a Forge {@link FluidStack} into the container's persisted fluid schema.
     *
     * @param stack the bucket stack to mutate in place
     * @param fluidStack the fluid to store; an empty stack clears the persisted fluid
     */
    public static void set(ItemStack stack, FluidStack fluidStack) {
        BucketState.setStoredFluid(stack, fluidStack.isEmpty() ? StoredFluid.EMPTY
                : new StoredFluid(fluidStack.getFluid(), fluidStack.getAmount(), components(fluidStack)));
    }

    /**
     * Builds the Forge stack for a stored fluid value.
     *
     * @param stored the loader-neutral fluid value
     * @return the assembled fluid stack carrying the stored custom data as its tag, or
     *         {@link FluidStack#EMPTY} when {@code stored} is empty
     */
    public static FluidStack of(StoredFluid stored) {
        if (stored.isEmpty()) return FluidStack.EMPTY;
        return new FluidStack(stored.fluid(), stored.amount(), tag(stored.components()));
    }

    /**
     * Copies {@code src} with a new amount, preserving fluid identity and variant payload.
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
     * Tests whether two stacks hold the same fluid with an equal variant payload.
     *
     * @param a first stack
     * @param b second stack
     * @return {@code true} when fluid identity and variant NBT match
     */
    public static boolean sameFluid(FluidStack a, FluidStack b) {
        return a.isFluidEqual(b);
    }

    /**
     * Returns a stack's variant payload as loader-neutral components.
     *
     * @param fluidStack the stack to read
     * @return a patch holding the stack's tag as custom data, or {@link DataComponentPatch#EMPTY}
     *         when it has none
     */
    public static DataComponentPatch components(FluidStack fluidStack) {
        CompoundTag tag = fluidStack.getTag();
        if (tag == null || tag.isEmpty()) return DataComponentPatch.EMPTY;
        return DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build();
    }

    /* A Forge fluid stack has only its tag, so any other component cannot be represented. */
    @Nullable
    private static CompoundTag tag(DataComponentPatch components) {
        Optional<? extends CustomData> customData = components.get(DataComponents.CUSTOM_DATA);
        return customData == null || customData.isEmpty() ? null : customData.get().copyTag();
    }
}
