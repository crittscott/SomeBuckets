package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class StorageBucketScenarios {
    private StorageBucketScenarios() {}
    private static final BlockPos PLAYER_POS = new BlockPos(4, 2, 4);
    /** Longer than a brain sensor's scan interval, so a goat's tempting sensor has run at least once. */
    private static final long GOAT_SENSOR_WAIT_TICKS = 45L;

    /**
     * In game: holding a Junk Bucket that stores 20 apples, stand on 10 dropped apples and use the
     * bucket; the dropped apples disappear and the bucket stores one stack of 30.
     */
    static void junk_bucket_absorbs_and_merges_nearby_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 20)));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket, new ItemStack(Items.APPLE, 30));
        GameTestSupport.check(!entity.isAlive(), "Fully absorbed item entity remained alive");
        helper.succeed();
    }

    /**
     * In game: holding a Junk Bucket that stores 50 apples, stand on two dropped piles of 20 and 10
     * apples and use the bucket once; both piles are collected, filling the stack to 64 and starting
     * a second stack of 16.
     */
    static void junk_bucket_absorbs_multiple_entities_in_one_activation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 50)));
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

    /**
     * In game: drop two diamonds with Q and immediately use an empty Junk Bucket on them; nothing is
     * collected until the dropped item could be picked up by hand.
     */
    static void junk_bucket_skips_pickup_delay(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);
        entity.setDefaultPickUpDelay();

        boolean acted = ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND)
                .getResult().consumesAction();

        GameTestSupport.check(!acted, "Pickup-delay item reported successful absorption");
        GameTestSupport.assertStored(bucket);
        GameTestSupport.check(entity.isAlive(), "Pickup-delay item was absorbed");
        GameTestSupport.check(entity.getItem().getCount() == 2, "Pickup-delay item count changed");
        helper.succeed();
    }

    /**
     * In game: use an empty Junk Bucket next to 32 dropped ender pearls; it stores them as two
     * stacks of 16, the pearl's stack limit.
     */
    static void junk_bucket_splits_large_input_across_entries(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.ENDER_PEARL, 32), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket,
                new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.ENDER_PEARL, 16));
        helper.succeed();
    }

    /**
     * In game: fill all nine entries of a Junk Bucket, the first with 20 apples, then use it next to
     * 10 dropped apples; they merge into the apple stack, which becomes 30.
     */
    static void full_junk_bucket_still_merges_compatible_partial_entry(GameTestHelper helper) {
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
        BucketState.setStoredItems(bucket, stored);
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        List<ItemStack> actual = BucketState.getStoredItems(bucket);
        GameTestSupport.check(actual.size() == ((JBItem) bucket.getItem()).getCapacity(),
                "Merge changed occupied entry count");
        GameTestSupport.check(actual.get(0).getCount() == 30,
                "Compatible stack did not merge when all entry slots were occupied");
        GameTestSupport.check(!entity.isAlive(), "Merged item entity remained alive");
        helper.succeed();
    }

    /**
     * In game: store two diamonds and then three apples in a Junk Bucket, sneak, and right-click the
     * top of a block; the two diamonds drop on top of it and the apples stay stored.
     */
    static void junk_bucket_world_ejection_is_fifo(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack first = new ItemStack(Items.DIAMOND, 2);
        ItemStack second = new ItemStack(Items.APPLE, 3);
        BucketState.setStoredItems(bucket, List.of(first, second));
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

    /**
     * In game: right-click an adult pig with a Junk Bucket that stores three carrots; the pig enters
     * love mode and the bucket keeps two carrots.
     */
    static void junk_bucket_feeds_adult_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3)));
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(5, 2, 4));

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stored carrot did not feed pig");
        GameTestSupport.check(pig.isInLove(), "Fed adult pig did not enter love mode");
        GameTestSupport.assertStored(player.getMainHandItem(), new ItemStack(Items.CARROT, 2));
        helper.succeed();
    }

    /**
     * In game: right-click a piglet with a Junk Bucket that stores two carrots; the piglet grows a
     * little and the bucket keeps one carrot.
     */
    static void junk_bucket_feeds_baby_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 2)));
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(5, 2, 4));
        pig.setAge(-1000);

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stored carrot did not feed baby pig");
        GameTestSupport.check(pig.getAge() > -1000, "Baby pig did not age up");
        GameTestSupport.assertStored(player.getMainHandItem(), new ItemStack(Items.CARROT));
        helper.succeed();
    }

    /**
     * In game: use a Trash Bucket that stores five diamonds next to 12 dropped dirt; the dirt is
     * collected and the diamonds are gone.
     */
    static void trash_bucket_replaces_incompatible_world_stack(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.DIAMOND, 5)));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT, 12), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket, new ItemStack(Items.DIRT, 12));
        GameTestSupport.check(!entity.isAlive(), "Replacement item entity remained alive");
        helper.succeed();
    }

    /**
     * In game: use a Trash Bucket that stores 60 apples next to 10 dropped apples; the bucket now
     * holds just the 10 new apples rather than topping its stack up to 64.
     */
    static void trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 60)));
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket, new ItemStack(Items.APPLE, 10));
        helper.succeed();
    }

    /**
     * In game: a Trash Bucket storing 60 apples takes 10 more apples the same way whether you
     * right-click an inventory slot of apples with the bucket on the cursor, right-click the bucket
     * with apples on the cursor, or use it next to dropped apples: it then holds just the 10.
     */
    static void trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake(GameTestHelper helper) {
        ItemStack slotBucket = trashWith(new ItemStack(Items.APPLE, 60));
        SimpleContainer slotContainer = new SimpleContainer(new ItemStack(Items.APPLE, 10));
        Slot inputSlot = new Slot(slotContainer, 0, 0, 0);
        Player slotPlayer = GameTestSupport.survivalPlayer(helper, PLAYER_POS);

        boolean slotActed = ((TBItem) slotBucket.getItem()).overrideStackedOnOther(
                slotBucket, inputSlot, ClickAction.SECONDARY, slotPlayer);

        GameTestSupport.check(slotActed, "Trash Bucket rejected slot overflow intake");
        GameTestSupport.assertStored(slotBucket, new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(inputSlot.getItem().isEmpty(), "Slot overflow intake left input behind");

        ItemStack cursorBucket = trashWith(new ItemStack(Items.APPLE, 60));
        ItemStack cursorInput = new ItemStack(Items.APPLE, 10);
        SimpleContainer bucketContainer = new SimpleContainer(cursorBucket);
        Slot bucketSlot = new Slot(bucketContainer, 0, 0, 0);
        SimpleContainer cursorContainer = new SimpleContainer(cursorInput);
        SlotAccess cursorAccess = SlotAccess.forContainer(cursorContainer, 0);

        boolean cursorActed = ((TBItem) cursorBucket.getItem()).overrideOtherStackedOnMe(
                cursorBucket, cursorInput, bucketSlot, ClickAction.SECONDARY, slotPlayer, cursorAccess);

        GameTestSupport.check(cursorActed, "Trash Bucket rejected cursor overflow intake");
        GameTestSupport.assertStored(cursorBucket, new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(cursorContainer.getItem(0).isEmpty(),
                "Cursor overflow intake left input behind");

        ItemStack worldBucket = trashWith(new ItemStack(Items.APPLE, 60));
        Player worldPlayer = playerWith(helper, worldBucket);
        ItemEntity worldInput = GameTestSupport.spawnItem(
                helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((TBItem) worldBucket.getItem()).use(helper.getLevel(), worldPlayer, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(worldBucket, new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(!worldInput.isAlive(), "World overflow intake left input behind");
        helper.succeed();
    }

    /**
     * In game: use an empty Trash Bucket next to 32 dropped ender pearls; it takes one stack of 16
     * and the other 16 stay on the ground.
     */
    static void trash_bucket_world_intake_preserves_excess_entity_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(
                helper, new ItemStack(Items.ENDER_PEARL, 32), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket, new ItemStack(Items.ENDER_PEARL, 16));
        GameTestSupport.check(entity.isAlive(), "Partially consumed item entity was discarded");
        GameTestSupport.check(entity.getItem().is(Items.ENDER_PEARL)
                        && entity.getItem().getCount() == 16,
                "Partially consumed item entity retained the wrong remainder");
        helper.succeed();
    }

    /**
     * In game: use an empty Trash Bucket next to a dropped diamond and a dropped emerald; only one of
     * them is collected per use.
     */
    static void trash_bucket_processes_only_one_world_entity_per_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = playerWith(helper, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND), PLAYER_POS);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD), PLAYER_POS);

        boolean acted = ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND)
                .getResult().consumesAction();

        GameTestSupport.check(acted, "Trash Bucket rejected both nearby item entities");
        int living = (first.isAlive() ? 1 : 0) + (second.isAlive() ? 1 : 0);
        GameTestSupport.check(living == 1, "Trash Bucket processed " + (2 - living) + " item entities");
        GameTestSupport.check(BucketState.getStoredItems(bucket).size() == 1, "Trash Bucket did not store one entry");
        helper.succeed();
    }

    /**
     * In game: stand a few blocks from a pig while holding a Junk Bucket that stores carrots; the
     * pig follows you as it would a held carrot. A Junk Bucket storing only dirt, or an empty one,
     * does not tempt it, and a Trash Bucket storing a carrot in the offhand does.
     */
    static void storage_bucket_with_stored_food_tempts_animal(GameTestHelper helper) {
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(2, 2, 2));
        TemptGoal goal = new TemptGoal(pig, 1.0D, Ingredient.of(Items.CARROT), false);
        // TemptGoal finds players through the level's player list; joining it alone avoids a login.
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        helper.getLevel().players().add(player);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, junkWith(new ItemStack(Items.CARROT, 3)));
            GameTestSupport.check(goal.canUse(), "Junk Bucket of carrots did not tempt the pig");
            player.setItemInHand(InteractionHand.MAIN_HAND, junkWith(new ItemStack(Items.DIRT)));
            GameTestSupport.check(!goal.canUse(), "Junk Bucket of dirt tempted the pig");
            player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.junk());
            GameTestSupport.check(!goal.canUse(), "Empty Junk Bucket tempted the pig");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, trashWith(new ItemStack(Items.CARROT)));
            GameTestSupport.check(goal.canUse(), "Offhand Trash Bucket of a carrot did not tempt the pig");
        } finally {
            helper.getLevel().players().remove(player);
        }
        helper.succeed();
    }

    /**
     * No in-game form; guards that a Junk Bucket's tempting follows its current contents, since the
     * stored items are decoded once per content change and shared.
     */
    static void storage_bucket_tempting_follows_content_changes(GameTestHelper helper) {
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(2, 2, 2));
        TemptGoal goal = new TemptGoal(pig, 1.0D, Ingredient.of(Items.CARROT), false);
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3), new ItemStack(Items.DIRT)));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.getLevel().players().add(player);
        try {
            GameTestSupport.check(goal.canUse(), "Junk Bucket of carrots and dirt did not tempt the pig");
            JBItem.removeOldest(bucket);
            GameTestSupport.assertStored(bucket, new ItemStack(Items.DIRT));
            GameTestSupport.check(!goal.canUse(), "Junk Bucket kept tempting after its carrots were removed");
        } finally {
            helper.getLevel().players().remove(player);
        }
        helper.succeed();
    }

    /**
     * Brain-driven mobs are tempted through their tempting sensor, not a goal. In game: hold a Junk
     * Bucket containing wheat near a goat: the goat follows you.
     */
    static void storage_bucket_with_stored_wheat_tempts_goat(GameTestHelper helper) {
        checkGoatTempting(helper, junkWith(new ItemStack(Items.WHEAT, 3)), true);
    }

    /**
     * Control for {@link #storage_bucket_with_stored_wheat_tempts_goat}. In game: hold an empty Junk
     * Bucket near a goat: the goat ignores you.
     */
    static void empty_storage_bucket_does_not_tempt_goat(GameTestHelper helper) {
        checkGoatTempting(helper, GameTestSupport.junk(), false);
    }

    /*
     * Holds bucket near a goat long enough for its tempting sensor to scan, then checks whether the
     * goat remembers this test's player as tempting it. Another test's player may tempt the goat, so
     * only this player's memory entry counts.
     */
    private static void checkGoatTempting(GameTestHelper helper, ItemStack bucket, boolean expected) {
        Goat goat = GameTestSupport.spawn(helper, EntityType.GOAT, new BlockPos(2, 2, 2));
        // The tempting sensor finds players through the level's player list; joining it alone avoids a login.
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.getLevel().players().add(player);
        helper.runAfterDelay(GOAT_SENSOR_WAIT_TICKS, () -> {
            try {
                boolean tempted = goat.getBrain().getMemory(MemoryModuleType.TEMPTING_PLAYER)
                        .filter(tempting -> tempting == player).isPresent();
                GameTestSupport.check(tempted == expected, expected
                        ? "Junk Bucket of wheat did not tempt the goat"
                        : "Empty Junk Bucket tempted the goat");
            } finally {
                helper.getLevel().players().remove(player);
            }
            helper.succeed();
        });
    }

    /**
     * In game: hold a stack of three empty Junk Buckets near a dropped item and use them; one bucket
     * collects the item and goes to your inventory, and two empties stay in your hand.
     */
    static void stack_of_empty_junk_buckets_fills_one(GameTestHelper helper) {
        ItemStack empties = GameTestSupport.junk();
        empties.setCount(3);
        Player player = playerWith(helper, empties);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);

        ((JBItem) empties.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        GameTestSupport.check(held.getCount() == 2 && BucketState.isEmptyBucket(held),
                "Expected two empty Junk Buckets in hand, got " + held + " " + held.getTag());
        ItemStack filled = findFilled(player, held.getItem());
        GameTestSupport.check(filled.getCount() == 1, "Filled Junk Bucket did not reach the inventory");
        GameTestSupport.assertStored(filled, new ItemStack(Items.DIAMOND, 2));
        helper.succeed();
    }

    /**
     * In game: with a stack of empty Junk Buckets on the cursor, right-click a slot of items; nothing
     * moves until the stack is split down to one bucket.
     */
    static void stack_of_empty_junk_buckets_refuses_inventory_insertion(GameTestHelper helper) {
        ItemStack empties = GameTestSupport.junk();
        empties.setCount(2);
        Slot inputSlot = new Slot(new SimpleContainer(new ItemStack(Items.APPLE, 10)), 0, 0, 0);
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);

        boolean acted = ((JBItem) empties.getItem()).overrideStackedOnOther(
                empties, inputSlot, ClickAction.SECONDARY, player);

        GameTestSupport.check(!acted, "A stack of empty Junk Buckets accepted inventory items");
        GameTestSupport.check(inputSlot.getItem().getCount() == 10, "Refused insertion changed the slot");
        GameTestSupport.check(BucketState.isEmptyBucket(empties), "Refused insertion filled the stack");
        helper.succeed();
    }

    /**
     * In game: a player drops an item for another player (as {@code /give} does with a full
     * inventory); using a Junk Bucket next to it leaves it for that player.
     */
    static void junk_bucket_leaves_items_dropped_for_another_player(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.junk());
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);
        entity.setTarget(UUID.randomUUID());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Junk Bucket collected an item dropped for another player");
        GameTestSupport.check(entity.isAlive() && entity.getItem().getCount() == 2,
                "Refused collection changed the item entity");
        GameTestSupport.assertStored(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: a chest picked up with its contents (creative pick-block with Ctrl) cannot be put in a
     * Junk Bucket, nor can a shulker box, so storage never nests; an ordinary chest can.
     */
    static void junk_bucket_refuses_items_carrying_an_inventory(GameTestHelper helper) {
        ItemStack chest = new ItemStack(Items.CHEST);
        CompoundTag blockEntity = new CompoundTag();
        ListTag contents = new ListTag();
        CompoundTag slot = new ItemStack(Items.DIAMOND).save(new CompoundTag());
        slot.putByte("Slot", (byte) 0);
        contents.add(slot);
        blockEntity.put("Items", contents);
        chest.getOrCreateTag().put("BlockEntityTag", blockEntity);
        Player player = playerWith(helper, GameTestSupport.junk());
        ItemEntity loadedChest = GameTestSupport.spawnItem(helper, chest, PLAYER_POS);
        ItemEntity shulkerBox = GameTestSupport.spawnItem(helper, new ItemStack(Items.SHULKER_BOX), PLAYER_POS);

        InteractionResult refused = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!refused.consumesAction(), "Junk Bucket took an item carrying an inventory");
        GameTestSupport.check(loadedChest.isAlive(), "Junk Bucket collected a chest carrying items");
        GameTestSupport.check(shulkerBox.isAlive(), "Junk Bucket collected a shulker box");
        GameTestSupport.assertStored(player.getMainHandItem());

        loadedChest.discard();
        shulkerBox.discard();
        ItemEntity emptyChest = GameTestSupport.spawnItem(helper, new ItemStack(Items.CHEST), PLAYER_POS);
        InteractionResult allowed = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(allowed.consumesAction() && !emptyChest.isAlive(), "Junk Bucket refused an empty chest");
        GameTestSupport.assertStored(player.getMainHandItem(), new ItemStack(Items.CHEST));
        helper.succeed();
    }

    /**
     * In game: store two diamonds and then three apples in a Junk Bucket, sneak, look at the sky, and
     * right-click; the two diamonds are thrown from you as if dropped with Q, and the apples stay.
     */
    static void sneak_use_in_air_throws_oldest_stack(GameTestHelper helper) {
        // A server player: only ServerPlayer adds the item entities Player#drop creates to the level.
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                junkWith(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.APPLE, 3)));
        player.setShiftKeyDown(true);
        player.setXRot(-90.0F);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneak-use in air did not throw a stored stack");
        GameTestSupport.assertStored(player.getMainHandItem(), new ItemStack(Items.APPLE, 3));
        List<ItemEntity> thrown = GameTestSupport.entities(helper, ItemEntity.class, PLAYER_POS, 3.0D);
        GameTestSupport.check(thrown.size() == 1, "Expected one thrown item entity, got " + thrown.size());
        GameTestSupport.assertSameStack(new ItemStack(Items.DIAMOND, 2), thrown.get(0).getItem(),
                "Sneak-use did not throw the oldest stack");
        helper.succeed();
    }

    /**
     * In game: in your inventory, right-click a slot of 10 apples with an empty Junk Bucket on the
     * cursor and the apples go into the bucket; right-click a Junk Bucket in a slot with 5 bread on
     * the cursor and the bread goes in; right-click a Junk Bucket holding items with an empty cursor
     * and the oldest stored stack comes out on the cursor.
     */
    static void junk_bucket_inventory_right_click_inserts_and_extracts(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        InventoryMenu menu = player.inventoryMenu;
        int first = menuSlot(player, 9);
        int second = menuSlot(player, 10);

        menu.getSlot(first).set(new ItemStack(Items.APPLE, 10));
        menu.setCarried(GameTestSupport.junk());
        menu.clicked(first, 1, ClickType.PICKUP, player);
        GameTestSupport.assertStored(menu.getCarried(), new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(menu.getSlot(first).getItem().isEmpty(), "Cursor-bucket insertion left apples in the slot");

        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(second).set(GameTestSupport.junk());
        menu.setCarried(new ItemStack(Items.BREAD, 5));
        menu.clicked(second, 1, ClickType.PICKUP, player);
        GameTestSupport.assertStored(menu.getSlot(second).getItem(), new ItemStack(Items.BREAD, 5));
        GameTestSupport.check(menu.getCarried().isEmpty(), "Slot-bucket insertion left bread on the cursor");

        menu.getSlot(second).set(junkWith(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.APPLE, 3)));
        menu.clicked(second, 1, ClickType.PICKUP, player);
        GameTestSupport.assertSameStack(new ItemStack(Items.DIAMOND, 2), menu.getCarried(),
                "Extraction did not put the oldest stack on the cursor");
        GameTestSupport.assertStored(menu.getSlot(second).getItem(), new ItemStack(Items.APPLE, 3));
        helper.succeed();
    }

    /**
     * In game: with statistics open, use a Junk Bucket next to 5 dropped apples; the apples' "Picked
     * Up" count rises by 5, as if you had walked over them.
     */
    static void junk_bucket_intake_awards_picked_up_statistic(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.junk());
        GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 5), PLAYER_POS);
        int before = player.getStats().getValue(Stats.ITEM_PICKED_UP.get(Items.APPLE));

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(player.getMainHandItem(), new ItemStack(Items.APPLE, 5));
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_PICKED_UP.get(Items.APPLE)) == before + 5,
                "Junk Bucket intake did not award five picked-up apples");
        helper.succeed();
    }

    private static ItemStack findFilled(Player player, Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item) && !BucketState.isEmptyBucket(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static Player playerWith(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }

    private static ItemStack junkWith(ItemStack... stored) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(stored));
        return bucket;
    }

    /* The inventory menu index of the player's inventory slot {@code inventorySlot}. */
    private static int menuSlot(Player player, int inventorySlot) {
        for (Slot slot : player.inventoryMenu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) return slot.index;
        }
        throw new GameTestAssertException("Inventory menu has no slot for inventory slot " + inventorySlot);
    }

    private static ItemStack trashWith(ItemStack stored) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(stored));
        return bucket;
    }
}
