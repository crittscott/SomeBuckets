package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * NeoForge-only conversion coverage for {@link NeoForgeFluidStacks}. NeoForge's {@link FluidStack}
 * and {@code StoredFluid} both carry a {@link DataComponentPatch}, so a fluid's components must survive
 * the conversion, bucket storage, and item-stack persistence with registry context unchanged.
 */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class NeoForgeFluidStacksGameTests {

    private NeoForgeFluidStacksGameTests() {}

    /**
     * Automation-only: round-trips a NeoForge fluid component through bucket storage and item-stack
     * persistence without losing it.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void fluid_components_survive_round_trip(GameTestHelper helper) {
        CompoundTag marker = new CompoundTag();
        marker.putString("sb_variant_probe", "kept");
        FluidStack fluidStack = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
        fluidStack.applyComponents(DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_DATA, CustomData.of(marker))
                .build());

        ItemStack bucket = GameTestSupport.big8();
        NeoForgeFluidStacks.set(bucket, fluidStack);
        GameTestSupport.check(NeoForgeFluidStacks.sameFluid(fluidStack, NeoForgeFluidStacks.get(bucket)),
                "Fluid components did not survive NeoForgeFluidStacks.set/get on an item stack");

        var registries = helper.getLevel().registryAccess();
        ItemStack reloaded = ItemStack.parse(registries, bucket.save(registries)).orElseThrow();
        GameTestSupport.check(NeoForgeFluidStacks.sameFluid(fluidStack, NeoForgeFluidStacks.get(reloaded)),
                "Fluid components did not survive item-stack persistence");

        FluidStack plain = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
        ItemStack plainBucket = GameTestSupport.big8();
        NeoForgeFluidStacks.set(plainBucket, plain);
        GameTestSupport.check(NeoForgeFluidStacks.get(plainBucket).getComponentsPatch().isEmpty(),
                "Plain water gained components through bucket storage");

        helper.succeed();
    }
}
