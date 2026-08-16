package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class StateGameTests {
    private StateGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void fluid_sound_resolution_prefers_registered_sound_then_fallback(GameTestHelper helper) {
        StateScenarios.fluid_sound_resolution_prefers_registered_sound_then_fallback(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void pristine_bucket_reads_do_not_attach_nbt(GameTestHelper helper) {
        StateScenarios.pristine_bucket_reads_do_not_attach_nbt(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void clear_removes_all_content_and_preserves_unrelated_nbt(GameTestHelper helper) {
        StateScenarios.clear_removes_all_content_and_preserves_unrelated_nbt(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void zero_content_modes_normalize_to_none(GameTestHelper helper) {
        StateScenarios.zero_content_modes_normalize_to_none(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        StateScenarios.stored_items_round_trip_with_order_counts_and_tags(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void stored_item_reads_are_detached_and_empty_writes_clean_tags(GameTestHelper helper) {
        StateScenarios.stored_item_reads_are_detached_and_empty_writes_clean_tags(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void negative_content_setters_fail_without_mutation(GameTestHelper helper) {
        StateScenarios.negative_content_setters_fail_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        StateScenarios.bucket_tooltips_preserve_translatable_components(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void entity_snapshots_are_fifo_and_final_removal_normalizes(GameTestHelper helper) {
        StateScenarios.entity_snapshots_are_fifo_and_final_removal_normalizes(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void finite_crafting_remainders_consume_one_unit(GameTestHelper helper) {
        StateScenarios.finite_crafting_remainders_consume_one_unit(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        StateScenarios.final_finite_crafting_remainder_is_empty(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_finite_and_source_buckets_have_no_crafting_remainder(GameTestHelper helper) {
        StateScenarios.empty_finite_and_source_buckets_have_no_crafting_remainder(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void assigned_source_crafting_remainder_is_unchanged(GameTestHelper helper) {
        StateScenarios.assigned_source_crafting_remainder_is_unchanged(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_simulation_does_not_mutate(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();
        IFluidHandlerItem handler = fluidHandler(stack);

        int filled = handler.fill(new FluidStack(Fluids.WATER, 3000), IFluidHandler.FluidAction.SIMULATE);

        GameTestSupport.check(filled == 3000, "Simulated fill reported " + filled + " instead of 3000");
        GameTestSupport.assertEmpty(stack);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_partial_drain_and_simulation_preserve_state(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2500);
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");
        ItemStack beforeSimulation = stack.copy();
        IFluidHandlerItem handler = fluidHandler(stack);

        FluidStack simulated = handler.drain(750, IFluidHandler.FluidAction.SIMULATE);

        GameTestSupport.check(simulated.getFluid() == Fluids.WATER && simulated.getAmount() == 750,
                "Simulated partial drain returned " + simulated);
        GameTestSupport.assertSameStack(beforeSimulation, stack, "Simulated drain mutated Big Bucket");

        FluidStack executed = handler.drain(750, IFluidHandler.FluidAction.EXECUTE);

        GameTestSupport.check(executed.getFluid() == Fluids.WATER && executed.getAmount() == 750,
                "Executed partial drain returned " + executed);
        GameTestSupport.assertFluid(stack, Fluids.WATER, 1750);
        GameTestSupport.check("preserve-me".equals(stack.getOrCreateTag().getString("Unrelated")),
                "Partial drain removed unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_honors_capacity_and_clears_on_final_drain(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");
        IFluidHandlerItem handler = fluidHandler(stack);

        int filled = handler.fill(new FluidStack(Fluids.WATER, 9000), IFluidHandler.FluidAction.EXECUTE);
        FluidStack drained = handler.drain(8000, IFluidHandler.FluidAction.EXECUTE);

        GameTestSupport.check(filled == 8000, "8-unit bucket accepted " + filled + " mB");
        GameTestSupport.check(drained.getFluid() == Fluids.WATER && drained.getAmount() == 8000,
                "Final drain returned " + drained);
        GameTestSupport.assertEmpty(stack);
        GameTestSupport.check("preserve-me".equals(stack.getOrCreateTag().getString("Unrelated")),
                "Final drain removed unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.milk(GameTestSupport.big8(), 1500);
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");

        int partial = NBTUtil.drainFiniteContent(stack, 600);

        GameTestSupport.check(partial == 600, "Partial milk drain reported " + partial + " mB");
        GameTestSupport.assertMilk(stack, 900);

        int finalDrain = NBTUtil.drainFiniteContent(stack, 900);

        GameTestSupport.check(finalDrain == 900, "Final milk drain reported " + finalDrain + " mB");
        GameTestSupport.assertEmpty(stack);
        GameTestSupport.check("preserve-me".equals(stack.getOrCreateTag().getString("Unrelated")),
                "Milk drain removed unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_rejects_incompatible_fluid(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        ItemStack before = stack.copy();
        IFluidHandlerItem handler = fluidHandler(stack);

        int filled = handler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);

        GameTestSupport.check(filled == 0, "Incompatible fluid fill reported " + filled);
        GameTestSupport.assertSameStack(before, stack, "Incompatible fill mutated Big Bucket");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void nonfluid_modes_are_hidden_from_fluid_capability(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 1);

        GameTestSupport.check(fluidHandler(milk).getFluidInTank(0).isEmpty(),
                "Milk appeared as a Forge fluid");
        GameTestSupport.check(fluidHandler(powder).getFluidInTank(0).isEmpty(),
                "Powder snow appeared as a Forge fluid");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_capability_is_an_infinite_source_and_sink(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.source();
        IFluidHandlerItem handler = fluidHandler(stack);

        int assigned = handler.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
        int accepted = handler.fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        FluidStack drained = handler.drain(1000, IFluidHandler.FluidAction.EXECUTE);

        GameTestSupport.check(assigned == 500, "Initial Source fill reported " + assigned);
        GameTestSupport.check(accepted == 1000, "Assigned Source accepted " + accepted + " mB");
        GameTestSupport.check(drained.getFluid() == Fluids.WATER && drained.getAmount() == 1000,
                "Source drain returned " + drained);
        GameTestSupport.assertFluid(stack, Fluids.WATER, 1000);
        helper.succeed();
    }

    private static IFluidHandlerItem fluidHandler(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                .orElseThrow(() -> new GameTestAssertException("Bucket exposed no fluid capability"));
    }

}


