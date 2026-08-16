package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

final class RecipeScenarios {
    private RecipeScenarios() {}
    static void all_shipped_recipe_ids_load(GameTestHelper helper) {
        for (String path : List.of("big_bucket_8", "big_bucket_64", "junk_bucket",
                "trash_bucket", "source_bucket", "mob_bucket")) {
            recipe(helper, path);
        }
        helper.succeed();
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


