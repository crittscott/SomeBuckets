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

}

