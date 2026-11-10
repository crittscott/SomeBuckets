package com.github.crittscott.somebuckets.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric GameTest wrappers for {@link LootScenarios}. */
public final class LootGameTests {
    /** See {@link LootScenarios#loot_manifest_has_intended_targets_and_overlaps}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.SHORT_TIMEOUT)
    public void loot_manifest_has_intended_targets_and_overlaps(GameTestHelper helper) {
        LootScenarios.loot_manifest_has_intended_targets_and_overlaps(helper);
    }

    /** See {@link LootScenarios#loot_injection_reaches_target_tables}. */
    @GameTest(structure = GameTestSupport.TEMPLATE, maxTicks = GameTestSupport.WORLD_TIMEOUT)
    public void loot_injection_reaches_target_tables(GameTestHelper helper) {
        LootScenarios.loot_injection_reaches_target_tables(helper);
    }
}
