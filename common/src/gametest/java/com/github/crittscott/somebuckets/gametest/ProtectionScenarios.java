package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.protection.AutomationPlayers;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.function.BooleanSupplier;

final class ProtectionScenarios {
    private ProtectionScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);
    /** Automation-only: authorizes an unowned automation context, which has no actor, and expects permission. */
    static void unowned_automation_is_permitted(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockPos pos = helper.absolutePos(TARGET);

        GameTestSupport.check(Protections.mayModify(helper.getLevel(), ProtectionContext.unownedAutomation(),
                        pos, Direction.UP, bucket),
                "Unowned automation was denied a world edit");
        GameTestSupport.check(Protections.mayInteract(helper.getLevel(), ProtectionContext.unownedAutomation(), pos),
                "Unowned automation was denied an entity interaction");
        helper.succeed();
    }
    /**
     * Automation-only: withdraws the automation player's build permission, attempts pickup, and expects no
     * world or bucket mutation.
     */
    static void automation_without_build_permission_cannot_take_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER);
        ProtectionContext context = automationContext(helper);

        boolean acted = withoutBuildPermission(context.actor(), () -> GameTestSupport.tryBigTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context));

        GameTestSupport.check(!acted, "Automation without build permission took fluid");
        GameTestSupport.assertSameStack(before, bucket, "Denied fluid edit mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }
    /**
     * Automation-only: withdraws the automation player's build permission and verifies cauldron and bucket
     * state remain unchanged.
     */
    static void automation_without_build_permission_cannot_use_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        ProtectionContext context = automationContext(helper);

        boolean acted = withoutBuildPermission(context.actor(), () -> GameTestSupport.trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context));

        GameTestSupport.check(!acted, "Automation without build permission used the cauldron");
        GameTestSupport.assertSameStack(before, bucket, "Denied cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(TARGET).getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL,
                "Denied cauldron interaction changed fill level");
        helper.succeed();
    }
    /**
     * Automation-only: withdraws the automation player's build permission and verifies release adds no
     * entity, places no water, and keeps the stored snapshot, for both a land and an aquatic mob.
     */
    static void automation_without_build_permission_cannot_release(GameTestHelper helper) {
        ItemStack pigBucket = storedMob(helper, EntityType.PIG, "minecraft:pig");
        ItemStack codBucket = storedMob(helper, EntityType.COD, "minecraft:cod");
        BlockPos codTarget = TARGET.east(2);
        ProtectionContext context = automationContext(helper);

        boolean pigActed = withoutBuildPermission(context.actor(), () -> MBItem.releaseOldest(
                helper.getLevel(), helper.absolutePos(TARGET), pigBucket, context, Direction.UP));
        boolean codActed = withoutBuildPermission(context.actor(), () -> MBItem.releaseOldest(
                helper.getLevel(), helper.absolutePos(codTarget), codBucket, context, Direction.UP));

        GameTestSupport.check(!pigActed && !codActed, "Automation without build permission released a mob");
        GameTestSupport.check(BucketState.getEntityCount(pigBucket) == 1
                        && BucketState.getEntityCount(codBucket) == 1,
                "Denied release consumed a stored snapshot");
        GameTestSupport.check(GameTestSupport.entities(helper, Pig.class, TARGET, 0.75D).isEmpty(),
                "Denied release added a mob to the world");
        GameTestSupport.assertBlock(helper, codTarget, Blocks.AIR);
        helper.succeed();
    }
    /**
     * Automation-only: withdraws a player's build permission and verifies fluid placement neither destroys
     * the replaceable target nor drains the bucket.
     */
    static void player_without_build_permission_cannot_place_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.SHORT_GRASS);

        boolean acted = withoutBuildPermission(player, () -> BBFluidLogic.tryPlace(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, player,
                InteractionHand.MAIN_HAND));

        GameTestSupport.check(!acted, "Player without build permission placed fluid");
        GameTestSupport.assertSameStack(before, bucket, "Denied placement drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.SHORT_GRASS);
        helper.succeed();
    }
    /**
     * Automation-only: withdraws a player's build permission and verifies sneak ejection against a block
     * neither removes the FIFO entry nor drops an item.
     */
    static void player_without_build_permission_cannot_eject(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 3);
        BucketState.setStoredItems(bucket, List.of(food));
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.STONE);

        InteractionResult[] result = new InteractionResult[1];
        withoutBuildPermission(player, () -> {
            result[0] = ((JBItem) bucket.getItem()).useOn(new UseOnContext(
                    player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, TARGET, Direction.EAST)));
            return result[0].consumesAction();
        });

        GameTestSupport.check(!result[0].consumesAction(), "Player without build permission ejected an item");
        GameTestSupport.assertStored(helper, bucket, food);
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, TARGET.east(), 0.6D).isEmpty(),
                "Denied ejection dropped an item entity");
        helper.succeed();
    }
    /**
     * Automation-only: fires a dispenser at source water and verifies the pickup completes, the level's
     * automation player is stable and named, and it earns no statistic.
     */
    static void dispenser_acts_as_stable_automation_player(GameTestHelper helper) {
        ServerPlayer automationPlayer = AutomationPlayers.get(helper.getLevel());
        GameTestSupport.check(automationPlayer == AutomationPlayers.get(helper.getLevel()),
                "Automation player is not stable across lookups");
        GameTestSupport.check("[SomeBuckets]".equals(automationPlayer.getGameProfile().getName()),
                "Automation player has the wrong name: " + automationPlayer.getGameProfile().getName());
        BlockPos dispenserPos = TARGET.west();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, dispenserPos, Direction.EAST, GameTestSupport.big8());
        helper.setBlock(TARGET, Blocks.WATER);
        Stat<Item> itemUsed = Stats.ITEM_USED.get(dispenser.getItem(0).getItem());
        int usedBefore = automationPlayer.getStats().getValue(itemUsed);

        GameTestSupport.triggerDispenser(helper, dispenserPos);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
            GameTestSupport.check(automationPlayer.getStats().getValue(itemUsed) == usedBefore,
                    "Automation player earned the item-use statistic");
            helper.succeed();
        });
    }
    /**
     * Manual: in adventure mode without an applicable permission, try collecting a source; world and
     * bucket remain unchanged.
     */
    static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        Player player = adventurePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = BBFluidLogic.tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, player,
                InteractionHand.MAIN_HAND);

        GameTestSupport.check(!acted, "Adventure player collected fluid without CanPlaceOn permission");
        GameTestSupport.assertSameStack(before, bucket, "Denied pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    private static ProtectionContext automationContext(GameTestHelper helper) {
        return ProtectionContext.dispenser(AutomationPlayers.get(helper.getLevel()));
    }

    /**
     * Runs {@code action} with {@code actor}'s build permission withdrawn, restoring it before returning.
     * The automation player is shared by every test in the level, so the withdrawal must never outlive
     * one synchronous call.
     */
    static boolean withoutBuildPermission(Player actor, BooleanSupplier action) {
        boolean mayBuild = actor.getAbilities().mayBuild;
        actor.getAbilities().mayBuild = false;
        try {
            return action.getAsBoolean();
        } finally {
            actor.getAbilities().mayBuild = mayBuild;
        }
    }

    private static ItemStack storedMob(GameTestHelper helper, EntityType<?> type, String id) {
        ItemStack bucket = GameTestSupport.mob();
        Entity stored = type.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(stored != null, "Could not create stored " + id + " fixture");
        CompoundTag snapshot = new CompoundTag();
        stored.saveWithoutId(snapshot);
        BucketState.addEntitySnapshot(bucket, id, snapshot);
        return bucket;
    }

    private static Player adventurePlayer(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        return player;
    }
}
