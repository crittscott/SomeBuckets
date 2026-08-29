package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_place_event_cancellation_is_atomic(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        int[] eventCalls = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getPos().equals(helper.absolutePos(TARGET))) {
                eventCalls[0]++;
                event.setCanceled(true);
            }
        };
        InteractionResult result;
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            result = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET, Direction.UP)));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(eventCalls[0] == 1, "Powder placement did not post one NeoForge place event");
        GameTestSupport.check(!result.consumesAction(),
                "Canceled NeoForge place event reported successful powder placement");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertSameStack(before, player.getItemInHand(InteractionHand.MAIN_HAND),
                "Canceled powder placement debited the Big Bucket");
        helper.succeed();
    }

}
