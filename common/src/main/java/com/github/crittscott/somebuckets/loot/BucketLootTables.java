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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps each bucket inject loot table to the vanilla structure loot tables it is added to on every
 * loader. The item, chance, and contents of each roll live in its data-pack inject table.
 */
public final class BucketLootTables {
    /** One independent structure-loot roll parsed from the shipped manifest. */
    public record Reward(String id, Set<ResourceLocation> targets) {
        public Reward {
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, id);
            targets = Collections.unmodifiableSet(new LinkedHashSet<>(targets));
        }

        /** Returns the data-pack loot table that performs this roll. */
        public ResourceKey<LootTable> injectTable() {
            return ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "inject/" + id));
        }
    }

    private static final String MANIFEST_PATH = "/somebuckets/bucket_loot.json";
    private static final List<Reward> REWARDS = loadDefinitions();
    private static final Map<ResourceLocation, List<Reward>> REWARDS_BY_TABLE = buildRewardsByTable();

    private BucketLootTables() {}

    /** Returns the manifest rewards in declaration order. */
    public static List<Reward> rewards() {
        return REWARDS;
    }

    /** Returns the independent bucket rolls applicable to {@code lootTableId}, in manifest order. */
    public static List<Reward> rewardsFor(ResourceLocation lootTableId) {
        return REWARDS_BY_TABLE.getOrDefault(lootTableId, List.of());
    }

    private static Map<ResourceLocation, List<Reward>> buildRewardsByTable() {
        Map<ResourceLocation, List<Reward>> rewards = new LinkedHashMap<>();
        for (Reward reward : REWARDS) {
            for (ResourceLocation target : reward.targets()) {
                rewards.computeIfAbsent(target, ignored -> new ArrayList<>()).add(reward);
            }
        }
        rewards.replaceAll((id, entries) -> List.copyOf(entries));
        return Collections.unmodifiableMap(rewards);
    }

    /** One manifest row before its target collection is made immutable. */
    private record Row(String id, List<ResourceLocation> targets) {
        private static final Codec<List<Row>> MANIFEST_CODEC = RecordCodecBuilder.<Row>create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Row::id),
                ResourceLocation.CODEC.listOf().fieldOf("targets").forGetter(Row::targets)
        ).apply(instance, Row::new)).listOf().fieldOf("rewards").codec();
    }

    /* The manifest ships in the mod jar, so any defect is a packaging error and fails class loading. */
    private static List<Reward> loadDefinitions() {
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

        Set<String> ids = new HashSet<>();
        List<Reward> rewards = new ArrayList<>(rows.size());
        for (Row row : rows) {
            if (!ids.add(row.id())) {
                throw new IllegalStateException("Duplicate reward " + row.id() + " in " + MANIFEST_PATH);
            }
            rewards.add(new Reward(row.id(), new LinkedHashSet<>(row.targets())));
        }

        long targetTables = rewards.stream().flatMap(reward -> reward.targets().stream()).distinct().count();
        SomeBuckets.LOGGER.info("Bucket loot manifest {} loaded: {} rewards across {} target tables",
                MANIFEST_PATH, rewards.size(), targetTables);
        return List.copyOf(rewards);
    }
}
