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

/**
 * Maps each bucket inject loot table to the vanilla structure loot tables it is added to on both
 * loaders. The item, chance, and contents of each roll live in its data-pack inject table,
 * {@code somebuckets:inject/<reward>}.
 */
public final class BucketLootTables {
    /** One independent structure-loot roll; the shipped manifest supplies its target-table set. */
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

        /** Returns the reward's manifest id, which also names its Forge global loot modifier. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Returns the data-pack loot table that performs this roll. */
        public ResourceLocation injectTable() {
            return new ResourceLocation(SomeBuckets.MODID, "inject/" + id());
        }

        /** Returns every loot table to which this independent roll applies. */
        public Set<ResourceLocation> targets() {
            return TARGETS.get(this);
        }
    }

    private static final Map<Reward, Set<ResourceLocation>> TARGETS = loadTargets();
    private static final Map<ResourceLocation, List<Reward>> REWARDS_BY_TABLE = buildRewardsByTable();

    private BucketLootTables() {}

    /** Returns the independent bucket rolls applicable to {@code lootTableId}, in declaration order. */
    public static List<Reward> rewardsFor(ResourceLocation lootTableId) {
        return REWARDS_BY_TABLE.getOrDefault(lootTableId, List.of());
    }

    private static Map<ResourceLocation, List<Reward>> buildRewardsByTable() {
        Map<ResourceLocation, List<Reward>> rewards = new LinkedHashMap<>();
        for (Reward reward : Reward.values()) {
            for (ResourceLocation target : reward.targets()) {
                rewards.computeIfAbsent(target, ignored -> new ArrayList<>()).add(reward);
            }
        }
        rewards.replaceAll((id, entries) -> List.copyOf(entries));
        return Collections.unmodifiableMap(rewards);
    }

    /* The manifest ships in the mod jar, so any defect is a packaging error and fails class loading. */
    private static Map<Reward, Set<ResourceLocation>> loadTargets() {
        InputStream input = BucketLootTables.class.getResourceAsStream("/somebuckets/bucket_loot.json");
        if (input == null) throw new IllegalStateException("Missing bucket loot manifest");

        JsonArray rewards;
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            rewards = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("rewards");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read bucket loot manifest", exception);
        }

        Map<Reward, Set<ResourceLocation>> targets = new EnumMap<>(Reward.class);
        for (JsonElement element : rewards) {
            JsonObject json = element.getAsJsonObject();
            String name = json.get("id").getAsString();
            Reward reward = Reward.valueOf(name.toUpperCase(Locale.ROOT));

            LinkedHashSet<ResourceLocation> tables = new LinkedHashSet<>();
            for (JsonElement target : json.getAsJsonArray("targets")) {
                tables.add(new ResourceLocation(target.getAsString()));
            }
            if (targets.put(reward, Collections.unmodifiableSet(tables)) != null) {
                throw new IllegalStateException("Duplicate bucket loot reward " + name);
            }
        }
        if (targets.size() != Reward.values().length) {
            throw new IllegalStateException("Missing bucket loot reward in manifest");
        }
        return Collections.unmodifiableMap(targets);
    }
}
