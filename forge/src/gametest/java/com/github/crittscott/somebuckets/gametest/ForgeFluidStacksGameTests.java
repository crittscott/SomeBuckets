package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Forge-only conversion coverage for {@link ForgeFluidStacks}. A Forge {@link FluidStack} carries its
 * variant as a tag that bucket storage keeps as custom data, so the tag must survive the conversion,
 * bucket storage, and item-stack persistence unchanged.
 */
@GameTestHolder(SomeBuckets.MODID)
public final class ForgeFluidStacksGameTests {

    private ForgeFluidStacksGameTests() {}

    /**
     * Automation-only: round-trips a Forge fluid tag through bucket storage and item-stack persistence
     * without losing it.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void fluid_tag_survives_round_trip(GameTestHelper helper) {
        CompoundTag marker = new CompoundTag();
        marker.putString("sb_variant_probe", "kept");
        FluidStack fluidStack = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME, marker);

        ItemStack bucket = GameTestSupport.big8();
        ForgeFluidStacks.set(bucket, fluidStack);
        GameTestSupport.check(ForgeFluidStacks.sameFluid(fluidStack, ForgeFluidStacks.get(bucket)),
                "Fluid tag did not survive ForgeFluidStacks.set/get on an item stack");

        var registries = helper.getLevel().registryAccess();
        ItemStack reloaded = ItemStack.parse(registries, bucket.save(registries)).orElseThrow();
        GameTestSupport.check(ForgeFluidStacks.sameFluid(fluidStack, ForgeFluidStacks.get(reloaded)),
                "Fluid tag did not survive item-stack persistence");

        FluidStack plain = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
        ItemStack plainBucket = GameTestSupport.big8();
        ForgeFluidStacks.set(plainBucket, plain);
        GameTestSupport.check(ForgeFluidStacks.get(plainBucket).getTag() == null,
                "Plain water gained a tag through bucket storage");
        GameTestSupport.check(!ForgeFluidStacks.sameFluid(fluidStack, ForgeFluidStacks.get(plainBucket)),
                "Tagged and plain water compared as the same fluid");

        helper.succeed();
    }
}
