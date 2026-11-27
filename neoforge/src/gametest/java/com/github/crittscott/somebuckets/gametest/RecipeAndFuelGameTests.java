package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** NeoForge GameTest wrappers for shared recipe and furnace-fuel scenarios. */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeAndFuelGameTests {
    private RecipeAndFuelGameTests() {}

    /** See {@link RecipeScenarios#all_shipped_recipe_ids_load}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void all_shipped_recipe_ids_load(GameTestHelper helper) {
        RecipeScenarios.all_shipped_recipe_ids_load(helper);
    }

    /** See {@link RecipeScenarios#huge_bucket_recipe_accepts_only_empty_big_buckets}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void huge_bucket_recipe_accepts_only_empty_big_buckets(GameTestHelper helper) {
        RecipeScenarios.huge_bucket_recipe_accepts_only_empty_big_buckets(helper);
    }

    /** See {@link RecipeScenarios#mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(GameTestHelper helper) {
        RecipeScenarios.mob_bucket_recipe_accepts_empty_source_and_standard_spawn_egg(helper);
    }

    /** See {@link RecipeScenarios#trash_bucket_recipe_accepts_only_empty_junk_buckets}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_recipe_accepts_only_empty_junk_buckets(GameTestHelper helper) {
        RecipeScenarios.trash_bucket_recipe_accepts_only_empty_junk_buckets(helper);
    }

    /** See {@link RecipeScenarios#source_bucket_recipe_accepts_only_empty_trash_buckets}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_bucket_recipe_accepts_only_empty_trash_buckets(GameTestHelper helper) {
        RecipeScenarios.source_bucket_recipe_accepts_only_empty_trash_buckets(helper);
    }

    /** See {@link RecipeScenarios#furnace_burns_one_lava_unit_and_returns_the_big_bucket}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void furnace_burns_one_lava_unit_and_returns_the_big_bucket(GameTestHelper helper) {
        RecipeScenarios.furnace_burns_one_lava_unit_and_returns_the_big_bucket(helper);
    }

    /** See {@link RecipeScenarios#lava_source_bucket_is_permanent_furnace_fuel}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void lava_source_bucket_is_permanent_furnace_fuel(GameTestHelper helper) {
        RecipeScenarios.lava_source_bucket_is_permanent_furnace_fuel(helper);
    }

    /** See {@link RecipeScenarios#non_lava_buckets_do_not_fuel_a_furnace}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void non_lava_buckets_do_not_fuel_a_furnace(GameTestHelper helper) {
        RecipeScenarios.non_lava_buckets_do_not_fuel_a_furnace(helper);
    }
}
