package com.github.crittscott.somebuckets.loot;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps each bucket inject loot table to the vanilla structure loot tables it is added to on every
 * loader. The item, chance, and contents of each roll live in its data-pack inject table.
 */
public final class BucketLootTables {
    /**
     * One independent structure-loot roll, defined by its {@code somebuckets:inject/<reward>} loot
     * table. The shipped manifest supplies each value's complete target-table set.
     */
    public enum Reward {
        /** Awards a finite Big Bucket in the general structure-chest group. */
        BIG_BUCKET,
        /** Awards a Junk Bucket in village profession and house chests. */
        JUNK_BUCKET,
        /** Awards a Source Bucket in ocean, shipwreck, and buried-treasure chests. */
        SOURCE_BUCKET_OCEAN,
        /** Awards a Source Bucket in bastion chests. */
        SOURCE_BUCKET_BASTION,
        /** Awards a Trash Bucket in end-city and stronghold chests. */
        TRASH_BUCKET,
        /** Awards a Mob Bucket in end-city and stronghold chests. */
        MOB_BUCKET,
        /** Awards a Huge Bucket initialized to capacity with powder snow. */
        HUGE_POWDER_SNOW_BUCKET;

        /**
         * Returns the global-loot-modifier resource ID for this rule. Both the Forge and NeoForge
         * builds key their generated loot-modifier resource on this ID.
         *
         * @return {@code somebuckets:<reward>} with the reward name lower-cased
         */
        public ResourceLocation modifierId() {
            return ResourceLocation.fromNamespaceAndPath(
                    SomeBuckets.MODID, name().toLowerCase(Locale.ROOT));
        }

        /**
         * Returns the data-pack loot table that performs this roll.
         *
         * @return {@code somebuckets:inject/<reward>} with the reward name lower-cased
         */
        public ResourceKey<LootTable> injectTable() {
            return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(
                    SomeBuckets.MODID, "inject/" + name().toLowerCase(Locale.ROOT)));
        }

        /**
         * Returns every loot table to which this independent roll applies.
         *
         * @return the target loot-table ids
         */
        public Set<ResourceLocation> targets() {
            return DEFINITIONS.get(this);
        }
    }

    private static final String MANIFEST_PATH = "/somebuckets/bucket_loot.json";

    private static final Map<Reward, Set<ResourceLocation>> DEFINITIONS = loadDefinitions();

    private static final Map<ResourceLocation, List<Reward>> REWARDS_BY_TABLE = buildRewardsByTable();

    private BucketLootTables() {}

    /**
     * Returns every independent bucket roll that applies to a loot table.
     *
     * @param lootTableId the loot table being populated
     * @return the applicable rewards in {@link Reward} declaration order, or an empty list when the
     *         table is not a bucket-roll target
     */
    public static List<Reward> rewardsFor(ResourceLocation lootTableId) {
        return REWARDS_BY_TABLE.getOrDefault(lootTableId, List.of());
    }

    private static Map<ResourceLocation, List<Reward>> buildRewardsByTable() {
        Map<ResourceLocation, List<Reward>> rewards = new LinkedHashMap<>();
        for (Reward reward : Reward.values()) add(rewards, reward.targets(), reward);

        rewards.replaceAll((id, entries) -> List.copyOf(entries));
        return Collections.unmodifiableMap(rewards);
    }

    private static void add(Map<ResourceLocation, List<Reward>> rewards,
                            Set<ResourceLocation> targets, Reward reward) {
        for (ResourceLocation target : targets) {
            rewards.computeIfAbsent(target, ignored -> new ArrayList<>()).add(reward);
        }
    }

    /** One manifest row: a reward and the loot tables it targets. */
    private record Row(Reward id, List<ResourceLocation> targets) {
        static final Codec<List<Row>> MANIFEST_CODEC = RecordCodecBuilder.<Row>create(instance -> instance.group(
                Codec.STRING.xmap(id -> Reward.valueOf(id.toUpperCase(Locale.ROOT)),
                        reward -> reward.name().toLowerCase(Locale.ROOT)).fieldOf("id").forGetter(Row::id),
                ResourceLocation.CODEC.listOf().fieldOf("targets").forGetter(Row::targets)
        ).apply(instance, Row::new)).listOf().fieldOf("rewards").codec();
    }

    /* The manifest ships in the mod jar, so any defect is a packaging error and fails class loading. */
    private static Map<Reward, Set<ResourceLocation>> loadDefinitions() {
        List<Row> rows;
        try (InputStream input = Objects.requireNonNull(
                BucketLootTables.class.getResourceAsStream(MANIFEST_PATH), MANIFEST_PATH);
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            rows = Row.MANIFEST_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(error -> new IllegalStateException(
                            "Malformed bucket loot manifest " + MANIFEST_PATH + ": " + error));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        Map<Reward, Set<ResourceLocation>> definitions = new EnumMap<>(Reward.class);
        for (Row row : rows) {
            if (definitions.put(row.id(), Collections.unmodifiableSet(new LinkedHashSet<>(row.targets()))) != null) {
                throw new IllegalStateException("Duplicate reward " + row.id() + " in " + MANIFEST_PATH);
            }
        }
        if (definitions.size() != Reward.values().length) {
            throw new IllegalStateException(MANIFEST_PATH + " defines only " + definitions.keySet());
        }

        long targetTables = definitions.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .count();
        SomeBuckets.LOGGER.info("Bucket loot manifest {} loaded: {} rewards across {} target tables",
                MANIFEST_PATH, definitions.size(), targetTables);
        return Collections.unmodifiableMap(definitions);
    }
}
