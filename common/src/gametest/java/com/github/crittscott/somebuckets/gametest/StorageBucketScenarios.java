package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

final class StorageBucketScenarios {
    private StorageBucketScenarios() {}
    private static final BlockPos PLAYER_POS = new BlockPos(4, 2, 4);
    static void junk_bucket_absorbs_and_merges_nearby_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 20)));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.APPLE, 30));
        GameTestSupport.check(!entity.isAlive(), "Fully absorbed item entity remained alive");
        helper.succeed();
    }
    static void junk_bucket_absorbs_multiple_entities_in_one_activation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 50)));
        Player player = playerWith(helper, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 20), PLAYER_POS);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket,
                new ItemStack(Items.APPLE, 64), new ItemStack(Items.APPLE, 16));
        GameTestSupport.check(!first.isAlive() && !second.isAlive(),
                "Junk Bucket did not absorb both item entities");
        helper.succeed();
    }
    static void junk_bucket_world_collect_is_bounded_by_pickup_radius(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity near = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND),
                new BlockPos(5, 2, 4));
        ItemEntity far = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD),
                new BlockPos(7, 2, 4));

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!near.isAlive(), "Junk Bucket did not collect the in-range item");
        GameTestSupport.check(far.isAlive(), "Junk Bucket collected an out-of-range item");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.DIAMOND));
        helper.succeed();
    }
    static void junk_bucket_skips_pickup_delay(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);
        entity.setDefaultPickUpDelay();

        boolean acted = ((JBItem) bucket.getItem()).absorbItemEntities(helper.getLevel(), bucket,
                List.of(entity), ProtectionContext.player(player, InteractionHand.MAIN_HAND), Direction.UP);

        GameTestSupport.check(!acted, "Pickup-delay item reported successful absorption");
        GameTestSupport.assertStored(helper, bucket);
        GameTestSupport.check(entity.isAlive(), "Pickup-delay item was absorbed");
        GameTestSupport.check(entity.getItem().getCount() == 2, "Pickup-delay item count changed");
        helper.succeed();
    }
    static void junk_bucket_splits_large_input_across_entries(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.ENDER_PEARL, 32), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket,
                new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.ENDER_PEARL, 16));
        helper.succeed();
    }
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
        GameTestSupport.assertStored(helper, bucket, second);
        List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class, clicked.above(), 0.75D);
        GameTestSupport.check(drops.size() == 1, "Expected one ejected item entity, got " + drops.size());
        GameTestSupport.assertSameStack(first, drops.get(0).getItem(), "Junk Bucket did not eject oldest stack");
        helper.succeed();
    }
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
    static void trash_bucket_replaces_incompatible_world_stack(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.DIAMOND, 5)));
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT, 12), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.DIRT, 12));
        GameTestSupport.check(!entity.isAlive(), "Replacement item entity remained alive");
        helper.succeed();
    }
    static void trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 60)));
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.APPLE, 10));
        helper.succeed();
    }
    static void trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake(GameTestHelper helper) {
        ItemStack slotBucket = trashWith(helper, new ItemStack(Items.APPLE, 60));
        SimpleContainer slotContainer = new SimpleContainer(new ItemStack(Items.APPLE, 10));
        Slot inputSlot = new Slot(slotContainer, 0, 0, 0);
        Player slotPlayer = GameTestSupport.survivalPlayer(helper, PLAYER_POS);

        boolean slotActed = ((TBItem) slotBucket.getItem()).overrideStackedOnOther(
                slotBucket, inputSlot, ClickAction.SECONDARY, slotPlayer);

        GameTestSupport.check(slotActed, "Trash Bucket rejected slot overflow intake");
        GameTestSupport.assertStored(helper, slotBucket, new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(inputSlot.getItem().isEmpty(), "Slot overflow intake left input behind");

        ItemStack cursorBucket = trashWith(helper, new ItemStack(Items.APPLE, 60));
        ItemStack cursorInput = new ItemStack(Items.APPLE, 10);
        SimpleContainer bucketContainer = new SimpleContainer(cursorBucket);
        Slot bucketSlot = new Slot(bucketContainer, 0, 0, 0);
        SimpleContainer cursorContainer = new SimpleContainer(cursorInput);
        SlotAccess cursorAccess = SlotAccess.forContainer(cursorContainer, 0);

        boolean cursorActed = ((TBItem) cursorBucket.getItem()).overrideOtherStackedOnMe(
                cursorBucket, cursorInput, bucketSlot, ClickAction.SECONDARY, slotPlayer, cursorAccess);

        GameTestSupport.check(cursorActed, "Trash Bucket rejected cursor overflow intake");
        GameTestSupport.assertStored(helper, cursorBucket, new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(cursorContainer.getItem(0).isEmpty(),
                "Cursor overflow intake left input behind");

        ItemStack worldBucket = trashWith(helper, new ItemStack(Items.APPLE, 60));
        Player worldPlayer = playerWith(helper, worldBucket);
        ItemEntity worldInput = GameTestSupport.spawnItem(
                helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((TBItem) worldBucket.getItem()).use(helper.getLevel(), worldPlayer, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, worldBucket, new ItemStack(Items.APPLE, 10));
        GameTestSupport.check(!worldInput.isAlive(), "World overflow intake left input behind");
        helper.succeed();
    }
    static void trash_bucket_world_intake_preserves_excess_entity_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(
                helper, new ItemStack(Items.ENDER_PEARL, 32), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.ENDER_PEARL, 16));
        GameTestSupport.check(entity.isAlive(), "Partially consumed item entity was discarded");
        GameTestSupport.check(entity.getItem().is(Items.ENDER_PEARL)
                        && entity.getItem().getCount() == 16,
                "Partially consumed item entity retained the wrong remainder");
        helper.succeed();
    }
    static void trash_bucket_processes_only_one_world_entity_per_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = playerWith(helper, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND), PLAYER_POS);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD), PLAYER_POS);

        boolean acted = ((TBItem) bucket.getItem()).absorbItemEntities(helper.getLevel(), bucket,
                List.of(first, second), ProtectionContext.player(player, InteractionHand.MAIN_HAND), Direction.UP);

        GameTestSupport.check(acted, "Trash Bucket rejected both supplied item entities");
        int living = (first.isAlive() ? 1 : 0) + (second.isAlive() ? 1 : 0);
        GameTestSupport.check(living == 1, "Trash Bucket processed " + (2 - living) + " item entities");
        GameTestSupport.check(BucketState.getStoredItems(bucket).size() == 1, "Trash Bucket did not store one entry");
        helper.succeed();
    }

    static void junk_bucket_screen_insert_and_fifo_extract(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);

        ItemStack cursorBucket = GameTestSupport.junk();
        SimpleContainer otherContainer = new SimpleContainer(new ItemStack(Items.APPLE, 40));
        Slot otherSlot = new Slot(otherContainer, 0, 0, 0);

        boolean pulled = ((JBItem) cursorBucket.getItem()).overrideStackedOnOther(
                cursorBucket, otherSlot, ClickAction.SECONDARY, player);

        GameTestSupport.check(pulled, "Junk Bucket rejected slot intake while on the cursor");
        GameTestSupport.check(otherSlot.getItem().isEmpty(), "Slot intake left items behind");
        GameTestSupport.assertStored(helper, cursorBucket, new ItemStack(Items.APPLE, 40));

        ItemStack primaryBucket = GameTestSupport.junk();
        SimpleContainer primaryContainer = new SimpleContainer(new ItemStack(Items.APPLE, 5));
        Slot primarySlot = new Slot(primaryContainer, 0, 0, 0);

        boolean primaryActed = ((JBItem) primaryBucket.getItem()).overrideStackedOnOther(
                primaryBucket, primarySlot, ClickAction.PRIMARY, player);

        GameTestSupport.check(!primaryActed, "Junk Bucket acted on a primary click");
        GameTestSupport.check(primarySlot.getItem().getCount() == 5, "Primary click moved items");
        GameTestSupport.assertStored(helper, primaryBucket);

        ItemStack slotBucket = GameTestSupport.junk();
        BucketState.setStoredItems(slotBucket, List.of(new ItemStack(Items.COAL, 2)));
        Slot bucketSlot = new Slot(new SimpleContainer(slotBucket), 0, 0, 0);
        ItemStack cursorInsert = new ItemStack(Items.DIAMOND, 3);
        SimpleContainer cursorContainer = new SimpleContainer(cursorInsert);
        SlotAccess cursorAccess = SlotAccess.forContainer(cursorContainer, 0);

        boolean inserted = ((JBItem) slotBucket.getItem()).overrideOtherStackedOnMe(
                slotBucket, cursorInsert, bucketSlot, ClickAction.SECONDARY, player, cursorAccess);

        GameTestSupport.check(inserted, "Junk Bucket rejected a cursor insert");
        GameTestSupport.check(cursorContainer.getItem(0).isEmpty(), "Cursor insert left items behind");
        GameTestSupport.assertStored(helper, slotBucket,
                new ItemStack(Items.COAL, 2), new ItemStack(Items.DIAMOND, 3));

        ItemStack extractBucket = GameTestSupport.junk();
        ItemStack oldest = new ItemStack(Items.COAL, 2);
        BucketState.setStoredItems(extractBucket, List.of(oldest, new ItemStack(Items.DIAMOND, 3)));
        Slot extractSlot = new Slot(new SimpleContainer(extractBucket), 0, 0, 0);
        SimpleContainer emptyCursor = new SimpleContainer(1);
        SlotAccess emptyCursorAccess = SlotAccess.forContainer(emptyCursor, 0);

        boolean extracted = ((JBItem) extractBucket.getItem()).overrideOtherStackedOnMe(
                extractBucket, ItemStack.EMPTY, extractSlot, ClickAction.SECONDARY, player, emptyCursorAccess);

        GameTestSupport.check(extracted, "Junk Bucket rejected FIFO extraction to an empty cursor");
        GameTestSupport.assertSameStack(oldest, emptyCursor.getItem(0),
                "FIFO extraction did not pop the oldest stack");
        GameTestSupport.assertStored(helper, extractBucket, new ItemStack(Items.DIAMOND, 3));
        helper.succeed();
    }

    static void storage_eligibility_rule_accepts_buckets_and_refuses_containers(GameTestHelper helper) {
        // JBItem.canStore refuses empty stacks, items that opt out of container nesting, bundles,
        // shulker boxes, vanilla inventory components, and loader item-inventory handlers.
        GameTestSupport.check(!JBItem.canStore(ItemStack.EMPTY), "Empty stack was storable");
        GameTestSupport.check(!JBItem.canStore(GameTestSupport.junk()), "Junk Bucket was storable");
        GameTestSupport.check(!JBItem.canStore(GameTestSupport.trash()), "Trash Bucket was storable");
        GameTestSupport.check(!JBItem.canStore(new ItemStack(Items.BUNDLE)), "Bundle was storable");
        GameTestSupport.check(!JBItem.canStore(new ItemStack(Items.WHITE_SHULKER_BOX)),
                "Shulker box was storable");

        ItemStack withContainerComponent = new ItemStack(Items.DIAMOND);
        withContainerComponent.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        GameTestSupport.check(!JBItem.canStore(withContainerComponent),
                "Item carrying minecraft:container was storable");

        GameTestSupport.check(JBItem.canStore(new ItemStack(Items.DIAMOND)),
                "Plain item was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.big8()), "Big Bucket was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.big64()), "Huge Bucket was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.source()), "Source Bucket was not storable");
        GameTestSupport.check(JBItem.canStore(GameTestSupport.mob()), "Mob Bucket was not storable");

        ItemStack filledBig = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 4000);
        GameTestSupport.check(JBItem.canStore(filledBig), "Filled Big Bucket was not storable");

        ItemStack bucket = GameTestSupport.junk();
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        ItemEntity bigEntity = GameTestSupport.spawnItem(helper, filledBig, PLAYER_POS);

        boolean stored = ((JBItem) bucket.getItem()).absorbItemEntities(helper.getLevel(), bucket,
                List.of(bigEntity), ProtectionContext.player(player, InteractionHand.MAIN_HAND), Direction.UP);

        GameTestSupport.check(stored, "Junk Bucket did not absorb a filled Big Bucket");
        GameTestSupport.check(!bigEntity.isAlive(), "Absorbed Big Bucket item entity remained alive");
        GameTestSupport.assertStored(helper, bucket,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 4000));

        ItemEntity junkEntity = GameTestSupport.spawnItem(helper, GameTestSupport.junk(), PLAYER_POS);
        GameTestSupport.check(!JBItem.isIntakeCandidate(junkEntity),
                "A dropped Junk Bucket was a world-intake candidate");
        helper.succeed();
    }

    private static Player playerWith(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }

    private static ItemStack trashWith(GameTestHelper helper, ItemStack stored) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(stored));
        return bucket;
    }
}


