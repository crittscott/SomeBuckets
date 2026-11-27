package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/** Crafting-recipe scenarios shared by both loaders. */
final class RecipeScenarios {
    private RecipeScenarios() {}

    /**
     * No in-game form: guards that every shipped recipe (Big, Huge, Junk, Trash, Source and Mob Bucket)
     * loads, including the custom ingredient serializers; in game a recipe that failed to load is simply
     * missing from the recipe book.
     */
    static void all_shipped_recipe_ids_load(GameTestHelper helper) {
        for (String path : List.of("big_bucket_8", "big_bucket_64", "junk_bucket",
                "trash_bucket", "source_bucket", "mob_bucket")) {
            recipe(helper, path);
        }
        helper.succeed();
    }

    /**
     * In game: open a crafting table and lay out the Huge Bucket recipe with empty Big Buckets, then with one
     * Big Bucket holding water; only the all-empty layout offers a Huge Bucket.
     */
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

    /**
     * In game: lay out the Mob Bucket recipe with an empty Source Bucket and a pig spawn egg; it offers a Mob
     * Bucket. With a water Source Bucket instead, or a porkchop instead of the egg, it offers nothing.
     */
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

    /**
     * In game: lay out the Source Bucket recipe with an empty Trash Bucket and a netherite block; it
     * offers a Source Bucket. With a Trash Bucket that holds an item, it offers nothing.
     */
    static void source_bucket_recipe_accepts_only_empty_trash_bucket(GameTestHelper helper) {
        Recipe<?> recipe = recipe(helper, "source_bucket");
        ItemStack empty = GameTestSupport.trash();
        ItemStack filled = GameTestSupport.trash();
        BucketState.setStoredItems(filled, List.of(new ItemStack(Items.APPLE)));

        GameTestSupport.check(anyIngredientMatches(recipe, empty),
                "Source Bucket recipe did not accept an empty Trash Bucket");
        GameTestSupport.check(!anyIngredientMatches(recipe, filled),
                "Source Bucket recipe accepted a Trash Bucket holding an item");
        helper.succeed();
    }

    /**
     * In game: lay out the Trash Bucket recipe with an empty Junk Bucket, an enderman spawn egg and an
     * eye of ender; it offers a Trash Bucket. With a Junk Bucket that holds items, it offers nothing.
     */
    static void trash_bucket_recipe_accepts_only_empty_junk_bucket(GameTestHelper helper) {
        Recipe<?> recipe = recipe(helper, "trash_bucket");
        ItemStack empty = GameTestSupport.junk();
        ItemStack filled = GameTestSupport.junk();
        BucketState.setStoredItems(filled, List.of(new ItemStack(Items.APPLE, 3)));

        GameTestSupport.check(anyIngredientMatches(recipe, empty),
                "Trash Bucket recipe did not accept an empty Junk Bucket");
        GameTestSupport.check(!anyIngredientMatches(recipe, filled),
                "Trash Bucket recipe accepted a Junk Bucket holding items");
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


