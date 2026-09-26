package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Forge fuel coverage. {@code IForgeItem#getBurnTime} is Forge's per-item furnace-fuel hook;
 * {@code Forge{BB,SB}Item} report the lava-bucket burn time for eligible lava content and {@code 0}
 * otherwise.
 */
@GameTestHolder(SomeBuckets.MODID)
public final class ForgeFuelGameTests {
    private ForgeFuelGameTests() {}

    private static int burnTime(ItemStack stack) {
        return stack.getItem().getBurnTime(stack, RecipeType.SMELTING);
    }

    /**
     * Manual: put a Big or Huge Bucket containing at least one lava unit into a furnace; it reports the
     * lava-bucket burn duration.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_big_bucket_is_furnace_fuel_at_one_unit_or_more(GameTestHelper helper) {
        ItemStack oneUnit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000);
        ItemStack severalUnits = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 4000);

        GameTestSupport.check(burnTime(oneUnit) == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "One-unit lava Big Bucket did not report lava-bucket burn time");
        GameTestSupport.check(burnTime(severalUnits) == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "Multi-unit lava Big Bucket did not report one-unit burn time");
        helper.succeed();
    }

    /** Automation-only: compares sub-unit lava and non-lava stacks and verifies neither qualifies as furnace fuel. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void subunit_lava_and_nonlava_buckets_are_not_fuel(GameTestHelper helper) {
        ItemStack subunit = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 999);
        ItemStack water = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 8000);

        GameTestSupport.check(burnTime(subunit) == 0, "Subunit lava Big Bucket was furnace fuel");
        GameTestSupport.check(burnTime(water) == 0, "Water Big Bucket was furnace fuel");
        GameTestSupport.check(burnTime(milk) == 0, "Milk Big Bucket was furnace fuel");
        helper.succeed();
    }

    /**
     * Manual: use an allowed lava Source Bucket as furnace fuel; it burns for the lava duration and
     * remains assigned.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_source_bucket_is_permanent_fuel(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        int result = burnTime(source);
        ItemStack remainder = source.getCraftingRemainder();

        GameTestSupport.check(result == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                "Lava Source Bucket burn time was " + result);
        GameTestSupport.assertSameStack(source, remainder, "Lava Source crafting remainder changed");
        helper.succeed();
    }

}
