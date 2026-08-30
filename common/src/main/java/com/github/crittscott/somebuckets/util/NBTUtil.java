package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.item.VariableStackItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Serializes, deserializes, and normalizes the persistent state of all bucket families. Mutators edit
 * the supplied stack in place and preserve unrelated custom data unless their contract says otherwise.
 */
public final class NBTUtil {

    /** Identifies which mutually exclusive payload family a bucket's root NBT contains. */
    public enum Mode {
        /** No mode marker, or an unrecognized serialized marker. */
        NONE("none"),
        /** A finite or source fluid payload stored in {@link NBTUtil#FLUID_STACK}. */
        FLUID("fluid"),
        /** A milk amount stored in {@link NBTUtil#AMOUNT}. */
        MILK("milk"),
        /** A powder-snow block count stored in {@link NBTUtil#POWDER_UNITS}. */
        POWDER_SNOW("powder_snow"),
        /** One or more captured entity snapshots stored in {@link NBTUtil#ENTITIES}. */
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
    private static final String STORED_ITEMS = "JunkItems";
    private static final String JUNK_LAYOUT_SEED = "JunkLayoutSeed";

    // Loader-neutral fluid payloads use the same field layout as Forge FluidStack compounds.
    private static final String FLUID_NAME = "FluidName";
    private static final String FLUID_AMOUNT = "Amount";
    private static final String FLUID_TAG = "Tag";

    private NBTUtil() {}

    /** Returns a detached copy of the stack's custom data. */
    private static CompoundTag getData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    /** Replaces the stack's custom data, removing the component when no data remains. */
    private static void setData(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        if (stack.getItem() instanceof VariableStackItem) {
            stack.set(DataComponents.MAX_STACK_SIZE, isEmptyBucketData(tag)
                    ? VariableStackItem.EMPTY_STACK_SIZE
                    : VariableStackItem.FILLED_STACK_SIZE);
        }
    }

    private static Mode modeOf(CompoundTag data) {
        return Mode.fromNbt(data.getString(MODE));
    }

    private static void writeMode(CompoundTag data, Mode mode) {
        data.putString(MODE, mode.toNbt());
    }

    private static int storedItemCount(CompoundTag data) {
        return data.getList(STORED_ITEMS, Tag.TAG_COMPOUND).size();
    }

    private static int entityCount(CompoundTag data) {
        return data.getList(ENTITIES, Tag.TAG_COMPOUND).size();
    }

    private static boolean isEmptyBucketData(CompoundTag data) {
        return modeOf(data) == Mode.NONE && storedItemCount(data) == 0;
    }

    /** Strips every mode-based payload key while leaving stored junk items and unrelated NBT. */
    private static void clearModeKeys(CompoundTag data) {
        data.remove(MODE);
        data.remove(AMOUNT);
        data.remove(FLUID_STACK);
        data.remove(POWDER_UNITS);
        data.remove(ENTITY_TYPE);
        data.remove(ENTITIES);
    }

    /** Returns the stored payload mode; missing or unrecognized state is treated as {@link Mode#NONE}. */
    public static Mode getMode(ItemStack stack) {
        return modeOf(getData(stack));
    }

    /** Returns whether the stack has neither a mode-based payload nor stored junk items. */
    public static boolean isEmptyBucket(ItemStack stack) {
        return isEmptyBucketData(getData(stack));
    }

    /**
     * Returns the finite fluid amount for fluid mode, otherwise the root {@link #AMOUNT} value.
     * Amounts use millibuckets.
     */
    public static int getAmount(ItemStack stack) {
        CompoundTag data = getData(stack);
        if (modeOf(data) == Mode.FLUID) return storedFluidOf(data).amount();
        return data.getInt(AMOUNT);
    }

    private static void setAmount(ItemStack stack, int mb) {
        requireNonNegative(mb, "Amount");
        CompoundTag tag = getData(stack);
        tag.putInt(AMOUNT, mb);
        setData(stack, tag);
    }

    /**
     * Reads a detached loader-neutral fluid value. Missing, empty, malformed, or unregistered fluid
     * state returns {@link StoredFluid#EMPTY}.
     */
    public static StoredFluid getStoredFluid(ItemStack stack) {
        return storedFluidOf(getData(stack));
    }

