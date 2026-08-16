package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeAndFuelGameTests {
    private RecipeAndFuelGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void all_shipped_recipe_ids_load(GameTestHelper helper) {
        for (String path : List.of("big_bucket_8", "big_bucket_64", "junk_bucket",
                "trash_bucket", "source_bucket", "mob_bucket")) {
            recipe(helper, path);
        }
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void huge_bucket_recipe_accepts_only_empty_big_buckets(GameTestHelper helper) {
        Recipe<?> recipe = recipe(helper, "big_bucket_64");
        ItemStack empty = GameTestSupport.big8();
        ItemStack filled = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);

        GameTestSupport.check(anyIngredientMatches(recipe, empty),
                "Huge Bucket recipe did not accept empty Big Bucket");
        GameTestSupport.check(!anyIngredientMatches(recipe, filled),
                "Huge Bucket recipe accepted filled Big Bucket");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(GameTestHelper helper) {
        Recipe<?> recipe = recipe(helper, "mob_bucket");
        ItemStack emptySource = GameTestSupport.source();
        ItemStack filledSource = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack spawnEgg = new ItemStack(Items.PIG_SPAWN_EGG);
        ItemStack ordinaryItem = new ItemStack(Items.PORKCHOP);

        GameTestSupport.check(anyIngredientMatches(recipe, emptySource),
                "Mob Bucket recipe did not accept empty Source Bucket");
        GameTestSupport.check(!anyIngredientMatches(recipe, filledSource),
                "Mob Bucket recipe accepted assigned Source Bucket");
        GameTestSupport.check(anyIngredientMatches(recipe, spawnEgg),
                "Mob Bucket recipe did not accept a standard spawn egg");
        GameTestSupport.check(!anyIngredientMatches(recipe, ordinaryItem),
                "Mob Bucket recipe accepted a non-spawn-egg item");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_big_bucket_is_furnace_fuel_at_one_unit_or_more(GameTestHelper helper) {
        ItemStack oneUnit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000);
        ItemStack severalUnits = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 4000);

        GameTestSupport.check(ForgeHooks.getBurnTime(oneUnit, RecipeType.SMELTING)
                        == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "One-unit lava Big Bucket did not report lava-bucket burn time");
        GameTestSupport.check(ForgeHooks.getBurnTime(severalUnits, RecipeType.SMELTING)
                        == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "Multi-unit lava Big Bucket did not report one-unit burn time");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void subunit_lava_and_nonlava_buckets_are_not_fuel(GameTestHelper helper) {
        ItemStack subunit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 999);
        ItemStack water = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 8000);

        GameTestSupport.check(ForgeHooks.getBurnTime(subunit, RecipeType.SMELTING) == 0,
                "Subunit lava Big Bucket was furnace fuel");
        GameTestSupport.check(ForgeHooks.getBurnTime(water, RecipeType.SMELTING) == 0,
                "Water Big Bucket was furnace fuel");
        GameTestSupport.check(ForgeHooks.getBurnTime(milk, RecipeType.SMELTING) == 0,
                "Milk Big Bucket was furnace fuel");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_source_bucket_is_permanent_fuel(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        int burnTime = ForgeHooks.getBurnTime(source, RecipeType.SMELTING);
        ItemStack remainder = source.getCraftingRemainingItem();

        GameTestSupport.check(burnTime == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "Lava Source Bucket burn time was " + burnTime);
        GameTestSupport.assertSameStack(source, remainder, "Lava Source crafting remainder changed");
        helper.succeed();
    }

    private static Recipe<?> recipe(GameTestHelper helper, String path) {
        return helper.getLevel().getRecipeManager().byKey(new ResourceLocation(SomeBuckets.MODID, path))
                .orElseThrow(() -> new GameTestAssertException("Missing recipe somebuckets:" + path));
    }

    private static boolean anyIngredientMatches(Recipe<?> recipe, ItemStack stack) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.test(stack)) return true;
        }
        return false;
    }
}
