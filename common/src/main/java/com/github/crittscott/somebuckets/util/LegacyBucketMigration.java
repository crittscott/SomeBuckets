package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Converts recognized 1.20.1 bucket fields in {@code minecraft:custom_data} into registered data
 * components. The complete payload is decoded, data-fixed, and previewed against current invariants
 * before the live stack changes. A failed payload is moved under a non-recognized quarantine key so
 * it is logged and processed at most once.
 */
public final class LegacyBucketMigration {
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

    /* Removed after either honoring or superseding the former retry-suppression behavior. */
    private static final String MIGRATION_FAILED = "SomeBucketsLegacyMigrationFailed";
    static final String QUARANTINE = "SomeBucketsLegacyMigrationQuarantine";

    private static final List<String> RECOGNIZED_KEYS = List.of(
            MODE, AMOUNT, FLUID_STACK, POWDER_UNITS, ENTITY_TYPE, ENTITIES,
            JUNK_ITEMS, JUNK_LAYOUT_SEED);

    private LegacyBucketMigration() {}

    /**
     * Atomically converts one recognized legacy payload, or quarantines it after one failed attempt.
     * A stack without recognized keys is inspected without copying its custom-data compound.
     *
     * @param stack bucket stack to migrate in place
     * @param level server level supplying registries and entity construction
     * @param location description of the stack's holder or location for log messages
     */
    public static void migrate(ItemStack stack, ServerLevel level, Supplier<String> location) {
        CustomData legacy = stack.get(DataComponents.CUSTOM_DATA);
        if (legacy == null) return;

        boolean recognized = legacy.contains(MODE) || legacy.contains(JUNK_ITEMS);
        if (!recognized) {
            removeObsoleteFailureMarker(stack, legacy);
            return;
        }

        CompoundTag tag = legacy.copyTag();
        if (tag.getBoolean(MIGRATION_FAILED)) {
            quarantineRecognized(tag);
            writeCustomData(stack, tag);
            return;
        }

        try {
            MigrationCandidates candidates = decodeCandidates(stack, tag, level);
            ItemStack preview = stack.copy();
            candidates.apply(preview);
            Optional<String> validationError = BucketState.validationError(preview);
            if (validationError.isPresent()) {
                throw new IllegalArgumentException(validationError.get());
            }

            candidates.apply(stack);
            removeRecognized(tag);
            writeCustomData(stack, tag);
            SomeBuckets.LOGGER.info("Migrated legacy 1.20.1 bucket data on {} at {}",
                    stack.getItem(), location.get());
        } catch (RuntimeException exception) {
            quarantineRecognized(tag);
            writeCustomData(stack, tag);
            SomeBuckets.LOGGER.warn(
                    "Failed to migrate legacy 1.20.1 bucket data on {} at {}; payload quarantined",
                    stack.getItem(), location.get(), exception);
        }
    }

    private static MigrationCandidates decodeCandidates(ItemStack stack, CompoundTag tag,
                                                        ServerLevel level) {
        ContentCandidate content = tag.contains(MODE) ? decodeContent(tag, level) : null;
        JunkCandidate junk = tag.contains(JUNK_ITEMS) ? decodeJunk(stack, tag, level) : null;
        return new MigrationCandidates(content, junk);
    }

    private static ContentCandidate decodeContent(CompoundTag tag, ServerLevel level) {
        return switch (tag.getString(MODE)) {
            case MODE_FLUID -> decodeFluid(tag);
            case MODE_MILK -> {
                int amount = tag.getInt(AMOUNT);
                requirePositive(amount, "legacy milk amount");
                yield new MilkCandidate(amount);
            }
            case MODE_POWDER_SNOW -> {
                int units = tag.getInt(POWDER_UNITS);
                requirePositive(units, "legacy powder-snow units");
                yield new PowderCandidate(units);
            }
            case MODE_ENTITY -> decodeEntities(tag, level);
            default -> throw new IllegalArgumentException(
                    "Unknown legacy bucket mode: " + tag.getString(MODE));
        };
    }

