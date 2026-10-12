package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The sole reader and writer of every bucket family's persistent state, kept in the stack's NBT.
 * Mutators edit the supplied stack in place, preserve unrelated root NBT, canonicalize empty state,
 * and enforce the invariants that relate state to the item holding it: content kinds are exclusive,
 * each payload sits on an item that can hold it, and amounts fit that item's capacity.
 *
 * <p>When a stack is loaded from saved NBT, {@link #admitLoadedTag} sets aside Junk and Trash
 * Bucket entries that no longer load (for example, items from a removed mod), restores set-aside
 * entries that load again while the bucket has room, and logs and removes malformed state.
 */
public final class BucketState {

    /** Identifies which mutually exclusive payload family a bucket's root NBT contains. */
    public enum Mode {
        /** No mode marker, or an unrecognized serialized marker. */
        NONE("none"),
        /** A finite or source fluid payload stored in {@link BucketState#FLUID_STACK}. */
        FLUID("fluid"),
        /** A milk amount stored in {@link BucketState#AMOUNT}. */
        MILK("milk"),
        /** A powder-snow block count stored in {@link BucketState#POWDER_UNITS}. */
        POWDER_SNOW("powder_snow"),
        /** One or more captured entity snapshots stored in {@link BucketState#ENTITIES}. */
        ENTITY("entity");

        private final String serializedName;

        Mode(String serializedName) {
            this.serializedName = serializedName;
        }

        private String toNbt() {
            return serializedName;
        }

        /** Returns the matching mode, or {@link #NONE} when the serialized value is unknown. */
        public static Mode fromNbt(String value) {
            for (Mode mode : values()) {
                if (mode.serializedName.equals(value)) return mode;
            }
            return NONE;
        }
    }

    public static final String MODE = "Mode";
    public static final String AMOUNT = "Amount";
    public static final String FLUID_STACK = "FluidStack";
    public static final String POWDER_UNITS = "Powder";
    public static final String ENTITY_TYPE = "EntityType";
    public static final String ENTITIES = "Entities";
    public static final String JUNK_ITEMS = "JunkItems";
    /** Saved Junk and Trash Bucket entries that do not currently load, kept verbatim, oldest first. */
    public static final String JUNK_SET_ASIDE = "JunkSetAside";
    private static final String JUNK_LAYOUT_SEED = "JunkLayoutSeed";

    // Loader-neutral fluid payloads use the same field layout as Forge FluidStack compounds.
    private static final String FLUID_NAME = "FluidName";
    private static final String FLUID_AMOUNT = "Amount";
    private static final String FLUID_TAG = "Tag";

    private BucketState() {}

    /** Returns the stored payload mode; missing or unrecognized state is treated as {@link Mode#NONE}. */
    public static Mode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? Mode.NONE : Mode.fromNbt(tag.getString(MODE));
    }

    /**
     * Returns whether the stack has no mode-based payload, no stored junk items, and no set-aside
     * junk entries.
     */
    public static boolean isEmptyBucket(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return getMode(stack) == Mode.NONE
                && (tag == null || tag.getList(JUNK_ITEMS, Tag.TAG_COMPOUND).isEmpty()
                && tag.getList(JUNK_SET_ASIDE, Tag.TAG_COMPOUND).isEmpty());
    }

    private static void setMode(ItemStack stack, Mode mode) {
        stack.getOrCreateTag().putString(MODE, mode.toNbt());
    }

    /**
     * Returns the finite fluid amount for fluid mode, otherwise the root {@link #AMOUNT} value.
     * Amounts use millibuckets.
     */
    public static int getAmount(ItemStack stack) {
        if (getMode(stack) == Mode.FLUID) return getStoredFluid(stack).amount();
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(AMOUNT);
    }

    /**
     * Reads a detached loader-neutral fluid value. Missing, empty, malformed, or unregistered fluid
     * state returns {@link StoredFluid#EMPTY}.
     */
    public static StoredFluid getStoredFluid(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(FLUID_STACK, Tag.TAG_COMPOUND)) return StoredFluid.EMPTY;

        CompoundTag fluidTag = root.getCompound(FLUID_STACK);
        ResourceLocation id = ResourceLocation.tryParse(fluidTag.getString(FLUID_NAME));
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return StoredFluid.EMPTY;

        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        int amount = fluidTag.getInt(FLUID_AMOUNT);
        if (fluid == Fluids.EMPTY || amount <= 0) return StoredFluid.EMPTY;
        CompoundTag variant = fluidTag.contains(FLUID_TAG, Tag.TAG_COMPOUND)
                ? fluidTag.getCompound(FLUID_TAG) : null;
        return new StoredFluid(fluid, amount, variant);
    }

    /**
     * Selects fluid mode and replaces the serialized fluid payload, or clears empty content.
     *
     * @throws IllegalArgumentException if the stack cannot hold that much fluid
     */
    public static void setStoredFluid(ItemStack stack, StoredFluid fluid) {
        if (!fluid.isEmpty()) requireValid(fluidError(stack.getItem(), fluid.amount()));
        clearBucket(stack);
        if (fluid.isEmpty()) return;

        setMode(stack, Mode.FLUID);
        CompoundTag root = stack.getOrCreateTag();
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.fluid());
        CompoundTag fluidTag = new CompoundTag();
        fluidTag.putString(FLUID_NAME, id.toString());
        fluidTag.putInt(FLUID_AMOUNT, fluid.amount());
        CompoundTag variant = fluid.variantTag();
        if (variant != null && !variant.isEmpty()) fluidTag.put(FLUID_TAG, variant);
        root.put(FLUID_STACK, fluidTag);
    }

    /**
     * Selects milk mode and writes its positive amount in millibuckets, or clears zero content.
     *
     * @throws IllegalArgumentException if {@code mb} is negative or exceeds what the stack holds
     */
    public static void setMilkAmount(ItemStack stack, int mb) {
        requireNonNegative(mb, "Milk amount");
        if (mb > 0) requireValid(milkError(stack.getItem(), mb));
        clearBucket(stack);
        if (mb == 0) return;
        setMode(stack, Mode.MILK);
        stack.getOrCreateTag().putInt(AMOUNT, mb);
    }

    public static int getPowderUnits(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(POWDER_UNITS);
    }

    /**
     * Selects powder-snow mode and writes its positive block count, or clears zero content.
     *
     * @throws IllegalArgumentException if {@code units} is negative or exceeds what the stack holds
     */
    public static void setPowderUnits(ItemStack stack, int units) {
        requireNonNegative(units, "Powder-snow units");
        if (units > 0) requireValid(powderError(stack.getItem(), units));
        clearBucket(stack);
        if (units == 0) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(MODE, Mode.POWDER_SNOW.toNbt());
        tag.putInt(POWDER_UNITS, units);
    }

    /**
     * Removes up to {@code requestedAmount} millibuckets from fluid or milk mode.
     *
     * <p>Other modes and nonpositive requests return zero without mutation. Removing the final amount
     * clears the mode-based payload while preserving unrelated and stored-item NBT.
     *
     * @return the amount actually removed, in millibuckets
     */
    public static int drainFiniteContent(ItemStack stack, int requestedAmount) {
        if (requestedAmount <= 0) return 0;

        Mode mode = getMode(stack);
        int currentAmount;
        if (mode == Mode.FLUID) {
            StoredFluid current = getStoredFluid(stack);
            if (current.isEmpty()) return 0;
            currentAmount = current.amount();
        } else if (mode == Mode.MILK) {
            currentAmount = getAmount(stack);
        } else {
            return 0;
        }

        int removedAmount = Math.min(currentAmount, requestedAmount);
        int remainingAmount = currentAmount - removedAmount;
        if (mode == Mode.FLUID) {
            setStoredFluid(stack, getStoredFluid(stack).withAmount(remainingAmount));
        } else {
            setMilkAmount(stack, remainingAmount);
        }
        return removedAmount;
    }

    public static int getEntityCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getList(ENTITIES, Tag.TAG_COMPOUND).size();
    }

    /**
     * Selects entity mode and appends one bucket-format entity snapshot. The supplied compound is
     * stored directly rather than copied.
     *
     * @throws IllegalArgumentException if the stack is not a Mob Bucket or is already full
     */
    public static void addEntitySnapshot(ItemStack stack, String entityTypeId, CompoundTag bucketTag) {
        requireValid(entitiesError(stack.getItem(), getEntityCount(stack) + 1));
        ListTag list = stack.getOrCreateTag().getList(ENTITIES, Tag.TAG_COMPOUND);
        clearBucket(stack);
        setMode(stack, Mode.ENTITY);
        CompoundTag root = stack.getOrCreateTag();
        root.putString(ENTITY_TYPE, entityTypeId);
        list.add(bucketTag);
        root.put(ENTITIES, list);
    }

    /**
     * Returns a detached copy of the first entity snapshot without changing the stack.
     *
     * @return the snapshot, or an empty compound when none is stored
     */
    public static CompoundTag copyFirstEntitySnapshot(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return new CompoundTag();
        ListTag list = tag.getList(ENTITIES, Tag.TAG_COMPOUND);
        return list.isEmpty() ? new CompoundTag() : list.getCompound(0).copy();
    }

    /**
     * Removes and returns a detached copy of the first entity snapshot. Removing the final snapshot
     * also clears entity mode and its header.
     *
     * @return the removed snapshot, or an empty compound when none is stored
     */
    public static CompoundTag removeFirstEntitySnapshot(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return new CompoundTag();
        ListTag list = tag.getList(ENTITIES, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return new CompoundTag();
        CompoundTag out = list.getCompound(0).copy();
        list.remove(0);
        if (list.isEmpty()) {
            clearBucket(stack);
        } else {
            tag.put(ENTITIES, list);
        }
        return out;
    }

    /**
     * Resolves the recorded entity type.
     *
     * @return the registered type, or {@code null} when the header is absent, malformed, or unknown
     */
    @Nullable
    public static EntityType<?> getCurrentEntityType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(ENTITY_TYPE));
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    /**
     * Deserializes stored junk contents into a detached, mutable list of detached stacks. Set-aside
     * entries are not included.
     */
    public static List<ItemStack> getStoredItems(ItemStack container) {
        List<ItemStack> result = new ArrayList<>();
        CompoundTag tag = container.getTag();
        if (tag == null) return result;
        ListTag tagList = tag.getList(JUNK_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            ItemStack stack = ItemStack.of(tagList.getCompound(i));
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    /** Returns the number of occupied junk entries, not counting set-aside entries. */
    public static int getStoredItemCount(ItemStack container) {
        CompoundTag tag = container.getTag();
        return tag == null ? 0 : tag.getList(JUNK_ITEMS, Tag.TAG_COMPOUND).size();
    }

    /** Returns the number of saved junk entries set aside because they do not currently load. */
    public static int getSetAsideCount(ItemStack container) {
        CompoundTag tag = container.getTag();
        return tag == null ? 0 : tag.getList(JUNK_SET_ASIDE, Tag.TAG_COMPOUND).size();
    }

    /**
     * Replaces stored junk contents with the nonempty entries in {@code items}, keeping set-aside
     * entries. If no entries remain, the stored-item list and layout seed are removed. Unrelated and
     * mode-based NBT is preserved.
     *
     * @throws IllegalArgumentException if {@code container} is not a Junk or Trash Bucket, an entry
     *         is not storable or exceeds its stack limit, or the entries exceed bucket capacity
     */
    public static void setStoredItems(ItemStack container, List<ItemStack> items) {
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) kept.add(stack);
        }
        requireValid(storedItemsError(container.getItem(), kept));

        ListTag out = new ListTag();
        for (ItemStack stack : kept) {
            out.add(stack.save(new CompoundTag()));
        }
        if (out.isEmpty()) {
            CompoundTag tag = container.getTag();
            if (tag != null) {
                tag.remove(JUNK_ITEMS);
                tag.remove(JUNK_LAYOUT_SEED);
                removeTagIfEmpty(container, tag);
            }
        } else {
            container.getOrCreateTag().put(JUNK_ITEMS, out);
        }
    }

    /** Returns the stored junk-layout seed, or zero when the stack has no seed. */
    public static long getJunkLayoutSeed(ItemStack container) {
        CompoundTag tag = container.getTag();
        return tag == null ? 0L : tag.getLong(JUNK_LAYOUT_SEED);
    }

    /** Replaces the junk-layout seed without changing stored items. */
    public static void rerollJunkLayout(ItemStack container) {
        container.getOrCreateTag().putLong(JUNK_LAYOUT_SEED,
                ThreadLocalRandom.current().nextLong());
    }

    /**
     * Clears fluid, milk, powder-snow, and entity state while preserving stored junk items, their
     * layout seed, set-aside entries, and unrelated NBT.
     */
    public static void clearBucket(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        clearContent(tag);
        removeTagIfEmpty(stack, tag);
    }

    // ---- Load-time admission ----

    /**
     * Admits the NBT of a {@code item} stack as it is loaded from saved data. Junk entries that do
     * not load move to {@link #JUNK_SET_ASIDE}, logged once as they move; set-aside entries that load
     * again return, oldest first, while the bucket has room; entries beyond capacity are set aside
     * rather than lost. Malformed mode-based state is logged with its full payload and removed.
     * Unresolved fluid and entity ids are left in place, so removing another mod does not destroy
     * them. Mutates {@code tag} in place.
     */
    public static void admitLoadedTag(Item item, CompoundTag tag) {
        Optional<String> contentError = contentError(item, tag);
        if (contentError.isPresent()) {
            SomeBuckets.LOGGER.warn("Removing malformed {} state ({}): {}",
                    BuiltInRegistries.ITEM.getKey(item), contentError.get(), tag);
            clearContent(tag);
        }

        boolean hasJunk = tag.contains(JUNK_ITEMS) || tag.contains(JUNK_SET_ASIDE);
        if (!hasJunk) return;
        if (!(item instanceof JBItem bucket)) {
            SomeBuckets.LOGGER.warn("Removing stored items from incompatible item {}: {}",
                    BuiltInRegistries.ITEM.getKey(item), tag);
            tag.remove(JUNK_ITEMS);
            tag.remove(JUNK_SET_ASIDE);
            tag.remove(JUNK_LAYOUT_SEED);
            return;
        }

        ListTag entries = tag.getList(JUNK_ITEMS, Tag.TAG_COMPOUND);
        ListTag setAside = tag.getList(JUNK_SET_ASIDE, Tag.TAG_COMPOUND);
        ListTag kept = new ListTag();
        ListTag aside = new ListTag();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (kept.size() < bucket.getCapacity() && loadsAsStorable(entry)) {
                kept.add(entry);
            } else {
                SomeBuckets.LOGGER.warn("Setting aside {} entry that cannot be stored: {}",
                        BuiltInRegistries.ITEM.getKey(item), entry);
                aside.add(entry);
            }
        }
        boolean restored = false;
        for (int i = 0; i < setAside.size(); i++) {
            CompoundTag entry = setAside.getCompound(i);
            if (kept.size() < bucket.getCapacity() && loadsAsStorable(entry)) {
                kept.add(entry);
                restored = true;
            } else {
                aside.add(entry);
            }
        }
        if (restored) {
            SomeBuckets.LOGGER.info("Restored set-aside {} entries that load again",
                    BuiltInRegistries.ITEM.getKey(item));
        }

        if (kept.isEmpty()) {
            tag.remove(JUNK_ITEMS);
            tag.remove(JUNK_LAYOUT_SEED);
        } else {
            tag.put(JUNK_ITEMS, kept);
        }
        if (aside.isEmpty()) {
            tag.remove(JUNK_SET_ASIDE);
        } else {
            tag.put(JUNK_SET_ASIDE, aside);
        }
    }

    private static boolean loadsAsStorable(CompoundTag entry) {
        return JBItem.isStorableEntry(ItemStack.of(entry));
    }

    // ---- Invariants ----

    /*
     * Checks mode-based state against the item holding it: exactly one recognized payload, on an
     * item that can hold it, within that item's capacity. Unregistered fluid and entity ids are
     * accepted.
     */
    private static Optional<String> contentError(Item item, CompoundTag tag) {
        Mode mode = Mode.fromNbt(tag.getString(MODE));
        if (mode == Mode.NONE) {
            boolean stray = tag.contains(MODE) || tag.contains(AMOUNT) || tag.contains(FLUID_STACK)
                    || tag.contains(POWDER_UNITS) || tag.contains(ENTITY_TYPE) || tag.contains(ENTITIES);
            return stray ? Optional.of("content without a recognized mode") : Optional.empty();
        }
        return switch (mode) {
            case FLUID -> {
                CompoundTag fluid = tag.getCompound(FLUID_STACK);
                yield fluid.getInt(FLUID_AMOUNT) <= 0 ? Optional.of("fluid mode without fluid")
                        : fluidError(item, fluid.getInt(FLUID_AMOUNT));
            }
            case MILK -> tag.getInt(AMOUNT) <= 0 ? Optional.of("milk mode without milk")
                    : milkError(item, tag.getInt(AMOUNT));
            case POWDER_SNOW -> tag.getInt(POWDER_UNITS) <= 0 ? Optional.of("powder-snow mode without powder")
                    : powderError(item, tag.getInt(POWDER_UNITS));
            case ENTITY -> {
                int count = tag.getList(ENTITIES, Tag.TAG_COMPOUND).size();
                yield count == 0 ? Optional.of("entity mode without entities") : entitiesError(item, count);
            }
            case NONE -> Optional.empty();
        };
    }

    private static Optional<String> fluidError(Item item, int amount) {
        if (item instanceof BBItem bucket) {
            return amount > bucket.getCapacityMb()
                    ? Optional.of("fluid amount exceeds the bucket capacity") : Optional.empty();
        }
        if (item instanceof SBItem) {
            return amount != FluidBucketItem.BUCKET_VOLUME_MB
                    ? Optional.of("Source Bucket fluid amount is not exactly one bucket") : Optional.empty();
        }
        return Optional.of("fluid on an incompatible item");
    }

    private static Optional<String> milkError(Item item, int amount) {
        if (item instanceof BBItem bucket) {
            return amount > bucket.getCapacityMb()
                    ? Optional.of("milk amount exceeds the bucket capacity") : Optional.empty();
        }
        if (item instanceof SBItem) {
            return amount != FluidBucketItem.BUCKET_VOLUME_MB
                    ? Optional.of("Source Bucket milk amount is not exactly one bucket") : Optional.empty();
        }
        return Optional.of("milk on an incompatible item");
    }

    private static Optional<String> powderError(Item item, int units) {
        if (!(item instanceof BBItem bucket)) return Optional.of("powder snow on an incompatible item");
        return units > bucket.getCapacityUnits()
                ? Optional.of("powder-snow amount exceeds the bucket capacity") : Optional.empty();
    }

    private static Optional<String> entitiesError(Item item, int count) {
        if (!(item instanceof MBItem)) return Optional.of("captured mobs on an incompatible item");
        return count > MBItem.MAX_MOBS
                ? Optional.of("captured-mob count exceeds the bucket capacity") : Optional.empty();
    }

    private static Optional<String> storedItemsError(Item item, List<ItemStack> items) {
        if (!(item instanceof JBItem bucket)) return Optional.of("stored items on an incompatible item");
        if (items.size() > bucket.getCapacity()) return Optional.of("stored-item count exceeds the bucket capacity");
        for (ItemStack stored : items) {
            if (!JBItem.isStorableEntry(stored)) {
                return Optional.of("stored item is oversized, nested, or inventory-bearing");
            }
        }
        return Optional.empty();
    }

    private static void requireValid(Optional<String> error) {
        error.ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative: " + value);
    }

    private static void clearContent(CompoundTag tag) {
        tag.remove(MODE);
        tag.remove(AMOUNT);
        tag.remove(FLUID_STACK);
        tag.remove(POWDER_UNITS);
        tag.remove(ENTITY_TYPE);
        tag.remove(ENTITIES);
    }

    private static void removeTagIfEmpty(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) stack.setTag(null);
    }
}
