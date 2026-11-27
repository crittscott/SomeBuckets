package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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

final class StorageBucketScenarios {
    private StorageBucketScenarios() {}
    private static final BlockPos PLAYER_POS = new BlockPos(4, 2, 4);
    /** Mouse button of an inventory right-click. */
    private static final int RIGHT_BUTTON = 1;
    /** Mouse button of an inventory left-click. */
    private static final int LEFT_BUTTON = 0;

    /**
     * In game: hold a Junk Bucket holding 20 apples, stand on 10 dropped apples, and right-click;
     * the dropped apples disappear and the bucket holds one stack of 30.
     */
    static void junk_bucket_absorbs_and_merges_nearby_items(GameTestHelper helper) {
        ItemStack bucket = junkWith(new ItemStack(Items.APPLE, 20));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.APPLE, 30));
        GameTestSupport.check(!entity.isAlive(), "Fully absorbed item entity remained alive");
        helper.succeed();
    }

    /**
     * In game: hold a Junk Bucket holding 50 apples next to two dropped piles of 20 and 10 apples,
     * and right-click once; both piles are taken, filling one stack to 64 and starting another of 16.
     */
    static void junk_bucket_absorbs_multiple_entities_in_one_activation(GameTestHelper helper) {
        ItemStack bucket = junkWith(new ItemStack(Items.APPLE, 50));
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
     * In game: drop a diamond one block away and an emerald three blocks away, then right-click with
     * an empty Junk Bucket; only the diamond is collected.
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
     * In game: throw two diamonds with Q and immediately right-click with an empty Junk Bucket; the
     * diamonds are not collected until they could be picked up by walking over them.
     */
    static void junk_bucket_skips_pickup_delay(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);
        entity.setDefaultPickUpDelay();

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket);
        GameTestSupport.check(entity.isAlive(), "Pickup-delay item was absorbed");
        GameTestSupport.check(entity.getItem().getCount() == 2, "Pickup-delay item count changed");
        helper.succeed();
    }

    /**
     * In game: right-click 32 dropped ender pearls with an empty Junk Bucket; it stores them as two
     * stacks of 16.
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
     * In game: fill every entry of a Junk Bucket, the first with 20 apples, then right-click 10
     * dropped apples; they still join the apple stack, making 30.
     */
    static void full_junk_bucket_still_merges_compatible_partial_entry(GameTestHelper helper) {
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
        ItemStack bucket = junkWith(stored.toArray(ItemStack[]::new));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        List<ItemStack> actual = BucketState.getStoredItems(bucket);
        GameTestSupport.check(actual.size() == 9, "Merge changed occupied entry count to " + actual.size());
        GameTestSupport.check(actual.get(0).getCount() == 30,
                "Compatible stack did not merge when all entry slots were occupied");
        GameTestSupport.check(!entity.isAlive(), "Merged item entity remained alive");
        helper.succeed();
    }

    /**
     * In game: store two diamonds and then three apples in a Junk Bucket, sneak, and right-click the
     * top of a block; the diamonds drop on top of it and the apples stay.
     */
    static void junk_bucket_world_ejection_is_fifo(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.DIAMOND, 2);
        ItemStack second = new ItemStack(Items.APPLE, 3);
        ItemStack bucket = junkWith(first.copy(), second.copy());
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

    /**
     * In game: store two diamonds and then three apples in a Junk Bucket, sneak, look at the sky, and
     * right-click; the two diamonds are thrown from you as if dropped with Q, and the apples stay.
     */
    static void sneak_use_in_air_throws_oldest_stack(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingAtAir(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                junkWith(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.APPLE, 3)));
        player.setShiftKeyDown(true);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneak-use in air did not throw a stored stack");
        GameTestSupport.assertStored(helper, player.getMainHandItem(), new ItemStack(Items.APPLE, 3));
        List<ItemEntity> thrown = GameTestSupport.entities(helper, ItemEntity.class, PLAYER_POS, 3.0D);
        GameTestSupport.check(thrown.size() == 1, "Expected one thrown item entity, got " + thrown.size());
        GameTestSupport.assertSameStack(new ItemStack(Items.DIAMOND, 2), thrown.get(0).getItem(),
                "Sneak-use did not throw the oldest stack");
        helper.succeed();
    }

    /**
     * In game: right-click a pig with a Junk Bucket holding three carrots; the pig shows hearts and
     * the bucket keeps two carrots.
     */
    static void junk_bucket_feeds_adult_animal(GameTestHelper helper) {
        ItemStack bucket = junkWith(new ItemStack(Items.CARROT, 3));
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(5, 2, 4));

        InteractionResult result = bucket.getItem().interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stored carrot did not feed pig");
        GameTestSupport.check(pig.isInLove(), "Fed adult pig did not enter love mode");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.CARROT, 2));
        helper.succeed();
    }

    /**
     * In game: right-click a piglet with a Junk Bucket holding two carrots; the piglet grows a little
     * (green particles) and the bucket keeps one carrot.
     */
    static void junk_bucket_feeds_baby_animal(GameTestHelper helper) {
        ItemStack bucket = junkWith(new ItemStack(Items.CARROT, 2));
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(5, 2, 4));
        pig.setAge(-1000);

        InteractionResult result = bucket.getItem().interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stored carrot did not feed baby pig");
        GameTestSupport.check(pig.getAge() > -1000, "Baby pig did not age up");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.CARROT));
        helper.succeed();
    }

    /**
     * In game: hold a Trash Bucket holding five diamonds next to 12 dropped dirt and right-click; the
     * dirt is taken and the diamonds are gone.
     */
    static void trash_bucket_replaces_incompatible_world_stack(GameTestHelper helper) {
        ItemStack bucket = trashWith(new ItemStack(Items.DIAMOND, 5));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT, 12), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.DIRT, 12));
        GameTestSupport.check(!entity.isAlive(), "Replacement item entity remained alive");
        helper.succeed();
    }

    /**
     * In game: hold a Trash Bucket holding 60 apples next to 10 dropped apples and right-click; the
     * bucket now holds just the 10 new apples rather than topping its stack up to 64.
     */
    static void trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(GameTestHelper helper) {
        ItemStack bucket = trashWith(new ItemStack(Items.APPLE, 60));
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.APPLE, 10));
        helper.succeed();
    }

    /**
     * In game: with a Trash Bucket holding 60 apples, take in 10 more apples three ways (carry the
     * bucket and right-click a slot of apples, right-click the bucket's slot with apples on the
     * cursor, or right-click dropped apples); each time it ends up holding just the 10 new apples.
     */
    static void trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        InventoryMenu menu = player.inventoryMenu;
        int first = menuSlot(player, 9);
        int second = menuSlot(player, 10);

        menu.getSlot(first).set(new ItemStack(Items.APPLE, 10));
        menu.setCarried(trashWith(new ItemStack(Items.APPLE, 60)));
        menu.clicked(first, RIGHT_BUTTON, ClickType.PICKUP, player);
        GameTestSupport.assertStored(helper, menu.getCarried(), new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(menu.getSlot(first).getItem().isEmpty(), "Slot overflow intake left input behind");

        menu.getSlot(second).set(trashWith(new ItemStack(Items.APPLE, 60)));
        menu.setCarried(new ItemStack(Items.APPLE, 10));
        menu.clicked(second, RIGHT_BUTTON, ClickType.PICKUP, player);
        GameTestSupport.assertStored(helper, menu.getSlot(second).getItem(), new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(menu.getCarried().isEmpty(), "Cursor overflow intake left input behind");

        ItemStack worldBucket = trashWith(new ItemStack(Items.APPLE, 60));
        Player worldPlayer = playerWith(helper, worldBucket);
        ItemEntity worldInput = GameTestSupport.spawnItem(
                helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        GameTestSupport.useItem(worldPlayer, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, worldBucket, new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(!worldInput.isAlive(), "World overflow intake left input behind");
        helper.succeed();
    }

    /**
     * In game: right-click 32 dropped ender pearls with an empty Trash Bucket; it takes 16 and the
     * other 16 stay on the ground.
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

    /**
     * In game: drop a diamond and an emerald together and right-click once with an empty Trash
     * Bucket; only one of them is taken.
     */
    static void trash_bucket_processes_only_one_world_entity_per_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = playerWith(helper, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND), PLAYER_POS);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD), PLAYER_POS);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        int living = (first.isAlive() ? 1 : 0) + (second.isAlive() ? 1 : 0);
        GameTestSupport.check(living == 1, "Trash Bucket processed " + (2 - living) + " item entities");
        GameTestSupport.check(BucketState.getStoredItems(bucket).size() == 1, "Trash Bucket did not store one entry");
        helper.succeed();
    }

    /**
     * In game, in your inventory: carry an empty Junk Bucket and right-click a slot of 40 apples, and
     * the apples go into the bucket (a left-click just swaps the two); right-click a Junk Bucket in a
     * slot with 3 diamonds on the cursor, and the diamonds go in after its coal; right-click a Junk
     * Bucket holding coal then diamonds with an empty cursor, and the coal comes out on the cursor.
     */
    static void junk_bucket_screen_insert_and_fifo_extract(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        InventoryMenu menu = player.inventoryMenu;
        int first = menuSlot(player, 9);
        int second = menuSlot(player, 10);

        menu.getSlot(first).set(new ItemStack(Items.APPLE, 40));
        menu.setCarried(GameTestSupport.junk());
        menu.clicked(first, RIGHT_BUTTON, ClickType.PICKUP, player);
        GameTestSupport.assertStored(helper, menu.getCarried(), new ItemStack(Items.APPLE, 40));
        GameTestSupport.check(menu.getSlot(first).getItem().isEmpty(), "Slot intake left items behind");

        menu.getSlot(first).set(new ItemStack(Items.APPLE, 5));
        menu.setCarried(GameTestSupport.junk());
        menu.clicked(first, LEFT_BUTTON, ClickType.PICKUP, player);
        ItemStack swapped = menu.getSlot(first).getItem();
        GameTestSupport.check(swapped.getItem() == GameTestSupport.junk().getItem(),
                "Left-click did not put the Junk Bucket in the slot");
        GameTestSupport.assertStored(helper, swapped);
        GameTestSupport.check(menu.getCarried().is(Items.APPLE) && menu.getCarried().getCount() == 5,
                "Left-click moved apples into the Junk Bucket");

        menu.setCarried(new ItemStack(Items.DIAMOND, 3));
        menu.getSlot(second).set(junkWith(new ItemStack(Items.COAL, 2)));
        menu.clicked(second, RIGHT_BUTTON, ClickType.PICKUP, player);
        GameTestSupport.check(menu.getCarried().isEmpty(), "Cursor insert left items behind");
        GameTestSupport.assertStored(helper, menu.getSlot(second).getItem(),
                new ItemStack(Items.COAL, 2), new ItemStack(Items.DIAMOND, 3));

        menu.clicked(second, RIGHT_BUTTON, ClickType.PICKUP, player);
        GameTestSupport.assertSameStack(new ItemStack(Items.COAL, 2), menu.getCarried(),
                "FIFO extraction did not put the oldest stack on the cursor");
        GameTestSupport.assertStored(helper, menu.getSlot(second).getItem(), new ItemStack(Items.DIAMOND, 3));
        helper.succeed();
    }

    /**
     * In game: drop a chest that still holds a diamond (picked with its contents) and a shulker box,
     * and right-click with an empty Junk Bucket; neither is collected, so storage never nests. A
     * dropped empty chest is collected.
     */
    static void junk_bucket_refuses_items_carrying_an_inventory(GameTestHelper helper) {
        ItemStack loadedChest = new ItemStack(Items.CHEST);
        loadedChest.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        Player player = playerWith(helper, GameTestSupport.junk());
        ItemEntity chestEntity = GameTestSupport.spawnItem(helper, loadedChest, PLAYER_POS);
        ItemEntity shulkerBox = GameTestSupport.spawnItem(helper, new ItemStack(Items.SHULKER_BOX), PLAYER_POS);

        InteractionResult refused = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!refused.consumesAction(), "Junk Bucket took an item carrying an inventory");
        GameTestSupport.check(chestEntity.isAlive(), "Junk Bucket collected a chest carrying items");
        GameTestSupport.check(shulkerBox.isAlive(), "Junk Bucket collected a shulker box");
        GameTestSupport.assertStored(helper, player.getMainHandItem());

        chestEntity.discard();
        shulkerBox.discard();
        ItemEntity emptyChest = GameTestSupport.spawnItem(helper, new ItemStack(Items.CHEST), PLAYER_POS);
        InteractionResult allowed = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(allowed.consumesAction() && !emptyChest.isAlive(), "Junk Bucket refused an empty chest");
        GameTestSupport.assertStored(helper, player.getMainHandItem(), new ItemStack(Items.CHEST));
        helper.succeed();
    }

    /**
     * No in-game form: guards the storage eligibility rule across item kinds no single gesture
     * reaches together (bundles, container components, Junk and Trash Buckets, every other bucket),
     * and that a dropped filled Big Bucket is collected with its contents.
     */
    static void storage_eligibility_rule_accepts_buckets_and_refuses_containers(GameTestHelper helper) {
        // JBItem.canStore refuses empty stacks, items that opt out of container nesting, bundles,
        // shulker boxes, vanilla inventory components with contents, and loader item-inventory handlers.
        GameTestSupport.check(!JBItem.canStore(ItemStack.EMPTY), "Empty stack was storable");
        GameTestSupport.check(!JBItem.canStore(GameTestSupport.junk()), "Junk Bucket was storable");
        GameTestSupport.check(!JBItem.canStore(GameTestSupport.trash()), "Trash Bucket was storable");
        GameTestSupport.check(!JBItem.canStore(new ItemStack(Items.BUNDLE)), "Bundle was storable");
        GameTestSupport.check(!JBItem.canStore(new ItemStack(Items.WHITE_SHULKER_BOX)),
                "Shulker box was storable");

        ItemStack withContainerComponent = new ItemStack(Items.DIAMOND);
        withContainerComponent.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.EMERALD))));
        GameTestSupport.check(!JBItem.canStore(withContainerComponent),
                "Item carrying a filled minecraft:container was storable");
        GameTestSupport.check(JBItem.canStore(new ItemStack(Items.CHEST)), "Empty chest was not storable");

        GameTestSupport.check(JBItem.canStore(new ItemStack(Items.DIAMOND)),
                "Plain item was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.big8()), "Big Bucket was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.big64()), "Huge Bucket was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.source()), "Source Bucket was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.mob()), "Mob Bucket was not storable");

        ItemStack filledBig = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 4000);
        GameTestSupport.check(JBItem.canStore(filledBig), "Filled Big Bucket was not storable");

        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity bigEntity = GameTestSupport.spawnItem(helper, filledBig, PLAYER_POS);

        InteractionResult stored = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(stored.consumesAction(), "Junk Bucket did not absorb a filled Big Bucket");
        GameTestSupport.check(!bigEntity.isAlive(), "Absorbed Big Bucket item entity remained alive");
        GameTestSupport.assertStored(helper, bucket,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 4000));

        ItemEntity junkEntity = GameTestSupport.spawnItem(helper, GameTestSupport.junk(), PLAYER_POS);
        GameTestSupport.check(!JBItem.isIntakeCandidate(junkEntity),
                "A dropped Junk Bucket was a world-intake candidate");
        helper.succeed();
    }

    /**
     * In game: hold a stack of 16 empty Junk Buckets, stand on 10 dropped apples, and right-click;
     * the apples disappear, 15 empty Junk Buckets stay in hand, and one Junk Bucket holding the 10
     * apples appears in the inventory.
     */
    static void stacked_empty_junk_buckets_absorb_into_one(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.junk().copyWithCount(GameTestSupport.STACKED_EMPTIES));
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Junk Buckets did not absorb the apples");
        GameTestSupport.check(!entity.isAlive(), "Absorbed item entity remained alive");
        GameTestSupport.assertStored(helper, GameTestSupport.assertOneFilledFromStack(
                player, InteractionHand.MAIN_HAND, GameTestSupport.junk().getItem()), new ItemStack(Items.APPLE, 10));
        helper.succeed();
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

    private static ItemStack trashWith(ItemStack stored) {
        ItemStack bucket = GameTestSupport.trash();
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
}
