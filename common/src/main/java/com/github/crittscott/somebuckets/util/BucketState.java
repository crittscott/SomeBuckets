package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.item.SomeBucketItem;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.CapturedMobs;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.FluidContent;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.JunkContents;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.SetAside;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.levelgen.RandomSupport;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Serializes, deserializes, and normalizes the persistent state of all bucket families. Every payload
 * lives in a registered data component from {@link ModDataComponentTypes}; runtime bucket behavior
 * reads and writes that state through this class. Mutators edit the supplied stack in place, leave
 * canonical empty state behind (an exhausted payload's component removed), and never touch unrelated
 * components.
 */
public final class BucketState {

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

    /** Odd 64-bit multiplier that spreads the resulting entry count before junk layout seed mixing. */
    private static final long ENTRY_COUNT_MULTIPLIER = 0xD1B54A32D192ED03L;

    private BucketState() {}

    private static Mode modeOf(ItemStack stack) {
        if (stack.has(ModDataComponentTypes.FLUID_CONTENT)) return Mode.FLUID;
        if (stack.has(ModDataComponentTypes.MILK_AMOUNT)) return Mode.MILK;
        if (stack.has(ModDataComponentTypes.POWDER_UNITS)) return Mode.POWDER_SNOW;
        if (stack.has(ModDataComponentTypes.CAPTURED_MOBS)) return Mode.ENTITY;
        return Mode.NONE;
    }

    /* Removes every mutually exclusive content payload, leaving stored junk items untouched. */
    private static void clearContent(ItemStack stack) {
        stack.remove(ModDataComponentTypes.FLUID_CONTENT);
        stack.remove(ModDataComponentTypes.MILK_AMOUNT);
        stack.remove(ModDataComponentTypes.POWDER_UNITS);
        stack.remove(ModDataComponentTypes.CAPTURED_MOBS);
    }

    /*
     * Keeps components derived from content in step with it: a Some Buckets item's max stack
     * size, and the vanilla milk consumable that makes a milk-mode fluid bucket drinkable.
     */
    private static void afterMutation(ItemStack stack) {
        if (stack.getItem() instanceof SomeBucketItem) {
            stack.set(DataComponents.MAX_STACK_SIZE, isEmptyBucket(stack)
                    ? SomeBucketItem.EMPTY_STACK_SIZE
                    : SomeBucketItem.FILLED_STACK_SIZE);
        }
        if (stack.getItem() instanceof FluidBucketItem) {
            if (getMode(stack) == Mode.MILK) {
                stack.set(DataComponents.CONSUMABLE, Consumables.MILK_BUCKET);
            } else {
                stack.remove(DataComponents.CONSUMABLE);
            }
        }
    }

    /**
     * Returns the stored payload mode.
     *
     * @param stack bucket stack to inspect
     * @return the current mode, or {@link Mode#NONE} when no content component is present
     */
    public static Mode getMode(ItemStack stack) {
        return modeOf(stack);
    }

    /**
     * Returns whether the stack holds nothing.
     *
     * @param stack bucket stack to inspect
     * @return {@code true} when the stack has neither a content payload nor stored junk items
     */
    public static boolean isEmptyBucket(ItemStack stack) {
        return modeOf(stack) == Mode.NONE && !stack.has(ModDataComponentTypes.JUNK_CONTENTS);
    }

    /**
     * Returns the finite content amount in millibuckets.
     *
     * @param stack bucket stack to inspect
     * @return the fluid amount in fluid mode, the milk amount in milk mode, otherwise zero
     */
    public static int getAmount(ItemStack stack) {
        FluidContent fluid = stack.get(ModDataComponentTypes.FLUID_CONTENT);
        if (fluid != null) return fluid.amount();
        Integer milk = stack.get(ModDataComponentTypes.MILK_AMOUNT);
        return milk != null ? milk : 0;
    }

    /**
     * Reads a detached loader-neutral fluid value.
     *
     * @param stack bucket stack to inspect
     * @return the stored fluid, or {@link StoredFluid#EMPTY} when the stack is not in fluid mode
     */
    public static StoredFluid getStoredFluid(ItemStack stack) {
        FluidContent fluid = stack.get(ModDataComponentTypes.FLUID_CONTENT);
        if (fluid == null) return StoredFluid.EMPTY;
        return new StoredFluid(fluid.fluid(), fluid.amount(), fluid.variant());
    }

    /**
     * Selects fluid mode and replaces the serialized fluid payload, discarding any other content
     * payload first so the stack is left holding only this fluid.
     *
     * @param stack bucket stack to mutate in place
     * @param fluid fluid to store; an empty value clears the stack to canonical empty state
     */
    public static void setStoredFluid(ItemStack stack, StoredFluid fluid) {
        if (fluid.isEmpty()) {
            clearBucket(stack);
            return;
        }
        if (!(stack.getItem() instanceof BBItem) && !(stack.getItem() instanceof SBItem)) {
            throw new IllegalArgumentException("Fluid may only be stored in a finite or Source Bucket");
        }
        requireFiniteAmount(fluid.amount(), "Fluid amount");
        if (stack.getItem() instanceof BBItem bucket && fluid.amount() > bucket.getCapacityMb()) {
            throw new IllegalArgumentException("Fluid amount exceeds bucket capacity: " + fluid.amount());
        }
        if (stack.getItem() instanceof SBItem && fluid.amount() != 1_000) {
            throw new IllegalArgumentException("Source Bucket fluid assignment must be exactly 1000 mB");
        }
        clearContent(stack);
        stack.set(ModDataComponentTypes.FLUID_CONTENT,
                new FluidContent(fluid.fluid(), fluid.amount(), fluid.components()));
        afterMutation(stack);
    }

    /**
     * Selects milk mode and writes its amount in millibuckets, discarding any other content payload
     * first.
     *
     * @param stack bucket stack to mutate in place
     * @param mb milk amount in millibuckets, a whole number of buckets; zero clears the stack to
     *           canonical empty state
     * @throws IllegalArgumentException if {@code mb} is negative or not a whole number of buckets
     */
    public static void setMilkAmount(ItemStack stack, int mb) {
        if (mb == 0) {
            clearBucket(stack);
            return;
        }
        if (!(stack.getItem() instanceof BBItem) && !(stack.getItem() instanceof SBItem)) {
            throw new IllegalArgumentException("Milk may only be stored in a finite or Source Bucket");
        }
        ModDataComponentTypes.validateMilkAmount(mb).getOrThrow(IllegalArgumentException::new);
        if (stack.getItem() instanceof BBItem bucket && mb > bucket.getCapacityMb()) {
            throw new IllegalArgumentException("Milk amount exceeds bucket capacity: " + mb);
        }
        if (stack.getItem() instanceof SBItem && mb != 1_000) {
            throw new IllegalArgumentException("Source Bucket milk assignment must be exactly 1000 mB");
        }
        clearContent(stack);
        stack.set(ModDataComponentTypes.MILK_AMOUNT, mb);
        afterMutation(stack);
    }

    /** Returns the stored powder-snow block count, or zero when the stack is not in powder mode. */
    public static int getPowderUnits(ItemStack stack) {
        Integer units = stack.get(ModDataComponentTypes.POWDER_UNITS);
        return units != null ? units : 0;
    }

    /**
     * Selects powder-snow mode and writes its block count, discarding any other content payload
     * first.
     *
     * @param stack bucket stack to mutate in place
     * @param units powder-snow block count; zero clears the stack to canonical empty state
     * @throws IllegalArgumentException if {@code units} is negative
     */
    public static void setPowderUnits(ItemStack stack, int units) {
        requireNonNegative(units, "Powder-snow units");
        if (units == 0) {
            clearBucket(stack);
            return;
        }
        if (!(stack.getItem() instanceof BBItem bucket)) {
            throw new IllegalArgumentException("Powder snow may only be stored in a finite bucket");
        }
        if (units > bucket.getCapacityUnits()) {
            throw new IllegalArgumentException("Powder-snow units exceed bucket capacity: " + units);
        }
        clearContent(stack);
        stack.set(ModDataComponentTypes.POWDER_UNITS, units);
        afterMutation(stack);
    }

    /**
     * Removes up to {@code requestedAmount} millibuckets from fluid or milk mode. Removing the final
     * amount clears the content payload while preserving stored-item state.
     *
     * @param stack bucket stack to mutate in place
     * @param requestedAmount millibuckets to remove; a nonpositive value is a no-op
     * @return the amount actually removed in millibuckets; zero for any other mode or a nonpositive
     *         request, with no mutation
     * @throws IllegalArgumentException if a milk request would leave a partial bucket of milk
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
            setMilkAmount(stack, milk - removed);
            return removed;
        }
        return 0;
    }

    /** Returns the number of stored mob snapshots, or zero when the stack is not in entity mode. */
    public static int getEntityCount(ItemStack stack) {
        CapturedMobs mobs = stack.get(ModDataComponentTypes.CAPTURED_MOBS);
        return mobs == null ? 0 : mobs.entities().size();
    }

    /**
     * Selects entity mode and appends one bucket-format entity snapshot, preserving any snapshots
     * already stored and discarding any other content payload first.
     *
     * @param stack bucket stack to mutate in place
     * @param entityTypeId registry id of the captured entity type
     * @param bucketTag the snapshot compound, stored directly rather than copied
     */
    public static void addEntitySnapshot(ItemStack stack, String entityTypeId, CompoundTag bucketTag) {
        if (!(stack.getItem() instanceof MBItem)) {
            throw new IllegalArgumentException("Captured mobs may only be stored in a Mob Bucket");
        }
        CapturedMobs current = stack.get(ModDataComponentTypes.CAPTURED_MOBS);
        List<CompoundTag> entities = current == null
                ? new ArrayList<>() : new ArrayList<>(current.entities());
        if (entities.size() >= MBItem.MAX_MOBS) {
            throw new IllegalArgumentException("Too many captured mobs: " + (entities.size() + 1));
        }
        ResourceLocation entityType = ResourceLocation.parse(entityTypeId);
        if (current != null && !current.entityType().equals(entityType)) {
            throw new IllegalArgumentException("A Mob Bucket may only contain one entity type");
        }
        entities.add(bucketTag);
        clearContent(stack);
        stack.set(ModDataComponentTypes.CAPTURED_MOBS,
                new CapturedMobs(entityType, List.copyOf(entities)));
        afterMutation(stack);
    }

    /**
     * Returns a detached copy of the first entity snapshot without changing the stack.
     *
     * @param stack bucket stack to inspect
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
     * @param stack bucket stack to mutate in place
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
     * @param stack bucket stack to inspect
     * @return the registered type, or {@code null} when none is stored or the id is unknown
     */
    @Nullable
    public static EntityType<?> getCurrentEntityType(ItemStack stack) {
        CapturedMobs mobs = stack.get(ModDataComponentTypes.CAPTURED_MOBS);
        return mobs == null ? null
                : BuiltInRegistries.ENTITY_TYPE.getOptional(mobs.entityType()).orElse(null);
    }

    /**
     * Deserializes stored junk contents into a detached, mutable list of detached stacks.
     *
     * @param container storage-bucket stack to read
     * @return a new list holding a copy of every stored stack; empty when nothing is stored
     */
    public static List<ItemStack> getStoredItems(ItemStack container) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        List<ItemStack> result = new ArrayList<>();
        if (junk == null) return result;
        for (ItemStack stack : junk.items()) {
            result.add(stack.copy());
        }
        return result;
    }

    /**
     * Returns the stored junk-entry count.
     *
     * @param container storage-bucket stack to inspect
     * @return the number of occupied stack entries
     */
    public static int getStoredItemCount(ItemStack container) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        return junk == null ? 0 : junk.items().size();
    }

    /**
     * Returns the number of saved storage-bucket entries set aside because they cannot currently be
     * stored.
     *
     * @param container storage-bucket stack to inspect
     * @return the set-aside entry count
     */
    public static int getSetAsideCount(ItemStack container) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        return junk == null ? 0 : junk.setAside().size();
    }

    /**
     * Moves set-aside entries that decode to storable stacks again back into the bucket's contents,
     * oldest first, while it has room. Runs as the stack is decoded, when the enclosing item and so
     * its capacity are known.
     *
     * @param container stack to settle in place
     */
    public static void restoreSetAside(ItemStack container) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        if (junk == null || junk.setAside().isEmpty() || !(container.getItem() instanceof JBItem bucket)) return;
        List<ItemStack> items = new ArrayList<>(junk.items());
        List<SetAside> remaining = new ArrayList<>();
        for (SetAside entry : junk.setAside()) {
            if (entry instanceof SetAside.Restorable restorable && items.size() < bucket.getCapacity()) {
                items.add(restorable.stack());
            } else {
                remaining.add(entry);
            }
        }
        int restored = junk.setAside().size() - remaining.size();
        if (restored == 0) return;
        container.set(ModDataComponentTypes.JUNK_CONTENTS, new JunkContents(items, junk.layoutSeed(), remaining));
        afterMutation(container);
        SomeBuckets.LOGGER.info("Restored {} set-aside entries to {}", restored, container);
    }

    /**
     * Returns the raw stored-junk component for change detection by client render caches. Bucket
     * state writers replace the component wholesale on every edit, so its object identity changes
     * when the stored items or layout seed change. The returned component exposes mutable item
     * stacks and must be treated as read-only.
     *
     * @param container storage-bucket stack to inspect
     * @return the current {@link JunkContents}, or {@code null} when no junk items are stored
     */
    @Nullable
    public static JunkContents getStoredItemsComponent(ItemStack container) {
        return container.get(ModDataComponentTypes.JUNK_CONTENTS);
    }

    /**
     * Checks the invariants that relate state components to the item holding them: content kinds
     * are exclusive, each component sits on an item that can hold it, amounts fit that item's
     * capacity, and stored junk entries are storable by {@link JBItem#canStoreByVanillaRules}.
     * Value bounds are enforced by the component codecs. Unresolved captured entity ids remain valid
     * so removing another mod does not destroy mobs. Does not change {@code stack}.
     *
     * @param stack stack to inspect
     * @return an explanation when the stack is malformed, otherwise empty
     */
    private static Optional<String> validationError(ItemStack stack) {
        FluidContent fluid = stack.get(ModDataComponentTypes.FLUID_CONTENT);
        Integer milk = stack.get(ModDataComponentTypes.MILK_AMOUNT);
        Integer powder = stack.get(ModDataComponentTypes.POWDER_UNITS);
        CapturedMobs mobs = stack.get(ModDataComponentTypes.CAPTURED_MOBS);
        JunkContents junk = stack.get(ModDataComponentTypes.JUNK_CONTENTS);

        int contentKinds = (fluid == null ? 0 : 1) + (milk == null ? 0 : 1)
                + (powder == null ? 0 : 1) + (mobs == null ? 0 : 1);
        if (contentKinds > 1) return Optional.of("multiple mutually exclusive content components");

        if (fluid != null) {
            if (!(stack.getItem() instanceof BBItem) && !(stack.getItem() instanceof SBItem)) {
                return Optional.of("fluid component on an incompatible item");
            }
            if (stack.getItem() instanceof BBItem bucket && fluid.amount() > bucket.getCapacityMb()) {
                return Optional.of("fluid amount exceeds the bucket capacity");
            }
            if (stack.getItem() instanceof SBItem && fluid.amount() != 1_000) {
                return Optional.of("Source Bucket fluid amount is not exactly one bucket");
            }
        }

        if (milk != null) {
            if (!(stack.getItem() instanceof BBItem) && !(stack.getItem() instanceof SBItem)) {
                return Optional.of("milk component on an incompatible item");
            }
            if (stack.getItem() instanceof BBItem bucket && milk > bucket.getCapacityMb()) {
                return Optional.of("milk amount exceeds the bucket capacity");
            }
            if (stack.getItem() instanceof SBItem && milk != 1_000) {
                return Optional.of("Source Bucket milk amount is not exactly one bucket");
            }
        }

        if (powder != null) {
            if (!(stack.getItem() instanceof BBItem bucket)) {
                return Optional.of("powder-snow component on an incompatible item");
            }
            if (powder > bucket.getCapacityUnits()) {
                return Optional.of("powder-snow amount exceeds the bucket capacity");
            }
        }

        if (mobs != null && !(stack.getItem() instanceof MBItem)) {
            return Optional.of("captured-mob component on an incompatible item");
        }

        if (junk != null) {
            if (!(stack.getItem() instanceof JBItem bucket)) {
                return Optional.of("stored-item component on an incompatible item");
            }
            if (junk.items().isEmpty() && junk.setAside().isEmpty()) {
                return Optional.of("stored-item component holds nothing");
            }
            if (junk.items().size() > bucket.getCapacity()) {
                return Optional.of("stored-item count exceeds the bucket capacity");
            }
            for (ItemStack stored : junk.items()) {
                if (!JBItem.canStoreByVanillaRules(stored) || stored.getCount() > stored.getMaxStackSize()) {
                    return Optional.of("stored item is empty, oversized, nested, or inventory-bearing");
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Removes malformed Some Buckets state as a fail-closed admission action, checking stored items
     * with {@link JBItem#canStoreByVanillaRules} rather than the loader item-inventory lookup.
     * Independent of loader and level state, so it is safe while a stack is being decoded.
     *
     * @param stack stack to normalize
     */
    public static void discardInvalidStructure(ItemStack stack) {
        Optional<String> error = validationError(stack);
        if (error.isEmpty()) return;
        SomeBuckets.LOGGER.warn("Discarding invalid Some Buckets state from {}: {}", stack, error.get());
        clearContent(stack);
        stack.remove(ModDataComponentTypes.JUNK_CONTENTS);
        afterMutation(stack);
    }

    /**
     * Replaces stored junk contents with the nonempty entries in {@code items}, keeping the existing
     * layout seed and set-aside entries.
     *
     * @param container storage-bucket stack to mutate in place
     * @param items new contents; each nonempty entry is stored as a copy, and an empty list removes
     *              the junk payload unless set-aside entries remain
     */
    public static void setStoredItems(ItemStack container, List<ItemStack> items) {
        if (!(container.getItem() instanceof JBItem bucket)) {
            throw new IllegalArgumentException("Stored items require a Junk or Trash Bucket");
        }
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                if (!JBItem.canStore(stack)) {
                    throw new IllegalArgumentException("Item may not be stored in a storage bucket: " + stack);
                }
                if (stack.getCount() > stack.getMaxStackSize()) {
                    throw new IllegalArgumentException("Stored item stack exceeds its maximum size: " + stack);
                }
                kept.add(stack.copy());
            }
        }
        if (kept.size() > bucket.getCapacity()) {
            throw new IllegalArgumentException("Stored item list exceeds bucket capacity: " + kept.size());
        }
        JunkContents existing = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        List<SetAside> setAside = existing == null ? List.of() : existing.setAside();
        if (kept.isEmpty() && setAside.isEmpty()) {
            container.remove(ModDataComponentTypes.JUNK_CONTENTS);
        } else {
            container.set(ModDataComponentTypes.JUNK_CONTENTS,
                    new JunkContents(kept, existing == null ? 0L : existing.layoutSeed(), setAside));
        }
        afterMutation(container);
    }

    /** Replaces the render-layout seed without changing stored items. */
    public static void setJunkLayoutSeed(ItemStack container, long layoutSeed) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        if (junk == null) return;
        container.set(ModDataComponentTypes.JUNK_CONTENTS,
                new JunkContents(junk.items(), layoutSeed, junk.setAside()));
    }

    /** Returns the stored render-layout seed, or zero for an empty storage bucket. */
    public static long getJunkLayoutSeed(ItemStack container) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        return junk == null ? 0L : junk.layoutSeed();
    }

    /**
     * Advances the render-layout seed from insertion state shared by client and server.
     * Registry-id characters are mixed directly, avoiding identity-dependent object hashes.
     */
    public static void advanceJunkLayout(ItemStack container, ItemStack incoming, int amountMoved) {
        JunkContents junk = container.get(ModDataComponentTypes.JUNK_CONTENTS);
        if (junk == null || amountMoved <= 0) return;
        setJunkLayoutSeed(container, nextJunkLayoutSeed(
                junk.layoutSeed(), incoming, amountMoved, junk.items().size()));
    }

    /** Pure transition used when several insertions are accumulated before storage is committed. */
    public static long nextJunkLayoutSeed(long previousSeed, ItemStack incoming,
                                          int amountMoved, int resultingEntryCount) {
        long input = BuiltInRegistries.ITEM.getKey(incoming.getItem()).hashCode()
                ^ Integer.toUnsignedLong(amountMoved) * RandomSupport.GOLDEN_RATIO_64
                ^ Integer.toUnsignedLong(resultingEntryCount) * ENTRY_COUNT_MULTIPLIER;
        long next = RandomSupport.mixStafford13(previousSeed ^ input);
        return next == previousSeed ? next ^ RandomSupport.SILVER_RATIO_64 : next;
    }

    /**
     * Clears fluid, milk, powder-snow, and entity state while preserving stored junk items and
     * unrelated components.
     *
     * @param stack bucket stack to mutate in place
     */
    public static void clearBucket(ItemStack stack) {
        clearContent(stack);
        afterMutation(stack);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative: " + value);
    }

    private static void requireFiniteAmount(int value, String name) {
        if (value < 1 || value > ModDataComponentTypes.MAX_FINITE_AMOUNT_MB) {
            throw new IllegalArgumentException(name + " must be between 1 and "
                    + ModDataComponentTypes.MAX_FINITE_AMOUNT_MB + ": " + value);
        }
    }

    /**
     * Overwrites {@code target}'s count and entire bucket state with {@code source}'s, in place: every
     * registered bucket-state component plus the derived {@code minecraft:max_stack_size} and
     * {@code minecraft:consumable} components. Settles a working copy back onto the real stack a
     * transaction or held transfer operates on.
     *
     * @param source stack to copy state from
     * @param target stack to overwrite in place
     */
    public static void copyState(ItemStack source, ItemStack target) {
        target.setCount(source.getCount());
        ModDataComponentTypes.forEach((id, type) -> copyComponent(source, target, type));
        copyComponent(source, target, DataComponents.MAX_STACK_SIZE);
        copyComponent(source, target, DataComponents.CONSUMABLE);
    }

    private static <T> void copyComponent(ItemStack source, ItemStack target, DataComponentType<T> type) {
        T value = source.get(type);
        if (value == null) {
            target.remove(type);
        } else {
            target.set(type, value);
        }
    }
}
