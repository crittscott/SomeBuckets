package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Cod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.function.Consumer;

/** Forge-specific Mob Bucket entity-insertion coverage. */
@GameTestHolder(SomeBuckets.MODID)
public final class ForgeOnlyMBGameTests {
    private ForgeOnlyMBGameTests() {}

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
            MinecraftForge.EVENT_BUS.addListener(listener);
            try {
                action.run();
            } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
            }
        });
    }
}
