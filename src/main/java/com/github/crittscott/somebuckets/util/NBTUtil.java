package com.github.crittscott.somebuckets.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Serializes, deserializes, and normalizes the persistent state of all bucket families. */
public final class NBTUtil {

    public enum Mode {
        NONE("none"),
        FLUID("fluid"),
        MILK("milk"),
        POWDER_SNOW("powder_snow"),
        ENTITY("entity");

        private final String serializedName;

        Mode(String serializedName) {
            this.serializedName = serializedName;
        }

        private String toNbt() {
            return serializedName;
        }

        public static Mode fromNbt(String value) {
            for (Mode mode : values()) {
                if (mode.serializedName.equals(value)) return mode;
            }
            return NONE;
        }
    }

    // ---- Schema keys ----
    public static final String MODE         = "Mode";           // "none" | "fluid" | "milk" | "powder_snow" | "entity"
    public static final String AMOUNT       = "Amount";         // fluids & milk in mB
    public static final String FLUID_STACK  = "FluidStack";     // FluidStack compound for generic fluids
    public static final String POWDER_UNITS = "Powder";         // powdered snow blocks count
    public static final String ENTITY_TYPE  = "EntityType";     // species id
    public static final String ENTITIES     = "Entities";       // ListTag of per-entity bucket NBT
    private static final String STORED_ITEMS = "JunkItems";     // JB/TB item storage

    private NBTUtil() {}

    /* ------------------------- BB/SB content state ------------------------- */

