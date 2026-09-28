package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge fuel coverage. {@code ItemStack#getBurnTime(RecipeType)} is NeoForge's furnace-fuel query;
 * it never returns a negative value, so {@code NeoForge{BB,SB}Item#getBurnTime} report {@code 0} for
 * the non-lava case rather than Forge's {@code -1} "defer to vanilla" sentinel.
 */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class NeoForgeFuelGameTests {
    private NeoForgeFuelGameTests() {}

    /**
     * Manual: put a Big or Huge Bucket containing at least one lava unit into a furnace; it reports the
     * lava-bucket burn duration.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_big_bucket_is_furnace_fuel_at_one_unit_or_more(GameTestHelper helper) {
        ItemStack oneUnit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000);
        ItemStack severalUnits = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 4000);
        FuelValues fuelValues = helper.getLevel().fuelValues();
        int lavaBucketBurnTime = fuelValues.burnDuration(new ItemStack(Items.LAVA_BUCKET));

        GameTestSupport.check(oneUnit.getBurnTime(RecipeType.SMELTING, fuelValues)
                        == lavaBucketBurnTime,
                "One-unit lava Big Bucket did not report lava-bucket burn time");
        GameTestSupport.check(severalUnits.getBurnTime(RecipeType.SMELTING, fuelValues)
                        == lavaBucketBurnTime,
                "Multi-unit lava Big Bucket did not report one-unit burn time");
        helper.succeed();
    }

    /**
     * Manual: transfer less than one bucket of lava from a compatible item into a Big Bucket and try it,
     * a water-filled Big Bucket, and a milk-filled Big Bucket as furnace fuel; none burns. Automation:
     * constructs the exact sub-unit lava state before querying NeoForge's fuel contract.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void subunit_lava_and_nonlava_buckets_are_not_fuel(GameTestHelper helper) {
        ItemStack subunit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 999);
        ItemStack water = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 8000);
        FuelValues fuelValues = helper.getLevel().fuelValues();

        GameTestSupport.check(subunit.getBurnTime(RecipeType.SMELTING, fuelValues) == 0,
                "Subunit lava Big Bucket was furnace fuel");
        GameTestSupport.check(water.getBurnTime(RecipeType.SMELTING, fuelValues) == 0,
                "Water Big Bucket was furnace fuel");
        GameTestSupport.check(milk.getBurnTime(RecipeType.SMELTING, fuelValues) == 0,
                "Milk Big Bucket was furnace fuel");
        helper.succeed();
    }

    /**
     * Manual: use an allowed lava Source Bucket as furnace fuel; it burns for the lava duration and
     * remains assigned.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_source_bucket_is_permanent_fuel(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        FuelValues fuelValues = helper.getLevel().fuelValues();
        int burnTime = source.getBurnTime(RecipeType.SMELTING, fuelValues);
        ItemStack remainder = source.getCraftingRemainder();

        GameTestSupport.check(burnTime == fuelValues.burnDuration(new ItemStack(Items.LAVA_BUCKET)),
                "Lava Source Bucket burn time was " + burnTime);
        GameTestSupport.assertSameStack(source, remainder, "Lava Source crafting remainder changed");
        helper.succeed();
    }

}
