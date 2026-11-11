package com.github.crittscott.somebuckets.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Cross-loader recipe and furnace scenarios registered on Fabric. */
public final class RecipeAndFuelGameTests {
    /** See {@link RecipeScenarios#all_shipped_recipe_ids_load}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.SHORT_TIMEOUT)
    public void all_shipped_recipe_ids_load(GameTestHelper helper) {
        RecipeScenarios.all_shipped_recipe_ids_load(helper);
    }

    /** See {@link RecipeScenarios#huge_bucket_recipe_accepts_only_empty_big_buckets}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.SHORT_TIMEOUT)
    public void huge_bucket_recipe_accepts_only_empty_big_buckets(GameTestHelper helper) {
        RecipeScenarios.huge_bucket_recipe_accepts_only_empty_big_buckets(helper);
    }

    /** See {@link RecipeScenarios#mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.SHORT_TIMEOUT)
    public void mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(GameTestHelper helper) {
        RecipeScenarios.mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(helper);
    }

    /** See {@link RecipeScenarios#trash_bucket_recipe_accepts_only_empty_junk_buckets}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.SHORT_TIMEOUT)
    public void trash_bucket_recipe_accepts_only_empty_junk_buckets(GameTestHelper helper) {
        RecipeScenarios.trash_bucket_recipe_accepts_only_empty_junk_buckets(helper);
    }

    /** See {@link RecipeScenarios#source_bucket_recipe_accepts_only_empty_trash_buckets}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_recipe_accepts_only_empty_trash_buckets(GameTestHelper helper) {
        RecipeScenarios.source_bucket_recipe_accepts_only_empty_trash_buckets(helper);
    }

    /** See {@link RecipeScenarios#lava_big_bucket_is_furnace_fuel_at_one_unit_or_more}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.SHORT_TIMEOUT)
    public void lava_big_bucket_is_furnace_fuel_at_one_unit_or_more(GameTestHelper helper) {
        RecipeScenarios.lava_big_bucket_is_furnace_fuel_at_one_unit_or_more(helper);
    }

    /** See {@link RecipeScenarios#subunit_lava_and_nonlava_buckets_are_not_fuel}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.SHORT_TIMEOUT)
    public void subunit_lava_and_nonlava_buckets_are_not_fuel(GameTestHelper helper) {
        RecipeScenarios.subunit_lava_and_nonlava_buckets_are_not_fuel(helper);
    }

    /** See {@link RecipeScenarios#lava_source_bucket_is_permanent_fuel}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.SHORT_TIMEOUT)
    public void lava_source_bucket_is_permanent_fuel(GameTestHelper helper) {
        RecipeScenarios.lava_source_bucket_is_permanent_fuel(helper);
    }

    /** See {@link RecipeScenarios#furnace_burns_one_lava_unit_and_returns_the_big_bucket}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.WORLD_TIMEOUT)
    public void furnace_burns_one_lava_unit_and_returns_the_big_bucket(GameTestHelper helper) {
        RecipeScenarios.furnace_burns_one_lava_unit_and_returns_the_big_bucket(helper);
    }
}
