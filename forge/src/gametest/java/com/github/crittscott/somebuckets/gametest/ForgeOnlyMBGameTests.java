package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.function.Predicate;

/** Forge-specific Mob Bucket entity-insertion coverage. */
public final class ForgeOnlyMBGameTests {
    private ForgeOnlyMBGameTests() {}

    /**
     * Automation-only: vetoes aquatic entity insertion after water placement and verifies committed water
     * and the stored snapshot both remain.
     */
    public static void rejected_aquatic_spawn_preserves_committed_water_and_snapshot(GameTestHelper helper) {
        MBScenarios.rejected_aquatic_spawn_preserves_committed_water_and_snapshot(helper, (storedUuid, action) -> {
            Predicate<EntityJoinLevelEvent> listener = event ->
                    event.getLevel() == helper.getLevel()
                        && event.getEntity() instanceof Cod
                        && event.getEntity().getUUID().equals(storedUuid);
            var registration = EntityJoinLevelEvent.BUS.addListener(listener);
            try {
                action.run();
            } finally {
                EntityJoinLevelEvent.BUS.removeListener(registration);
            }
        });
    }

    /** See {@link MBScenarios#cancelled_place_check_keeps_aquatic_mob_in_bucket}. */
    public static void cancelled_place_check_keeps_aquatic_mob_in_bucket(GameTestHelper helper) {
        MBScenarios.cancelled_place_check_keeps_aquatic_mob_in_bucket(
                helper, (denied, action) -> ProtectionGameTests.denyingPlacements(helper, denied, action));
    }
}
