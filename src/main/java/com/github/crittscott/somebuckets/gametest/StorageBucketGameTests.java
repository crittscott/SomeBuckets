package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class StorageBucketGameTests {
    private static final BlockPos PLAYER_POS = new BlockPos(4, 2, 4);

    private StorageBucketGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_absorbs_and_merges_nearby_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 20)));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket, new ItemStack(Items.APPLE, 30));
        GameTestSupport.check(!entity.isAlive(), "Fully absorbed item entity remained alive");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_absorbs_multiple_entities_in_one_activation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 50)));
        Player player = playerWith(helper, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 20), PLAYER_POS);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket,
                new ItemStack(Items.APPLE, 64), new ItemStack(Items.APPLE, 16));
        GameTestSupport.check(!first.isAlive() && !second.isAlive(),
                "Junk Bucket did not absorb both item entities");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_skips_pickup_delay(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);
        entity.setDefaultPickUpDelay();

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket);
        GameTestSupport.check(entity.isAlive(), "Pickup-delay item was absorbed");
        GameTestSupport.check(entity.getItem().getCount() == 2, "Pickup-delay item count changed");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_splits_large_input_across_entries(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.ENDER_PEARL, 32), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket,
                new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.ENDER_PEARL, 16));
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void full_junk_bucket_still_merges_compatible_partial_entry(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        List<ItemStack> stored = new ArrayList<>();
        stored.add(new ItemStack(Items.APPLE, 20));
        stored.add(new ItemStack(Items.DIAMOND));
        stored.add(new ItemStack(Items.EMERALD));
        stored.add(new ItemStack(Items.IRON_INGOT));
        stored.add(new ItemStack(Items.GOLD_INGOT));
        stored.add(new ItemStack(Items.COAL));
        stored.add(new ItemStack(Items.REDSTONE));
        stored.add(new ItemStack(Items.LAPIS_LAZULI));
        stored.add(new ItemStack(Items.QUARTZ));
        NBTUtil.setStoredItems(bucket, stored);
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        List<ItemStack> actual = NBTUtil.getStoredItems(bucket);
        GameTestSupport.check(actual.size() == 9, "Merge changed occupied entry count");
        GameTestSupport.check(actual.get(0).getCount() == 30,
                "Compatible stack did not merge when all entry slots were occupied");
        GameTestSupport.check(!entity.isAlive(), "Merged item entity remained alive");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_world_ejection_is_fifo(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack first = new ItemStack(Items.DIAMOND, 2);
        ItemStack second = new ItemStack(Items.APPLE, 3);
        NBTUtil.setStoredItems(bucket, List.of(first, second));
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        BlockPos clicked = new BlockPos(4, 1, 4);
        helper.setBlock(clicked, Blocks.STONE);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, clicked, Direction.UP));

        InteractionResult result = ((JBItem) bucket.getItem()).useOn(context);

        GameTestSupport.check(result.consumesAction(), "Junk Bucket did not eject stored stack");
        GameTestSupport.assertStored(bucket, second);
        List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class, clicked.above(), 0.75D);
        GameTestSupport.check(drops.size() == 1, "Expected one ejected item entity, got " + drops.size());
        GameTestSupport.assertSameStack(first, drops.get(0).getItem(), "Junk Bucket did not eject oldest stack");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_feeds_adult_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3)));
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(5, 2, 4));

        InteractionResult result = ((JBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stored carrot did not feed pig");
        GameTestSupport.check(pig.isInLove(), "Fed adult pig did not enter love mode");
        GameTestSupport.assertStored(bucket, new ItemStack(Items.CARROT, 2));
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_feeds_baby_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 2)));
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(5, 2, 4));
        pig.setAge(-1000);

        InteractionResult result = ((JBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stored carrot did not feed baby pig");
        GameTestSupport.check(pig.getAge() > -1000, "Baby pig did not age up");
        GameTestSupport.assertStored(bucket, new ItemStack(Items.CARROT));
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_replaces_incompatible_world_stack(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.DIAMOND, 5)));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT, 12), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket, new ItemStack(Items.DIRT, 12));
        GameTestSupport.check(!entity.isAlive(), "Replacement item entity remained alive");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 60)));
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket, new ItemStack(Items.APPLE, 10));
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_processes_only_one_world_entity_per_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = playerWith(helper, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND), PLAYER_POS);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        int living = (first.isAlive() ? 1 : 0) + (second.isAlive() ? 1 : 0);
        GameTestSupport.check(living == 1, "Trash Bucket processed " + (2 - living) + " item entities");
        GameTestSupport.check(NBTUtil.getStoredItems(bucket).size() == 1, "Trash Bucket did not store one entry");
        helper.succeed();
    }

    private static Player playerWith(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }
}
