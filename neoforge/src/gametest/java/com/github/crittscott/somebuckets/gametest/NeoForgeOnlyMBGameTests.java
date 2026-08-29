package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void rejected_aquatic_spawn_preserves_committed_water_and_snapshot(GameTestHelper helper) {
        ItemStack bucket = storedCod(helper.getLevel());
        UUID storedUuid = NBTUtil.copyFirstEntitySnapshot(bucket).getUUID("UUID");
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
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 1,
                "Rejected cod insertion consumed the stored snapshot");
        GameTestSupport.check(entitiesAt(helper, Cod.class, SPAWN).isEmpty(),
                "Entity-join cancellation still added the cod");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem())) == statBefore,
                "Rejected entity insertion awarded a Mob Bucket use");
        helper.succeed();
    }

    private static ItemStack storedCod(Level level) {
        Cod cod = EntityType.COD.create(level);
        GameTestSupport.check(cod != null, "Could not create stored cod fixture");
        CompoundTag snapshot = new CompoundTag();
        cod.saveWithoutId(snapshot);
        ItemStack bucket = GameTestSupport.mob();
        NBTUtil.addEntitySnapshot(bucket, "minecraft:cod", snapshot);
        return bucket;
    }

    private static <T extends Entity> List<T> entitiesAt(
            GameTestHelper helper, Class<T> type, BlockPos relative) {
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(relative));
        return helper.getLevel().getEntitiesOfClass(type, new AABB(center, center).inflate(0.75D),
                Entity::isAlive);
    }
}
