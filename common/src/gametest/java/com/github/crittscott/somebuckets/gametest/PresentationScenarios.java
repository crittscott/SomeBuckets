package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.register.CreativeBucketCatalog;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loader-neutral dynamic-name, language-resource, and item-definition scenarios. */
final class PresentationScenarios {
    private static final String ASSET_ROOT = "/assets/" + SomeBuckets.MODID + "/";
    private static final String FLUID_BUCKET_MODEL = SomeBuckets.MODID + ":fluid_bucket";

    private PresentationScenarios() {}

    /**
     * Manual: inspect empty and filled bucket names in English; each registered item and content variant
     * has the expected name.
     */
    static void dynamic_bucket_names_match_registered_identity_and_language(
            GameTestHelper helper, Item big, Item huge, Item source) {
        Map<String, String> expectedNames = new LinkedHashMap<>();
        assertFiniteNames(big, "item.somebuckets.big_bucket_8", "Big", expectedNames);
        assertFiniteNames(huge, "item.somebuckets.big_bucket_64", "Huge", expectedNames);
        assertSourceNames(source, expectedNames);

        JsonObject language = readJson("lang/en_us.json");
        expectedNames.forEach((key, expected) -> {
            JsonElement value = language.get(key);
            GameTestSupport.check(value != null && expected.equals(value.getAsString()),
                    "Language entry " + key + " was not '" + expected + "'");
        });
        helper.succeed();
    }

    /**
     * Automation-only: checks that every bucket has an item definition and that the component
     * conditions selecting its models match the components each serialized bucket state carries.
     */
    static void item_definitions_match_bucket_state(GameTestHelper helper, Item big, Item mob) {
        for (String item : List.of("big_bucket_8", "big_bucket_64", "source_bucket", "junk_bucket",
                "mob_bucket", "trash_bucket")) {
            GameTestSupport.check(readJson("items/" + item + ".json").has("model"),
                    "items/" + item + ".json has no model");
        }

        List<ResourceLocation> finite = List.of(ModDataComponentTypes.MILK_AMOUNT_ID,
                ModDataComponentTypes.POWDER_UNITS_ID);
        assertConditionChain("items/big_bucket_8.json", finite, FLUID_BUCKET_MODEL);
        assertConditionChain("items/big_bucket_64.json", finite, FLUID_BUCKET_MODEL);
        assertConditionChain("items/source_bucket.json",
                List.of(ModDataComponentTypes.MILK_AMOUNT_ID), FLUID_BUCKET_MODEL);
        assertConditionChain("items/mob_bucket.json",
                List.of(ModDataComponentTypes.CAPTURED_MOBS_ID), "minecraft:model");

        ItemStack empty = new ItemStack(big);
        assertComponents(empty, false, false, "empty BB");
        assertComponents(storedFluid(big, Fluids.WATER), false, false, "fluid BB");
        ItemStack milk = new ItemStack(big);
        BucketState.setMilkAmount(milk, FluidBucketItem.BUCKET_VOLUME_MB);
        assertComponents(milk, true, false, "milk BB");
        ItemStack powder = new ItemStack(big);
        BucketState.setPowderUnits(powder, 1);
        assertComponents(powder, false, true, "powder-snow BB");

        ItemStack mobStack = new ItemStack(mob);
        GameTestSupport.check(!mobStack.has(ModDataComponentTypes.CAPTURED_MOBS),
                "empty MB carried captured_mobs");
        BucketState.addEntitySnapshot(mobStack, "minecraft:pig", new CompoundTag());
        GameTestSupport.check(mobStack.has(ModDataComponentTypes.CAPTURED_MOBS),
                "filled MB lacked captured_mobs");
        helper.succeed();
    }

