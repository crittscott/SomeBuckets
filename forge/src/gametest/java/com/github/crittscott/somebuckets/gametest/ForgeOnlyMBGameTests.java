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
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class ForgeOnlyMBGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(3, 2, 4);
    private static final BlockPos CLICKED = new BlockPos(5, 2, 4);
    private static final BlockPos SPAWN = CLICKED.east();

    private ForgeOnlyMBGameTests() {}

    /**
     * A cod release whose entity join another mod cancels keeps the cod in the bucket, leaves the
     * water it placed, and awards no use. No in-game form without such a mod: it guards the release
     * order against a Forge {@code EntityJoinLevelEvent} listener that refuses the mob.
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
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                    player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
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

    /**
     * Taking the mob from a held vanilla mob bucket runs its spawn initialization as a bucket spawn,
     * so Forge's finalize-spawn event fires once. In game: take a Bucket of Tropical Fish from the
     * creative menu, hold a Mob Bucket in the other hand and use it, then release the fish: it has a
     * random pattern, as when emptying the vanilla bucket.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void held_mob_bucket_intake_finalizes_spawn_as_bucket(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TROPICAL_FISH_BUCKET));

        int[] bucketSpawns = {0};
        Consumer<MobSpawnEvent.FinalizeSpawn> listener = event -> {
            if (event.getEntity() instanceof TropicalFish && event.getSpawnType() == MobSpawnType.BUCKET) {
                bucketSpawns[0]++;
            }
        };
        InteractionResultHolder<ItemStack> result;
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            result = ((MBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(result.getResult().consumesAction(), "Bucket of Tropical Fish did not empty");
        GameTestSupport.check(BucketState.getEntityCount(player.getMainHandItem()) == 1,
                "Mob Bucket did not store the tropical fish");
        GameTestSupport.check(bucketSpawns[0] == 1,
                "Intake fired " + bucketSpawns[0] + " bucket finalize-spawn events instead of one");
        helper.succeed();
    }
}
