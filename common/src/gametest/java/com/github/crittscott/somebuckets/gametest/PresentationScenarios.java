package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.register.CreativeBucketCatalog;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.google.gson.JsonArray;
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

/** Loader-neutral dynamic-name, language-resource, and model-protocol scenarios. */
final class PresentationScenarios {
    private static final String ASSET_ROOT = "/assets/" + SomeBuckets.MODID + "/";

    private PresentationScenarios() {}

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

    static void model_predicates_match_java_protocol(GameTestHelper helper, Item big, Item mob,
                                                      boolean explicitFluidOverrides) {
        ItemStack empty = new ItemStack(big);
        assertFloat(FluidBucketItem.getContentProperty(empty),
                FluidBucketItem.CONTENT_EMPTY, "empty BB predicate");

        ItemStack fluid = storedFluid(big, Fluids.WATER);
        assertFloat(FluidBucketItem.getContentProperty(fluid),
                FluidBucketItem.CONTENT_FLUID, "fluid BB predicate");

        ItemStack milk = new ItemStack(big);
        NBTUtil.setMilkAmount(milk, FluidBucketItem.BUCKET_VOLUME_MB);
        assertFloat(FluidBucketItem.getContentProperty(milk),
                FluidBucketItem.CONTENT_MILK, "milk BB predicate");

        ItemStack powder = new ItemStack(big);
        NBTUtil.setPowderUnits(powder, 1);
        assertFloat(FluidBucketItem.getContentProperty(powder),
                FluidBucketItem.CONTENT_POWDER_SNOW, "powder-snow BB predicate");

        ItemStack mobStack = new ItemStack(mob);
        assertFloat(MBItem.getFilledProperty(mobStack), MBItem.MODEL_EMPTY, "empty MB predicate");
        NBTUtil.addEntitySnapshot(mobStack, "minecraft:pig", new CompoundTag());
        assertFloat(MBItem.getFilledProperty(mobStack), MBItem.MODEL_FILLED, "filled MB predicate");

        float[] finiteValues = explicitFluidOverrides
                ? new float[]{FluidBucketItem.CONTENT_FLUID, FluidBucketItem.CONTENT_MILK,
                FluidBucketItem.CONTENT_POWDER_SNOW}
                : new float[]{FluidBucketItem.CONTENT_MILK, FluidBucketItem.CONTENT_POWDER_SNOW};
        float[] sourceValues = explicitFluidOverrides
                ? new float[]{FluidBucketItem.CONTENT_FLUID, FluidBucketItem.CONTENT_MILK}
                : new float[]{FluidBucketItem.CONTENT_MILK};
        assertModelPredicates("models/item/big_bucket_8.json",
                FluidBucketItem.CONTENT_PROPERTY, finiteValues);
        assertModelPredicates("models/item/big_bucket_64.json",
                FluidBucketItem.CONTENT_PROPERTY, finiteValues);
        assertModelPredicates("models/item/source_bucket.json",
                FluidBucketItem.CONTENT_PROPERTY, sourceValues);
        assertModelPredicates("models/item/mob_bucket.json", MBItem.FILLED_PROPERTY,
                MBItem.MODEL_FILLED);
        helper.succeed();
    }

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
        NBTUtil.setMilkAmount(milk, FluidBucketItem.BUCKET_VOLUME_MB);
        assertName(milk, baseKey + ".milk");
        expectedNames.put(baseKey + ".milk", displayPrefix + " Milk Bucket");

        ItemStack powder = new ItemStack(item);
        NBTUtil.setPowderUnits(powder, 1);
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
        NBTUtil.setMilkAmount(milk, FluidBucketItem.BUCKET_VOLUME_MB);
        assertName(milk, baseKey + ".milk");
        expectedNames.put(baseKey + ".milk", "Source Milk Bucket");
    }

    private static void assertFluidName(Item item, Fluid fluid, String expectedKey) {
        assertName(storedFluid(item, fluid), expectedKey);
    }

    private static ItemStack storedFluid(Item item, Fluid fluid) {
        ItemStack stack = new ItemStack(item);
        NBTUtil.setStoredFluid(stack,
                new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB, null));
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

    private static void assertModelPredicates(String path, ResourceLocation property,
                                              float... expectedValues) {
        JsonArray overrides = readJson(path).getAsJsonArray("overrides");
        GameTestSupport.check(overrides.size() == expectedValues.length,
                path + " had " + overrides.size() + " overrides instead of " + expectedValues.length);

        for (int index = 0; index < expectedValues.length; index++) {
            JsonObject predicate = overrides.get(index).getAsJsonObject().getAsJsonObject("predicate");
            GameTestSupport.check(predicate.size() == 1 && predicate.has(property.toString()),
                    path + " override " + index + " did not use " + property);
            assertFloat(predicate.get(property.toString()).getAsFloat(), expectedValues[index],
                    path + " override " + index);
        }
    }

    private static void assertFloat(float actual, float expected, String label) {
        GameTestSupport.check(Float.compare(actual, expected) == 0,
                label + " was " + actual + " instead of " + expected);
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
