package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class PresentationGameTests {
    private static final String ASSET_ROOT = "/assets/" + SomeBuckets.MODID + "/";

    private PresentationGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void dynamic_bucket_names_match_registered_identity_and_language(GameTestHelper helper) {
        Map<String, String> expectedNames = new LinkedHashMap<>();
        assertFiniteNames(ModItems.BIG_BUCKET_8.get(), "item.somebuckets.big_bucket_8", "Big", expectedNames);
        assertFiniteNames(ModItems.BIG_BUCKET_64.get(), "item.somebuckets.big_bucket_64", "Huge", expectedNames);
        assertSourceNames(expectedNames);

        JsonObject language = readJson("lang/en_us.json");
        expectedNames.forEach((key, expected) -> {
            JsonElement value = language.get(key);
            GameTestSupport.check(value != null && expected.equals(value.getAsString()),
                    "Language entry " + key + " was not '" + expected + "'");
        });
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void model_predicates_match_java_protocol(GameTestHelper helper) {
        ItemStack empty = new ItemStack(ModItems.BIG_BUCKET_8.get());
        assertFloat(FluidBucketItem.getContentProperty(empty), FluidBucketItem.CONTENT_EMPTY, "empty BB predicate");

        ItemStack fluid = new ItemStack(ModItems.BIG_BUCKET_8.get());
        ForgeFluidStacks.set(fluid, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        assertFloat(FluidBucketItem.getContentProperty(fluid), FluidBucketItem.CONTENT_FLUID, "fluid BB predicate");

        ItemStack milk = new ItemStack(ModItems.BIG_BUCKET_8.get());
        NBTUtil.setMilkAmount(milk, FluidType.BUCKET_VOLUME);
        assertFloat(FluidBucketItem.getContentProperty(milk), FluidBucketItem.CONTENT_MILK, "milk BB predicate");

        ItemStack powder = new ItemStack(ModItems.BIG_BUCKET_8.get());
        NBTUtil.setPowderUnits(powder, 1);
        assertFloat(FluidBucketItem.getContentProperty(powder), FluidBucketItem.CONTENT_POWDER_SNOW,
                "powder-snow BB predicate");

        ItemStack mob = new ItemStack(ModItems.MOB_BUCKET.get());
        assertFloat(MBItem.getFilledProperty(mob), MBItem.MODEL_EMPTY, "empty MB predicate");
        NBTUtil.addEntitySnapshot(mob, "minecraft:pig", new CompoundTag());
        assertFloat(MBItem.getFilledProperty(mob), MBItem.MODEL_FILLED, "filled MB predicate");

        assertModelPredicates("models/item/big_bucket_8.json", FluidBucketItem.CONTENT_PROPERTY,
                FluidBucketItem.CONTENT_MILK, FluidBucketItem.CONTENT_POWDER_SNOW);
        assertModelPredicates("models/item/big_bucket_64.json", FluidBucketItem.CONTENT_PROPERTY,
                FluidBucketItem.CONTENT_MILK, FluidBucketItem.CONTENT_POWDER_SNOW);
        assertModelPredicates("models/item/source_bucket.json", FluidBucketItem.CONTENT_PROPERTY,
                FluidBucketItem.CONTENT_MILK);
        assertModelPredicates("models/item/mob_bucket.json", MBItem.FILLED_PROPERTY,
                MBItem.MODEL_FILLED);
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
        NBTUtil.setMilkAmount(milk, FluidType.BUCKET_VOLUME);
        assertName(milk, baseKey + ".milk");
        expectedNames.put(baseKey + ".milk", displayPrefix + " Milk Bucket");

        ItemStack powder = new ItemStack(item);
        NBTUtil.setPowderUnits(powder, 1);
        assertName(powder, baseKey + ".powder_snow");
        expectedNames.put(baseKey + ".powder_snow", displayPrefix + " Powder Snow Bucket");
    }

    private static void assertSourceNames(Map<String, String> expectedNames) {
        Item item = ModItems.SOURCE_BUCKET.get();
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
        NBTUtil.setMilkAmount(milk, FluidType.BUCKET_VOLUME);
        assertName(milk, baseKey + ".milk");
        expectedNames.put(baseKey + ".milk", "Source Milk Bucket");
    }

    private static void assertFluidName(Item item, net.minecraft.world.level.material.Fluid fluid,
                                        String expectedKey) {
        ItemStack stack = new ItemStack(item);
        ForgeFluidStacks.set(stack, new FluidStack(fluid, FluidType.BUCKET_VOLUME));
        assertName(stack, expectedKey);
    }

    private static void assertName(ItemStack stack, String expectedKey) {
        Component name = stack.getHoverName();
        GameTestSupport.check(name.getContents() instanceof TranslatableContents,
                "Bucket name was not translatable: " + name);
        String actualKey = ((TranslatableContents) name.getContents()).getKey();
        GameTestSupport.check(expectedKey.equals(actualKey),
                "Expected name key " + expectedKey + ", got " + actualKey);
    }

    private static void assertModelPredicates(String path, ResourceLocation property, float... expectedValues) {
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
            throw new GameTestAssertException("Could not read " + resourcePath + ": " + exception.getMessage());
        }
    }
}
