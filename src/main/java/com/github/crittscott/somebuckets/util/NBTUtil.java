package com.github.crittscott.somebuckets.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized NBT manipulation utilities for all bucket types.
 */
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

    // ---- NBT keys / modes ----
    public static final String MODE         = "Mode";           // "none" | "fluid" | "milk" | "powder_snow" | "entity"
    public static final String AMOUNT       = "Amount";         // fluids & milk in mB
    public static final String FLUID_STACK  = "FluidStack";     // FluidStack compound for generic fluids
    public static final String POWDER_UNITS = "Powder";         // powdered snow blocks count
    public static final String ENTITY_TYPE  = "EntityType";     // species id
    public static final String ENTITIES     = "Entities";       // ListTag of per-entity bucket NBT
    private static final String STORED_ITEMS = "JunkItems";     // JB/TB item storage

    private NBTUtil() {}

    /* ------------------------- Basic state helpers ------------------------- */

    public static Mode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? Mode.NONE : Mode.fromNbt(tag.getString(MODE));
    }

    /** True when the bucket holds nothing. Never attaches NBT to the inspected stack. */
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

    public static void setAmount(ItemStack stack, int mb) {
        stack.getOrCreateTag().putInt(AMOUNT, Math.max(0, mb));
    }

    /* ------------------------- FluidStack storage ------------------------- */

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
        setMode(stack, Mode.MILK);
        setAmount(stack, mb);
    }

    public static int getPowderUnits(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(POWDER_UNITS);
    }

    public static void setPowderUnits(ItemStack stack, int units) {
        setMode(stack, Mode.POWDER_SNOW);
        stack.getOrCreateTag().putInt(POWDER_UNITS, Math.max(0, units));
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

    /** Returns a detached copy of the oldest snapshot without changing the bucket. */
    public static CompoundTag copyFirstEntitySnapshot(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return new CompoundTag();
        ListTag list = tag.getList(ENTITIES, Tag.TAG_COMPOUND);
        return list.isEmpty() ? new CompoundTag() : list.getCompound(0).copy();
    }

    public static CompoundTag removeFirstEntitySnapshot(ItemStack stack) {
        ListTag list = stack.getOrCreateTag().getList(ENTITIES, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return new CompoundTag();
        CompoundTag out = list.getCompound(0);
        list.remove(0);
        stack.getOrCreateTag().put(ENTITIES, list);
        return out;
    }

    /* ------------------------- Multi-entity helpers ------------------------- */

    public static EntityType<?> getCurrentEntityType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        String entityTypeId = tag.getString(ENTITY_TYPE);
        if (entityTypeId.isEmpty()) return null;
        return ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityTypeId));
    }

    public static boolean isSameEntityType(ItemStack stack, EntityType<?> entityType) {
        EntityType<?> current = getCurrentEntityType(stack);
        return current != null && current == entityType;
    }

    public static boolean canAcceptEntity(ItemStack stack, EntityType<?> entityType) {
        int count = getEntityCount(stack);
        if (count == 0) return true;
        if (count >= 8) return false;
        return isSameEntityType(stack, entityType);
    }

    /* ------------------------- Item Storage (JB/TB) ------------------------- */

    public static List<ItemStack> getStoredItems(ItemStack container) {
        List<ItemStack> result = new ArrayList<>();
        CompoundTag tag = container.getTag();
        if (tag == null) return result;
        ListTag tagList = tag.getList(STORED_ITEMS, 10); // 10 = CompoundTag
        for (int i = 0; i < tagList.size(); i++) {
            ItemStack s = ItemStack.of(tagList.getCompound(i));
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

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

    /* ------------------------- Consolidated Operations ------------------------- */

    /** Clear all bucket content, making it empty */
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
     * Container remainder for a finite bucket: the same bucket with one unit of its content consumed.
     * An empty bucket has no remainder, so recipes that use it as a material consume it.
     */
    public static ItemStack getCraftingRemainder(ItemStack stack) {
        if (isEmptyBucket(stack)) return ItemStack.EMPTY;

        ItemStack result = stack.copy();
        result.setCount(1);

        switch (getMode(result)) {
            case FLUID, MILK -> drainFluid(result, 1000);
            case POWDER_SNOW -> setPowderUnits(result, getPowderUnits(result) - 1);
            default -> clearBucket(result);
        }

        normalizeEmptyState(result);
        return result;
    }

    /** Drain fluid from bucket, handling empty state normalization */
    public static void drainFluid(ItemStack stack, int amount) {
        if (getMode(stack) == Mode.FLUID) {
            FluidStack current = getFluidStack(stack);
            if (current.isEmpty()) return;

            int remaining = current.getAmount() - amount;
            if (remaining <= 0) {
                clearBucket(stack);
            } else {
                setFluidStack(stack, new FluidStack(current.getFluid(), remaining, current.getTag()));
            }
        } else {
            int current = getAmount(stack);
            int remaining = current - amount;
            if (remaining <= 0) {
                clearBucket(stack);
            } else {
                setAmount(stack, remaining);
            }
        }
    }

    /* ------------------------- Normalization helper ------------------------- */

    /** Ensure zero-content modes are returned to "none" so the bucket behaves empty. */
    public static void normalizeEmptyState(ItemStack stack) {
        Mode mode = getMode(stack);

        if (mode == Mode.MILK && getAmount(stack) <= 0) {
            CompoundTag tag = stack.getTag();
            if (tag == null) return;
            tag.remove(AMOUNT);
            tag.remove(MODE);
            removeTagIfEmpty(stack, tag);
            return;
        }
        if (mode == Mode.POWDER_SNOW && getPowderUnits(stack) <= 0) {
            CompoundTag tag = stack.getTag();
            if (tag == null) return;
            tag.remove(POWDER_UNITS);
            tag.remove(MODE);
            removeTagIfEmpty(stack, tag);
            return;
        }
        if (mode == Mode.FLUID && getFluidStack(stack).isEmpty()) {
            CompoundTag tag = stack.getTag();
            if (tag == null) return;
            tag.remove(FLUID_STACK);
            tag.remove(MODE);
            removeTagIfEmpty(stack, tag);
            return;
        }
        if (mode == Mode.ENTITY && getEntityCount(stack) <= 0) {
            CompoundTag tag = stack.getTag();
            if (tag == null) return;
            tag.remove(ENTITY_TYPE);
            tag.remove(ENTITIES);
            tag.remove(MODE);
            removeTagIfEmpty(stack, tag);
        }
    }

    private static void removeTagIfEmpty(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) stack.setTag(null);
    }

    /* ------------------------- Normal bucket utilities ------------------------- */

    public static boolean isNormalBucket(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.BUCKET || item == Items.WATER_BUCKET ||
                item == Items.LAVA_BUCKET || item == Items.MILK_BUCKET;
    }

    public static FluidStack getNormalBucketFluidStack(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.WATER_BUCKET) return new FluidStack(Fluids.WATER, 1000);
        if (item == Items.LAVA_BUCKET) return new FluidStack(Fluids.LAVA, 1000);
        return FluidStack.EMPTY;
    }

}
