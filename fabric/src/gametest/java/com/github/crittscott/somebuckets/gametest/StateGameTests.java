package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Set;

/** Fabric bucket-state GameTests, including shared scenarios and Transfer API coverage. */
public final class StateGameTests {
    /** See {@link StateScenarios#vanilla_fluids_report_their_bucket_sounds}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void vanilla_fluids_report_their_bucket_sounds(GameTestHelper helper) {
        StateScenarios.vanilla_fluids_report_their_bucket_sounds(helper);
    }

    /** See {@link StateScenarios#pristine_bucket_reads_do_not_attach_components}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void pristine_bucket_reads_do_not_attach_components(GameTestHelper helper) {
        StateScenarios.pristine_bucket_reads_do_not_attach_components(helper);
    }

    /** See {@link StateScenarios#clear_removes_all_content_and_preserves_unrelated_components}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void clear_removes_all_content_and_preserves_unrelated_components(GameTestHelper helper) {
        StateScenarios.clear_removes_all_content_and_preserves_unrelated_components(helper);
    }

    /** See {@link StateScenarios#zero_content_mutators_leave_canonical_empty_state}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void zero_content_mutators_leave_canonical_empty_state(GameTestHelper helper) {
        StateScenarios.zero_content_mutators_leave_canonical_empty_state(helper);
    }

    /** See {@link StateScenarios#stored_items_round_trip_with_order_counts_and_tags}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        StateScenarios.stored_items_round_trip_with_order_counts_and_tags(helper);
    }

    /** See {@link StateScenarios#partial_bucket_refuses_different_fluid_variant}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void partial_bucket_refuses_different_fluid_variant(GameTestHelper helper) {
        StateScenarios.partial_bucket_refuses_different_fluid_variant(helper);
    }

    /** See {@link StateScenarios#stored_item_reads_are_detached_and_empty_writes_clean_components}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void stored_item_reads_are_detached_and_empty_writes_clean_components(GameTestHelper helper) {
        StateScenarios.stored_item_reads_are_detached_and_empty_writes_clean_components(helper);
    }

    /** See {@link StateScenarios#negative_content_setters_fail_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void negative_content_setters_fail_without_mutation(GameTestHelper helper) {
        StateScenarios.negative_content_setters_fail_without_mutation(helper);
    }

    /** See {@link StateScenarios#load_time_admission_discards_invalid_state}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void load_time_admission_discards_invalid_state(GameTestHelper helper) {
        StateScenarios.load_time_admission_discards_invalid_state(helper);
    }

    /** See {@link StateScenarios#bucket_tooltips_preserve_translatable_components}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        StateScenarios.bucket_tooltips_preserve_translatable_components(helper);
    }

    /** See {@link StateScenarios#entity_snapshots_are_fifo_and_final_removal_is_canonical}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void entity_snapshots_are_fifo_and_final_removal_is_canonical(GameTestHelper helper) {
        StateScenarios.entity_snapshots_are_fifo_and_final_removal_is_canonical(helper);
    }

    /** See {@link StateScenarios#entity_snapshot_network_sync_preserves_payloads}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void entity_snapshot_network_sync_preserves_payloads(GameTestHelper helper) {
        StateScenarios.entity_snapshot_network_sync_preserves_payloads(helper);
    }

    /** See {@link StateScenarios#fluid_content_network_sync_preserves_variant_and_rejects_empty}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void fluid_content_network_sync_preserves_variant_and_rejects_empty(GameTestHelper helper) {
        StateScenarios.fluid_content_network_sync_preserves_variant_and_rejects_empty(helper);
    }

    /** See {@link StateScenarios#junk_contents_network_sync_bounds_set_aside_entries}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void junk_contents_network_sync_bounds_set_aside_entries(GameTestHelper helper) {
        StateScenarios.junk_contents_network_sync_bounds_set_aside_entries(helper);
    }

    /** See {@link StateScenarios#finite_crafting_remainders_consume_one_unit}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void finite_crafting_remainders_consume_one_unit(GameTestHelper helper) {
        StateScenarios.finite_crafting_remainders_consume_one_unit(helper);
    }

    /** See {@link StateScenarios#final_finite_crafting_remainder_is_empty}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        StateScenarios.final_finite_crafting_remainder_is_empty(helper);
    }

    /** See {@link StateScenarios#empty_finite_and_source_buckets_have_no_crafting_remainder}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_finite_and_source_buckets_have_no_crafting_remainder(GameTestHelper helper) {
        StateScenarios.empty_finite_and_source_buckets_have_no_crafting_remainder(helper);
    }

    /** See {@link StateScenarios#assigned_source_crafting_remainder_is_unchanged}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_crafting_remainder_is_unchanged(GameTestHelper helper) {
        StateScenarios.assigned_source_crafting_remainder_is_unchanged(helper);
    }

    /** See {@link StateScenarios#released_bucket_state_loads_in_current_form}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void released_bucket_state_loads_in_current_form(GameTestHelper helper) {
        StateScenarios.released_bucket_state_loads_in_current_form(helper);
    }

    /** See {@link StateScenarios#unreadable_storage_entries_are_set_aside_and_restored}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void unreadable_storage_entries_are_set_aside_and_restored(GameTestHelper helper) {
        StateScenarios.unreadable_storage_entries_are_set_aside_and_restored(helper);
    }

    /**
     * Automation-only: simulates fluid-capability fill and drain calls and verifies the Big Bucket stack
     * is unchanged.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_simulation_does_not_mutate(GameTestHelper helper) {
        NativeFluidStorageScenarios.big_bucket_capability_simulation_does_not_mutate(
                helper, StateGameTests::fluidProbe);
    }

    /**
     * Automation-only: exercises simulated and partial capability drains and verifies amount, identity,
     * and components are preserved.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_partial_drain_and_simulation_preserve_state(GameTestHelper helper) {
        NativeFluidStorageScenarios.big_bucket_capability_partial_drain_and_simulation_preserve_state(
                helper, StateGameTests::fluidProbe);
    }

    /**
     * Automation-only: fills through the item capability to capacity, rejects excess, and verifies the
     * final drain returns canonical empty state.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_honors_capacity_and_clears_on_final_drain(GameTestHelper helper) {
        NativeFluidStorageScenarios.big_bucket_capability_honors_capacity_and_clears_on_final_drain(
                helper, StateGameTests::fluidProbe);
    }

    /** See {@link StateScenarios#finite_content_drain_handles_partial_and_final_milk}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        StateScenarios.finite_content_drain_handles_partial_and_final_milk(helper);
    }

    /**
     * Automation-only: offers a different fluid to a nonempty Big Bucket capability and verifies no
     * content is accepted or changed.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_rejects_incompatible_fluid(GameTestHelper helper) {
        NativeFluidStorageScenarios.big_bucket_capability_rejects_incompatible_fluid(
                helper, StateGameTests::fluidProbe);
    }

    /**
     * Automation-only: converts a component-carrying fluid through {@link FabricFluidVariants}, inserts
     * it into a Big Bucket through Transfer API storage, and verifies the variant is kept and a plain
     * variant of the same fluid is neither accepted nor extracted.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void fluid_variant_components_survive_storage_round_trip(GameTestHelper helper) {
        CompoundTag marker = new CompoundTag();
        marker.putString("sb_variant_probe", "kept");
        DataComponentPatch components = DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_DATA, CustomData.of(marker))
                .build();
        FluidVariant variant = FabricFluidVariants.toVariant(new StoredFluid(Fluids.WATER, 1000, components));
        GameTestSupport.check(variant.getFluid() == Fluids.WATER && variant.getComponents().equals(components),
                "FabricFluidVariants did not carry the stored components");

        SimpleContainer container = GameTestSupport.containerOf(GameTestSupport.big8());
        long filled = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                variant, 2000L * GameTestSupport.DROPLETS_PER_MB, true);
        GameTestSupport.check(filled == 2000L * GameTestSupport.DROPLETS_PER_MB,
                "Variant fluid insert moved " + filled + " droplets");
        StoredFluid stored = BucketState.getStoredFluid(container.getItem(0));
        GameTestSupport.check(stored.amount() == 2000 && stored.components().equals(components),
                "Big Bucket did not keep the inserted fluid variant: " + stored);

        FluidVariant plain = FluidVariant.of(Fluids.WATER);
        long plainFilled = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                plain, 1000L * GameTestSupport.DROPLETS_PER_MB, true);
        long plainDrained = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                plain, 1000L * GameTestSupport.DROPLETS_PER_MB, true);
        GameTestSupport.check(plainFilled == 0 && plainDrained == 0,
                "Plain water mixed with a component-carrying variant");
        GameTestSupport.check(GameTestSupport.fluidStorage(container).iterator().next().getResource().equals(variant),
                "Storage view did not report the stored variant");
        helper.succeed();
    }

    /**
     * Automation-only: moves bottle-sized amounts through Big Bucket storage and verifies each transfer
     * rounds down to whole millibuckets and reports exactly the droplets it moved.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void bottle_transfers_round_down_to_whole_millibuckets(GameTestHelper helper) {
        long bottleMb = FluidConstants.BOTTLE / GameTestSupport.DROPLETS_PER_MB;
        long bottleDroplets = bottleMb * GameTestSupport.DROPLETS_PER_MB;

        SimpleContainer empty = GameTestSupport.containerOf(GameTestSupport.big8());
        long inserted = GameTestSupport.insert(GameTestSupport.fluidStorage(empty),
                FluidVariant.of(Fluids.WATER), FluidConstants.BOTTLE, true);
        GameTestSupport.check(inserted == bottleDroplets,
                "Bottle insert reported " + inserted + " droplets instead of " + bottleDroplets);
        GameTestSupport.assertFluid(empty.getItem(0), Fluids.WATER, (int) bottleMb);

        SimpleContainer full = GameTestSupport.containerOf(
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));
        long extracted = GameTestSupport.extract(GameTestSupport.fluidStorage(full),
                FluidVariant.of(Fluids.WATER), FluidConstants.BOTTLE, true);
        GameTestSupport.check(extracted == bottleDroplets,
                "Bottle extract reported " + extracted + " droplets instead of " + bottleDroplets);
        GameTestSupport.assertFluid(full.getItem(0), Fluids.WATER, 1000 - (int) bottleMb);
        helper.succeed();
    }

    /**
     * Automation-only: queries a powder-snow Big Bucket through the fluid capability and requires it
     * to appear empty.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void powder_snow_is_hidden_from_fluid_capability(GameTestHelper helper) {
        NativeFluidStorageScenarios.powder_snow_is_hidden_from_fluid_capability(
                helper, StateGameTests::fluidProbe);
    }

    /**
     * Automation-only: exchanges milk with Big and Source Buckets through the fluid capability.
     * Fabric has no shared milk fluid, so a milk Big Bucket in a tank or machine slot appears empty to the machine.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void milk_is_exchanged_as_the_loader_milk_fluid(GameTestHelper helper) {
        NativeFluidStorageScenarios.milk_is_exchanged_as_the_loader_milk_fluid(
                helper, StateGameTests::fluidProbe);
    }

    /**
     * Automation-only: fills and drains an assigned Source Bucket capability repeatedly and verifies its
     * identity never changes or depletes.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_capability_is_an_infinite_source_and_sink(GameTestHelper helper) {
        NativeFluidStorageScenarios.source_capability_is_an_infinite_source_and_sink(
                helper, StateGameTests::fluidProbe);
    }

    private static NativeFluidStorageScenarios.FluidProbe fluidProbe(ItemStack initial) {
        SimpleContainer container = GameTestSupport.containerOf(initial);
        return new NativeFluidStorageScenarios.FluidProbe() {
            @Override
            public ItemStack stack() {
                return container.getItem(0);
            }

            @Override
            public int fill(StoredFluid offered, boolean execute) {
                long moved = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                        FabricFluidVariants.toVariant(offered),
                        (long) offered.amount() * GameTestSupport.DROPLETS_PER_MB, execute);
                return (int) (moved / GameTestSupport.DROPLETS_PER_MB);
            }

            @Override
            public StoredFluid drain(int amountMb, boolean execute) {
                StoredFluid available = BucketState.getStoredFluid(stack());
                if (available.isEmpty()) return StoredFluid.EMPTY;
                long moved = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                        FabricFluidVariants.toVariant(available),
                        (long) amountMb * GameTestSupport.DROPLETS_PER_MB, execute);
                return available.withAmount((int) (moved / GameTestSupport.DROPLETS_PER_MB));
            }

            @Override
            public boolean isEmpty() {
                return GameTestSupport.isEmpty(GameTestSupport.fluidStorage(container));
            }
        };
    }

    /** See {@link StateScenarios#variable_stack_size_tracks_fill_state}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void variable_stack_size_tracks_fill_state(GameTestHelper helper) {
        StateScenarios.variable_stack_size_tracks_fill_state(helper);
    }

    /**
     * Automation-only: verifies every shared scenario is wrapped by a GameTest method of this loader,
     * so none silently goes unrun.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void every_shared_scenario_has_a_loader_wrapper(GameTestHelper helper) {
        GameTestSupport.assertEveryScenarioWrapped(List.of(
                AutomationGameTests.class, BBGameTests.class, BlockCapabilityGameTests.class,
                CauldronGameTests.class, LootGameTests.class, MBGameTests.class, PresentationGameTests.class,
                ProtectionGameTests.class, RecipeAndFuelGameTests.class, SBGameTests.class,
                SBPolicyNetworkGameTests.class, StateGameTests.class, StorageBucketGameTests.class,
                TransferGameTests.class), Set.of(
                GameTestSupport.scenarioId(BBScenarios.class,
                        "powder_snow_place_event_cancellation_is_atomic"),
                GameTestSupport.scenarioId(MBScenarios.class,
                        "rejected_aquatic_spawn_preserves_committed_water_and_snapshot"),
                GameTestSupport.scenarioId(ProtectionScenarios.class,
                        "cancelled_place_check_denies_player_fluid_place")));
        helper.succeed();
    }
}
