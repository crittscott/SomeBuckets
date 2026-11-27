package com.github.crittscott.somebuckets.gametest;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Set;

public final class StateGameTests {
    /* This test mod's id in its fabric.mod.json; filters out other mods' GameTest entrypoints. */
    private static final String TEST_MOD_ID = "somebuckets-gametest";

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void fluid_bucket_sounds_follow_the_loader_fluid(GameTestHelper helper) {
        StateScenarios.fluid_bucket_sounds_follow_the_loader_fluid(helper);
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
    public void loading_removes_malformed_content_and_keeps_unrelated_nbt(GameTestHelper helper) {
        StateScenarios.loading_removes_malformed_content_and_keeps_unrelated_nbt(helper);
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
    public void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        StateScenarios.bucket_tooltips_preserve_translatable_components(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_buckets_stack_to_sixteen_and_filled_ones_do_not_stack(GameTestHelper helper) {
        StateScenarios.empty_buckets_stack_to_sixteen_and_filled_ones_do_not_stack(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void entity_snapshots_are_fifo_and_final_removal_is_canonical(GameTestHelper helper) {
        StateScenarios.entity_snapshots_are_fifo_and_final_removal_is_canonical(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        StateScenarios.finite_content_drain_handles_partial_and_final_milk(helper);
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

    /**
     * No in-game form: guards that a simulated fill through the loader's item fluid storage reports
     * the accepted amount without changing the Big Bucket, as a pipe or tank mod's preview expects.
     */
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

    /**
     * No in-game form: guards that a simulated partial drain through the loader's item fluid storage
     * changes nothing, and an executed one removes exactly the amount drained and keeps NBT the mod
     * does not own.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_partial_drain_and_simulation_preserve_state(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2500);
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");
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
        GameTestSupport.check("preserve-me".equals(after.getOrCreateTag().getString("Unrelated")),
                "Partial drain removed unrelated NBT");
        helper.succeed();
    }

    /**
     * No in-game form: guards that filling a Big Bucket through the loader's item fluid storage stops
     * at eight units, and draining all of it leaves an empty bucket with NBT the mod does not own.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_honors_capacity_and_clears_on_final_drain(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");
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
        GameTestSupport.check("preserve-me".equals(after.getOrCreateTag().getString("Unrelated")),
                "Final drain removed unrelated NBT");
        helper.succeed();
    }

    /**
     * No in-game form: guards that the loader's item fluid storage refuses lava into a Big Bucket
     * holding water and leaves the bucket unchanged.
     */
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

    /**
     * No in-game form: guards that a Big Bucket of milk or powder snow shows no fluid through the
     * loader's item fluid storage, so pipes and tanks cannot drain either as a fluid.
     */
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

    /**
     * No in-game form: guards that an empty Source Bucket filled through the loader's item fluid
     * storage is assigned from a partial amount, then accepts and supplies one unit per call
     * without changing its assignment.
     */
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
    public void unloadable_junk_entry_is_set_aside(GameTestHelper helper) {
        StateScenarios.unloadable_junk_entry_is_set_aside(helper);
    }

    /**
     * No in-game form: verifies every shared scenario is wrapped by a GameTest method of a class this
     * test mod registers as a {@code fabric-gametest} entrypoint, the classes Fabric API actually
     * runs, so none silently goes unrun. Fabric API has no block-place or item-pickup event, so the
     * scenarios that refuse those cannot run here.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void every_shared_scenario_has_a_loader_wrapper(GameTestHelper helper) {
        List<Class<?>> testClasses = FabricLoader.getInstance()
                .getEntrypointContainers("fabric-gametest", Object.class).stream()
                .filter(container -> container.getProvider().getMetadata().getId().equals(TEST_MOD_ID))
                .<Class<?>>map(container -> container.getEntrypoint().getClass())
                .toList();
        GameTestSupport.assertEveryScenarioWrapped(testClasses, Set.of(
                GameTestSupport.scenarioId(BBScenarios.class, "powder_snow_protection_denial_precedes_native_placement"),
                GameTestSupport.scenarioId(ProtectionScenarios.class, "refused_pickup_denies_dispenser_absorption"),
                GameTestSupport.scenarioId(ProtectionScenarios.class, "refused_place_denies_aquatic_release_water"),
                GameTestSupport.scenarioId(ProtectionScenarios.class, "refused_place_checks_fallthrough_destination"),
                GameTestSupport.scenarioId(ProtectionScenarios.class, "refused_pickup_denies_player_junk_absorption"),
                GameTestSupport.scenarioId(ProtectionScenarios.class, "refused_pickup_denies_player_trash_absorption")));
        helper.succeed();
    }
}