    /**
     * Manual: open the Some Buckets creative tab and verify its order and that every displayed filled
     * variant is full.
     */
    static void creative_catalog_has_shared_order_and_full_variants(
            GameTestHelper helper, Item big, Item huge, Item source, Item junk, Item mob, Item trash) {
        List<ItemStack> stacks = new ArrayList<>();
        CreativeBucketCatalog.populate(big, huge, source, junk, mob, trash, stacks::add);

        GameTestSupport.check(stacks.size() == 17,
                "Creative catalog contained " + stacks.size() + " stacks instead of 17");
        assertItem(stacks.get(0), big, "empty Big Bucket");
        GameTestSupport.assertEmpty(stacks.get(0));
        assertItem(stacks.get(1), huge, "empty Huge Bucket");
        GameTestSupport.assertEmpty(stacks.get(1));

        assertItem(stacks.get(2), big, "Big Water Bucket");
        GameTestSupport.assertFluid(stacks.get(2), Fluids.WATER, ((BBItem) big).getCapacityMb());
        assertItem(stacks.get(3), huge, "Huge Water Bucket");
        GameTestSupport.assertFluid(stacks.get(3), Fluids.WATER, ((BBItem) huge).getCapacityMb());
        assertItem(stacks.get(4), big, "Big Lava Bucket");
        GameTestSupport.assertFluid(stacks.get(4), Fluids.LAVA, ((BBItem) big).getCapacityMb());
        assertItem(stacks.get(5), huge, "Huge Lava Bucket");
        GameTestSupport.assertFluid(stacks.get(5), Fluids.LAVA, ((BBItem) huge).getCapacityMb());

        assertItem(stacks.get(6), big, "Big Milk Bucket");
        GameTestSupport.assertMilk(stacks.get(6), ((BBItem) big).getCapacityMb());
        assertItem(stacks.get(7), huge, "Huge Milk Bucket");
        GameTestSupport.assertMilk(stacks.get(7), ((BBItem) huge).getCapacityMb());
        assertItem(stacks.get(8), big, "Big Powder Snow Bucket");
        GameTestSupport.assertPowder(stacks.get(8), ((BBItem) big).getCapacityUnits());
        assertItem(stacks.get(9), huge, "Huge Powder Snow Bucket");
        GameTestSupport.assertPowder(stacks.get(9), ((BBItem) huge).getCapacityUnits());

        assertItem(stacks.get(10), source, "empty Source Bucket");
        GameTestSupport.assertEmpty(stacks.get(10));
        assertItem(stacks.get(11), source, "Source Water Bucket");
        GameTestSupport.assertFluid(stacks.get(11), Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
        assertItem(stacks.get(12), source, "Source Lava Bucket");
        GameTestSupport.assertFluid(stacks.get(12), Fluids.LAVA, FluidBucketItem.BUCKET_VOLUME_MB);
        assertItem(stacks.get(13), source, "Source Milk Bucket");
        GameTestSupport.assertMilk(stacks.get(13), FluidBucketItem.BUCKET_VOLUME_MB);
        assertItem(stacks.get(14), junk, "Junk Bucket");
        assertItem(stacks.get(15), mob, "Mob Bucket");
        assertItem(stacks.get(16), trash, "Trash Bucket");
        helper.succeed();
    }

    private static void assertFiniteNames(Item item, String baseKey, String displayPrefix,
                                          Map<String, String> expectedNames) {
        assertName(new ItemStack(item), baseKey);
        expectedNames.put(baseKey, displayPrefix + " Bucket");

        assertFluidName(item, Fluids.WATER, baseKey + ".water");
        expectedNames.put(baseKey + ".water", displayPrefix + " Water Bucket");
        assertFluidName(item, Fluids.LAVA, baseKey + ".lava");
        expectedNames.put(baseKey + ".lava", displayPrefix + " Lava Bucket");
        assertFluidName(item, Fluids.FLOWING_WATER, baseKey + ".fluid");
        expectedNames.put(baseKey + ".fluid", displayPrefix + " %s Bucket");

        ItemStack milk = new ItemStack(item);
        BucketState.setMilkAmount(milk, FluidBucketItem.BUCKET_VOLUME_MB);
        assertName(milk, baseKey + ".milk");
        expectedNames.put(baseKey + ".milk", displayPrefix + " Milk Bucket");

        ItemStack powder = new ItemStack(item);
        BucketState.setPowderUnits(powder, 1);
        assertName(powder, baseKey + ".powder_snow");
        expectedNames.put(baseKey + ".powder_snow", displayPrefix + " Powder Snow Bucket");
    }

    private static void assertSourceNames(Item item, Map<String, String> expectedNames) {
        String baseKey = "item.somebuckets.source_bucket";
        assertName(new ItemStack(item), baseKey);
        expectedNames.put(baseKey, "Source Bucket");

        assertFluidName(item, Fluids.WATER, baseKey + ".water");
        expectedNames.put(baseKey + ".water", "Source Water Bucket");
        assertFluidName(item, Fluids.LAVA, baseKey + ".lava");
        expectedNames.put(baseKey + ".lava", "Source Lava Bucket");
        assertFluidName(item, Fluids.FLOWING_WATER, baseKey + ".fluid");
        expectedNames.put(baseKey + ".fluid", "Source %s Bucket");

        ItemStack milk = new ItemStack(item);
        BucketState.setMilkAmount(milk, FluidBucketItem.BUCKET_VOLUME_MB);
        assertName(milk, baseKey + ".milk");
        expectedNames.put(baseKey + ".milk", "Source Milk Bucket");
    }

    private static void assertFluidName(Item item, Fluid fluid, String expectedKey) {
        assertName(storedFluid(item, fluid), expectedKey);
    }

    private static ItemStack storedFluid(Item item, Fluid fluid) {
        ItemStack stack = new ItemStack(item);
        BucketState.setStoredFluid(stack,
                new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB));
        return stack;
    }

    private static void assertItem(ItemStack stack, Item expected, String label) {
        GameTestSupport.check(stack.is(expected),
                label + " used " + stack.getItem() + " instead of " + expected);
    }

    private static void assertName(ItemStack stack, String expectedKey) {
        Component name = stack.getHoverName();
        GameTestSupport.check(name.getContents() instanceof TranslatableContents,
                "Bucket name was not translatable: " + name);
        String actualKey = ((TranslatableContents) name.getContents()).getKey();
        GameTestSupport.check(expectedKey.equals(actualKey),
                "Expected name key " + expectedKey + ", got " + actualKey);
    }

    /*
     * Follows the has_component conditions through each on_false branch and checks the tested
     * components in order and the type of the model that remains.
     */
    private static void assertConditionChain(String path, List<ResourceLocation> components,
                                             String terminalType) {
        JsonObject model = readJson(path).getAsJsonObject("model");
        for (ResourceLocation component : components) {
            GameTestSupport.check("minecraft:condition".equals(model.get("type").getAsString())
                            && "minecraft:has_component".equals(model.get("property").getAsString())
                            && component.toString().equals(model.get("component").getAsString()),
                    path + " did not test " + component);
            model = model.getAsJsonObject("on_false");
        }
        GameTestSupport.check(terminalType.equals(model.get("type").getAsString()),
                path + " fell through to " + model.get("type") + " instead of " + terminalType);
    }

    private static void assertComponents(ItemStack stack, boolean milk, boolean powder, String label) {
        GameTestSupport.check(stack.has(ModDataComponentTypes.MILK_AMOUNT) == milk,
                label + " milk_amount presence was not " + milk);
        GameTestSupport.check(stack.has(ModDataComponentTypes.POWDER_UNITS) == powder,
                label + " powder_units presence was not " + powder);
    }

    private static JsonObject readJson(String path) {
        String resourcePath = ASSET_ROOT + path;
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
