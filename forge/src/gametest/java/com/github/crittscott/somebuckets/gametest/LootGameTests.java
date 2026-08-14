package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.loot.BucketLootTables;
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
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class LootGameTests {
    private static final String DATA_ROOT = "/data/";

    private LootGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void loot_manifest_has_intended_targets_and_overlaps(GameTestHelper helper) {
        GameTestSupport.check(BucketLootTables.BIG_BUCKET_TARGETS.size() == 26,
                "Big Bucket did not have 26 non-village structure targets");
        GameTestSupport.check(BucketLootTables.JUNK_BUCKET_TARGETS.size() == 16,
                "Junk Bucket did not have all 16 village targets");

        assertRewards("village/village_armorer", Reward.JUNK_BUCKET);
        assertRewards("stronghold_library", Reward.BIG_BUCKET, Reward.TRASH_BUCKET, Reward.MOB_BUCKET);
        assertRewards("bastion_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_TEN);
        assertRewards("buried_treasure", Reward.BIG_BUCKET, Reward.SOURCE_BUCKET_FIVE);
        assertRewards("ancient_city_ice_box", Reward.BIG_BUCKET, Reward.HUGE_POWDER_SNOW_BUCKET);
        assertRewards("spawn_bonus_chest");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void forge_loot_modifier_resources_match_shared_manifest(GameTestHelper helper) {
        assertModifier("big_bucket", Reward.BIG_BUCKET, BucketLootTables.BIG_BUCKET_TARGETS);
        assertModifier("junk_bucket", Reward.JUNK_BUCKET, BucketLootTables.JUNK_BUCKET_TARGETS);
        assertModifier("source_bucket_five", Reward.SOURCE_BUCKET_FIVE,
                BucketLootTables.SOURCE_BUCKET_FIVE_TARGETS);
        assertModifier("source_bucket_ten", Reward.SOURCE_BUCKET_TEN,
                BucketLootTables.SOURCE_BUCKET_TEN_TARGETS);
        assertModifier("trash_bucket", Reward.TRASH_BUCKET,
                BucketLootTables.TRASH_AND_MOB_BUCKET_TARGETS);
        assertModifier("mob_bucket", Reward.MOB_BUCKET,
                BucketLootTables.TRASH_AND_MOB_BUCKET_TARGETS);
        assertModifier("huge_powder_snow_bucket", Reward.HUGE_POWDER_SNOW_BUCKET,
                BucketLootTables.HUGE_POWDER_SNOW_BUCKET_TARGETS);

        JsonArray entries = readJson("forge/loot_modifiers/global_loot_modifiers.json")
                .getAsJsonArray("entries");
        GameTestSupport.check(entries.size() == 7,
                "Global loot modifier list had " + entries.size() + " entries instead of 7");
        helper.succeed();
    }

    private static void assertModifier(String name, Reward reward, Set<ResourceLocation> expectedTargets) {
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
            actualTargets.add(new ResourceLocation(term.getAsJsonObject()
                    .get("loot_table_id").getAsString()));
        }
        GameTestSupport.check(actualTargets.equals(expectedTargets),
                name + " target mismatch: " + actualTargets);
    }

    private static void assertRewards(String chestPath, Reward... expected) {
        ResourceLocation id = new ResourceLocation("minecraft", "chests/" + chestPath);
        List<Reward> actual = BucketLootTables.rewardsFor(id);
        GameTestSupport.check(actual.equals(List.of(expected)),
                id + " rewards were " + actual + " instead of " + List.of(expected));
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
