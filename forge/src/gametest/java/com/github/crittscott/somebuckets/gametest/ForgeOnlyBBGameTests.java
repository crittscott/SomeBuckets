package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.function.Consumer;

/** Forge-specific Big Bucket protection-event coverage. */
@GameTestHolder(SomeBuckets.MODID)
public final class ForgeOnlyBBGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private ForgeOnlyBBGameTests() {}

    /**
     * Cancels a real player's native powder-snow placement event and verifies neither world nor
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

    /** Dispenser-context powder placement does not manufacture a player place event. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_automation_posts_no_player_place_event(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        ProtectionContext context = ProtectionContext.dispenser(
                BucketOperations.get().automationPlayer(helper.getLevel()));
        int[] eventCalls = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getPos().equals(helper.absolutePos(TARGET))) eventCalls[0]++;
        };

        boolean acted;
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            acted = FluidTransactions.tryPlacePowder(helper.getLevel(),
                    GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context, true);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(acted, "Automation powder placement did not succeed");
        GameTestSupport.check(eventCalls[0] == 0, "Automation powder placement posted a player place event");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
}

