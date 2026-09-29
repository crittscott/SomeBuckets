package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Cod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/**
 * NeoForge-only Mob Bucket coverage: an aquatic spawn vetoed through NeoForge's
 * {@link EntityJoinLevelEvent} leaves the committed water and the stored snapshot intact.
 */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class NeoForgeOnlyMBGameTests {
    private NeoForgeOnlyMBGameTests() {}

    /**
     * Automation-only: vetoes aquatic entity insertion after water placement and verifies committed water
     * and the stored snapshot both remain.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void rejected_aquatic_spawn_preserves_committed_water_and_snapshot(GameTestHelper helper) {
        MBScenarios.rejected_aquatic_spawn_preserves_committed_water_and_snapshot(helper, (storedUuid, action) -> {
            Consumer<EntityJoinLevelEvent> listener = event -> {
                if (event.getLevel() == helper.getLevel()
                        && event.getEntity() instanceof Cod
                        && event.getEntity().getUUID().equals(storedUuid)) {
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, listener);
            try {
                action.run();
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
        });
    }

    /** See {@link MBScenarios#cancelled_place_check_keeps_aquatic_mob_in_bucket}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void cancelled_place_check_keeps_aquatic_mob_in_bucket(GameTestHelper helper) {
        MBScenarios.cancelled_place_check_keeps_aquatic_mob_in_bucket(
                helper, (denied, action) -> ProtectionGameTests.denyingPlacements(helper, denied, action));
    }
}