    public static Mode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? Mode.NONE : Mode.fromNbt(tag.getString(MODE));
    }

    /**
     * Whether a BB, SB, or MB has no mode-based content. This does not inspect the separate
     * {@code JunkItems} storage used by JB and TB. The read never attaches NBT.
     */
    public static boolean isEmptyBucket(ItemStack stack) {
        return getMode(stack) == Mode.NONE;
    }

    private static void setMode(ItemStack stack, Mode mode) {
        stack.getOrCreateTag().putString(MODE, mode.toNbt());
    }

    public static int getAmount(ItemStack stack) {
        if (getMode(stack) == Mode.FLUID) {
            FluidStack fluidStack = getFluidStack(stack);
            return fluidStack.isEmpty() ? 0 : fluidStack.getAmount();
        }
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(AMOUNT);
    }

    /** Writes a nonnegative amount without changing the current mode. */
    public static void setAmount(ItemStack stack, int mb) {
        requireNonNegative(mb, "Amount");
        stack.getOrCreateTag().putInt(AMOUNT, mb);
    }

    public static FluidStack getFluidStack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(FLUID_STACK)) return FluidStack.EMPTY;
        return FluidStack.loadFluidStackFromNBT(tag.getCompound(FLUID_STACK));
    }

    public static void setFluidStack(ItemStack stack, FluidStack fluidStack) {
        setMode(stack, Mode.FLUID);
        CompoundTag tag = stack.getOrCreateTag();
        if (fluidStack.isEmpty()) {
            tag.remove(FLUID_STACK);
        } else {
            CompoundTag fluidTag = new CompoundTag();
            fluidStack.writeToNBT(fluidTag);
            tag.put(FLUID_STACK, fluidTag);
        }
    }

    public static void setMilkAmount(ItemStack stack, int mb) {
        requireNonNegative(mb, "Milk amount");
        setMode(stack, Mode.MILK);
        setAmount(stack, mb);
    }

    public static int getPowderUnits(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(POWDER_UNITS);
    }

    /** Assigns a nonnegative number of powder-snow blocks. */
    public static void setPowderUnits(ItemStack stack, int units) {
        requireNonNegative(units, "Powder-snow units");
        setMode(stack, Mode.POWDER_SNOW);
        stack.getOrCreateTag().putInt(POWDER_UNITS, units);
    }

    /**
     * Removes up to {@code requestedAmount} mB from finite fluid or milk content and returns the
     * amount removed. Exhausting the content normalizes the bucket to {@link Mode#NONE}; unrelated
     * root NBT is preserved. Other content modes are not changed.
     */
    public static int drainFiniteContent(ItemStack stack, int requestedAmount) {
        if (requestedAmount <= 0) return 0;

        Mode mode = getMode(stack);
        int currentAmount;
        if (mode == Mode.FLUID) {
            FluidStack current = getFluidStack(stack);
            if (current.isEmpty()) return 0;
            currentAmount = current.getAmount();
        } else if (mode == Mode.MILK) {
            currentAmount = getAmount(stack);
        } else {
            return 0;
        }

        int removedAmount = Math.min(currentAmount, requestedAmount);
        if (removedAmount <= 0) return 0;

        int remainingAmount = currentAmount - removedAmount;
        if (remainingAmount == 0) {
            clearBucket(stack);
        } else if (mode == Mode.FLUID) {
            FluidStack current = getFluidStack(stack);
            setFluidStack(stack,
                    new FluidStack(current.getFluid(), remainingAmount, current.getTag()));
        } else {
            setAmount(stack, remainingAmount);
        }
        return removedAmount;
    }

    /* ------------------------- MB snapshot state ------------------------- */

    public static int getEntityCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getList(ENTITIES, Tag.TAG_COMPOUND).size();
    }

    public static void setEntityHeader(ItemStack stack, String entityTypeId) {
        setMode(stack, Mode.ENTITY);
        stack.getOrCreateTag().putString(ENTITY_TYPE, entityTypeId);
    }

    /** Appends the newest entity snapshot to the FIFO list while preserving unrelated root NBT. */
    public static void addEntitySnapshot(ItemStack stack, CompoundTag bucketTag) {
        ListTag list = stack.getOrCreateTag().getList(ENTITIES, Tag.TAG_COMPOUND);
        list.add(bucketTag);
        stack.getOrCreateTag().put(ENTITIES, list);
    }

    /** Returns a detached copy of the oldest snapshot without changing the bucket. */
    public static CompoundTag copyFirstEntitySnapshot(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return new CompoundTag();
        ListTag list = tag.getList(ENTITIES, Tag.TAG_COMPOUND);
        return list.isEmpty() ? new CompoundTag() : list.getCompound(0).copy();
    }

    /**
     * Removes and returns the oldest entity snapshot. Snapshots are stored in FIFO order, and this
     * operation preserves unrelated root NBT. It does not normalize the emptied entity mode.
     */
    public static CompoundTag removeFirstEntitySnapshot(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return new CompoundTag();
        ListTag list = tag.getList(ENTITIES, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return new CompoundTag();
        CompoundTag out = list.getCompound(0).copy();
        list.remove(0);
        tag.put(ENTITIES, list);
        return out;
    }

    public static EntityType<?> getCurrentEntityType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        String entityTypeId = tag.getString(ENTITY_TYPE);
        if (entityTypeId.isEmpty()) return null;
        return ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityTypeId));
    }


    /* ------------------------- JB/TB stored-item state ------------------------- */

    /** Returns a detached mutable list of the stored stacks in FIFO order. */
    public static List<ItemStack> getStoredItems(ItemStack container) {
        List<ItemStack> result = new ArrayList<>();
        CompoundTag tag = container.getTag();
        if (tag == null) return result;
        ListTag tagList = tag.getList(STORED_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            ItemStack s = ItemStack.of(tagList.getCompound(i));
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    /**
     * Replaces stored-item state with the nonempty entries in {@code items}. Empty entries are
     * skipped; when none remain, the storage key and then an empty root tag are removed.
     */
    public static void setStoredItems(ItemStack container, List<ItemStack> items) {
        ListTag out = new ListTag();
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            s.save(c);
            out.add(c);
        }
        if (out.isEmpty()) {
            CompoundTag tag = container.getTag();
            if (tag != null) {
                tag.remove(STORED_ITEMS);
                removeTagIfEmpty(container, tag);
            }
        } else {
            container.getOrCreateTag().put(STORED_ITEMS, out);
        }
    }

    /* ------------------------- Shared content cleanup ------------------------- */

    /**
     * Clears the mode-based BB/SB/MB fields. This does not remove the separate {@code JunkItems}
     * field used by JB and TB, and unrelated root NBT is preserved.
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

    /** Normalizes zero fluid, milk, powder-snow, or entity content to {@link Mode#NONE}. */
    public static void normalizeEmptyState(ItemStack stack) {
        Mode mode = getMode(stack);
        if (mode == Mode.NONE) return;

        CompoundTag tag = stack.getTag();
        if (tag == null) throw new IllegalStateException("Nonempty bucket mode requires NBT");

        boolean empty = switch (mode) {
            case FLUID -> FluidStack.loadFluidStackFromNBT(tag.getCompound(FLUID_STACK)).isEmpty();
            case MILK -> tag.getInt(AMOUNT) <= 0;
            case POWDER_SNOW -> tag.getInt(POWDER_UNITS) <= 0;
            case ENTITY -> tag.getList(ENTITIES, Tag.TAG_COMPOUND).isEmpty();
            case NONE -> false;
        };
        if (!empty) return;

        switch (mode) {
            case FLUID -> tag.remove(FLUID_STACK);
            case MILK -> tag.remove(AMOUNT);
            case POWDER_SNOW -> tag.remove(POWDER_UNITS);
            case ENTITY -> {
                tag.remove(ENTITY_TYPE);
                tag.remove(ENTITIES);
            }
            case NONE -> { }
        }
        tag.remove(MODE);
        removeTagIfEmpty(stack, tag);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative: " + value);
    }

    private static void removeTagIfEmpty(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) stack.setTag(null);
    }

}
