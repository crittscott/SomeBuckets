package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

/** Shared behavioral contract for each loader's native item-fluid storage API. */
final class NativeFluidStorageScenarios {
    private NativeFluidStorageScenarios() {}

    /** Automation-only: simulated native fluid fill leaves the Big Bucket unchanged. */
    static void big_bucket_capability_simulation_does_not_mutate(
            GameTestHelper helper, ProbeFactory probes) {
        FluidProbe probe = probes.open(GameTestSupport.big8());

        int filled = probe.fill(new StoredFluid(Fluids.WATER, 3_000), false);

        GameTestSupport.check(filled == 3_000,
                "Simulated fill reported " + filled + " mB instead of 3000");
        GameTestSupport.assertEmpty(probe.stack());
        helper.succeed();
    }

    /** Automation-only: simulated and partial native drains preserve identity, components, and state. */
    static void big_bucket_capability_partial_drain_and_simulation_preserve_state(
            GameTestHelper helper, ProbeFactory probes) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2_500);
        GameTestSupport.updateCustomData(stack, tag -> tag.putString("Unrelated", "preserve-me"));
        FluidProbe probe = probes.open(stack);
        ItemStack beforeSimulation = probe.stack().copy();

        StoredFluid simulated = probe.drain(750, false);

        GameTestSupport.check(simulated.fluid() == Fluids.WATER && simulated.amount() == 750,
                "Simulated partial drain returned " + simulated);
        GameTestSupport.assertSameStack(beforeSimulation, probe.stack(), "Simulated drain mutated Big Bucket");

        StoredFluid executed = probe.drain(750, true);

        GameTestSupport.check(executed.fluid() == Fluids.WATER && executed.amount() == 750,
                "Executed partial drain returned " + executed);
        ItemStack after = probe.stack();
        GameTestSupport.assertFluid(after, Fluids.WATER, 1_750);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(after).getString("Unrelated")),
                "Partial drain removed unrelated components");
        helper.succeed();
    }

    /** Automation-only: native fill honors capacity and the final drain restores canonical emptiness. */
    static void big_bucket_capability_honors_capacity_and_clears_on_final_drain(
            GameTestHelper helper, ProbeFactory probes) {
        ItemStack stack = GameTestSupport.big8();
        GameTestSupport.updateCustomData(stack, tag -> tag.putString("Unrelated", "preserve-me"));
        FluidProbe probe = probes.open(stack);

        int filled = probe.fill(new StoredFluid(Fluids.WATER, 9_000), true);
        StoredFluid drained = probe.drain(8_000, true);

        GameTestSupport.check(filled == 8_000, "8-unit bucket accepted " + filled + " mB");
        GameTestSupport.check(drained.fluid() == Fluids.WATER && drained.amount() == 8_000,
                "Final drain returned " + drained);
        ItemStack after = probe.stack();
        GameTestSupport.assertEmpty(after);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(after).getString("Unrelated")),
                "Final drain removed unrelated components");
        helper.succeed();
    }

    /** Automation-only: native item storage refuses a different fluid without mutation. */
    static void big_bucket_capability_rejects_incompatible_fluid(
            GameTestHelper helper, ProbeFactory probes) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1_000);
        FluidProbe probe = probes.open(stack);
        ItemStack before = probe.stack().copy();

        int filled = probe.fill(new StoredFluid(Fluids.LAVA, 1_000), true);

        GameTestSupport.check(filled == 0, "Incompatible fluid fill reported " + filled + " mB");
        GameTestSupport.assertSameStack(before, probe.stack(), "Incompatible fill mutated Big Bucket");
        helper.succeed();
    }

    /** Automation-only: milk and powder-snow modes appear empty through the native fluid API. */
    static void nonfluid_modes_are_hidden_from_fluid_capability(
            GameTestHelper helper, ProbeFactory probes) {
        FluidProbe milk = probes.open(GameTestSupport.milk(GameTestSupport.big8(), 1_000));
        FluidProbe powder = probes.open(GameTestSupport.powder(GameTestSupport.big8(), 1));

        GameTestSupport.check(milk.isEmpty(), "Milk appeared as a loader fluid");
        GameTestSupport.check(powder.isEmpty(), "Powder snow appeared as a loader fluid");
        helper.succeed();
    }

    /** Automation-only: an assigned Source Bucket is an infinite native fluid source and sink. */
    static void source_capability_is_an_infinite_source_and_sink(
            GameTestHelper helper, ProbeFactory probes) {
        FluidProbe probe = probes.open(GameTestSupport.source());

        int assigned = probe.fill(new StoredFluid(Fluids.WATER, 500), true);
        int accepted = probe.fill(new StoredFluid(Fluids.WATER, 4_000), true);
        StoredFluid drained = probe.drain(1_000, true);

        GameTestSupport.check(assigned == 500, "Initial Source fill reported " + assigned + " mB");
        GameTestSupport.check(accepted == 1_000, "Assigned Source accepted " + accepted + " mB");
        GameTestSupport.check(drained.fluid() == Fluids.WATER && drained.amount() == 1_000,
                "Source drain returned " + drained);
        GameTestSupport.assertFluid(probe.stack(), Fluids.WATER, 1_000);
        helper.succeed();
    }

    @FunctionalInterface
    interface ProbeFactory {
        FluidProbe open(ItemStack stack);
    }

    interface FluidProbe {
        ItemStack stack();

        int fill(StoredFluid offered, boolean execute);

        StoredFluid drain(int amountMb, boolean execute);

        boolean isEmpty();
    }
}
