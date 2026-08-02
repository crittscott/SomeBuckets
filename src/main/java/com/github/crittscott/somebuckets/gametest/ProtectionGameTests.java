package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private ProtectionGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void null_automation_player_is_permitted(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();

        GameTestSupport.check(Protections.mayModify(helper.getLevel(), null,
                        helper.absolutePos(TARGET), Direction.UP, bucket),
                "Null automation player was denied");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        ServerPlayer player = adventurePlayer(helper);
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = BBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, player);

        GameTestSupport.check(!acted, "Adventure player collected fluid without CanPlaceOn permission");
        GameTestSupport.assertSameStack(before, bucket, "Denied pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void fallthrough_neighbor_requires_its_own_permission(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        allowPlacementOn(bucket, "minecraft:stone");
        ItemStack before = bucket.copy();
        ServerPlayer player = adventurePlayer(helper);
        BlockPos neighbor = TARGET.east();
        helper.setBlock(TARGET, Blocks.STONE);

        boolean acted = BBFluidLogic.getInstance().tryPlace(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.EAST), bucket, player);

        GameTestSupport.check(!acted, "Clicked-block permission improperly authorized neighbor placement");
        GameTestSupport.assertSameStack(before, bucket, "Denied neighbor placement drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.AIR);
        helper.succeed();
    }

    private static ServerPlayer adventurePlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
        return player;
    }

    private static void allowPlacementOn(ItemStack stack, String blockId) {
        ListTag blocks = new ListTag();
        blocks.add(StringTag.valueOf(blockId));
        stack.getOrCreateTag().put("CanPlaceOn", blocks);
    }
}
