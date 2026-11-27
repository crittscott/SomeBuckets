package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Cross-loader Junk and Trash Bucket scenarios; each loader wraps every method as a GameTest. */
final class StorageBucketScenarios {
    private StorageBucketScenarios() {}
    private static final BlockPos PLAYER_POS = new BlockPos(4, 2, 4);
    /** Longer than a brain sensor's scan interval, so a goat's tempting sensor has run at least once. */
    private static final long GOAT_SENSOR_WAIT_TICKS = 45L;
    /**
     * In game: holding a Junk Bucket with 20 apples, drop 10 apples at your feet and right-click the
     * air; the dropped apples vanish and the bucket holds one entry of 30 apples.
     */
    static void junk_bucket_absorbs_and_merges_nearby_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 20)));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.APPLE, 30));
        GameTestSupport.check(!entity.isAlive(), "Fully absorbed item entity remained alive");
        helper.succeed();
    }
    /**
     * In game: holding a Junk Bucket with 50 apples, drop 20 and then 10 apples at your feet as separate
     * stacks and right-click the air once; both stacks vanish and the bucket holds 64 and 16 apples.
     */
    static void junk_bucket_absorbs_multiple_entities_in_one_activation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 50)));
        Player player = playerWith(helper, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 20), PLAYER_POS);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket,
                new ItemStack(Items.APPLE, 64), new ItemStack(Items.APPLE, 16));
        GameTestSupport.check(!first.isAlive() && !second.isAlive(),
                "Junk Bucket did not absorb both item entities");
        helper.succeed();
    }
    /**
     * In game: use a Junk Bucket with one item nearby and one beyond its pickup radius; only the nearby
     * item is collected.
     */
    static void junk_bucket_world_collect_is_bounded_by_pickup_radius(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity near = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND),
                new BlockPos(5, 2, 4));
        ItemEntity far = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD),
                new BlockPos(7, 2, 4));

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!near.isAlive(), "Junk Bucket did not collect the in-range item");
        GameTestSupport.check(far.isAlive(), "Junk Bucket collected an out-of-range item");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.DIAMOND));
        helper.succeed();
    }
    /**
     * In game: immediately use a Junk Bucket beside a freshly dropped item; it remains until its normal
     * pickup delay expires.
     */
    static void junk_bucket_honors_pickup_delay(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);
        entity.setDefaultPickUpDelay();

        boolean acted = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND)
                .consumesAction();

        GameTestSupport.check(!acted, "Pickup-delay item reported successful absorption");
        GameTestSupport.assertStored(helper, bucket);
        GameTestSupport.check(entity.isAlive(), "Pickup-delay item was absorbed");
        GameTestSupport.check(entity.getItem().getCount() == 2, "Pickup-delay item count changed");
        helper.succeed();
    }
    /**
     * In game: with player A's inventory full, use {@code /give} on player A so the overflow is targeted
     * to them; player B's Junk Bucket cannot collect it until that target expires, then collection
     * increases B's picked-up statistic. Automation: clears the target directly before the second use.
     */
    static void junk_bucket_respects_item_target_and_records_pickup(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);
        entity.setTarget(UUID.randomUUID());

        boolean targeted = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND).consumesAction();

        GameTestSupport.check(!targeted, "Junk Bucket collected an item dropped for another player");
        GameTestSupport.check(entity.isAlive() && entity.getItem().getCount() == 2,
                "Targeted item entity changed");
        GameTestSupport.assertStored(helper, bucket);

        entity.setTarget(null);
        int pickedUpBefore = player.getStats().getValue(Stats.ITEM_PICKED_UP.get(Items.DIAMOND));
        boolean untargeted = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND).consumesAction();

        GameTestSupport.check(untargeted, "Junk Bucket did not collect an untargeted item");
        GameTestSupport.check(!entity.isAlive(), "Collected item entity remains");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.DIAMOND, 2));
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_PICKED_UP.get(Items.DIAMOND))
                        == pickedUpBefore + 2,
                "Junk Bucket intake did not award the picked-up statistic");
        helper.succeed();
    }
    /**
     * No in-game form: a vanilla item entity never holds more than one full stack. Guards that a Junk
     * Bucket collecting a dropped stack of 32 ender pearls stores it as two 16-pearl entries in order.
     */
    static void junk_bucket_splits_large_input_across_entries(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.ENDER_PEARL, 32), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket,
                new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.ENDER_PEARL, 16));
        helper.succeed();
    }
    /**
     * In game: fill all nine Junk Bucket entries but leave one compatible stack partial; matching items
     * still merge into it.
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

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        List<ItemStack> actual = BucketState.getStoredItems(bucket);
        GameTestSupport.check(actual.size() == ((JBItem) bucket.getItem()).getCapacity(),
                "Merge changed occupied entry count");
        GameTestSupport.check(actual.get(0).getCount() == 30,
                "Compatible stack did not merge when all entry slots were occupied");
        GameTestSupport.check(!entity.isAlive(), "Merged item entity remained alive");
        helper.succeed();
    }
    /** In game: store two different stacks, then sneak-use on a block; the oldest stack is ejected first. */
    static void junk_bucket_world_ejection_is_fifo(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack first = new ItemStack(Items.DIAMOND, 2);
        ItemStack second = new ItemStack(Items.APPLE, 3);
        BucketState.setStoredItems(bucket, List.of(first, second));
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        BlockPos clicked = new BlockPos(4, 1, 4);
        helper.setBlock(clicked, Blocks.STONE);

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, clicked, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Junk Bucket did not eject stored stack");
        GameTestSupport.assertStored(helper, bucket, second);
        List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class, clicked.above(), 0.75D);
        GameTestSupport.check(drops.size() == 1, "Expected one ejected item entity, got " + drops.size());
        GameTestSupport.assertSameStack(first, drops.get(0).getItem(), "Junk Bucket did not eject oldest stack");
        helper.succeed();
    }
    /** In game: store two different stacks, then sneak-use on air; the oldest stack is thrown from the player. */
    static void junk_bucket_sneak_use_in_air_throws_oldest(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack first = new ItemStack(Items.DIAMOND, 2);
        ItemStack second = new ItemStack(Items.APPLE, 3);
        BucketState.setStoredItems(bucket, List.of(first, second));
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        player.setXRot(-90.0F);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Junk Bucket did not throw a stored stack");
        GameTestSupport.assertStored(helper, bucket, second);
        List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class, PLAYER_POS, 3.0D);
        GameTestSupport.check(drops.size() == 1, "Expected one thrown item entity, got " + drops.size());
        GameTestSupport.assertSameStack(first, drops.get(0).getItem(), "Junk Bucket did not throw oldest stack");
        helper.succeed();
    }
    /**
     * In game: store suitable food and use the Junk Bucket on an adult animal; one item is consumed and
     * breeding begins.
     */
    static void junk_bucket_feeds_adult_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3)));
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(5, 2, 4));

        InteractionResult result = ((JBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stored carrot did not feed pig");
        GameTestSupport.check(pig.isInLove(), "Fed adult pig did not enter love mode");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.CARROT, 2));
        helper.succeed();
    }
    /**
     * In game: store suitable food and use the Junk Bucket on a baby animal; one item is consumed and
     * growth advances.
     */
    static void junk_bucket_feeds_baby_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 2)));
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(5, 2, 4));
        pig.setAge(-1000);

        InteractionResult result = ((JBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stored carrot did not feed baby pig");
        GameTestSupport.check(pig.getAge() > -1000, "Baby pig did not age up");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.CARROT));
        helper.succeed();
    }
    /**
     * In game: breed your tamed wolf, let it get hurt, then use a Junk Bucket holding beef on it while
     * it is still on breeding cooldown; one beef is consumed and the wolf heals, as with beef in hand.
     */
    static void junk_bucket_heals_tamed_wolf_on_breeding_cooldown(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.BEEF, 2)));
        Player player = playerWith(helper, bucket);
        Wolf wolf = GameTestSupport.spawn(helper, EntityType.WOLF, new BlockPos(5, 2, 4));
        wolf.tame(player);
        wolf.setAge(100);
        wolf.setHealth(wolf.getMaxHealth() - 6.0F);
        float healthBefore = wolf.getHealth();

        InteractionResult result = ((JBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, wolf, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stored beef did not feed the hurt tamed wolf");
        GameTestSupport.check(wolf.getHealth() > healthBefore, "Tamed wolf on breeding cooldown did not heal");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.BEEF));
        helper.succeed();
    }
    /**
     * In game: stand a few blocks from a pig while holding a Junk Bucket that stores carrots; the
     * pig follows you as it would a held carrot. A Junk Bucket storing only dirt, or an empty one,
     * does not tempt it, and a Trash Bucket storing a carrot in the off hand does.
     */
    static void storage_bucket_with_stored_food_tempts_animal(GameTestHelper helper) {
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(2, 2, 2));
        TemptGoal goal = new TemptGoal(pig, 1.0D, stack -> stack.is(Items.CARROT), false);
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
            player.setItemInHand(InteractionHand.OFF_HAND, trashWith(helper, new ItemStack(Items.CARROT)));
            GameTestSupport.check(goal.canUse(), "Off-hand Trash Bucket of a carrot did not tempt the pig");
        } finally {
            helper.getLevel().players().remove(player);
        }
        helper.succeed();
    }
    /**
     * In game: hold a Junk Bucket storing carrots and dirt near a pig, then throw the carrots out
     * with a sneak-use in the air; the pig stops following you.
     */
    static void storage_bucket_tempting_follows_content_changes(GameTestHelper helper) {
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(2, 2, 2));
        TemptGoal goal = new TemptGoal(pig, 1.0D, stack -> stack.is(Items.CARROT), false);
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3), new ItemStack(Items.DIRT)));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.getLevel().players().add(player);
        try {
            GameTestSupport.check(goal.canUse(), "Junk Bucket of carrots and dirt did not tempt the pig");
            player.setShiftKeyDown(true);
            player.setXRot(-90.0F);
            GameTestSupport.check(GameTestSupport.useItem(player, InteractionHand.MAIN_HAND).consumesAction(),
                    "Junk Bucket did not throw its carrots");
            GameTestSupport.assertStored(helper, player.getMainHandItem(), new ItemStack(Items.DIRT));
            GameTestSupport.check(!goal.canUse(), "Junk Bucket kept tempting after its carrots were removed");
        } finally {
            helper.getLevel().players().remove(player);
        }
        helper.succeed();
    }
    /**
     * Brain-driven mobs are tempted through their tempting sensor, not a goal. In game: hold a Junk
     * Bucket storing wheat near a goat; the goat follows you.
     */
    static void storage_bucket_with_stored_wheat_tempts_goat(GameTestHelper helper) {
        checkGoatTempting(helper, junkWith(new ItemStack(Items.WHEAT, 3)), true);
    }
    /**
     * Control for {@link #storage_bucket_with_stored_wheat_tempts_goat}. In game: hold an empty Junk
     * Bucket near a goat; the goat ignores you.
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

    private static ItemStack junkWith(ItemStack stored) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(stored));
        return bucket;
    }
    /**
     * In game: holding a Trash Bucket with 5 diamonds, drop 12 dirt at your feet and right-click the
     * air; the diamonds are destroyed and the bucket holds the 12 dirt.
     */
    static void trash_bucket_replaces_incompatible_world_stack(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.DIAMOND, 5)));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT, 12), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.DIRT, 12));
        GameTestSupport.check(!entity.isAlive(), "Replacement item entity remained alive");
        helper.succeed();
    }
    /**
     * In game: holding a Trash Bucket with 60 apples, drop 10 apples at your feet and right-click the
     * air; the bucket holds just the 10 apples, not 64 with 6 left on the ground.
     */
    static void trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 60)));
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.APPLE, 10));
        helper.succeed();
    }
    /**
     * In game: store 60 apples in a Trash Bucket, then offer 10 apples from a slot, from the cursor, and
     * as a dropped stack in separate trials; each trial destroys the old entry and stores the 10 apples.
     */
    static void trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake(GameTestHelper helper) {
        Player menuPlayer = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        InventoryMenu menu = menuPlayer.inventoryMenu;
        int slot = menuSlot(menuPlayer, 9);

        menu.getSlot(slot).set(new ItemStack(Items.APPLE, 10));
        menu.setCarried(trashWith(helper, new ItemStack(Items.APPLE, 60)));
        rightClick(menuPlayer, slot);

        GameTestSupport.assertStored(helper, menu.getCarried(), new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(menu.getSlot(slot).getItem().isEmpty(), "Slot overflow intake left input behind");

        menu.getSlot(slot).set(trashWith(helper, new ItemStack(Items.APPLE, 60)));
        menu.setCarried(new ItemStack(Items.APPLE, 10));
        rightClick(menuPlayer, slot);

        GameTestSupport.assertStored(helper, menu.getSlot(slot).getItem(), new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(menu.getCarried().isEmpty(), "Cursor overflow intake left input behind");

        ItemStack worldBucket = trashWith(helper, new ItemStack(Items.APPLE, 60));
        Player worldPlayer = playerWith(helper, worldBucket);
        ItemEntity worldInput = GameTestSupport.spawnItem(
                helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        GameTestSupport.useItem(worldPlayer, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, worldBucket, new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(!worldInput.isAlive(), "World overflow intake left input behind");
        helper.succeed();
    }
    /**
     * No in-game form: a vanilla item entity never holds more than one full stack. Guards that a Trash
     * Bucket collecting a dropped stack of 32 ender pearls stores 16 and leaves the other 16 in the
     * dropped stack.
     */
    static void trash_bucket_world_intake_preserves_excess_entity_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(
                helper, new ItemStack(Items.ENDER_PEARL, 32), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.ENDER_PEARL, 16));
        GameTestSupport.check(entity.isAlive(), "Partially consumed item entity was discarded");
        GameTestSupport.check(entity.getItem().is(Items.ENDER_PEARL)
                        && entity.getItem().getCount() == 16,
                "Partially consumed item entity retained the wrong remainder");
        helper.succeed();
    }
    /** In game: place two eligible item entities nearby and use a Trash Bucket once; only one entity is processed. */
    static void trash_bucket_processes_only_one_world_entity_per_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = playerWith(helper, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND), PLAYER_POS);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD), PLAYER_POS);

        boolean acted = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND)
                .consumesAction();

        GameTestSupport.check(acted, "Trash Bucket rejected both nearby item entities");
        int living = (first.isAlive() ? 1 : 0) + (second.isAlive() ? 1 : 0);
        GameTestSupport.check(living == 1, "Trash Bucket processed " + (2 - living) + " item entities");
        GameTestSupport.check(BucketState.getStoredItems(bucket).size() == 1, "Trash Bucket did not store one entry");
        helper.succeed();
    }

    /**
     * In game: in your inventory, right-click a slot of 40 apples with an empty Junk Bucket on the cursor
     * and the apples go into the bucket; left-clicking a slot of apples with it only swaps the two;
     * right-click a Junk Bucket holding coal with three diamonds on the cursor and the diamonds go in
     * after the coal; right-click it with an empty cursor and the oldest stored stack, the coal, comes
     * out on the cursor.
     */
    static void junk_bucket_inventory_right_click_inserts_and_extracts(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        InventoryMenu menu = player.inventoryMenu;
        int first = menuSlot(player, 9);
        int second = menuSlot(player, 10);

        menu.getSlot(first).set(new ItemStack(Items.APPLE, 40));
        menu.setCarried(GameTestSupport.junk());
        rightClick(player, first);

        GameTestSupport.assertStored(helper, menu.getCarried(), new ItemStack(Items.APPLE, 40));
        GameTestSupport.check(menu.getSlot(first).getItem().isEmpty(), "Slot intake left apples behind");

        menu.getSlot(first).set(new ItemStack(Items.APPLE, 5));
        menu.setCarried(GameTestSupport.junk());
        menu.clicked(first, 0, ClickType.PICKUP, player);

        ItemStack slotAfter = menu.getSlot(first).getItem();
        ItemStack carriedAfter = menu.getCarried();
        boolean bucketInSlot = slotAfter.getItem() instanceof JBItem;
        ItemStack bucketAfter = bucketInSlot ? slotAfter : carriedAfter;
        ItemStack applesAfter = bucketInSlot ? carriedAfter : slotAfter;
        GameTestSupport.check(bucketAfter.getItem() instanceof JBItem, "Primary click lost the Junk Bucket");
        GameTestSupport.assertStored(helper, bucketAfter);
        GameTestSupport.check(applesAfter.is(Items.APPLE) && applesAfter.getCount() == 5,
                "Primary click moved apples into the Junk Bucket");

        menu.getSlot(first).set(ItemStack.EMPTY);
        ItemStack slotted = GameTestSupport.junk();
        BucketState.setStoredItems(slotted, List.of(new ItemStack(Items.COAL, 2)));
        menu.getSlot(second).set(slotted);
        menu.setCarried(new ItemStack(Items.DIAMOND, 3));
        rightClick(player, second);

        GameTestSupport.check(menu.getCarried().isEmpty(), "Cursor insert left diamonds behind");
        GameTestSupport.assertStored(helper, menu.getSlot(second).getItem(),
                new ItemStack(Items.COAL, 2), new ItemStack(Items.DIAMOND, 3));

        rightClick(player, second);

        GameTestSupport.assertSameStack(new ItemStack(Items.COAL, 2), menu.getCarried(),
                "Extraction did not put the oldest stack on the cursor");
        GameTestSupport.assertStored(helper, menu.getSlot(second).getItem(), new ItemStack(Items.DIAMOND, 3));
        helper.succeed();
    }

    /** No in-game form: identical inventory inputs produce identical contents, cursors, and seeds. */
    static void junk_bucket_inventory_prediction_is_deterministic(GameTestHelper helper) {
        List<ItemStack> predicted = runInventoryPredictionSequence(helper, "predicted");
        List<ItemStack> authoritative = runInventoryPredictionSequence(helper, "authoritative");
        GameTestSupport.check(predicted.size() == authoritative.size(),
                "Prediction sequence returned different result counts");
        for (int i = 0; i < predicted.size(); i++) {
            GameTestSupport.assertSameStack(predicted.get(i), authoritative.get(i),
                    "Client/server inventory prediction diverged at result " + i);
        }
        helper.succeed();
    }

    /**
     * In game: drop a Junk Bucket, a Trash Bucket, a bundle, and a shulker box beside you and use an
     * empty Junk Bucket; nothing is collected. Drop a Big Bucket holding water as well and use it again;
     * only the Big Bucket is collected. Automation: also checks the storage rule directly for an empty
     * stack, an item carrying a container component, and every other bucket kind.
     */
    static void storage_eligibility_rule_accepts_buckets_and_refuses_containers(GameTestHelper helper) {
        // JBItem.canStore refuses empty stacks, items that opt out of container nesting, bundles,
        // shulker boxes, vanilla inventory components, and loader item-inventory handlers.
        GameTestSupport.check(!JBItem.canStore(ItemStack.EMPTY), "Empty stack was storable");
        ItemStack withContainerComponent = new ItemStack(Items.DIAMOND);
        withContainerComponent.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        GameTestSupport.check(!JBItem.canStore(withContainerComponent),
                "Item carrying minecraft:container was storable");
        GameTestSupport.check(JBItem.canStore(new ItemStack(Items.DIAMOND)),
                "Plain item was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.big64()), "Huge Bucket was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.source()), "Source Bucket was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.mob()), "Mob Bucket was not storable");

        Player player = playerWith(helper, GameTestSupport.junk());
        List<ItemEntity> containers = new ArrayList<>();
        for (ItemStack container : List.of(GameTestSupport.junk(), GameTestSupport.trash(),
                new ItemStack(Items.BUNDLE), new ItemStack(Items.WHITE_SHULKER_BOX))) {
            containers.add(GameTestSupport.spawnItem(helper, container, PLAYER_POS));
        }

        InteractionResult refused = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!refused.consumesAction(), "Junk Bucket collected a container item");
        GameTestSupport.assertStored(helper, player.getMainHandItem());

        ItemEntity bigEntity = GameTestSupport.spawnItem(helper,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 4000), PLAYER_POS);

        InteractionResult stored = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(stored.consumesAction(), "Junk Bucket did not collect a filled Big Bucket");
        GameTestSupport.check(!bigEntity.isAlive(), "Collected Big Bucket item entity remained alive");
        GameTestSupport.assertStored(helper, player.getMainHandItem(),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 4000));
        for (ItemEntity container : containers) {
            GameTestSupport.check(container.isAlive(), "Junk Bucket collected " + container.getItem());
        }
        helper.succeed();
    }

    private static List<ItemStack> runInventoryPredictionSequence(GameTestHelper helper, String side) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        List<ItemStack> results = new ArrayList<>();

        ItemStack bucket = GameTestSupport.junk();
        SimpleContainer slotInput = new SimpleContainer(new ItemStack(Items.APPLE, 32));
        Slot inputSlot = new Slot(slotInput, 0, 0, 0);
        long emptySeed = BucketState.getJunkLayoutSeed(bucket);
        boolean newEntry = ((JBItem) bucket.getItem()).overrideStackedOnOther(
                bucket, inputSlot, ClickAction.SECONDARY, player);
        GameTestSupport.check(newEntry && inputSlot.getItem().isEmpty(),
                side + " bucket-on-slot insertion failed");
        long firstSeed = BucketState.getJunkLayoutSeed(bucket);
        GameTestSupport.check(firstSeed != emptySeed,
                side + " new-entry insertion did not change the layout seed");
        results.add(bucket.copy());
        results.add(inputSlot.getItem().copy());

        Slot bucketSlot = new Slot(new SimpleContainer(bucket), 0, 0, 0);
        ItemStack mergeCursor = new ItemStack(Items.APPLE, 16);
        SimpleContainer mergeCursorContainer = new SimpleContainer(mergeCursor);
        boolean merged = ((JBItem) bucket.getItem()).overrideOtherStackedOnMe(
                bucket, mergeCursor, bucketSlot, ClickAction.SECONDARY, player,
                SlotAccess.forContainer(mergeCursorContainer, 0));
        GameTestSupport.check(merged && mergeCursorContainer.getItem(0).isEmpty(),
                side + " compatible cursor merge failed");
        long mergedSeed = BucketState.getJunkLayoutSeed(bucket);
        GameTestSupport.check(mergedSeed != firstSeed,
                side + " compatible merge did not change the layout seed");
        results.add(bucket.copy());
        results.add(mergeCursorContainer.getItem(0).copy());

        ItemStack newCursor = new ItemStack(Items.DIAMOND, 3);
        SimpleContainer newCursorContainer = new SimpleContainer(newCursor);
        boolean cursorNewEntry = ((JBItem) bucket.getItem()).overrideOtherStackedOnMe(
                bucket, newCursor, bucketSlot, ClickAction.SECONDARY, player,
                SlotAccess.forContainer(newCursorContainer, 0));
        GameTestSupport.check(cursorNewEntry && newCursorContainer.getItem(0).isEmpty(),
                side + " cursor-on-bucket new entry failed");
        GameTestSupport.check(BucketState.getJunkLayoutSeed(bucket) != mergedSeed,
                side + " cursor new entry did not change the layout seed");
        results.add(bucket.copy());
        results.add(newCursorContainer.getItem(0).copy());

        ItemStack partialBucket = GameTestSupport.junk();
        BucketState.setStoredItems(partialBucket, List.of(
                new ItemStack(Items.APPLE, 60), new ItemStack(Items.STONE, 64),
                new ItemStack(Items.DIRT, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.IRON_INGOT, 64),
                new ItemStack(Items.GOLD_INGOT, 64), new ItemStack(Items.DIAMOND, 64),
                new ItemStack(Items.EMERALD, 64)));
        BucketState.setJunkLayoutSeed(partialBucket, 91_823L);
        SimpleContainer partialInput = new SimpleContainer(new ItemStack(Items.APPLE, 10));
        Slot partialSlot = new Slot(partialInput, 0, 0, 0);
        boolean partial = ((JBItem) partialBucket.getItem()).overrideStackedOnOther(
                partialBucket, partialSlot, ClickAction.SECONDARY, player);
        GameTestSupport.check(partial && partialSlot.getItem().getCount() == 6,
                side + " partial compatible move did not leave six items");
        GameTestSupport.check(BucketState.getStoredItems(partialBucket).get(0).getCount() == 64,
                side + " partial compatible move did not fill the existing entry");
        GameTestSupport.check(BucketState.getJunkLayoutSeed(partialBucket) != 91_823L,
                side + " partial move did not change the layout seed");
        results.add(partialBucket.copy());
        results.add(partialSlot.getItem().copy());

        ItemStack extractBucket = GameTestSupport.junk();
        BucketState.setStoredItems(extractBucket,
                List.of(new ItemStack(Items.COAL, 2), new ItemStack(Items.DIAMOND, 3)));
        BucketState.setJunkLayoutSeed(extractBucket, 44L);
        Slot extractSlot = new Slot(new SimpleContainer(extractBucket), 0, 0, 0);
        SimpleContainer emptyCursor = new SimpleContainer(1);
        boolean extracted = ((JBItem) extractBucket.getItem()).overrideOtherStackedOnMe(
                extractBucket, ItemStack.EMPTY, extractSlot, ClickAction.SECONDARY, player,
                SlotAccess.forContainer(emptyCursor, 0));
        GameTestSupport.check(extracted && emptyCursor.getItem(0).is(Items.COAL)
                        && emptyCursor.getItem(0).getCount() == 2,
                side + " FIFO extraction did not assign the oldest entry to the cursor");
        GameTestSupport.check(BucketState.getStoredItems(extractBucket).get(0).is(Items.DIAMOND),
                side + " FIFO extraction removed the wrong entry");
        results.add(extractBucket.copy());
        results.add(emptyCursor.getItem(0).copy());
        return results;
    }

    /**
     * In game: in your inventory, with a stack of two empty Junk Buckets on the cursor, right-click a
     * slot of apples; nothing moves. Put the two buckets in a slot and right-click them with apples on
     * the cursor; nothing moves. Two empty Trash Buckets behave the same.
     */
    static void stacked_storage_buckets_refuse_inventory_gestures(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);

        for (ItemStack bucket : List.of(GameTestSupport.junk(), GameTestSupport.trash())) {
            bucket.setCount(2);
            JBItem item = (JBItem) bucket.getItem();
            Slot inputSlot = new Slot(new SimpleContainer(new ItemStack(Items.APPLE, 10)), 0, 0, 0);
            ItemStack cursor = new ItemStack(Items.APPLE, 10);
            Slot bucketSlot = new Slot(new SimpleContainer(bucket), 0, 0, 0);
            SlotAccess cursorAccess = SlotAccess.forContainer(new SimpleContainer(cursor), 0);

            boolean pulled = item.overrideStackedOnOther(bucket, inputSlot, ClickAction.SECONDARY, player);
            boolean inserted = item.overrideOtherStackedOnMe(
                    bucket, cursor, bucketSlot, ClickAction.SECONDARY, player, cursorAccess);

            GameTestSupport.check(!pulled, "A stack of " + item + " pulled items from a slot");
            GameTestSupport.check(!inserted, "A stack of " + item + " accepted a cursor insert");
            GameTestSupport.check(inputSlot.getItem().getCount() == 10, "Refused slot intake moved items");
            GameTestSupport.check(cursor.getCount() == 10, "Refused cursor insert moved items");
            GameTestSupport.check(bucket.getCount() == 2, "Refused gestures changed the bucket stack size");
            GameTestSupport.assertStored(helper, bucket);
        }
        helper.succeed();
    }

    /**
     * In game: with a Junk or Trash Bucket on the cursor, right-click a crafting result; the grid is
     * consumed once and the result is not duplicated. Automation: offers a pickup-refusing slot and a
     * result-style slot to both buckets on the cursor, and a cursor stack to a bucket sitting in a
     * result-style slot. The refusing slot and the slotted bucket are untouched; the result slot is
     * emptied through its take handling.
     */
    static void storage_buckets_honor_slot_take_rules(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);

        for (ItemStack bucket : List.of(GameTestSupport.junk(), GameTestSupport.trash())) {
            JBItem item = (JBItem) bucket.getItem();

            Slot locked = new Slot(new SimpleContainer(new ItemStack(Items.APPLE, 10)), 0, 0, 0) {
                @Override
                public boolean mayPickup(Player p) { return false; }
            };
            boolean pulledLocked = item.overrideStackedOnOther(bucket, locked, ClickAction.SECONDARY, player);
            GameTestSupport.check(!pulledLocked, item + " pulled from a slot that refuses pickup");
            GameTestSupport.check(locked.getItem().getCount() == 10, "A pickup-refusing slot lost items");
            GameTestSupport.assertStored(helper, bucket);

            int[] takenByHandler = {0};
            Slot result = new Slot(new SimpleContainer(new ItemStack(Items.APPLE, 10)), 0, 0, 0) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }

                @Override
                public void onTake(Player p, ItemStack stack) {
                    takenByHandler[0] += stack.getCount();
                    super.onTake(p, stack);
                }
            };
            boolean pulledResult = item.overrideStackedOnOther(bucket, result, ClickAction.SECONDARY, player);
            GameTestSupport.check(pulledResult, item + " refused a whole result-slot take");
            GameTestSupport.check(!result.hasItem(), "Result-slot take left items behind");
            GameTestSupport.check(takenByHandler[0] == 10, "Result-slot take skipped the slot's take handling");
            GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.APPLE, 10));

            ItemStack slotted = bucket.getItem() instanceof TBItem ? GameTestSupport.trash() : GameTestSupport.junk();
            Slot bucketResult = new Slot(new SimpleContainer(slotted), 0, 0, 0) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
            };
            ItemStack cursor = new ItemStack(Items.APPLE, 5);
            SlotAccess cursorAccess = SlotAccess.forContainer(new SimpleContainer(cursor), 0);
            boolean inserted = item.overrideOtherStackedOnMe(
                    slotted, cursor, bucketResult, ClickAction.SECONDARY, player, cursorAccess);
            GameTestSupport.check(!inserted, item + " in an unmodifiable slot accepted a cursor insert");
            GameTestSupport.check(cursor.getCount() == 5, "Refused cursor insert moved items");
            GameTestSupport.assertStored(helper, slotted);
        }
        helper.succeed();
    }

    /**
     * In game: use a stack of three empty Junk Buckets beside a dropped item; two empties stay in hand
     * and one bucket holding the item goes into the inventory.
     */
    static void stacked_empty_junk_vacuum_moves_one_filled_bucket_to_inventory(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        ItemStack held = GameTestSupport.junk();
        held.setCount(3);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Junk Bucket did not collect the item");
        GameTestSupport.check(!entity.isAlive(), "Collected item entity remained in the world");
        ItemStack hand = player.getMainHandItem();
        GameTestSupport.check(hand.getItem() instanceof JBItem && hand.getCount() == 2,
                "Expected two empty Junk Buckets in hand, got " + hand);
        GameTestSupport.assertStored(helper, hand);
        List<ItemStack> filled = GameTestSupport.inventoryStacks(player, stack ->
                stack.getItem() instanceof JBItem && !BucketState.getStoredItems(stack).isEmpty());
        GameTestSupport.check(filled.size() == 1, "Expected one filled Junk Bucket in inventory, got " + filled);
        GameTestSupport.assertStored(helper, filled.get(0), new ItemStack(Items.DIAMOND, 2));
        helper.succeed();
    }

    private static Player playerWith(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }

    /** The inventory menu index of the player's inventory slot {@code inventorySlot}. */
    private static int menuSlot(Player player, int inventorySlot) {
        for (Slot slot : player.inventoryMenu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) {
                return slot.index;
            }
        }
        throw new GameTestAssertException("Inventory menu has no slot for inventory slot " + inventorySlot);
    }

    /** Right-clicks menu slot {@code index} of the player's inventory screen with the current cursor. */
    private static void rightClick(Player player, int index) {
        player.inventoryMenu.clicked(index, 1, ClickType.PICKUP, player);
    }

    private static ItemStack trashWith(GameTestHelper helper, ItemStack stored) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(stored));
        return bucket;
    }
}


