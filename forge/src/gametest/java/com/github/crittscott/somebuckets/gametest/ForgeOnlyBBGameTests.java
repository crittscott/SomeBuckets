package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.function.Consumer;

/** Forge-specific Big Bucket protection-event coverage. */
@GameTestHolder(SomeBuckets.MODID)
public final class ForgeOnlyBBGameTests {
    private ForgeOnlyBBGameTests() {}

    /**
     * Automation-only: cancels the native powder-snow placement event and verifies neither world nor
     * bucket state changes.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_place_event_cancellation_is_atomic(GameTestHelper helper) {
        BBScenarios.powder_snow_place_event_cancellation_is_atomic(
                helper, (target, onEvent, action) -> {
                    Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
                        if (event.getPos().equals(target)) {
                            onEvent.run();
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

