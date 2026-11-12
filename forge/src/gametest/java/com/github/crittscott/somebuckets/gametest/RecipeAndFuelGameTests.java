package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/** Forge GameTest wrappers for shared recipe scenarios. */
public final class RecipeAndFuelGameTests {
    private RecipeAndFuelGameTests() {}

    /** See {@link RecipeScenarios#all_shipped_recipe_ids_load}. */
    public static void all_shipped_recipe_ids_load(GameTestHelper helper) {
        RecipeScenarios.all_shipped_recipe_ids_load(helper);
    }

    /** See {@link RecipeScenarios#huge_bucket_recipe_accepts_only_empty_big_buckets}. */
    public static void huge_bucket_recipe_accepts_only_empty_big_buckets(GameTestHelper helper) {
        RecipeScenarios.huge_bucket_recipe_accepts_only_empty_big_buckets(helper);
    }

    /** See {@link RecipeScenarios#mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg}. */
    public static void mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(GameTestHelper helper) {
        RecipeScenarios.mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(helper);
    }

    /** See {@link RecipeScenarios#trash_bucket_recipe_accepts_only_empty_junk_buckets}. */
    public static void trash_bucket_recipe_accepts_only_empty_junk_buckets(GameTestHelper helper) {
        RecipeScenarios.trash_bucket_recipe_accepts_only_empty_junk_buckets(helper);
    }

    /** See {@link RecipeScenarios#source_bucket_recipe_accepts_only_empty_trash_buckets}. */
    public static void source_bucket_recipe_accepts_only_empty_trash_buckets(GameTestHelper helper) {
        RecipeScenarios.source_bucket_recipe_accepts_only_empty_trash_buckets(helper);
    }

    /** See {@link RecipeScenarios#furnace_burns_one_lava_unit_and_returns_the_big_bucket}. */
    public static void furnace_burns_one_lava_unit_and_returns_the_big_bucket(GameTestHelper helper) {
        RecipeScenarios.furnace_burns_one_lava_unit_and_returns_the_big_bucket(helper);
    }
}
