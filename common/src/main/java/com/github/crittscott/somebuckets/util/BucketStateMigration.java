package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Brings saved bucket-state components up to the current format before their persistent codecs
 * decode them, so the rest of the mod sees only current state.
 *
 * <p>Two stamps drive the conversion. {@value #SCHEMA} is the Some Buckets component format; a
 * component without it was written by the 1.21.1 release. {@value #DATA_VERSION} is the Minecraft
 * data version of the vanilla entity and item-stack data a component embeds, which vanilla's data
 * fixer does not reach inside mod components; a component without it holds 1.21.1 data. Upgrading
 * runs vanilla's data fixer over that embedded data, and encoding always writes the current stamps.
 * Conversion happens wherever the component is decoded, including inside other mods' storage, and
 * repeats on each load until the holder saves the current form.
 */
public final class BucketStateMigration {
    /** Field recording the Some Buckets component format. */
    public static final String SCHEMA = "schema";
    /** Field recording the Minecraft data version of embedded vanilla data. */
    public static final String DATA_VERSION = "data_version";

    /** Current Some Buckets component format. */
    private static final int CURRENT_SCHEMA = 1;
    /** Format of the 1.21.1 release, which wrote no schema field. */
    private static final int RELEASED_SCHEMA = 0;
    /** Minecraft 1.21.1, whose release wrote embedded vanilla data without a data version. */
    private static final int RELEASED_DATA_VERSION = 3955;

    private static final String VARIANT = "variant";
    private static final String CUSTOM_DATA = "minecraft:custom_data";
    private static final String ENTITY_TYPE = "entity_type";
    private static final String ENTITIES = "entities";
    private static final String ENTITY_ID = "id";
    private static final String ITEMS = "items";
    private static final String SET_ASIDE = "set_aside";

    private BucketStateMigration() {}

    /**
     * Wraps the current fluid-content codec. A released component's variant was a loader-native raw
     * tag on Forge, which now travels as the patch's {@code minecraft:custom_data}; NeoForge and
     * Fabric already wrote an encoded component patch.
     *
     * @param current codec for the current format
     * @return a codec that upgrades released data before decoding and writes the current format
     */
    public static <A> Codec<A> fluidContent(Codec<A> current) {
        return upgrading(current, BucketStateMigration::upgradeFluidContent);
    }

    /**
     * Wraps the current captured-mob codec, data-fixing each entity snapshot to the current version.
     *
     * @param current codec for the current format
     * @return a codec that upgrades older data before decoding and writes the current format
     */
    public static <A> Codec<A> capturedMobs(Codec<A> current) {
        return upgrading(current, BucketStateMigration::upgradeCapturedMobs);
    }

    /**
     * Wraps the current junk-contents codec, data-fixing each stored and set-aside item stack to the
     * current version.
     *
     * @param current codec for the current format
     * @return a codec that upgrades older data before decoding and writes the current format
     */
    public static <A> Codec<A> junkContents(Codec<A> current) {
        return upgrading(current, BucketStateMigration::upgradeJunkContents);
    }

    /**
     * The {@value #SCHEMA} field of a current-format record codec: written as the current schema and
     * required to equal it when read.
     */
    public static <A> RecordCodecBuilder<A, Integer> schemaField() {
        return Codec.INT.validate(schema -> schema == CURRENT_SCHEMA ? DataResult.success(schema)
                        : DataResult.error(() -> "Unsupported Some Buckets component schema: " + schema))
                .fieldOf(SCHEMA).forGetter(value -> CURRENT_SCHEMA);
    }

    /**
     * The {@value #DATA_VERSION} field of a current-format record codec: written as the running
     * Minecraft data version and required to equal it when read.
     */
    public static <A> RecordCodecBuilder<A, Integer> dataVersionField() {
        return Codec.INT.validate(version -> version == currentDataVersion() ? DataResult.success(version)
                        : DataResult.error(() -> "Unsupported embedded data version: " + version))
                .fieldOf(DATA_VERSION).forGetter(value -> currentDataVersion());
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    /** Upgrades one component's raw data to the current format; implemented by method references. */
    @FunctionalInterface
    private interface Upgrade {
        <T> Dynamic<T> apply(Dynamic<T> data);
    }

    private static <A> Codec<A> upgrading(Codec<A> current, Upgrade upgrade) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return current.decode(upgrade.apply(new Dynamic<>(ops, input)));
            }

            @Override
            public <T> DataResult<T> encode(A value, DynamicOps<T> ops, T prefix) {
                return current.encode(value, ops, prefix);
            }
        };
    }

    private static <T> Dynamic<T> upgradeFluidContent(Dynamic<T> data) {
        if (schemaOf(data) != RELEASED_SCHEMA) return data;
        Dynamic<T> upgraded = data.set(SCHEMA, data.createInt(CURRENT_SCHEMA));
        if (!BucketOperations.get().releasedFluidVariantIsRawTag()) return upgraded;
        return upgraded.update(VARIANT, variant -> variant.emptyMap().set(CUSTOM_DATA, variant));
    }

    private static <T> Dynamic<T> upgradeCapturedMobs(Dynamic<T> data) {
        int schema = schemaOf(data);
        if (schema != RELEASED_SCHEMA && schema != CURRENT_SCHEMA) return data;
        int from = data.get(DATA_VERSION).asInt(RELEASED_DATA_VERSION);
        if (schema == CURRENT_SCHEMA && from == currentDataVersion()) return data;
        Optional<String> entityType = data.get(ENTITY_TYPE).asString().result();
        return updateList(stamp(data), ENTITIES, entity -> fixEntity(entity, entityType, from));
    }

    private static <T> Dynamic<T> upgradeJunkContents(Dynamic<T> data) {
        int schema = schemaOf(data);
        if (schema != RELEASED_SCHEMA && schema != CURRENT_SCHEMA) return data;
        int from = data.get(DATA_VERSION).asInt(RELEASED_DATA_VERSION);
        if (schema == CURRENT_SCHEMA && from == currentDataVersion()) return data;
        UnaryOperator<Dynamic<T>> fixItem = item -> fix(References.ITEM_STACK, item, from);
        return updateList(updateList(stamp(data), ITEMS, fixItem), SET_ASIDE, fixItem);
    }

    /* Snapshots are saved without an id, which vanilla's entity fixes use to select type-specific rules. */
    private static <T> Dynamic<T> fixEntity(Dynamic<T> entity, Optional<String> entityType, int from) {
        Dynamic<T> identified = entityType.map(id -> entity.set(ENTITY_ID, entity.createString(id))).orElse(entity);
        return fix(References.ENTITY, identified, from).remove(ENTITY_ID);
    }

    private static <T> Dynamic<T> fix(DSL.TypeReference type, Dynamic<T> data, int from) {
        return DataFixers.getDataFixer().update(type, data, from, currentDataVersion());
    }

    private static int schemaOf(Dynamic<?> data) {
        return data.get(SCHEMA).asInt(RELEASED_SCHEMA);
    }

    private static <T> Dynamic<T> stamp(Dynamic<T> data) {
        return data.set(SCHEMA, data.createInt(CURRENT_SCHEMA))
                .set(DATA_VERSION, data.createInt(currentDataVersion()));
    }

    private static <T> Dynamic<T> updateList(Dynamic<T> data, String key, UnaryOperator<Dynamic<T>> element) {
        return data.get(key).result()
                .map(list -> data.set(key, data.createList(
                        list.asList(Function.identity()).stream().map(element))))
                .orElse(data);
    }
}