    private static FluidCandidate decodeFluid(CompoundTag tag) {
        if (!tag.contains(FLUID_STACK, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Legacy fluid mode has no fluid stack");
        }
        CompoundTag fluidTag = tag.getCompound(FLUID_STACK);
        ResourceLocation id = ResourceLocation.tryParse(fluidTag.getString(FLUID_NAME));
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) {
            throw new IllegalArgumentException("Unknown legacy fluid id: " + fluidTag.getString(FLUID_NAME));
        }
        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        if (fluid == Fluids.EMPTY) {
            throw new IllegalArgumentException("Legacy fluid mode uses the empty fluid");
        }
        int amount = fluidTag.getInt(AMOUNT);
        requirePositive(amount, "legacy fluid amount");
        // A legacy fluid-stack tag is free-form NBT, which components carry as custom data.
        CompoundTag variant = fluidTag.getCompound(FLUID_TAG);
        DataComponentPatch components = variant.isEmpty() ? DataComponentPatch.EMPTY
                : DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(variant)).build();
        return new FluidCandidate(new StoredFluid(fluid, amount, components));
    }

    private static EntityCandidate decodeEntities(CompoundTag tag, ServerLevel level) {
        ResourceLocation typeId = ResourceLocation.tryParse(tag.getString(ENTITY_TYPE));
        if (typeId == null) {
            throw new IllegalArgumentException("Invalid legacy entity type id: " + tag.getString(ENTITY_TYPE));
        }
        if (!tag.contains(ENTITIES, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Legacy entity mode has no snapshot list");
        }
        ListTag entities = tag.getList(ENTITIES, Tag.TAG_COMPOUND);
        if (entities.isEmpty() || entities.size() > MBItem.MAX_MOBS) {
            throw new IllegalArgumentException("Invalid legacy captured-mob count: " + entities.size());
        }

        BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).ifPresent(type -> validateEntityType(type, level));

        DataFixer fixer = DataFixers.getDataFixer();
        int currentVersion = currentDataVersion();
        List<CompoundTag> fixed = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            Tag value = fixer.update(References.ENTITY,
                    new Dynamic<>(NbtOps.INSTANCE, entities.getCompound(i)),
                    LEGACY_DATA_VERSION, currentVersion).getValue();
            if (!(value instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Legacy entity data fixer returned a non-compound value");
            }
            fixed.add(compound.copy());
        }
        return new EntityCandidate(typeId, fixed);
    }

    private static void validateEntityType(EntityType<?> type, ServerLevel level) {
        if (!type.canSerialize()) {
            throw new IllegalArgumentException("Legacy entity type cannot be serialized: "
                    + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }
        Entity probe = type.create(level, EntitySpawnReason.BUCKET);
        if (probe == null || !MBItem.canCapture(probe)) {
            throw new IllegalArgumentException("Legacy entity type is not Mob Bucket eligible: "
                    + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }
        probe.discard();
    }

    private static JunkCandidate decodeJunk(ItemStack stack, CompoundTag tag, ServerLevel level) {
        if (!(stack.getItem() instanceof JBItem bucket)) {
            throw new IllegalArgumentException("Legacy junk contents require a Junk or Trash Bucket");
        }
        if (!tag.contains(JUNK_ITEMS, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Legacy junk contents are not a list");
        }
        ListTag items = tag.getList(JUNK_ITEMS, Tag.TAG_COMPOUND);
        if (items.size() > bucket.getCapacity()) {
            throw new IllegalArgumentException("Legacy junk contents exceed bucket capacity: " + items.size());
        }

        DataFixer fixer = DataFixers.getDataFixer();
        int currentVersion = currentDataVersion();
        List<ItemStack> migrated = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Tag fixed = fixer.update(References.ITEM_STACK,
                    new Dynamic<>(NbtOps.INSTANCE, items.getCompound(i)),
                    LEGACY_DATA_VERSION, currentVersion).getValue();
            int index = i;
            ItemStack decoded = ItemStack.parse(level.registryAccess(), fixed)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unreadable legacy junk-bucket entry at index " + index));
            if (decoded.isEmpty() || !JBItem.canStore(decoded)
                    || decoded.getCount() > decoded.getMaxStackSize()) {
                throw new IllegalArgumentException(
                        "Illegal legacy junk-bucket entry at index " + i + ": " + decoded);
            }
            migrated.add(decoded.copy());
        }
        return new JunkCandidate(migrated, tag.getLong(JUNK_LAYOUT_SEED));
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive: " + value);
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    private static void removeObsoleteFailureMarker(ItemStack stack, CustomData legacy) {
        if (!legacy.contains(MIGRATION_FAILED)) return;
        CompoundTag tag = legacy.copyTag();
        tag.remove(MIGRATION_FAILED);
        writeCustomData(stack, tag);
    }

    private static void quarantineRecognized(CompoundTag tag) {
        CompoundTag quarantine = tag.contains(QUARANTINE, Tag.TAG_COMPOUND)
                ? tag.getCompound(QUARANTINE).copy() : new CompoundTag();
        for (String key : RECOGNIZED_KEYS) {
            Tag value = tag.get(key);
            if (value != null) quarantine.put(key, value.copy());
        }
        removeRecognized(tag);
        if (!quarantine.isEmpty()) tag.put(QUARANTINE, quarantine);
    }

    private static void removeRecognized(CompoundTag tag) {
        RECOGNIZED_KEYS.forEach(tag::remove);
        tag.remove(MIGRATION_FAILED);
    }

    private static void writeCustomData(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private record MigrationCandidates(@Nullable ContentCandidate content,
                                       @Nullable JunkCandidate junk) {
        private void apply(ItemStack stack) {
            if (content != null) content.apply(stack);
            if (junk != null) junk.apply(stack);
        }
    }

    private sealed interface ContentCandidate
            permits FluidCandidate, MilkCandidate, PowderCandidate, EntityCandidate {
        void apply(ItemStack stack);
    }

    private record FluidCandidate(StoredFluid fluid) implements ContentCandidate {
        @Override
        public void apply(ItemStack stack) {
            BucketState.setStoredFluid(stack, fluid);
        }
    }

    private record MilkCandidate(int amount) implements ContentCandidate {
        @Override
        public void apply(ItemStack stack) {
            BucketState.setMilkAmount(stack, amount);
        }
    }

    private record PowderCandidate(int units) implements ContentCandidate {
        @Override
        public void apply(ItemStack stack) {
            BucketState.setPowderUnits(stack, units);
        }
    }

    private record EntityCandidate(ResourceLocation typeId, List<CompoundTag> entities)
            implements ContentCandidate {
        private EntityCandidate {
            entities = entities.stream().map(CompoundTag::copy).toList();
        }

        @Override
        public void apply(ItemStack stack) {
            BucketState.clearBucket(stack);
            for (CompoundTag entity : entities) {
                BucketState.addEntitySnapshot(stack, typeId.toString(), entity.copy());
            }
        }
    }

    private record JunkCandidate(List<ItemStack> items, long layoutSeed) {
        private JunkCandidate {
            items = items.stream().map(ItemStack::copy).toList();
        }

        private void apply(ItemStack stack) {
            BucketState.setStoredItems(stack, items);
            BucketState.setJunkLayoutSeed(stack, layoutSeed);
        }
    }
}
