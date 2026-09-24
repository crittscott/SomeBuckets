package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts the recognized bucket payload in {@code minecraft:custom_data} into registered data
 * components. Stored entity and item compounds are upgraded through the vanilla {@link DataFixer}
 * before current codecs consume them. Successfully converted keys are removed from custom data;
 * an unrecognized payload or failed conversion is left intact.
 */
public final class LegacyBucketMigration {

    /* Source data version used when upgrading nested entity and item-stack payloads. */
    private static final int LEGACY_DATA_VERSION = 3465;

    private static final String MODE = "Mode";
    private static final String MODE_FLUID = "fluid";
    private static final String MODE_MILK = "milk";
    private static final String MODE_POWDER_SNOW = "powder_snow";
    private static final String MODE_ENTITY = "entity";
    private static final String AMOUNT = "Amount";
    private static final String FLUID_STACK = "FluidStack";
    private static final String FLUID_NAME = "FluidName";
    private static final String FLUID_TAG = "Tag";
    private static final String POWDER_UNITS = "Powder";
    private static final String ENTITY_TYPE = "EntityType";
    private static final String ENTITIES = "Entities";
    private static final String JUNK_ITEMS = "JunkItems";
    private static final String JUNK_LAYOUT_SEED = "JunkLayoutSeed";

    private LegacyBucketMigration() {}

    /**
     * Converts a recognized bucket payload in this stack's {@code custom_data} component, then
     * removes the consumed keys. A no-op when no recognized key is present.
     *
     * @param stack bucket stack to migrate in place
     * @param registries registry access used to decode migrated stored item stacks
     */
    public static void migrate(ItemStack stack, HolderLookup.Provider registries) {
        CustomData legacy = stack.get(DataComponents.CUSTOM_DATA);
        if (legacy == null) return;
        CompoundTag tag = legacy.copyTag();
        if (!tag.contains(MODE) && !tag.contains(JUNK_ITEMS)) return;

        try {
            migrateContent(stack, tag);
            migrateJunkItems(stack, tag, registries);
        } catch (RuntimeException e) {
            SomeBuckets.LOGGER.warn("Failed to migrate legacy 1.20.1 bucket data on {}: {}",
                    stack.getItem(), e.toString());
            return;
        }

        tag.remove(MODE);
        tag.remove(AMOUNT);
        tag.remove(FLUID_STACK);
        tag.remove(POWDER_UNITS);
        tag.remove(ENTITY_TYPE);
        tag.remove(ENTITIES);
        tag.remove(JUNK_ITEMS);
        tag.remove(JUNK_LAYOUT_SEED);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        SomeBuckets.LOGGER.info("Migrated legacy 1.20.1 bucket data on {}", stack.getItem());
    }

    private static void migrateContent(ItemStack stack, CompoundTag tag) {
        switch (tag.getString(MODE)) {
            case MODE_FLUID -> migrateFluid(stack, tag);
            case MODE_MILK -> BucketState.setMilkAmount(stack, tag.getInt(AMOUNT));
            case MODE_POWDER_SNOW -> BucketState.setPowderUnits(stack, tag.getInt(POWDER_UNITS));
            case MODE_ENTITY -> migrateEntities(stack, tag);
            default -> {}
        }
    }

    private static void migrateFluid(ItemStack stack, CompoundTag tag) {
        if (!tag.contains(FLUID_STACK, Tag.TAG_COMPOUND)) return;
        CompoundTag fluidTag = tag.getCompound(FLUID_STACK);
        ResourceLocation id = ResourceLocation.tryParse(fluidTag.getString(FLUID_NAME));
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return;
        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        int amount = fluidTag.getInt(AMOUNT);
        CompoundTag variant = fluidTag.contains(FLUID_TAG, Tag.TAG_COMPOUND)
                ? fluidTag.getCompound(FLUID_TAG) : null;
        BucketState.setStoredFluid(stack, new StoredFluid(fluid, amount, variant));
    }

    /*
     * Runs every snapshot through the vanilla entity data fixer and collects the results before
     * writing any of them, so a fixer failure on one snapshot cannot leave the others committed.
     */
    private static void migrateEntities(ItemStack stack, CompoundTag tag) {
        ListTag entities = tag.getList(ENTITIES, Tag.TAG_COMPOUND);
        if (entities.isEmpty()) return;
        if (entities.size() > MBItem.MAX_MOBS) {
            SomeBuckets.LOGGER.warn("Dropped {} legacy captured mobs on {}: exceeds capacity {}",
                    entities.size(), stack.getItem(), MBItem.MAX_MOBS);
            return;
        }

        DataFixer fixer = DataFixers.getDataFixer();
        int currentVersion = currentDataVersion();
        List<CompoundTag> fixed = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            Dynamic<Tag> result = fixer.update(References.ENTITY,
                    new Dynamic<>(NbtOps.INSTANCE, entities.getCompound(i)),
                    LEGACY_DATA_VERSION, currentVersion);
            fixed.add((CompoundTag) result.getValue());
        }

        String entityTypeId = tag.getString(ENTITY_TYPE);
        for (CompoundTag snapshot : fixed) {
            BucketState.addEntitySnapshot(stack, entityTypeId, snapshot);
        }
    }

    /*
     * Runs every stored stack through the vanilla item-stack data fixer and decodes it with the
     * current codec, collecting the results before writing any of them.
     */
    private static void migrateJunkItems(ItemStack stack, CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(JUNK_ITEMS, Tag.TAG_LIST)) return;
        ListTag items = tag.getList(JUNK_ITEMS, Tag.TAG_COMPOUND);
        DataFixer fixer = DataFixers.getDataFixer();
        int currentVersion = currentDataVersion();
        List<ItemStack> migrated = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Dynamic<Tag> fixed = fixer.update(References.ITEM_STACK,
                    new Dynamic<>(NbtOps.INSTANCE, items.getCompound(i)),
                    LEGACY_DATA_VERSION, currentVersion);
            ItemStack.parse(registries, fixed.getValue()).ifPresentOrElse(migrated::add,
                    () -> SomeBuckets.LOGGER.warn(
                            "Dropped an unreadable legacy junk-bucket entry on {}", stack.getItem()));
        }
        if (!migrated.isEmpty()) {
            BucketState.setStoredItems(stack, migrated);
            BucketState.rerollJunkLayout(stack);
        }
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }
}
