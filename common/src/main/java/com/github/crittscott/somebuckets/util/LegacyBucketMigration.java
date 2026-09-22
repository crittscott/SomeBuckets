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
 * One-time conversion of a bucket's pre-1.21.1 NBT payload into the current data components.
 *
 * <p>Loading a save from before Minecraft 1.20.5's data-component rework relocates any item NBT
 * this mod doesn't own into the vanilla {@code minecraft:custom_data} component, verbatim; nothing
 * else reads that component. {@link #migrate} recognizes the mod's pre-1.21.1 key layout there,
 * rebuilds it through {@link BucketState}'s public API, and strips the recognized keys, so the
 * check is a cheap no-op on every stack that was never in that old format and never runs twice on
 * one that was. Nested entity snapshots and stored item stacks are themselves versioned NBT that
 * the automatic sweep never reaches, so each is run through the vanilla {@link DataFixer} for
 * {@link References#ENTITY} and {@link References#ITEM_STACK} before being handed to the current
 * codecs. Every stack's replacement state is fully built before anything is written to it, so a
 * failure partway through leaves the stack, and its legacy payload, untouched.
 */
public final class LegacyBucketMigration {

    /** Minecraft 1.20.1's data version; the mod's last pre-components release only ever targeted it. */
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
     * Detects and converts a legacy payload trapped in this stack's {@code custom_data} component,
     * then strips the recognized keys. A no-op when no recognized legacy key is present.
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

    /**
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

    /**
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
