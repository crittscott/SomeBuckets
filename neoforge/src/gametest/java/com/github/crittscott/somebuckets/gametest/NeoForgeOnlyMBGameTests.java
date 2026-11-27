package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * NeoForge-only Mob Bucket coverage: an aquatic spawn vetoed through NeoForge's
 * {@link EntityJoinLevelEvent} leaves the committed water and the stored snapshot intact.
 */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class NeoForgeOnlyMBGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(3, 2, 4);
    private static final BlockPos CLICKED = new BlockPos(5, 2, 4);
    private static final BlockPos SPAWN = CLICKED.east();

    private NeoForgeOnlyMBGameTests() {}

    /**
     * No in-game form without a mod that refuses the mob: a sneaking player's cod release whose
     * entity join a NeoForge {@code EntityJoinLevelEvent} listener cancels keeps the cod in the
     * bucket, leaves the water it placed, and awards no Mob Bucket use.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void rejected_aquatic_spawn_preserves_committed_water_and_snapshot(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        UUID storedUuid = BucketState.copyFirstEntitySnapshot(bucket).getUUID("UUID");
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getLevel() == helper.getLevel()
                    && event.getEntity() instanceof Cod
                    && event.getEntity().getUUID().equals(storedUuid)) {
                event.setCanceled(true);
            }
        };
        InteractionResult result;
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, listener);
        try {
            result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                    player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(!result.consumesAction(), "Rejected cod insertion reported success");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Rejected cod insertion consumed the stored snapshot");
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, 0.75D).isEmpty(),
                "Entity-join cancellation still added the cod");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem())) == statBefore,
                "Rejected entity insertion awarded a Mob Bucket use");
        helper.succeed();
    }
}
