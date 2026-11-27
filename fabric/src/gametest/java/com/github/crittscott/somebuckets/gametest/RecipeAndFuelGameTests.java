package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class RecipeAndFuelGameTests {
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void all_shipped_recipe_ids_load(GameTestHelper helper) {
        RecipeScenarios.all_shipped_recipe_ids_load(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void huge_bucket_recipe_accepts_only_empty_big_buckets(GameTestHelper helper) {
        RecipeScenarios.huge_bucket_recipe_accepts_only_empty_big_buckets(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(GameTestHelper helper) {
        RecipeScenarios.mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void trash_bucket_recipe_accepts_only_empty_junk_buckets(GameTestHelper helper) {
        RecipeScenarios.trash_bucket_recipe_accepts_only_empty_junk_buckets(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_bucket_recipe_accepts_only_empty_trash_buckets(GameTestHelper helper) {
        RecipeScenarios.source_bucket_recipe_accepts_only_empty_trash_buckets(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void lava_big_bucket_burns_one_unit_in_furnace(GameTestHelper helper) {
        StateScenarios.lava_big_bucket_burns_one_unit_in_furnace(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void lava_source_bucket_is_permanent_furnace_fuel(GameTestHelper helper) {
        StateScenarios.lava_source_bucket_is_permanent_furnace_fuel(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void non_lava_buckets_do_not_fuel_a_furnace(GameTestHelper helper) {
        StateScenarios.non_lava_buckets_do_not_fuel_a_furnace(helper);
    }
}
