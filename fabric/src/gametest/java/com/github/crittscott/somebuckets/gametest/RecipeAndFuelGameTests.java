package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.SBItem;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;

/**
 * Recipe coverage plus the Fabric furnace-fuel path. The mixin
 * {@code AbstractFurnaceBlockEntityMixin} makes a lava-holding finite bucket report as fuel and
 * gives it a lava-bucket burn duration; {@code AbstractFurnaceBlockEntity#isFuel} is vanilla's own
 * fuel query and reflects the {@code isFuel} injector.
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
    public void lava_big_bucket_is_furnace_fuel_at_one_unit_or_more(GameTestHelper helper) {
        GameTestSupport.check(AbstractFurnaceBlockEntity.isFuel(
                        GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000)),
                "One-unit lava Big Bucket was not furnace fuel");
        GameTestSupport.check(AbstractFurnaceBlockEntity.isFuel(
                        GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 4000)),
                "Multi-unit lava Big Bucket was not furnace fuel");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void subunit_lava_and_nonlava_buckets_are_not_fuel(GameTestHelper helper) {
        GameTestSupport.check(!AbstractFurnaceBlockEntity.isFuel(
                        GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 999)),
                "Subunit lava Big Bucket was furnace fuel");
        GameTestSupport.check(!AbstractFurnaceBlockEntity.isFuel(
                        GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000)),
                "Water Big Bucket was furnace fuel");
        GameTestSupport.check(!AbstractFurnaceBlockEntity.isFuel(
                        GameTestSupport.milk(GameTestSupport.big8(), 8000)),
                "Milk Big Bucket was furnace fuel");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void lava_source_bucket_is_permanent_furnace_fuel(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        GameTestSupport.check(AbstractFurnaceBlockEntity.isFuel(source),
                "Lava Source Bucket was not furnace fuel");
        ItemStack remainder = ((FabricItem) source.getItem()).getRecipeRemainder(source);
        GameTestSupport.check(source.getItem() instanceof SBItem, "Source Bucket item type changed");
        GameTestSupport.assertSameStack(source, remainder,
                "Lava Source Bucket fuel remainder changed the assignment");
        helper.succeed();
    }

}

