package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/** NeoForge bucket-state GameTests, including shared scenarios and capability coverage. */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class StateGameTests {
    private StateGameTests() {}

    /** See {@link StateScenarios#vanilla_fluids_report_their_bucket_sounds}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void vanilla_fluids_report_their_bucket_sounds(GameTestHelper helper) {
        StateScenarios.vanilla_fluids_report_their_bucket_sounds(helper);
    }

    /** See {@link StateScenarios#pristine_bucket_reads_do_not_attach_components}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void pristine_bucket_reads_do_not_attach_components(GameTestHelper helper) {
        StateScenarios.pristine_bucket_reads_do_not_attach_components(helper);
    }

    /** See {@link StateScenarios#clear_removes_all_content_and_preserves_unrelated_components}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void clear_removes_all_content_and_preserves_unrelated_components(GameTestHelper helper) {
        StateScenarios.clear_removes_all_content_and_preserves_unrelated_components(helper);
    }

    /** See {@link StateScenarios#zero_content_mutators_leave_canonical_empty_state}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void zero_content_mutators_leave_canonical_empty_state(GameTestHelper helper) {
        StateScenarios.zero_content_mutators_leave_canonical_empty_state(helper);
    }

    /** See {@link StateScenarios#stored_items_round_trip_with_order_counts_and_tags}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        StateScenarios.stored_items_round_trip_with_order_counts_and_tags(helper);
    }

    /** See {@link StateScenarios#partial_bucket_refuses_different_fluid_variant}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_bucket_refuses_different_fluid_variant(GameTestHelper helper) {
        StateScenarios.partial_bucket_refuses_different_fluid_variant(helper);
    }

    /** See {@link StateScenarios#stored_item_reads_are_detached_and_empty_writes_clean_components}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void stored_item_reads_are_detached_and_empty_writes_clean_components(GameTestHelper helper) {
        StateScenarios.stored_item_reads_are_detached_and_empty_writes_clean_components(helper);
    }

    /** See {@link StateScenarios#negative_content_setters_fail_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void negative_content_setters_fail_without_mutation(GameTestHelper helper) {
        StateScenarios.negative_content_setters_fail_without_mutation(helper);
    }

    /** See {@link StateScenarios#load_time_admission_discards_invalid_state}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void load_time_admission_discards_invalid_state(GameTestHelper helper) {
        StateScenarios.load_time_admission_discards_invalid_state(helper);
    }

    /** See {@link StateScenarios#bucket_tooltips_preserve_translatable_components}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        StateScenarios.bucket_tooltips_preserve_translatable_components(helper);
    }

    /** See {@link StateScenarios#entity_snapshots_are_fifo_and_final_removal_is_canonical}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void entity_snapshots_are_fifo_and_final_removal_is_canonical(GameTestHelper helper) {
        StateScenarios.entity_snapshots_are_fifo_and_final_removal_is_canonical(helper);
    }

    /** See {@link StateScenarios#entity_snapshot_network_sync_preserves_payloads}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void entity_snapshot_network_sync_preserves_payloads(GameTestHelper helper) {
        StateScenarios.entity_snapshot_network_sync_preserves_payloads(helper);
    }

    /** See {@link StateScenarios#fluid_content_network_sync_preserves_variant_and_rejects_empty}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void fluid_content_network_sync_preserves_variant_and_rejects_empty(GameTestHelper helper) {
        StateScenarios.fluid_content_network_sync_preserves_variant_and_rejects_empty(helper);
    }

    /** See {@link StateScenarios#junk_contents_network_sync_bounds_set_aside_entries}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_contents_network_sync_bounds_set_aside_entries(GameTestHelper helper) {
        StateScenarios.junk_contents_network_sync_bounds_set_aside_entries(helper);
    }

    /** See {@link StateScenarios#finite_crafting_remainders_consume_one_unit}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void finite_crafting_remainders_consume_one_unit(GameTestHelper helper) {
        StateScenarios.finite_crafting_remainders_consume_one_unit(helper);
    }

    /** See {@link StateScenarios#final_finite_crafting_remainder_is_empty}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        StateScenarios.final_finite_crafting_remainder_is_empty(helper);
    }

    /** See {@link StateScenarios#empty_finite_and_source_buckets_have_no_crafting_remainder}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_finite_and_source_buckets_have_no_crafting_remainder(GameTestHelper helper) {
        StateScenarios.empty_finite_and_source_buckets_have_no_crafting_remainder(helper);
    }

    /** See {@link StateScenarios#assigned_source_crafting_remainder_is_unchanged}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void assigned_source_crafting_remainder_is_unchanged(GameTestHelper helper) {
        StateScenarios.assigned_source_crafting_remainder_is_unchanged(helper);
    }

    /** See {@link StateScenarios#released_bucket_state_loads_in_current_form}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void released_bucket_state_loads_in_current_form(GameTestHelper helper) {
        StateScenarios.released_bucket_state_loads_in_current_form(helper);
    }

    /** See {@link StateScenarios#unreadable_storage_entries_are_set_aside_and_restored}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void unreadable_storage_entries_are_set_aside_and_restored(GameTestHelper helper) {
        StateScenarios.unreadable_storage_entries_are_set_aside_and_restored(helper);
    }

    /**
     * Automation-only: simulates fluid-capability fill and drain calls and verifies the Big Bucket stack
     * is unchanged.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_simulation_does_not_mutate(GameTestHelper helper) {
        NativeFluidStorageScenarios.big_bucket_capability_simulation_does_not_mutate(
                helper, StateGameTests::fluidProbe);
    }

    /**
     * Automation-only: exercises simulated and partial capability drains and verifies amount, identity,
     * and components are preserved.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_partial_drain_and_simulation_preserve_state(GameTestHelper helper) {
        NativeFluidStorageScenarios.big_bucket_capability_partial_drain_and_simulation_preserve_state(
                helper, StateGameTests::fluidProbe);
    }

    /**
     * Automation-only: fills through the item capability to capacity, rejects excess, and verifies the
     * final drain returns canonical empty state.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_honors_capacity_and_clears_on_final_drain(GameTestHelper helper) {
        NativeFluidStorageScenarios.big_bucket_capability_honors_capacity_and_clears_on_final_drain(
                helper, StateGameTests::fluidProbe);
    }

    /** See {@link StateScenarios#finite_content_drain_handles_partial_and_final_milk}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        StateScenarios.finite_content_drain_handles_partial_and_final_milk(helper);
    }

    /**
     * Automation-only: offers a different fluid to a nonempty Big Bucket capability and verifies no
     * content is accepted or changed.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_rejects_incompatible_fluid(GameTestHelper helper) {
        NativeFluidStorageScenarios.big_bucket_capability_rejects_incompatible_fluid(
                helper, StateGameTests::fluidProbe);
    }

    /**
     * Automation-only: queries milk and powder-snow Big Buckets through the fluid capability and
     * requires them to appear empty.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void nonfluid_modes_are_hidden_from_fluid_capability(GameTestHelper helper) {
        NativeFluidStorageScenarios.nonfluid_modes_are_hidden_from_fluid_capability(
                helper, StateGameTests::fluidProbe);
    }

    /**
     * Automation-only: fills and drains an assigned Source Bucket capability repeatedly and verifies its
     * identity never changes or depletes.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_capability_is_an_infinite_source_and_sink(GameTestHelper helper) {
        NativeFluidStorageScenarios.source_capability_is_an_infinite_source_and_sink(
                helper, StateGameTests::fluidProbe);
    }

    /** See {@link StateScenarios#variable_stack_size_tracks_fill_state}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void variable_stack_size_tracks_fill_state(GameTestHelper helper) {
        StateScenarios.variable_stack_size_tracks_fill_state(helper);
    }

    private static IFluidHandlerItem fluidHandler(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) throw new GameTestAssertException("Bucket exposed no fluid capability");
        return handler;
    }

    private static NativeFluidStorageScenarios.FluidProbe fluidProbe(ItemStack stack) {
        return new NativeFluidStorageScenarios.FluidProbe() {
            @Override
            public ItemStack stack() {
                return stack;
            }

            @Override
            public int fill(StoredFluid offered, boolean execute) {
                return fluidHandler(stack).fill(NeoForgeFluidStacks.of(offered), action(execute));
            }

            @Override
            public StoredFluid drain(int amountMb, boolean execute) {
                return NeoForgeFluidStacks.stored(fluidHandler(stack).drain(amountMb, action(execute)));
            }

            @Override
            public boolean isEmpty() {
                return fluidHandler(stack).getFluidInTank(0).isEmpty();
            }
        };
    }

    private static IFluidHandler.FluidAction action(boolean execute) {
        return execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
    }

    /**
     * Automation-only: verifies every shared scenario is wrapped by a GameTest method of this loader,
     * so none silently goes unrun.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void every_shared_scenario_has_a_loader_wrapper(GameTestHelper helper) {
        GameTestSupport.assertEveryScenarioWrapped(List.of(
                AutomationGameTests.class, BBGameTests.class, BlockCapabilityGameTests.class,
                CauldronGameTests.class, LootGameTests.class, MBGameTests.class,
                NeoForgeFluidStacksGameTests.class, NeoForgeFuelGameTests.class,
                NeoForgeOnlyBBGameTests.class, NeoForgeOnlyMBGameTests.class, PresentationGameTests.class,
                ProtectionGameTests.class, RecipeAndFuelGameTests.class, SBGameTests.class,
                StateGameTests.class, StorageBucketGameTests.class, TransferGameTests.class), Set.of());
        helper.succeed();
    }
}