    private static StoredFluid storedFluidOf(CompoundTag root) {
        if (!root.contains(FLUID_STACK, Tag.TAG_COMPOUND)) return StoredFluid.EMPTY;

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
     * Selects fluid mode and replaces the serialized fluid payload, or clears empty content. Any
     * other mode's payload is discarded first, so the stack is left holding only this fluid.
     */
    public static void setStoredFluid(ItemStack stack, StoredFluid fluid) {
        if (fluid.isEmpty()) {
            clearBucket(stack);
            return;
        }

        CompoundTag root = getData(stack);
        clearModeKeys(root);
        writeMode(root, Mode.FLUID);
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.fluid());
        CompoundTag fluidTag = new CompoundTag();
        fluidTag.putString(FLUID_NAME, id.toString());
        fluidTag.putInt(FLUID_AMOUNT, fluid.amount());
        CompoundTag variant = fluid.variantTag();
        if (variant != null && !variant.isEmpty()) fluidTag.put(FLUID_TAG, variant);
        root.put(FLUID_STACK, fluidTag);
        setData(stack, root);
    }

    /**
     * Selects milk mode and writes its positive amount in millibuckets, or clears zero content. Any
     * other mode's payload is discarded first, so the stack is left holding only milk.
     *
     * @throws IllegalArgumentException if {@code mb} is negative
     */
    public static void setMilkAmount(ItemStack stack, int mb) {
        requireNonNegative(mb, "Milk amount");
        if (mb == 0) {
            clearBucket(stack);
            return;
        }
        CompoundTag data = getData(stack);
        clearModeKeys(data);
        writeMode(data, Mode.MILK);
        data.putInt(AMOUNT, mb);
        setData(stack, data);
    }

    public static int getPowderUnits(ItemStack stack) {
        return getData(stack).getInt(POWDER_UNITS);
    }

    /**
     * Creates canonical root NBT for a bucket initialized with the given powder-snow block count.
     *
     * @throws IllegalArgumentException if {@code units} is negative
     */
    public static CompoundTag createPowderSnowTag(int units) {
        requireNonNegative(units, "Powder-snow units");
        CompoundTag tag = new CompoundTag();
        if (units > 0) writePowderSnow(tag, units);
        return tag;
    }

    /**
     * Selects powder-snow mode and writes its positive block count, or clears zero content. Any
     * other mode's payload is discarded first, so the stack is left holding only powder snow.
     *
     * @throws IllegalArgumentException if {@code units} is negative
     */
    public static void setPowderUnits(ItemStack stack, int units) {
        requireNonNegative(units, "Powder-snow units");
        if (units == 0) {
            clearBucket(stack);
            return;
        }
        CompoundTag tag = getData(stack);
        clearModeKeys(tag);
        writePowderSnow(tag, units);
        setData(stack, tag);
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

        CompoundTag data = getData(stack);
        Mode mode = modeOf(data);
        int currentAmount;
        StoredFluid currentFluid = null;
        if (mode == Mode.FLUID) {
            currentFluid = storedFluidOf(data);
            if (currentFluid.isEmpty()) return 0;
            currentAmount = currentFluid.amount();
        } else if (mode == Mode.MILK) {
            currentAmount = data.getInt(AMOUNT);
        } else {
            return 0;
        }

        int removedAmount = Math.min(currentAmount, requestedAmount);
        int remainingAmount = currentAmount - removedAmount;
        if (remainingAmount == 0) {
            clearBucket(stack);
        } else if (mode == Mode.FLUID) {
            setStoredFluid(stack, currentFluid.withAmount(remainingAmount));
        } else {
            setAmount(stack, remainingAmount);
        }
        return removedAmount;
    }

    public static int getEntityCount(ItemStack stack) {
        return entityCount(getData(stack));
    }

    /**
     * Selects entity mode and appends one bucket-format entity snapshot, preserving any snapshots
     * already stored. Any other mode's payload is discarded first. The supplied compound is stored
     * directly rather than copied.
     */
    public static void addEntitySnapshot(ItemStack stack, String entityTypeId, CompoundTag bucketTag) {
        CompoundTag root = getData(stack);
        ListTag list = root.getList(ENTITIES, Tag.TAG_COMPOUND);
        clearModeKeys(root);
        writeMode(root, Mode.ENTITY);
        root.putString(ENTITY_TYPE, entityTypeId);
        list.add(bucketTag);
        root.put(ENTITIES, list);
        setData(stack, root);
    }

