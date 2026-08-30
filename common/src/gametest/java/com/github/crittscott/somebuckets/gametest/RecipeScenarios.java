package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;

final class RecipeScenarios {
    private RecipeScenarios() {}
    static void all_shipped_recipe_ids_load(GameTestHelper helper) {
        checkRecipeResult(helper, "big_bucket_8", GameTestSupport.big8());
        checkRecipeResult(helper, "big_bucket_64", GameTestSupport.big64());
        checkRecipeResult(helper, "junk_bucket", GameTestSupport.junk());
        checkRecipeResult(helper, "trash_bucket", GameTestSupport.trash());
        checkRecipeResult(helper, "source_bucket", GameTestSupport.source());
        checkRecipeResult(helper, "mob_bucket", GameTestSupport.mob());
        helper.succeed();
    }

    private static void checkRecipeResult(GameTestHelper helper, String path, ItemStack expected) {
        Recipe<?> recipe = recipe(helper, path);
        ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
        GameTestSupport.check(result.getItem() == expected.getItem(),
                "Recipe somebuckets:" + path + " produced " + result);
        GameTestSupport.check(NBTUtil.isEmptyBucket(result),
                "Recipe somebuckets:" + path + " produced a non-empty bucket");
    }
    static void huge_bucket_recipe_accepts_only_empty_big_buckets(GameTestHelper helper) {
        Recipe<?> recipe = recipe(helper, "big_bucket_64");
        ItemStack empty = GameTestSupport.big8();
        ItemStack filled = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);

        GameTestSupport.check(anyIngredientMatches(recipe, empty),
                "Huge Bucket recipe did not accept empty Big Bucket");
        GameTestSupport.check(!anyIngredientMatches(recipe, filled),
                "Huge Bucket recipe accepted filled Big Bucket");
        helper.succeed();
    }
    static void mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(GameTestHelper helper) {
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

    private static Recipe<?> recipe(GameTestHelper helper, String path) {
        return helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, path))
                .orElseThrow(() -> new GameTestAssertException("Missing recipe somebuckets:" + path))
                .value();
    }

    private static boolean anyIngredientMatches(Recipe<?> recipe, ItemStack stack) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.test(stack)) return true;
        }
        return false;
    }
}


