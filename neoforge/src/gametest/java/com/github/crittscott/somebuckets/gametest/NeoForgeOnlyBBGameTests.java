package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
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
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private NeoForgeOnlyBBGameTests() {}

    /**
     * Cancels a real player's powder-snow placement event and verifies neither world nor bucket
     * state changes.
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
                    NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
                    try {
                        action.run();
                    } finally {
                        NeoForge.EVENT_BUS.unregister(listener);
                    }
                });
    }

    /** A successful real-player placement posts exactly one NeoForge place event. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_place_event_fires_once_on_success(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 2);
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        int[] eventCalls = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getPos().equals(helper.absolutePos(TARGET))) eventCalls[0]++;
        };

        InteractionResult result;
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            result = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET, Direction.UP)));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(result.consumesAction(), "Powder placement did not succeed");
        GameTestSupport.check(eventCalls[0] == 1, "Powder placement did not post exactly one place event");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertPowder(bucket, 1);
        helper.succeed();
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
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            acted = FluidTransactions.tryPlacePowder(helper.getLevel(),
                    GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context, true);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(acted, "Automation powder placement did not succeed");
        GameTestSupport.check(eventCalls[0] == 0, "Automation powder placement posted a player place event");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

}