    /**
     * Returns a detached copy of the first entity snapshot without changing the stack.
     *
     * @return the snapshot, or an empty compound when none is stored
     */
    public static CompoundTag copyFirstEntitySnapshot(ItemStack stack) {
        CompoundTag tag = getData(stack);
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
        CompoundTag tag = getData(stack);
        ListTag list = tag.getList(ENTITIES, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return new CompoundTag();
        CompoundTag out = list.getCompound(0).copy();
        list.remove(0);
        if (list.isEmpty()) {
            clearModeKeys(tag);
        } else {
            tag.put(ENTITIES, list);
        }
        setData(stack, tag);
        return out;
    }

    /**
     * Resolves the recorded entity type.
     *
     * @return the registered type, or {@code null} when the header is absent, malformed, or unknown
     */
    @Nullable
    public static EntityType<?> getCurrentEntityType(ItemStack stack) {
        CompoundTag tag = getData(stack);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(ENTITY_TYPE));
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    /**
     * Deserializes stored junk contents into a detached, mutable list of detached stacks. Empty
     * serialized entries are omitted.
     */
    public static List<ItemStack> getStoredItems(ItemStack container, HolderLookup.Provider registries) {
        List<ItemStack> result = new ArrayList<>();
        CompoundTag tag = getData(container);
        ListTag tagList = tag.getList(STORED_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, tagList.getCompound(i));
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    /** Returns the serialized junk-entry count without decoding the nested stacks. */
    public static int getStoredItemCount(ItemStack container) {
        return storedItemCount(getData(container));
    }

    /**
     * Returns a value that changes whenever the stored junk items or the layout seed change, without
     * decoding the nested stacks. Intended for client-side render caches; not stable across sessions.
     */
    public static long storedItemsFingerprint(ItemStack container) {
        CompoundTag data = getData(container);
        long hash = data.getList(STORED_ITEMS, Tag.TAG_COMPOUND).hashCode();
        return hash * 31L + data.getLong(JUNK_LAYOUT_SEED);
    }

    /**
     * Replaces stored junk contents with the nonempty entries in {@code items}.
     *
     * <p>If no entries remain, both the stored-item list and layout seed are removed. Unrelated and
     * mode-based NBT is preserved.
     */
    public static void setStoredItems(ItemStack container, List<ItemStack> items,
                                      HolderLookup.Provider registries) {
        ListTag out = new ListTag();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            out.add(stack.save(registries));
        }
        CompoundTag tag = getData(container);
        if (out.isEmpty()) {
            tag.remove(STORED_ITEMS);
            tag.remove(JUNK_LAYOUT_SEED);
        } else {
            tag.put(STORED_ITEMS, out);
        }
        setData(container, tag);
    }

    /** Returns the stored junk-layout seed, or zero when the stack has no seed. */
    public static long getJunkLayoutSeed(ItemStack container) {
        return getData(container).getLong(JUNK_LAYOUT_SEED);
    }

    /** Replaces the junk-layout seed without changing stored items. */
    public static void rerollJunkLayout(ItemStack container) {
        CompoundTag tag = getData(container);
        tag.putLong(JUNK_LAYOUT_SEED, ThreadLocalRandom.current().nextLong());
        setData(container, tag);
    }

    /**
     * Clears fluid, milk, powder-snow, and entity state while preserving stored junk items, their
     * layout seed, and unrelated NBT.
     */
    public static void clearBucket(ItemStack stack) {
        CompoundTag tag = getData(stack);
        clearModeKeys(tag);
        setData(stack, tag);
    }

    /**
     * Removes an empty mode-based payload and its mode marker. Stored junk items, their layout seed,
     * and unrelated NBT are not considered or changed.
     */
    public static void normalizeEmptyState(ItemStack stack) {
        CompoundTag tag = getData(stack);
        Mode mode = modeOf(tag);
        if (mode == Mode.NONE) return;

        boolean empty = switch (mode) {
            case FLUID -> storedFluidOf(tag).isEmpty();
            case MILK -> tag.getInt(AMOUNT) <= 0;
            case POWDER_SNOW -> tag.getInt(POWDER_UNITS) <= 0;
            case ENTITY -> tag.getList(ENTITIES, Tag.TAG_COMPOUND).isEmpty();
            case NONE -> false;
        };
        if (empty) {
            clearModeKeys(tag);
            setData(stack, tag);
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative: " + value);
    }

    private static void writePowderSnow(CompoundTag tag, int units) {
        tag.putString(MODE, Mode.POWDER_SNOW.toNbt());
        tag.putInt(POWDER_UNITS, units);
    }

}
