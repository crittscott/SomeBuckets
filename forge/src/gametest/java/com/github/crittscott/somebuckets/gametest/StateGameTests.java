package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class StateGameTests {
    private StateGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void fluid_bucket_sounds_follow_the_loader_fluid(GameTestHelper helper) {
        StateScenarios.fluid_bucket_sounds_follow_the_loader_fluid(helper);
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
    public static void zero_content_mutators_leave_canonical_empty_state(GameTestHelper helper) {
        StateScenarios.zero_content_mutators_leave_canonical_empty_state(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void loading_removes_malformed_content_and_keeps_unrelated_nbt(GameTestHelper helper) {
        StateScenarios.loading_removes_malformed_content_and_keeps_unrelated_nbt(helper);
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
    public static void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        StateScenarios.bucket_tooltips_preserve_translatable_components(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_buckets_stack_to_sixteen_and_filled_ones_do_not_stack(GameTestHelper helper) {
        StateScenarios.empty_buckets_stack_to_sixteen_and_filled_ones_do_not_stack(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void entity_snapshots_are_fifo_and_final_removal_is_canonical(GameTestHelper helper) {
        StateScenarios.entity_snapshots_are_fifo_and_final_removal_is_canonical(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        StateScenarios.finite_content_drain_handles_partial_and_final_milk(helper);
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

    /**
     * No in-game form: guards that a simulated fill through the loader's item fluid storage reports
     * the accepted amount without changing the Big Bucket, as a pipe or tank mod's preview expects.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_simulation_does_not_mutate(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();
        IFluidHandlerItem handler = fluidHandler(stack);

        int filled = handler.fill(new FluidStack(Fluids.WATER, 3000), IFluidHandler.FluidAction.SIMULATE);

        GameTestSupport.check(filled == 3000, "Simulated fill reported " + filled + " instead of 3000");
        GameTestSupport.assertEmpty(stack);
        helper.succeed();
    }

    /**
     * No in-game form: guards that a simulated partial drain through the loader's item fluid storage
     * changes nothing, and an executed one removes exactly the amount drained and keeps NBT the mod
     * does not own.
     */
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

    /**
     * No in-game form: guards that filling a Big Bucket through the loader's item fluid storage stops
     * at eight units, and draining all of it leaves an empty bucket with NBT the mod does not own.
     */
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

    /**
     * No in-game form: guards that the loader's item fluid storage refuses lava into a Big Bucket
     * holding water and leaves the bucket unchanged.
     */
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

    /**
     * No in-game form: guards that a Big Bucket of milk or powder snow shows no fluid through the
     * loader's item fluid storage, so pipes and tanks cannot drain either as a fluid.
     */
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

    /**
     * No in-game form: guards that an empty Source Bucket filled through the loader's item fluid
     * storage is assigned from a partial amount, then accepts and supplies one unit per call
     * without changing its assignment.
     */
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void unloadable_junk_entry_is_set_aside(GameTestHelper helper) {
        StateScenarios.unloadable_junk_entry_is_set_aside(helper);
    }

    /**
     * No in-game form: verifies every shared scenario is wrapped by a GameTest method of a class this
     * loader's test mod holds, so none silently goes unrun.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void every_shared_scenario_has_a_loader_wrapper(GameTestHelper helper) {
        GameTestSupport.assertEveryScenarioWrapped(List.of(
                AutomationGameTests.class, BBGameTests.class, BlockCapabilityGameTests.class,
                CauldronGameTests.class, FillBucketEventGameTests.class,
                ForgeOnlyBBGameTests.class, ForgeOnlyMBGameTests.class, LootGameTests.class,
                MBGameTests.class, PresentationGameTests.class, ProtectionGameTests.class,
                RecipeAndFuelGameTests.class, SBGameTests.class, StateGameTests.class,
                StorageBucketGameTests.class, TransferGameTests.class), Set.of());
        helper.succeed();
    }
}
