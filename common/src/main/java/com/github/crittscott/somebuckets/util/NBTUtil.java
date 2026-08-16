package com.github.crittscott.somebuckets.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Serializes, deserializes, and normalizes the persistent state of all bucket families. Mutators edit
 * the supplied stack in place and preserve unrelated root NBT unless their contract says otherwise.
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

    /** Returns the stored payload mode; missing or unrecognized state is treated as {@link Mode#NONE}. */
    public static Mode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? Mode.NONE : Mode.fromNbt(tag.getString(MODE));
    }

    /** Returns whether the stack has neither a mode-based payload nor stored junk items. */
    public static boolean isEmptyBucket(ItemStack stack) {
        return getMode(stack) == Mode.NONE && getStoredItems(stack).isEmpty();
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

    private static void setAmount(ItemStack stack, int mb) {
        requireNonNegative(mb, "Amount");
        stack.getOrCreateTag().putInt(AMOUNT, mb);
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

    /** Selects fluid mode and replaces the serialized fluid payload, or clears empty content. */
    public static void setStoredFluid(ItemStack stack, StoredFluid fluid) {
        if (fluid.isEmpty()) {
            clearBucket(stack);
            return;
        }

        clearBucket(stack);
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
     * @throws IllegalArgumentException if {@code mb} is negative
     */
    public static void setMilkAmount(ItemStack stack, int mb) {
        requireNonNegative(mb, "Milk amount");
        if (mb == 0) {
            clearBucket(stack);
            return;
        }
        clearBucket(stack);
        setMode(stack, Mode.MILK);
        setAmount(stack, mb);
    }

    public static int getPowderUnits(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(POWDER_UNITS);
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
     * Selects powder-snow mode and writes its positive block count, or clears zero content.
     *
     * @throws IllegalArgumentException if {@code units} is negative
     */
    public static void setPowderUnits(ItemStack stack, int units) {
        requireNonNegative(units, "Powder-snow units");
        if (units == 0) {
            clearBucket(stack);
            return;
        }
        clearBucket(stack);
        writePowderSnow(stack.getOrCreateTag(), units);
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
        if (remainingAmount == 0) {
            clearBucket(stack);
        } else if (mode == Mode.FLUID) {
            setStoredFluid(stack, getStoredFluid(stack).withAmount(remainingAmount));
        } else {
            setAmount(stack, remainingAmount);
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
     */
    public static void addEntitySnapshot(ItemStack stack, String entityTypeId, CompoundTag bucketTag) {
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
     * Deserializes stored junk contents into a detached, mutable list of detached stacks. Empty
     * serialized entries are omitted.
     */
    public static List<ItemStack> getStoredItems(ItemStack container) {
        List<ItemStack> result = new ArrayList<>();
        CompoundTag tag = container.getTag();
        if (tag == null) return result;
        ListTag tagList = tag.getList(STORED_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            ItemStack stack = ItemStack.of(tagList.getCompound(i));
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    /**
     * Replaces stored junk contents with the nonempty entries in {@code items}.
     *
     * <p>If no entries remain, both the stored-item list and layout seed are removed. Unrelated and
     * mode-based NBT is preserved.
     */
    public static void setStoredItems(ItemStack container, List<ItemStack> items) {
        ListTag out = new ListTag();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            CompoundTag serialized = new CompoundTag();
            stack.save(serialized);
            out.add(serialized);
        }
        if (out.isEmpty()) {
            CompoundTag tag = container.getTag();
            if (tag != null) {
                tag.remove(STORED_ITEMS);
                tag.remove(JUNK_LAYOUT_SEED);
                removeTagIfEmpty(container, tag);
            }
        } else {
            container.getOrCreateTag().put(STORED_ITEMS, out);
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
     * layout seed, and unrelated NBT.
     */
    public static void clearBucket(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(MODE);
        tag.remove(AMOUNT);
        tag.remove(FLUID_STACK);
        tag.remove(POWDER_UNITS);
        tag.remove(ENTITY_TYPE);
        tag.remove(ENTITIES);
        removeTagIfEmpty(stack, tag);
    }

    /**
     * Removes an empty mode-based payload and its mode marker. Stored junk items, their layout seed,
     * and unrelated NBT are not considered or changed.
     */
    public static void normalizeEmptyState(ItemStack stack) {
        Mode mode = getMode(stack);
        if (mode == Mode.NONE) return;
        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        boolean empty = switch (mode) {
            case FLUID -> getStoredFluid(stack).isEmpty();
            case MILK -> tag.getInt(AMOUNT) <= 0;
            case POWDER_SNOW -> tag.getInt(POWDER_UNITS) <= 0;
            case ENTITY -> tag.getList(ENTITIES, Tag.TAG_COMPOUND).isEmpty();
            case NONE -> false;
        };
        if (empty) clearBucket(stack);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative: " + value);
    }

    private static void writePowderSnow(CompoundTag tag, int units) {
        tag.putString(MODE, Mode.POWDER_SNOW.toNbt());
        tag.putInt(POWDER_UNITS, units);
    }

    private static void removeTagIfEmpty(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) stack.setTag(null);
    }
}
