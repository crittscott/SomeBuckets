package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.loot.BucketLootTables;
import com.github.crittscott.somebuckets.loot.BucketLootTables.Reward;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** NeoForge structure-loot GameTests, including shared scenarios and loader resource validation. */
public final class LootGameTests {
    private static final FileToIdConverter MODIFIER_FILES = FileToIdConverter.json("loot_modifiers");

    private LootGameTests() {}

    /** See {@link LootScenarios#loot_manifest_has_intended_targets_and_overlaps}. */
    public static void loot_manifest_has_intended_targets_and_overlaps(GameTestHelper helper) {
        LootScenarios.loot_manifest_has_intended_targets_and_overlaps(helper);
    }

    /** See {@link LootScenarios#loot_injection_reaches_target_tables}. */
    public static void loot_injection_reaches_target_tables(GameTestHelper helper) {
        LootScenarios.loot_injection_reaches_target_tables(helper);
    }

    /**
     * Automation-only: discovers the mod's NeoForge loot modifiers in the active data packs and
     * compares their IDs, targets, and inject tables with the shared manifest.
     */
    public static void neoforge_loot_modifier_resources_match_shared_manifest(GameTestHelper helper) {
        Map<Identifier, Resource> resources = MODIFIER_FILES.listMatchingResourcesFromNamespace(
                helper.getLevel().getServer().getResourceManager(), SomeBuckets.MODID);
        Set<Identifier> expectedFiles = BucketLootTables.rewards().stream()
                .map(reward -> MODIFIER_FILES.idToFile(SomeBuckets.id(reward.id())))
                .collect(Collectors.toSet());
        GameTestSupport.check(resources.keySet().equals(expectedFiles),
                "Loot modifier files were " + resources.keySet() + " instead of " + expectedFiles);

        for (Reward reward : BucketLootTables.rewards()) {
            Identifier file = MODIFIER_FILES.idToFile(SomeBuckets.id(reward.id()));
            assertModifier(reward, readJson(file, resources.get(file)));
        }
        helper.succeed();
    }

    private static void assertModifier(Reward reward, JsonObject modifier) {
        String name = reward.id();
        GameTestSupport.check("neoforge:add_table".equals(modifier.get("type").getAsString()),
                name + " used the wrong modifier type");
        GameTestSupport.check(reward.injectTable().identifier().toString().equals(modifier.get("table").getAsString()),
                name + " referenced the wrong inject table");

        JsonArray conditions = modifier.getAsJsonArray("conditions");
        GameTestSupport.check(conditions.size() == 1, name + " carried extra conditions");
        JsonArray terms = conditions.get(0).getAsJsonObject().getAsJsonArray("terms");
        Set<Identifier> actualTargets = new LinkedHashSet<>();
        for (JsonElement term : terms) {
            actualTargets.add(Identifier.parse(term.getAsJsonObject()
                    .get("loot_table_id").getAsString()));
        }
        GameTestSupport.check(actualTargets.equals(reward.targets()),
                name + " target mismatch: " + actualTargets);
    }

    private static JsonObject readJson(Identifier file, Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new GameTestAssertException(Component.literal("Could not read " + file + ": "
                    + exception.getMessage()), 0);
        }
    }
}
