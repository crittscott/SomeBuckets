package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.item.VariableStackItem;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.CapturedMobs;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.FluidContent;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.JunkContents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Serializes, deserializes, and normalizes the persistent state of all bucket families. Every payload
 * lives in a registered data component from {@link ModDataComponentTypes}, and this class is the sole
 * reader and writer of that state. Mutators edit the supplied stack in place, leave canonical empty
 * state behind (an exhausted payload's component removed), and never touch unrelated components.
 */
public final class NBTUtil {

    /** Identifies which mutually exclusive payload a bucket currently holds. */
    public enum Mode {
        /** No content component present. */
        NONE,
        /** A finite or source fluid payload in {@link ModDataComponentTypes#FLUID_CONTENT}. */
        FLUID,
        /** A milk amount in {@link ModDataComponentTypes#MILK_AMOUNT}. */
        MILK,
        /** A powder-snow block count in {@link ModDataComponentTypes#POWDER_UNITS}. */
        POWDER_SNOW,
        /** One or more captured mob snapshots in {@link ModDataComponentTypes#CAPTURED_MOBS}. */
        ENTITY
    }

    private NBTUtil() {}

    private static Mode modeOf(ItemStack stack) {
        if (stack.has(ModDataComponentTypes.FLUID_CONTENT)) return Mode.FLUID;
        if (stack.has(ModDataComponentTypes.MILK_AMOUNT)) return Mode.MILK;
        if (stack.has(ModDataComponentTypes.POWDER_UNITS)) return Mode.POWDER_SNOW;
        if (stack.has(ModDataComponentTypes.CAPTURED_MOBS)) return Mode.ENTITY;
        return Mode.NONE;
    }

    /** Removes every mutually exclusive content payload, leaving stored junk items untouched. */
    private static void clearContent(ItemStack stack) {
        stack.remove(ModDataComponentTypes.FLUID_CONTENT);
        stack.remove(ModDataComponentTypes.MILK_AMOUNT);
        stack.remove(ModDataComponentTypes.POWDER_UNITS);
        stack.remove(ModDataComponentTypes.CAPTURED_MOBS);
    }

    /** Keeps a {@link VariableStackItem}'s max stack size in step with its fill state. */
    private static void afterMutation(ItemStack stack) {
        if (stack.getItem() instanceof VariableStackItem) {
            stack.set(DataComponents.MAX_STACK_SIZE, isEmptyBucket(stack)
                    ? VariableStackItem.EMPTY_STACK_SIZE
                    : VariableStackItem.FILLED_STACK_SIZE);
        }
    }

    /** Returns the stored payload mode; a bucket with no content component is {@link Mode#NONE}. */
    public static Mode getMode(ItemStack stack) {
        return modeOf(stack);
    }

    /** Returns whether the stack has neither a content payload nor stored junk items. */
    public static boolean isEmptyBucket(ItemStack stack) {
        return modeOf(stack) == Mode.NONE && !stack.has(ModDataComponentTypes.JUNK_CONTENTS);
    }

    /**
     * Returns the finite fluid amount for fluid mode, the milk amount for milk mode, otherwise zero.
     * Amounts use millibuckets.
     */
    public static int getAmount(ItemStack stack) {
        FluidContent fluid = stack.get(ModDataComponentTypes.FLUID_CONTENT);
        if (fluid != null) return fluid.amount();
        Integer milk = stack.get(ModDataComponentTypes.MILK_AMOUNT);
        return milk != null ? milk : 0;
    }

    /**
     * Reads a detached loader-neutral fluid value. Missing, empty, or unregistered fluid state
     * returns {@link StoredFluid#EMPTY}.
     */
    public static StoredFluid getStoredFluid(ItemStack stack) {
        FluidContent fluid = stack.get(ModDataComponentTypes.FLUID_CONTENT);
        if (fluid == null || fluid.fluid() == Fluids.EMPTY || fluid.amount() <= 0) {
            return StoredFluid.EMPTY;
        }
        return new StoredFluid(fluid.fluid(), fluid.amount(), fluid.variant().orElse(null));
    }

    /**
     * Selects fluid mode and replaces the serialized fluid payload, or clears empty content. Any
     * other content payload is discarded first, so the stack is left holding only this fluid.
     */
    public static void setStoredFluid(ItemStack stack, StoredFluid fluid) {
        if (fluid.isEmpty()) {
            clearBucket(stack);
            return;
        }
        clearContent(stack);
        CompoundTag variant = fluid.variantTag();
        Optional<CompoundTag> variantPayload = variant == null || variant.isEmpty()
                ? Optional.empty() : Optional.of(variant);
        stack.set(ModDataComponentTypes.FLUID_CONTENT,
                new FluidContent(fluid.fluid(), fluid.amount(), variantPayload));
        afterMutation(stack);
    }

    /**
     * Selects milk mode and writes its positive amount in millibuckets, or clears zero content. Any
     * other content payload is discarded first.
     *
     * @throws IllegalArgumentException if {@code mb} is negative
     */
    public static void setMilkAmount(ItemStack stack, int mb) {
        requireNonNegative(mb, "Milk amount");
        if (mb == 0) {
            clearBucket(stack);
            return;
        }
        clearContent(stack);
        stack.set(ModDataComponentTypes.MILK_AMOUNT, mb);
        afterMutation(stack);
    }

    public static int getPowderUnits(ItemStack stack) {
        Integer units = stack.get(ModDataComponentTypes.POWDER_UNITS);
        return units != null ? units : 0;
    }

    /**
     * Selects powder-snow mode and writes its positive block count, or clears zero content. Any
     * other content payload is discarded first.
     *
     * @throws IllegalArgumentException if {@code units} is negative
     */
    public static void setPowderUnits(ItemStack stack, int units) {
        requireNonNegative(units, "Powder-snow units");
        if (units == 0) {
            clearBucket(stack);
            return;
        }
        clearContent(stack);
        stack.set(ModDataComponentTypes.POWDER_UNITS, units);
        afterMutation(stack);
    }

    /**
     * Removes up to {@code requestedAmount} millibuckets from fluid or milk mode.
     *
     * <p>Other modes and nonpositive requests return zero without mutation. Removing the final amount
     * clears the content payload while preserving stored-item state.
     *
     * @return the amount actually removed, in millibuckets
     */
    public static int drainFiniteContent(ItemStack stack, int requestedAmount) {
        if (requestedAmount <= 0) return 0;

        StoredFluid fluid = getStoredFluid(stack);
        if (!fluid.isEmpty()) {
            int removed = Math.min(fluid.amount(), requestedAmount);
            int remaining = fluid.amount() - removed;
            if (remaining == 0) clearBucket(stack);
            else setStoredFluid(stack, fluid.withAmount(remaining));
            return removed;
        }

        Integer milk = stack.get(ModDataComponentTypes.MILK_AMOUNT);
        if (milk != null) {
            int removed = Math.min(milk, requestedAmount);
            int remaining = milk - removed;
            if (remaining == 0) {
                clearBucket(stack);
            } else {
                stack.set(ModDataComponentTypes.MILK_AMOUNT, remaining);
                afterMutation(stack);
            }
            return removed;
        }
        return 0;
    }

    public static int getEntityCount(ItemStack stack) {
        CapturedMobs mobs = stack.get(ModDataComponentTypes.CAPTURED_MOBS);
        return mobs == null ? 0 : mobs.entities().size();
    }

    /**
     * Selects entity mode and appends one bucket-format entity snapshot, preserving any snapshots
     * already stored. Any other content payload is discarded first. The supplied compound is stored
     * directly rather than copied.
     */
    public static void addEntitySnapshot(ItemStack stack, String entityTypeId, CompoundTag bucketTag) {
        CapturedMobs current = stack.get(ModDataComponentTypes.CAPTURED_MOBS);
        List<CompoundTag> entities = current == null
                ? new ArrayList<>() : new ArrayList<>(current.entities());
        clearContent(stack);
        entities.add(bucketTag);
        stack.set(ModDataComponentTypes.CAPTURED_MOBS,
                new CapturedMobs(ResourceLocation.parse(entityTypeId), List.copyOf(entities)));
        afterMutation(stack);
    }

    /**
     * Returns a detached copy of the first entity snapshot without changing the stack.
     *
     * @return the snapshot, or an empty compound when none is stored
     */
    public static CompoundTag copyFirstEntitySnapshot(ItemStack stack) {
        CapturedMobs mobs = stack.get(ModDataComponentTypes.CAPTURED_MOBS);
        return mobs == null || mobs.entities().isEmpty()
                ? new CompoundTag() : mobs.entities().get(0).copy();
    }

    /**
     * Removes and returns a detached copy of the first entity snapshot. Removing the final snapshot
     * also clears entity mode.
     *
     * @return the removed snapshot, or an empty compound when none is stored
     */
    public static CompoundTag removeFirstEntitySnapshot(ItemStack stack) {
        CapturedMobs mobs = stack.get(ModDataComponentTypes.CAPTURED_MOBS);
        if (mobs == null || mobs.entities().isEmpty()) return new CompoundTag();
        List<CompoundTag> remaining = new ArrayList<>(mobs.entities());
        CompoundTag out = remaining.remove(0).copy();
        if (remaining.isEmpty()) {
            clearContent(stack);
        } else {
            stack.set(ModDataComponentTypes.CAPTURED_MOBS,
                    new CapturedMobs(mobs.entityType(), List.copyOf(remaining)));
        }
        afterMutation(stack);
        return out;
    }

    /**
     * Resolves the recorded entity type.
     *
     * @return the registered type, or {@code null} when none is stored or the id is unknown
     */
    @Nullable
    public static EntityType<?> getCurrentEntityType(ItemStack stack) {
        CapturedMobs mobs = stack.get(ModDataComponentTypes.CAPTURED_MOBS);
        return mobs == null ? null
                : BuiltInRegistries.ENTITY_TYPE.getOptional(mobs.entityType()).orElse(null);
    }

    /** Deserializes stored junk contents into a detached, mutable list of detached stacks. */
    public static List<ItemStack> getStoredItems(ItemStack container, HolderLookup.Provider registries) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        List<ItemStack> result = new ArrayList<>();
        if (junk == null) return result;
        for (ItemStack stack : junk.items()) {
            if (!stack.isEmpty()) result.add(stack.copy());
        }
        return result;
    }

    /** Returns the stored junk-entry count. */
    public static int getStoredItemCount(ItemStack container) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        return junk == null ? 0 : junk.items().size();
    }

    /**
     * Returns a value that changes whenever the stored junk items or the layout seed change.
     * Intended for client-side render caches; not stable across sessions.
     */
    public static long storedItemsFingerprint(ItemStack container) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        if (junk == null) return 0L;
        Tag encoded = JunkContents.CODEC.encodeStart(NbtOps.INSTANCE, junk).result().orElse(null);
        return encoded != null ? encoded.hashCode() : junk.layoutSeed();
    }

    /**
     * Replaces stored junk contents with the nonempty entries in {@code items}, keeping the existing
     * layout seed. Writing an empty list removes the junk payload entirely.
     */
    public static void setStoredItems(ItemStack container, List<ItemStack> items,
                                      HolderLookup.Provider registries) {
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) kept.add(stack.copy());
        }
        if (kept.isEmpty()) {
            container.remove(ModDataComponentTypes.JUNK_CONTENTS);
        } else {
            JunkContents existing = container.get(ModDataComponentTypes.JUNK_CONTENTS);
            container.set(ModDataComponentTypes.JUNK_CONTENTS,
                    new JunkContents(List.copyOf(kept), existing == null ? 0L : existing.layoutSeed()));
        }
        afterMutation(container);
    }

    /** Returns the stored junk-layout seed, or zero when the stack stores no junk items. */
    public static long getJunkLayoutSeed(ItemStack container) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        return junk == null ? 0L : junk.layoutSeed();
    }

    /** Replaces the junk-layout seed without changing stored items; a no-op when none are stored. */
    public static void rerollJunkLayout(ItemStack container) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        if (junk == null) return;
        container.set(ModDataComponentTypes.JUNK_CONTENTS,
                new JunkContents(junk.items(), ThreadLocalRandom.current().nextLong()));
    }

    /**
     * Clears fluid, milk, powder-snow, and entity state while preserving stored junk items and
     * unrelated components.
     */
    public static void clearBucket(ItemStack stack) {
        clearContent(stack);
        afterMutation(stack);
    }

    /**
     * Removes a content payload whose value has decayed to empty. Stored junk items and unrelated
     * components are not considered or changed.
     */
    public static void normalizeEmptyState(ItemStack stack) {
        boolean empty = switch (modeOf(stack)) {
            case FLUID -> getStoredFluid(stack).isEmpty();
            case MILK -> {
                Integer milk = stack.get(ModDataComponentTypes.MILK_AMOUNT);
                yield milk == null || milk <= 0;
            }
            case POWDER_SNOW -> getPowderUnits(stack) <= 0;
            case ENTITY -> getEntityCount(stack) == 0;
            case NONE -> false;
        };
        if (empty) {
            clearContent(stack);
            afterMutation(stack);
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative: " + value);
    }
}
