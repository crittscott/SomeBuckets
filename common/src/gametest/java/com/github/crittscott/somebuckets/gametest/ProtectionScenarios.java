package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Cross-loader protection and authorization scenarios; each loader wraps every method as a GameTest. */
final class ProtectionScenarios {
    private ProtectionScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);
    /** Blocks between a test structure and the one-block world border {@link #outsideWorldBorder} sets. */
    private static final int DISTANT_BORDER_OFFSET = 1024;

    @FunctionalInterface
    interface ScopedDenial {
        void run(List<BlockPos> denied, Runnable action);
    }

    /**
     * In game: configure a protection mod to deny block breaking, then right-click source water there
     * with an empty Big Bucket; the water stays and the bucket stays empty, while a dispenser holding an
     * empty Big Bucket in the same area still collects water. Automation: cancels the loader's break
     * event and verifies it was posted once, at the player's target only.
     */
    static void cancelled_break_check_denies_player_but_not_automation(
            GameTestHelper helper, ScopedDenial denyingBreaks) {
        BlockPos automationTarget = TARGET.east(2);
        BlockPos dispenserPos = automationTarget.east();
        helper.setBlock(TARGET, Blocks.WATER);
        helper.setBlock(automationTarget, Blocks.WATER);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.big8());
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, dispenserPos, Direction.WEST, GameTestSupport.big8());
        List<BlockPos> denied = new java.util.ArrayList<>();

        InteractionResult[] result = new InteractionResult[1];
        denyingBreaks.run(denied, () -> {
            result[0] = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
            GameTestSupport.dispenseNow(helper, dispenserPos);
        });

        GameTestSupport.check(!result[0].consumesAction(), "A cancelled break check did not deny the player's pickup");
        GameTestSupport.check(denied.equals(List.of(helper.absolutePos(TARGET))),
                "Expected one break check at the player's target, got " + denied);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
        GameTestSupport.assertBlock(helper, automationTarget, Blocks.AIR);
        helper.succeed();
    }

    /** A refused loader place event: its absolute position and the block it reported as placed. */
    record DeniedPlacement(BlockPos pos, BlockState placed) {}

    @FunctionalInterface
    interface ScopedPlaceDenial {
        void run(List<DeniedPlacement> denied, Runnable action);
    }

    /**
     * In game: configure a protection mod to deny fluid placement, then right-click the top of a stone
     * block there with a full Big Bucket of water; no water appears and the bucket is unchanged.
     * Automation: cancels the loader's place event and verifies one event reporting water above the
     * stone.
     */
    static void cancelled_place_check_denies_player_fluid_place(
            GameTestHelper helper, ScopedPlaceDenial denyingPlacements) {
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(TARGET.above(), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.above().east(2), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(
                GameTestSupport.big8(), Fluids.WATER, 8 * FluidBucketItem.BUCKET_VOLUME_MB));
        ItemStack before = player.getMainHandItem().copy();
        List<DeniedPlacement> denied = new java.util.ArrayList<>();

        InteractionResult[] result = new InteractionResult[1];
        denyingPlacements.run(denied, () -> result[0] = GameTestSupport.clickBlock(player,
                InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, TARGET, Direction.UP)));

        GameTestSupport.check(!result[0].consumesAction(), "A cancelled place check did not deny the player's placement");
        GameTestSupport.check(denied.equals(List.of(new DeniedPlacement(
                        helper.absolutePos(TARGET.above()), Blocks.WATER.defaultBlockState()))),
                "Expected one place check reporting water at the resolved target, got " + denied);
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Denied placement drained the bucket");
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: configure a protection mod to deny fluid placement, then pour a water-filled Big Bucket
     * onto short grass there; the grass stays, nothing drops, and the bucket is unchanged.
     * Automation: cancels the loader's place event and verifies the replaced grass is restored without
     * drops.
     */
    static void cancelled_place_check_restores_replaced_plant_without_drops(
            GameTestHelper helper, ScopedPlaceDenial denyingPlacements) {
        helper.setBlock(TARGET, Blocks.DIRT);
        helper.setBlock(TARGET.above(), Blocks.SHORT_GRASS);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.above().east(), TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(
                GameTestSupport.big8(), Fluids.WATER, 8 * FluidBucketItem.BUCKET_VOLUME_MB));
        ItemStack before = player.getMainHandItem().copy();
        List<DeniedPlacement> denied = new java.util.ArrayList<>();

        InteractionResult[] result = new InteractionResult[1];
        denyingPlacements.run(denied, () -> result[0] = GameTestSupport.clickBlock(player,
                InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, TARGET.above(), Direction.UP)));

        GameTestSupport.check(!result[0].consumesAction(), "A cancelled place check did not deny the pour onto grass");
        GameTestSupport.check(denied.equals(List.of(new DeniedPlacement(
                        helper.absolutePos(TARGET.above()), Blocks.WATER.defaultBlockState()))),
                "Expected one place check reporting water at the grass, got " + denied);
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.SHORT_GRASS);
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, TARGET.above(), 1.5D).isEmpty(),
                "Denied pour dropped the replaced grass's loot");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Denied pour drained the bucket");
        helper.succeed();
    }

    /**
     * In game: in a claim that allows using blocks but forbids building, right-click a full water
     * cauldron with a stack of empty Source Buckets: one bucket is assigned and the cauldron
     * empties; no extra bucket appears.
     * Automation: drives the server's block-use dispatch while the loader's place event is
     * cancelled, and verifies the cauldron take is judged as block use, not as a placement.
     */
    static void source_bucket_stack_on_cauldron_is_block_use_not_placement(
            GameTestHelper helper, ScopedPlaceDenial denyingPlacements) {
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.west());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source().copyWithCount(4));
        List<DeniedPlacement> denied = new java.util.ArrayList<>();

        InteractionResult[] result = new InteractionResult[1];
        denyingPlacements.run(denied, () -> result[0] = player.gameMode.useItemOn(player, helper.getLevel(),
                player.getMainHandItem(), InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP)));

        GameTestSupport.check(result[0].consumesAction(), "Source Bucket stack did not take from the cauldron");
        GameTestSupport.check(denied.isEmpty(), "The cauldron take was posted as a placement: " + denied);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.CAULDRON);
        ItemStack held = player.getMainHandItem();
        GameTestSupport.check(held.getItem() instanceof SBItem && held.getCount() == 3,
                "Expected three empty Source Buckets left in hand, got " + held);
        GameTestSupport.assertEmpty(held);
        List<ItemStack> assigned = GameTestSupport.inventoryStacks(player,
                stack -> BucketState.getMode(stack) == BucketState.Mode.FLUID);
        GameTestSupport.check(assigned.size() == 1 && assigned.get(0).getCount() == 1,
                "Expected one assigned Source Bucket in the inventory, got " + assigned);
        GameTestSupport.assertFluid(assigned.get(0), Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
        helper.succeed();
    }
    /**
     * No in-game form: fires a dispenser holding an empty Big Bucket at source water while the
     * automation player's build permission is withdrawn, and verifies the pickup still completes, as a
     * vanilla dispenser ignores player build permission.
     */
    static void automation_ignores_build_permission(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, dispenserPos, Direction.EAST, GameTestSupport.big8());
        helper.setBlock(TARGET, Blocks.WATER);

        withoutBuildPermission(BucketOperations.get().automationPlayer(helper.getLevel()), () -> {
            GameTestSupport.dispenseNow(helper, dispenserPos);
            return true;
        });

        GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    /**
     * In game: build a dispenser aimed at source water, move the world border so both are outside it,
     * and pulse the dispenser; the water and empty Big Bucket remain unchanged. After restoring the
     * border, the next pulse collects the water.
     */
    static void automation_outside_world_border_cannot_take_fluid(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, dispenserPos, Direction.EAST, GameTestSupport.big8());
        ItemStack before = dispenser.getItem(0).copy();
        helper.setBlock(TARGET, Blocks.WATER);

        dispenseOutsideWorldBorder(helper, dispenserPos);

        GameTestSupport.assertSameStack(before, dispenser.getItem(0), "Denied fluid edit mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        GameTestSupport.dispenseNow(helper, dispenserPos);
        GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    /**
     * In game: build a dispenser with an empty Source Bucket aimed at a full water cauldron, move the
     * world border so both are outside it, and pulse it; cauldron and bucket remain unchanged. After
     * restoring the border, the next pulse assigns the bucket to water and empties the cauldron.
     */
    static void automation_outside_world_border_cannot_use_cauldron(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, dispenserPos, Direction.EAST, GameTestSupport.source());
        ItemStack before = dispenser.getItem(0).copy();
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));

        dispenseOutsideWorldBorder(helper, dispenserPos);

        GameTestSupport.assertSameStack(before, dispenser.getItem(0), "Denied cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(TARGET).getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL,
                "Denied cauldron interaction changed fill level");
        GameTestSupport.dispenseNow(helper, dispenserPos);
        GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.CAULDRON);
        helper.succeed();
    }
    /**
     * In game: place dispensers holding pig- and cod-filled Mob Buckets, move the world border so their
     * targets are outside it, and pulse them; neither mob is released, no water appears, and both
     * buckets remain filled. After restoring the border, the next pulses release the pig and the cod
     * with its water.
     */
    static void automation_outside_world_border_cannot_release(GameTestHelper helper) {
        BlockPos codTarget = TARGET.east(2);
        BlockPos pigDispenserPos = TARGET.west();
        BlockPos codDispenserPos = codTarget.west();
        DispenserBlockEntity pigDispenser = GameTestSupport.dispenser(helper, pigDispenserPos, Direction.EAST,
                GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null));
        DispenserBlockEntity codDispenser = GameTestSupport.dispenser(helper, codDispenserPos, Direction.EAST,
                GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null));

        dispenseOutsideWorldBorder(helper, pigDispenserPos);
        dispenseOutsideWorldBorder(helper, codDispenserPos);

        GameTestSupport.check(BucketState.getEntityCount(pigDispenser.getItem(0)) == 1
                        && BucketState.getEntityCount(codDispenser.getItem(0)) == 1,
                "Denied release consumed a stored snapshot");
        GameTestSupport.check(GameTestSupport.entities(helper, Pig.class, TARGET, 0.75D).isEmpty(),
                "Denied release added a mob to the world");
        GameTestSupport.assertBlock(helper, codTarget, Blocks.AIR);
        GameTestSupport.dispenseNow(helper, pigDispenserPos);
        GameTestSupport.dispenseNow(helper, codDispenserPos);
        GameTestSupport.check(GameTestSupport.entities(helper, Pig.class, TARGET, 0.75D).size() == 1,
                "Dispenser did not release the pig inside the world border");
        GameTestSupport.assertBlock(helper, codTarget, Blocks.WATER);
        GameTestSupport.check(BucketState.getEntityCount(pigDispenser.getItem(0)) == 0
                        && BucketState.getEntityCount(codDispenser.getItem(0)) == 0,
                "Release inside the world border kept a stored snapshot");
        helper.succeed();
    }
    /**
     * In game: in adventure mode, use a water-filled Big Bucket without an applicable
     * {@code can_place_on} component on short grass; the grass and bucket remain unchanged.
     */
    static void player_without_build_permission_cannot_place_fluid(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.SHORT_GRASS);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.above().east(), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000));
        ItemStack before = player.getMainHandItem().copy();

        boolean acted = withoutBuildPermission(player, () -> GameTestSupport.clickBlock(player,
                InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, TARGET, Direction.UP)).consumesAction());

        GameTestSupport.check(!acted, "Player without build permission placed fluid");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Denied placement drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.SHORT_GRASS);
        helper.succeed();
    }
    /**
     * In game: in adventure mode, sneak-use a filled Junk Bucket without an applicable
     * {@code can_place_on} component on stone; no item is ejected beside the stone and the oldest
     * entry remains stored.
     */
    static void player_without_build_permission_cannot_eject(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 3);
        BucketState.setStoredItems(bucket, List.of(food));
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.STONE);

        boolean acted = withoutBuildPermission(player, () -> player.getMainHandItem().useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, TARGET, Direction.EAST)))
                .consumesAction());

        GameTestSupport.check(!acted, "Player without build permission ejected an item");
        GameTestSupport.assertStored(helper, player.getMainHandItem(), food);
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, TARGET.east(), 0.6D).isEmpty(),
                "Denied ejection dropped an item entity");
        helper.succeed();
    }
    /**
     * No in-game form: fires a dispenser at source water and verifies the pickup completes, the level's
     * automation player is stable and named, and it earns no statistic.
     */
    static void dispenser_acts_as_stable_automation_player(GameTestHelper helper) {
        ServerPlayer automationPlayer = BucketOperations.get().automationPlayer(helper.getLevel());
        GameTestSupport.check(automationPlayer == BucketOperations.get().automationPlayer(helper.getLevel()),
                "Automation player is not stable across lookups");
        GameTestSupport.check(BucketOperations.DISPENSER_PROFILE.equals(automationPlayer.getGameProfile()),
                "Automation player has the wrong profile: " + automationPlayer.getGameProfile());
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
     * In game: in adventure mode, use an empty Big Bucket without an applicable {@code can_place_on}
     * component on source water; world and bucket remain unchanged.
     */
    static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.big8());
        ItemStack before = player.getMainHandItem().copy();

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Adventure player collected fluid without CanPlaceOn permission");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Denied pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    /**
     * In game: move the world border so a nearby player and pig are outside it and use an empty Mob
     * Bucket on the pig; capture fails there but succeeds after restoring the border.
     */
    static void player_outside_world_border_cannot_capture_mob(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.mob());
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);

        InteractionResult denied = outsideWorldBorder(helper, () -> GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND));

        GameTestSupport.check(!denied.consumesAction(), "Player captured a mob outside the world border");
        GameTestSupport.check(pig.isAlive(), "Denied capture removed the pig");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.check(GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND).consumesAction() && !pig.isAlive(),
                "Player could not capture the pig inside the world border");
        GameTestSupport.check(BucketState.getEntityCount(player.getMainHandItem()) == 1,
                "Capture inside the world border stored nothing");
        helper.succeed();
    }
    /**
     * In game: move the world border so a nearby player and adult cow are outside it; neither an empty
     * Big nor Source Bucket milks the cow there, while the Big Bucket does after restoring the border.
     */
    static void player_outside_world_border_cannot_milk(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, TARGET);

        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());
        InteractionResult sourceDenied = outsideWorldBorder(helper, () -> GameTestSupport.interactWithEntity(player, cow, InteractionHand.MAIN_HAND));
        GameTestSupport.check(!sourceDenied.consumesAction(), "Source Bucket milked a cow outside the world border");
        GameTestSupport.assertEmpty(player.getMainHandItem());

        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.big8());
        InteractionResult bigDenied = outsideWorldBorder(helper, () -> GameTestSupport.interactWithEntity(player, cow, InteractionHand.MAIN_HAND));
        GameTestSupport.check(!bigDenied.consumesAction(), "Big Bucket milked a cow outside the world border");
        GameTestSupport.assertEmpty(player.getMainHandItem());

        GameTestSupport.check(GameTestSupport.interactWithEntity(player, cow, InteractionHand.MAIN_HAND).consumesAction(),
                "Big Bucket could not milk the cow inside the world border");
        GameTestSupport.assertMilk(player.getMainHandItem(), FluidBucketItem.BUCKET_VOLUME_MB);
        helper.succeed();
    }
    /**
     * In game: aim a dispenser containing an empty Source Bucket at an adult cow, move the world border
     * so they are outside it, and pulse it; the bucket stays empty there but assigns milk after the
     * border is restored.
     */
    static void automation_outside_world_border_cannot_milk(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, dispenserPos, Direction.EAST, GameTestSupport.source());
        GameTestSupport.spawn(helper, EntityType.COW, TARGET);

        dispenseOutsideWorldBorder(helper, dispenserPos);

        GameTestSupport.assertEmpty(dispenser.getItem(0));
        GameTestSupport.dispenseNow(helper, dispenserPos);
        GameTestSupport.assertMilk(dispenser.getItem(0), FluidBucketItem.BUCKET_VOLUME_MB);
        helper.succeed();
    }
    /**
     * In game: move the world border so a player and dropped item are outside it and use Junk and Trash
     * Buckets nearby; neither collects there, while each collects after the border is restored.
     */
    static void player_outside_world_border_cannot_vacuum_items(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingAtAir(helper, TARGET);

        for (ItemStack bucket : List.of(GameTestSupport.junk(), GameTestSupport.trash())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
            ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), TARGET);

            InteractionResult denied = outsideWorldBorder(helper,
                    () -> GameTestSupport.useItem(player, InteractionHand.MAIN_HAND));

            GameTestSupport.check(!denied.consumesAction(), bucket + " collected an item outside the world border");
            GameTestSupport.check(entity.isAlive() && entity.getItem().getCount() == 2,
                    bucket + " changed an item entity outside the world border");
            GameTestSupport.assertStored(helper, player.getMainHandItem());
            GameTestSupport.check(GameTestSupport.useItem(player, InteractionHand.MAIN_HAND).consumesAction()
                            && !entity.isAlive(),
                    bucket + " could not collect the item inside the world border");
        }
        helper.succeed();
    }
    /**
     * In game: move the world border so a player and adult pig are outside it and use a Junk Bucket
     * containing carrots on the pig; it is not fed there but enters love mode after the border is restored.
     */
    static void player_outside_world_border_cannot_feed_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3)));
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);

        InteractionResult denied = outsideWorldBorder(helper, () -> GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND));

        GameTestSupport.check(!denied.consumesAction(), "Junk Bucket fed an animal outside the world border");
        GameTestSupport.check(!pig.isInLove(), "Denied feeding put the pig in love mode");
        GameTestSupport.assertStored(helper, player.getMainHandItem(), new ItemStack(Items.CARROT, 3));
        GameTestSupport.check(GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND).consumesAction(),
                "Junk Bucket could not feed the pig inside the world border");
        GameTestSupport.check(pig.isInLove(), "Fed pig did not enter love mode");
        helper.succeed();
    }

    /**
     * In game: have player A tame a wolf, then have player B right-click it with an empty Mob Bucket;
     * nothing happens. The owner's control runs the bucket's own entity interaction rather than a whole
     * click, because a tamed wolf answers its owner's right-click itself.
     */
    static void player_cannot_capture_another_players_pet(GameTestHelper helper) {
        Player owner = GameTestSupport.survivalPlayer(helper, TARGET.west());
        Player other = GameTestSupport.survivalPlayer(helper, TARGET.east());
        Wolf wolf = GameTestSupport.spawn(helper, EntityType.WOLF, TARGET);
        wolf.tame(owner);
        other.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.mob());
        owner.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.mob());

        InteractionResult byOther = GameTestSupport.interactWithEntity(other, wolf, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!byOther.consumesAction(), "A player captured another player's pet");
        GameTestSupport.check(wolf.isAlive(), "Denied capture removed the pet");
        GameTestSupport.assertEmpty(other.getMainHandItem());
        GameTestSupport.check(owner.getMainHandItem()
                        .interactLivingEntity(owner, wolf, InteractionHand.MAIN_HAND).consumesAction()
                        && !wolf.isAlive(),
                "The owner could not capture their own pet");
        helper.succeed();
    }
    /**
     * In game: aim a dispenser with an empty Mob Bucket at a tamed wolf and pulse it; the wolf is not
     * captured. With an untamed pig beside the wolf, the next pulse captures the pig.
     */
    static void automation_cannot_capture_owned_pet(GameTestHelper helper) {
        Player owner = GameTestSupport.survivalPlayer(helper, TARGET.east());
        Wolf wolf = GameTestSupport.spawn(helper, EntityType.WOLF, TARGET);
        wolf.tame(owner);
        BlockPos dispenserPos = TARGET.west();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, dispenserPos, Direction.EAST, GameTestSupport.mob());

        GameTestSupport.dispenseNow(helper, dispenserPos);

        GameTestSupport.check(wolf.isAlive(), "Automation captured an owned pet");
        GameTestSupport.assertEmpty(dispenser.getItem(0));
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        GameTestSupport.dispenseNow(helper, dispenserPos);
        GameTestSupport.check(!pig.isAlive() && wolf.isAlive(),
                "Automation did not capture the unowned pig beside the pet");
        GameTestSupport.check(BucketState.getEntityCount(dispenser.getItem(0)) == 1,
                "Automation capture stored nothing");
        helper.succeed();
    }
    /**
     * In game: aim a dispenser containing a Junk Bucket of cod at an untamed cat and pulse it; the cat
     * is not fed or tamed and the cod remains stored.
     */
    static void automation_does_not_feed_untamed_cat(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.COD, 3)));
        Cat cat = GameTestSupport.spawn(helper, EntityType.CAT, TARGET);
        BlockPos dispenserPos = TARGET.west();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, dispenserPos, Direction.EAST, bucket);

        GameTestSupport.dispenseNow(helper, dispenserPos);

        GameTestSupport.check(!cat.isTame() && !cat.isInLove(), "Automation fed or tamed an untamed cat");
        GameTestSupport.assertStored(helper, dispenser.getItem(0), new ItemStack(Items.COD, 3));
        helper.succeed();
    }

    /**
     * Fires the dispenser at {@code relative} within {@link #outsideWorldBorder}, so the moved border
     * is in force when it dispenses.
     */
    private static void dispenseOutsideWorldBorder(GameTestHelper helper, BlockPos relative) {
        outsideWorldBorder(helper, () -> {
            GameTestSupport.dispenseNow(helper, relative);
            return null;
        });
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

    /**
     * Runs {@code action} with the level's world border moved far from every test structure, restoring
     * it before returning. The border is shared by every test in the level, so the change must never
     * outlive one synchronous call.
     */
    static <R> R outsideWorldBorder(GameTestHelper helper, Supplier<R> action) {
        WorldBorder border = helper.getLevel().getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        border.setCenter(origin.getX() + DISTANT_BORDER_OFFSET, origin.getZ() + DISTANT_BORDER_OFFSET);
        border.setSize(1.0D);
        try {
            return action.get();
        } finally {
            border.setSize(size);
            border.setCenter(centerX, centerZ);
        }
    }
}
