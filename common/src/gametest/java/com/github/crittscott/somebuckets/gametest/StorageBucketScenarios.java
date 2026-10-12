package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
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
    static void junk_bucket_skips_pickup_delay(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);
        entity.setDefaultPickUpDelay();

        boolean acted = ((JBItem) bucket.getItem()).absorbItemEntities(helper.getLevel(), bucket,
                List.of(entity), ProtectionContext.player(player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(!acted, "Pickup-delay item reported successful absorption");
        GameTestSupport.assertStored(bucket);
        GameTestSupport.check(entity.isAlive(), "Pickup-delay item was absorbed");
        GameTestSupport.check(entity.getItem().getCount() == 2, "Pickup-delay item count changed");
        helper.succeed();
    }
    static void junk_bucket_splits_large_input_across_entries(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.ENDER_PEARL, 32), PLAYER_POS);

        ((JBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket,
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
        GameTestSupport.assertStored(bucket, second);
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
        GameTestSupport.assertStored(bucket, new ItemStack(Items.CARROT, 2));
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
        GameTestSupport.assertStored(bucket, new ItemStack(Items.CARROT));
        helper.succeed();
    }
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
    static void trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 60)));
        Player player = playerWith(helper, bucket);
        GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), PLAYER_POS);

        ((TBItem) bucket.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertStored(bucket, new ItemStack(Items.APPLE, 10));
        helper.succeed();
    }
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
    static void trash_bucket_processes_only_one_world_entity_per_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = playerWith(helper, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND), PLAYER_POS);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD), PLAYER_POS);

        boolean acted = ((TBItem) bucket.getItem()).absorbItemEntities(helper.getLevel(), bucket,
                List.of(first, second), ProtectionContext.player(player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(acted, "Trash Bucket rejected both supplied item entities");
        int living = (first.isAlive() ? 1 : 0) + (second.isAlive() ? 1 : 0);
        GameTestSupport.check(living == 1, "Trash Bucket processed " + (2 - living) + " item entities");
        GameTestSupport.check(BucketState.getStoredItems(bucket).size() == 1, "Trash Bucket did not store one entry");
        helper.succeed();
    }

    /**
     * In-game: stand a few blocks from a pig while holding a Junk Bucket that stores carrots; the
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
     * In-game: hold a stack of three empty Junk Buckets near a dropped item and use them; one bucket
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
     * In-game: with a stack of empty Junk Buckets on the cursor, right-click a slot of items; nothing
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
     * In-game: a player drops an item for another player (as {@code /give} does with a full
     * inventory); using a Junk Bucket next to it leaves it for that player.
     */
    static void junk_bucket_leaves_items_dropped_for_another_player(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = playerWith(helper, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PLAYER_POS);
        entity.setTarget(UUID.randomUUID());

        boolean acted = ((JBItem) bucket.getItem()).absorbItemEntities(helper.getLevel(), bucket,
                List.of(entity), ProtectionContext.player(player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(!acted, "Junk Bucket collected an item dropped for another player");
        GameTestSupport.check(entity.isAlive() && entity.getItem().getCount() == 2,
                "Refused collection changed the item entity");
        GameTestSupport.assertStored(bucket);
        helper.succeed();
    }

    /**
     * In-game: a chest picked up with its contents (creative pick-block with Ctrl) cannot be put in a
     * Junk Bucket, so storage never nests.
     */
    static void junk_bucket_refuses_items_carrying_an_inventory(GameTestHelper helper) {
        ItemStack chest = new ItemStack(Items.CHEST);
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put("Items", new ListTag());
        chest.getOrCreateTag().put("BlockEntityTag", blockEntity);

        GameTestSupport.check(!JBItem.canStore(chest), "Junk Bucket accepted a chest carrying items");
        GameTestSupport.check(!JBItem.canStore(new ItemStack(Items.SHULKER_BOX)),
                "Junk Bucket accepted a shulker box");
        GameTestSupport.check(JBItem.canStore(new ItemStack(Items.CHEST)), "Junk Bucket refused an empty chest");
        helper.succeed();
    }

    private static ItemStack findFilled(Player player, net.minecraft.world.item.Item item) {
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

    private static ItemStack junkWith(ItemStack stored) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(stored));
        return bucket;
    }

    private static ItemStack trashWith(ItemStack stored) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(stored));
        return bucket;
    }
}


