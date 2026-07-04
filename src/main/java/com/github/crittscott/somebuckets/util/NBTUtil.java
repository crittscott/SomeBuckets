package com.github.crittscott.somebuckets.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized NBT manipulation utilities for all bucket types.
 */
public final class NBTUtil {

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

    public static String getMode(ItemStack stack) {
        String m = stack.getOrCreateTag().getString(MODE);
        return m.isEmpty() ? "none" : m;
    }

    public static void setMode(ItemStack stack, String mode) {
        stack.getOrCreateTag().putString(MODE, mode);
    }

    public static int getAmount(ItemStack stack) {
        String mode = getMode(stack);
        if ("fluid".equals(mode)) {
            FluidStack fluidStack = getFluidStack(stack);
            return fluidStack.isEmpty() ? 0 : fluidStack.getAmount();
        } else {
            return stack.getOrCreateTag().getInt(AMOUNT);
        }
    }

    public static void setAmount(ItemStack stack, int mb) {
        stack.getOrCreateTag().putInt(AMOUNT, Math.max(0, mb));
    }

    /* ------------------------- FluidStack storage ------------------------- */

    public static FluidStack getFluidStack(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(FLUID_STACK)) return FluidStack.EMPTY;
        return FluidStack.loadFluidStackFromNBT(tag.getCompound(FLUID_STACK));
    }

    public static void setFluidStack(ItemStack stack, FluidStack fluidStack) {
        setMode(stack, "fluid");
        CompoundTag tag = stack.getOrCreateTag();
        if (fluidStack.isEmpty()) {
            tag.remove(FLUID_STACK);
        } else {
            CompoundTag fluidTag = new CompoundTag();
            fluidStack.writeToNBT(fluidTag);
            tag.put(FLUID_STACK, fluidTag);
        }
    }

    public static void setFluid(ItemStack stack, String id, int mb) {
        // Legacy method - now creates a proper FluidStack
        FluidStack existing = getFluidStack(stack);
        if (existing.isEmpty()) {
            // For legacy compatibility, assume water/lava from string ids
            if ("minecraft:water".equals(id)) {
                setFluidStack(stack, new FluidStack(Fluids.WATER, mb));
            } else if ("minecraft:lava".equals(id)) {
                setFluidStack(stack, new FluidStack(Fluids.LAVA, mb));
            }
        } else {
            // Update amount of existing fluid
            FluidStack updated = new FluidStack(existing.getFluid(), mb, existing.getTag());
            setFluidStack(stack, updated);
        }
    }

    public static void setMilkAmount(ItemStack stack, int mb) {
        setMode(stack, "milk");
        setAmount(stack, mb);
    }

    public static int getPowderUnits(ItemStack stack) {
        return stack.getOrCreateTag().getInt(POWDER_UNITS);
    }

    public static void setPowderUnits(ItemStack stack, int units) {
        setMode(stack, "powder_snow");
        stack.getOrCreateTag().putInt(POWDER_UNITS, Math.max(0, units));
    }

    public static int getEntityCount(ItemStack stack) {
        return stack.getOrCreateTag().getList(ENTITIES, Tag.TAG_COMPOUND).size();
    }

    public static void setEntityHeader(ItemStack stack, String entityTypeId) {
        setMode(stack, "entity");
        stack.getOrCreateTag().putString(ENTITY_TYPE, entityTypeId);
    }

    public static void addEntitySnapshot(ItemStack stack, CompoundTag bucketTag) {
        ListTag list = stack.getOrCreateTag().getList(ENTITIES, Tag.TAG_COMPOUND);
        list.add(bucketTag);
        stack.getOrCreateTag().put(ENTITIES, list);
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
        String entityTypeId = stack.getOrCreateTag().getString(ENTITY_TYPE);
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
        CompoundTag tag = container.getOrCreateTag();
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
        container.getOrCreateTag().put(STORED_ITEMS, out);
    }

    /* ------------------------- Consolidated Operations ------------------------- */

    /** Clear all bucket content, making it empty */
    public static void clearBucket(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(MODE);
        tag.remove(AMOUNT);
        tag.remove(FLUID_STACK);
        tag.remove(POWDER_UNITS);
        tag.remove(ENTITY_TYPE);
        tag.remove(ENTITIES);
    }

    /** Get crafting remainder for bucket items */
    public static ItemStack getCraftingRemainder(ItemStack stack, boolean consumeLavaFuel) {
        // Special case for lava fuel consumption
        if (consumeLavaFuel && "fluid".equals(getMode(stack))) {
            FluidStack fluidStack = getFluidStack(stack);
            if (!fluidStack.isEmpty() && fluidStack.getFluid() == Fluids.LAVA) {
                int amt = fluidStack.getAmount();
                ItemStack result = stack.copy();
                result.setCount(1);
                if (amt > 1000) {
                    setFluidStack(result, new FluidStack(fluidStack.getFluid(), amt - 1000, fluidStack.getTag()));
                } else {
                    clearBucket(result);
                }
                return result;
            }
        }

        // Normal crafting: return empty bucket
        ItemStack result = stack.copy();
        result.setCount(1);
        clearBucket(result);
        return result;
    }

    /** Drain fluid from bucket, handling empty state normalization */
    public static void drainFluid(ItemStack stack, int amount) {
        String mode = getMode(stack);
        if ("fluid".equals(mode)) {
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
        String mode = getMode(stack);

        if ("milk".equals(mode) && getAmount(stack) <= 0) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.remove(AMOUNT);
            tag.remove(MODE);
            return;
        }
        if ("powder_snow".equals(mode) && getPowderUnits(stack) <= 0) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.remove(POWDER_UNITS);
            tag.remove(MODE);
            return;
        }
        if ("fluid".equals(mode) && getFluidStack(stack).isEmpty()) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.remove(FLUID_STACK);
            tag.remove(MODE);
            return;
        }
        if ("entity".equals(mode) && getEntityCount(stack) <= 0) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.remove(ENTITY_TYPE);
            tag.remove(ENTITIES);
            tag.remove(MODE);
        }
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

    /* ------------------------- Legacy compatibility helpers ------------------------- */

    /** Get fluid type string for display/compatibility - returns registry name */
    public static String getFluidTypeString(ItemStack stack) {
        String mode = getMode(stack);
        if ("milk".equals(mode)) return "milk";
        if ("fluid".equals(mode)) {
            FluidStack fluidStack = getFluidStack(stack);
            return fluidStack.isEmpty() ? "none" : fluidStack.getFluid().getFluidType().getDescription().getString();
        }
        return "none";
    }

    /** Check if current fluid matches the given fluid */
    public static boolean hasFluid(ItemStack stack, Fluid fluid) {
        if (!"fluid".equals(getMode(stack))) return false;
        FluidStack fluidStack = getFluidStack(stack);
        return !fluidStack.isEmpty() && fluidStack.getFluid() == fluid;
    }
}
