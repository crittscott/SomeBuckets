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

    public static final String MODE = "Mode";
    public static final String AMOUNT = "Amount";
    public static final String FLUID_STACK = "FluidStack";
    public static final String POWDER_UNITS = "Powder";
    public static final String ENTITY_TYPE = "EntityType";
    public static final String ENTITIES = "Entities";
    private static final String STORED_ITEMS = "JunkItems";
    private static final String JUNK_LAYOUT_SEED = "JunkLayoutSeed";

    // Matches Forge FluidStack's established serialized compound so existing item data remains valid.
    private static final String FLUID_NAME = "FluidName";
    private static final String FLUID_AMOUNT = "Amount";
    private static final String FLUID_TAG = "Tag";

    private NBTUtil() {}

    public static Mode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? Mode.NONE : Mode.fromNbt(tag.getString(MODE));
    }

    public static boolean isEmptyBucket(ItemStack stack) {
        return getMode(stack) == Mode.NONE && getStoredItems(stack).isEmpty();
    }

    private static void setMode(ItemStack stack, Mode mode) {
        stack.getOrCreateTag().putString(MODE, mode.toNbt());
    }

    public static int getAmount(ItemStack stack) {
        if (getMode(stack) == Mode.FLUID) return getStoredFluid(stack).amount();
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(AMOUNT);
    }

    public static void setAmount(ItemStack stack, int mb) {
        requireNonNegative(mb, "Amount");
        stack.getOrCreateTag().putInt(AMOUNT, mb);
    }

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

    public static void setStoredFluid(ItemStack stack, StoredFluid fluid) {
        setMode(stack, Mode.FLUID);
        CompoundTag root = stack.getOrCreateTag();
        if (fluid.isEmpty()) {
            root.remove(FLUID_STACK);
            return;
        }

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.fluid());
        CompoundTag fluidTag = new CompoundTag();
        fluidTag.putString(FLUID_NAME, id.toString());
        fluidTag.putInt(FLUID_AMOUNT, fluid.amount());
        CompoundTag variant = fluid.variantTag();
        if (variant != null && !variant.isEmpty()) fluidTag.put(FLUID_TAG, variant);
        root.put(FLUID_STACK, fluidTag);
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

    public static void setPowderUnits(ItemStack stack, int units) {
        requireNonNegative(units, "Powder-snow units");
        setMode(stack, Mode.POWDER_SNOW);
        stack.getOrCreateTag().putInt(POWDER_UNITS, units);
    }

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

    public static void setEntityHeader(ItemStack stack, String entityTypeId) {
        setMode(stack, Mode.ENTITY);
        stack.getOrCreateTag().putString(ENTITY_TYPE, entityTypeId);
    }

    public static void addEntitySnapshot(ItemStack stack, CompoundTag bucketTag) {
        ListTag list = stack.getOrCreateTag().getList(ENTITIES, Tag.TAG_COMPOUND);
        list.add(bucketTag);
        stack.getOrCreateTag().put(ENTITIES, list);
    }

    public static CompoundTag copyFirstEntitySnapshot(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return new CompoundTag();
        ListTag list = tag.getList(ENTITIES, Tag.TAG_COMPOUND);
        return list.isEmpty() ? new CompoundTag() : list.getCompound(0).copy();
    }

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

    @Nullable
    public static EntityType<?> getCurrentEntityType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(ENTITY_TYPE));
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

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

    public static long getJunkLayoutSeed(ItemStack container) {
        CompoundTag tag = container.getTag();
        return tag == null ? 0L : tag.getLong(JUNK_LAYOUT_SEED);
    }

    public static void rerollJunkLayout(ItemStack container) {
        container.getOrCreateTag().putLong(JUNK_LAYOUT_SEED,
                ThreadLocalRandom.current().nextLong());
    }

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
