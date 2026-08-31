package com.github.crittscott.somebuckets.loot;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Defines the vanilla structure loot tables and independent bucket rolls shared by both loaders. */
public final class BucketLootTables {
    /**
     * One independent structure-loot roll. The shipped manifest supplies each value's item,
     * probability, optional powder-snow content, and complete target-table set.
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
         * Returns the item awarded by a successful roll.
         *
         * @return the awarded item's registry id
         */
        public ResourceLocation itemId() {
            return definition(this).itemId();
        }

        /**
         * Returns the independent probability of this reward in each target table.
         *
         * @return the per-table roll chance
         */
        public float chance() {
            return definition(this).chance();
        }

        /**
         * Returns the initial powder-snow block count.
         *
         * @return the powder-snow units to prefill, or zero for an ordinary empty item
         */
        public int powderUnits() {
            return definition(this).powderUnits();
        }

        /**
         * Returns every loot table to which this independent roll applies.
         *
         * @return the target loot-table ids
         */
        public Set<ResourceLocation> targets() {
            return definition(this).targets();
        }
    }

    private static final String MANIFEST_PATH = "/somebuckets/bucket_loot.json";

    private static final Map<Reward, RewardDefinition> DEFINITIONS = loadDefinitions();

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

    private static RewardDefinition definition(Reward reward) {
        return DEFINITIONS.get(reward);
    }

    private static Map<Reward, RewardDefinition> loadDefinitions() {
        InputStream input = BucketLootTables.class.getResourceAsStream(MANIFEST_PATH);
        if (input == null) {
            SomeBuckets.LOGGER.error("Bucket loot manifest {} is missing from the mod jar", MANIFEST_PATH);
            throw new IllegalStateException("Missing bucket loot manifest");
        }

        JsonArray rewards;
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            rewards = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("rewards");
        } catch (java.io.IOException exception) {
            SomeBuckets.LOGGER.error("Could not read bucket loot manifest {}", MANIFEST_PATH, exception);
            throw new IllegalStateException("Could not read bucket loot manifest", exception);
        }

        Map<Reward, RewardDefinition> definitions = new EnumMap<>(Reward.class);
        for (JsonElement element : rewards) {
            JsonObject json = element.getAsJsonObject();
            String name = json.get("id").getAsString();
            Reward reward = Reward.valueOf(name.toUpperCase(Locale.ROOT));

            LinkedHashSet<ResourceLocation> targets = new LinkedHashSet<>();
            for (JsonElement target : json.getAsJsonArray("targets")) {
                targets.add(ResourceLocation.parse(target.getAsString()));
            }
            RewardDefinition previous = definitions.put(reward, new RewardDefinition(
                    ResourceLocation.parse(json.get("item").getAsString()),
                    json.get("chance").getAsFloat(),
                    json.has("powder_units") ? json.get("powder_units").getAsInt() : 0,
                    Collections.unmodifiableSet(targets)));
            if (previous != null) {
                SomeBuckets.LOGGER.error(
                        "Duplicate reward '{}' in bucket loot manifest {}; offending row: {}",
                        name, MANIFEST_PATH, json);
                throw new IllegalStateException("Duplicate bucket loot reward " + name);
            }
        }
        if (definitions.size() != Reward.values().length) {
            SomeBuckets.LOGGER.error(
                    "Bucket loot manifest {} defines {} of {} rewards; parsed {}",
                    MANIFEST_PATH, definitions.size(), Reward.values().length, definitions.keySet());
            throw new IllegalStateException("Missing bucket loot reward in manifest");
        }
        return Collections.unmodifiableMap(definitions);
    }

    private record RewardDefinition(ResourceLocation itemId, float chance, int powderUnits,
                                    Set<ResourceLocation> targets) {}
}
