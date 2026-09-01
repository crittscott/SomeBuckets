package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.loot.BucketLootTables.Reward;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(SomeBuckets.MODID)
public final class LootGameTests {
    private static final String DATA_ROOT = "/data/";

    private LootGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void loot_manifest_has_intended_targets_and_overlaps(GameTestHelper helper) {
        LootScenarios.loot_manifest_has_intended_targets_and_overlaps(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void loot_injection_reaches_target_tables(GameTestHelper helper) {
        LootScenarios.loot_injection_reaches_target_tables(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void forge_loot_modifier_resources_match_shared_manifest(GameTestHelper helper) {
        for (Reward reward : Reward.values()) assertModifier(reward);

        JsonArray entries = readJson("forge/loot_modifiers/global_loot_modifiers.json")
                .getAsJsonArray("entries");
        List<String> actualEntries = new ArrayList<>();
        for (JsonElement entry : entries) actualEntries.add(entry.getAsString());
        List<String> expectedEntries = Arrays.stream(Reward.values())
                .map(reward -> reward.modifierId().toString())
                .toList();
        GameTestSupport.check(actualEntries.equals(expectedEntries),
                "Global loot modifier entries were " + actualEntries + " instead of " + expectedEntries);
        helper.succeed();
    }

    private static void assertModifier(Reward reward) {
        String name = reward.modifierId().getPath();
        JsonObject modifier = readJson("somebuckets/loot_modifiers/" + name + ".json");
        GameTestSupport.check("somebuckets:add_bucket".equals(modifier.get("type").getAsString()),
                name + " used the wrong modifier type");
        GameTestSupport.check(reward.itemId().toString().equals(modifier.get("item").getAsString()),
                name + " used the wrong item");
        GameTestSupport.check(modifier.has("powder_units")
                        ? modifier.get("powder_units").getAsInt() == reward.powderUnits()
                        : reward.powderUnits() == 0,
                name + " used the wrong powder-snow amount");

        JsonArray conditions = modifier.getAsJsonArray("conditions");
        float chance = conditions.get(1).getAsJsonObject().get("chance").getAsFloat();
        GameTestSupport.check(Float.compare(chance, reward.chance()) == 0,
                name + " used chance " + chance + " instead of " + reward.chance());

        JsonArray terms = conditions.get(0).getAsJsonObject().getAsJsonArray("terms");
        Set<ResourceLocation> actualTargets = new LinkedHashSet<>();
        for (JsonElement term : terms) {
            actualTargets.add(ResourceLocation.parse(term.getAsJsonObject()
                    .get("loot_table_id").getAsString()));
        }
        GameTestSupport.check(actualTargets.equals(reward.targets()),
                name + " target mismatch: " + actualTargets);
    }

    private static JsonObject readJson(String path) {
        String resourcePath = DATA_ROOT + path;
        try (InputStream input = SomeBuckets.class.getResourceAsStream(resourcePath)) {
            if (input == null) throw new GameTestAssertException("Missing resource " + resourcePath);
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException exception) {
            throw new GameTestAssertException("Could not read " + resourcePath + ": "
                    + exception.getMessage());
        }
    }
}
