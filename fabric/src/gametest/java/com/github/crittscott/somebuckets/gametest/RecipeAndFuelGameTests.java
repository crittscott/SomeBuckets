package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.material.Fluids;

/**
 * Recipe coverage plus the Fabric furnace-fuel path. {@code FuelValuesMixin} makes an eligible
 * lava-holding bucket report as fuel and gives it a lava-bucket burn duration.
 */
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
    public void lava_big_bucket_is_furnace_fuel_at_one_unit_or_more(GameTestHelper helper) {
        FuelValues fuelValues = helper.getLevel().fuelValues();
        ItemStack oneUnit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000);
        ItemStack severalUnits = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 4000);
        GameTestSupport.check(fuelValues.isFuel(oneUnit),
                "One-unit lava Big Bucket was not furnace fuel");
        GameTestSupport.check(fuelValues.isFuel(severalUnits),
                "Multi-unit lava Big Bucket was not furnace fuel");
        GameTestSupport.check(fuelValues.burnDuration(oneUnit)
                        == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "Lava Big Bucket did not report lava-bucket burn time");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void subunit_lava_and_nonlava_buckets_are_not_fuel(GameTestHelper helper) {
        FuelValues fuelValues = helper.getLevel().fuelValues();
        ItemStack subunit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 999);
        GameTestSupport.check(!fuelValues.isFuel(subunit),
                "Subunit lava Big Bucket was furnace fuel");
        GameTestSupport.check(!fuelValues.isFuel(
                        GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000)),
                "Water Big Bucket was furnace fuel");
        GameTestSupport.check(!fuelValues.isFuel(
                        GameTestSupport.milk(GameTestSupport.big8(), 8000)),
                "Milk Big Bucket was furnace fuel");
        GameTestSupport.check(fuelValues.burnDuration(subunit) == 0,
                "Subunit lava Big Bucket had a burn duration");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void lava_source_bucket_is_permanent_furnace_fuel(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        GameTestSupport.check(helper.getLevel().fuelValues().isFuel(source),
                "Lava Source Bucket was not furnace fuel");
        ItemStack remainder = ((FabricItem) source.getItem()).getRecipeRemainder(source);
        GameTestSupport.check(source.getItem() instanceof SBItem, "Source Bucket item type changed");
        GameTestSupport.assertSameStack(source, remainder,
                "Lava Source Bucket fuel remainder changed the assignment");
        helper.succeed();
    }

}

