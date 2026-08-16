package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class ForgeFuelGameTests {
    private ForgeFuelGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_big_bucket_is_furnace_fuel_at_one_unit_or_more(GameTestHelper helper) {
        ItemStack oneUnit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000);
        ItemStack severalUnits = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 4000);

        GameTestSupport.check(ForgeHooks.getBurnTime(oneUnit, RecipeType.SMELTING)
                        == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "One-unit lava Big Bucket did not report lava-bucket burn time");
        GameTestSupport.check(ForgeHooks.getBurnTime(severalUnits, RecipeType.SMELTING)
                        == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "Multi-unit lava Big Bucket did not report one-unit burn time");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void subunit_lava_and_nonlava_buckets_are_not_fuel(GameTestHelper helper) {
        ItemStack subunit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 999);
        ItemStack water = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 8000);

        GameTestSupport.check(ForgeHooks.getBurnTime(subunit, RecipeType.SMELTING) == 0,
                "Subunit lava Big Bucket was furnace fuel");
        GameTestSupport.check(ForgeHooks.getBurnTime(water, RecipeType.SMELTING) == 0,
                "Water Big Bucket was furnace fuel");
        GameTestSupport.check(ForgeHooks.getBurnTime(milk, RecipeType.SMELTING) == 0,
                "Milk Big Bucket was furnace fuel");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_source_bucket_is_permanent_fuel(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        int burnTime = ForgeHooks.getBurnTime(source, RecipeType.SMELTING);
        ItemStack remainder = source.getCraftingRemainingItem();

        GameTestSupport.check(burnTime == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "Lava Source Bucket burn time was " + burnTime);
        GameTestSupport.assertSameStack(source, remainder, "Lava Source crafting remainder changed");
        helper.succeed();
    }

}

