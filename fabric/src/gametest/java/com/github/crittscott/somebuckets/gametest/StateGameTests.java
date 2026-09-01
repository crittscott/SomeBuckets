package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

public final class StateGameTests {
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void fluid_sound_resolution_prefers_registered_sound_then_fallback(GameTestHelper helper) {
        StateScenarios.fluid_sound_resolution_prefers_registered_sound_then_fallback(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void pristine_bucket_reads_do_not_attach_nbt(GameTestHelper helper) {
        StateScenarios.pristine_bucket_reads_do_not_attach_nbt(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void clear_removes_all_content_and_preserves_unrelated_nbt(GameTestHelper helper) {
        StateScenarios.clear_removes_all_content_and_preserves_unrelated_nbt(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void zero_content_mutators_leave_canonical_empty_state(GameTestHelper helper) {
        StateScenarios.zero_content_mutators_leave_canonical_empty_state(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        StateScenarios.stored_items_round_trip_with_order_counts_and_tags(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void stored_item_reads_are_detached_and_empty_writes_clean_tags(GameTestHelper helper) {
        StateScenarios.stored_item_reads_are_detached_and_empty_writes_clean_tags(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void negative_content_setters_fail_without_mutation(GameTestHelper helper) {
        StateScenarios.negative_content_setters_fail_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        StateScenarios.bucket_tooltips_preserve_translatable_components(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void entity_snapshots_are_fifo_and_final_removal_is_canonical(GameTestHelper helper) {
        StateScenarios.entity_snapshots_are_fifo_and_final_removal_is_canonical(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void finite_crafting_remainders_consume_one_unit(GameTestHelper helper) {
        StateScenarios.finite_crafting_remainders_consume_one_unit(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        StateScenarios.final_finite_crafting_remainder_is_empty(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_finite_and_source_buckets_have_no_crafting_remainder(GameTestHelper helper) {
        StateScenarios.empty_finite_and_source_buckets_have_no_crafting_remainder(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_crafting_remainder_is_unchanged(GameTestHelper helper) {
        StateScenarios.assigned_source_crafting_remainder_is_unchanged(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_simulation_does_not_mutate(GameTestHelper helper) {
        SimpleContainer container = GameTestSupport.containerOf(GameTestSupport.big8());
        Storage<FluidVariant> storage = GameTestSupport.fluidStorage(container);

        long filled = GameTestSupport.insert(storage, FluidVariant.of(Fluids.WATER),
                3000L * GameTestSupport.DROPLETS_PER_MB, false);

        GameTestSupport.check(filled == 3000L * GameTestSupport.DROPLETS_PER_MB,
                "Simulated fill moved " + filled + " droplets instead of 3000 mB worth");
        GameTestSupport.assertEmpty(container.getItem(0));
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_partial_drain_and_simulation_preserve_state(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2500);
        GameTestSupport.updateCustomData(stack, tag -> tag.putString("Unrelated", "preserve-me"));
        ItemStack beforeSimulation = stack.copy();
        SimpleContainer container = GameTestSupport.containerOf(stack);

        long simulated = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 750L * GameTestSupport.DROPLETS_PER_MB, false);

        GameTestSupport.check(simulated == 750L * GameTestSupport.DROPLETS_PER_MB,
                "Simulated partial drain moved " + simulated + " droplets instead of 750 mB worth");
        GameTestSupport.assertSameStack(beforeSimulation, container.getItem(0), "Simulated drain mutated Big Bucket");

        long executed = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 750L * GameTestSupport.DROPLETS_PER_MB, true);

        GameTestSupport.check(executed == 750L * GameTestSupport.DROPLETS_PER_MB,
                "Executed partial drain moved " + executed + " droplets instead of 750 mB worth");
        ItemStack after = container.getItem(0);
        GameTestSupport.assertFluid(after, Fluids.WATER, 1750);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(after).getString("Unrelated")),
                "Partial drain removed unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_honors_capacity_and_clears_on_final_drain(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();
        GameTestSupport.updateCustomData(stack, tag -> tag.putString("Unrelated", "preserve-me"));
        SimpleContainer container = GameTestSupport.containerOf(stack);

        long filled = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 9000L * GameTestSupport.DROPLETS_PER_MB, true);
        long drained = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 8000L * GameTestSupport.DROPLETS_PER_MB, true);

        GameTestSupport.check(filled == 8000L * GameTestSupport.DROPLETS_PER_MB,
                "8-unit bucket accepted " + filled + " droplets instead of 8000 mB worth");
        GameTestSupport.check(drained == 8000L * GameTestSupport.DROPLETS_PER_MB,
                "Final drain moved " + drained + " droplets instead of 8000 mB worth");
        ItemStack after = container.getItem(0);
        GameTestSupport.assertEmpty(after);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(after).getString("Unrelated")),
                "Final drain removed unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.milk(GameTestSupport.big8(), 1500);
        GameTestSupport.updateCustomData(stack, tag -> tag.putString("Unrelated", "preserve-me"));

        int partial = BucketState.drainFiniteContent(stack, 600);

        GameTestSupport.check(partial == 600, "Partial milk drain reported " + partial + " mB");
        GameTestSupport.assertMilk(stack, 900);

        int finalDrain = BucketState.drainFiniteContent(stack, 900);

        GameTestSupport.check(finalDrain == 900, "Final milk drain reported " + finalDrain + " mB");
        GameTestSupport.assertEmpty(stack);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(stack).getString("Unrelated")),
                "Milk drain removed unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_rejects_incompatible_fluid(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        ItemStack before = stack.copy();
        SimpleContainer container = GameTestSupport.containerOf(stack);

        long filled = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.LAVA), 1000L * GameTestSupport.DROPLETS_PER_MB, true);

        GameTestSupport.check(filled == 0, "Incompatible fluid fill moved " + filled + " droplets");
        GameTestSupport.assertSameStack(before, container.getItem(0), "Incompatible fill mutated Big Bucket");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void nonfluid_modes_are_hidden_from_fluid_capability(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 1);

        GameTestSupport.check(
                GameTestSupport.isEmpty(GameTestSupport.fluidStorage(GameTestSupport.containerOf(milk))),
                "Milk appeared as a Fabric fluid");
        GameTestSupport.check(
                GameTestSupport.isEmpty(GameTestSupport.fluidStorage(GameTestSupport.containerOf(powder))),
                "Powder snow appeared as a Fabric fluid");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_capability_is_an_infinite_source_and_sink(GameTestHelper helper) {
        SimpleContainer container = GameTestSupport.containerOf(GameTestSupport.source());

        long assigned = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 500L * GameTestSupport.DROPLETS_PER_MB, true);
        long accepted = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 4000L * GameTestSupport.DROPLETS_PER_MB, true);
        long drained = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 1000L * GameTestSupport.DROPLETS_PER_MB, true);

        GameTestSupport.check(assigned == 500L * GameTestSupport.DROPLETS_PER_MB,
                "Initial Source fill moved " + assigned + " droplets instead of 500 mB worth");
        GameTestSupport.check(accepted == 1000L * GameTestSupport.DROPLETS_PER_MB,
                "Assigned Source accepted " + accepted + " droplets instead of 1000 mB worth");
        GameTestSupport.check(drained == 1000L * GameTestSupport.DROPLETS_PER_MB,
                "Source drain moved " + drained + " droplets instead of 1000 mB worth");
        GameTestSupport.assertFluid(container.getItem(0), Fluids.WATER, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void variable_stack_size_tracks_fill_state(GameTestHelper helper) {
        StateScenarios.variable_stack_size_tracks_fill_state(helper);
    }

}
