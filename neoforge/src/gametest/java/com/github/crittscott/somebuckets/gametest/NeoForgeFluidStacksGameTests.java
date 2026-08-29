package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge-only conversion coverage for {@link NeoForgeFluidStacks}. NeoForge's {@link FluidStack} is
 * component-based, so {@code StoredFluid}'s optional variant {@link CompoundTag} is bridged to and
 * from a {@code DataComponentPatch} through {@code DataComponentPatch.CODEC} over plain
 * {@code NbtOps}. A component that needs registry context to serialize degrades to a blank patch;
 * this asserts that a registry-free component ({@code minecraft:custom_data}) survives the bridge in
 * both directions, including through the item-stack storage path used by Big and Source Buckets.
 */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class NeoForgeFluidStacksGameTests {

    private NeoForgeFluidStacksGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void custom_data_fluid_variant_survives_round_trip(GameTestHelper helper) {
        CompoundTag inner = new CompoundTag();
        inner.putString("sb_variant_probe", "kept");
        CompoundTag variantTag = new CompoundTag();
        variantTag.put("minecraft:custom_data", inner);

        // CompoundTag -> DataComponentPatch -> FluidStack
        FluidStack fluidStack = NeoForgeFluidStacks.of(Fluids.WATER, FluidType.BUCKET_VOLUME, variantTag);
        GameTestSupport.check(!fluidStack.getComponentsPatch().isEmpty(),
                "NeoForgeFluidStacks.of dropped a registry-free custom_data variant");

        // FluidStack -> DataComponentPatch -> CompoundTag
        CompoundTag out = NeoForgeFluidStacks.variantTag(fluidStack);
        GameTestSupport.check(variantTag.equals(out),
                "custom_data variant did not round-trip: " + variantTag + " -> " + out);

        // The item-stack storage path (FluidStack <-> StoredFluid component) used by BB and SB.
        ItemStack bucket = GameTestSupport.big8();
        NeoForgeFluidStacks.set(bucket, fluidStack);
        GameTestSupport.check(NeoForgeFluidStacks.sameFluid(fluidStack, NeoForgeFluidStacks.get(bucket)),
                "custom_data variant did not survive NeoForgeFluidStacks.set/get on an item stack");

        // A variantless fluid stays variantless.
        FluidStack plain = NeoForgeFluidStacks.of(Fluids.WATER, FluidType.BUCKET_VOLUME, null);
        GameTestSupport.check(NeoForgeFluidStacks.variantTag(plain) == null,
                "plain water reported a non-null variant tag");

        helper.succeed();
    }
}
