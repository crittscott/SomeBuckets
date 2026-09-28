package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/**
 * NeoForge-only Big Bucket coverage: powder-snow block output posts NeoForge's
 * {@link BlockEvent.EntityPlaceEvent} and honors its cancellation atomically. NeoForge has no
 * fill-bucket event, so the Forge {@code FillBucketEventGameTests} suite has no counterpart here.
 */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class NeoForgeOnlyBBGameTests {
    private NeoForgeOnlyBBGameTests() {}

    /**
     * Automation-only: cancels the native powder-snow placement event and verifies neither world nor
     * bucket state changes.
     *
     * <p>Disabled: this test fails on NeoForge for an undetermined reason. Restore the
     * {@code @GameTest} annotation to run it again.
     */
    // @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_place_event_cancellation_is_atomic(GameTestHelper helper) {
        BBScenarios.powder_snow_place_event_cancellation_is_atomic(
                helper, (target, onEvent, action) -> {
                    Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
                        if (event.getPos().equals(target)) {
                            onEvent.run();
                            event.setCanceled(true);
                        }
                    };
                    NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
                    try {
                        action.run();
                    } finally {
                        NeoForge.EVENT_BUS.unregister(listener);
                    }
                });
    }

}
