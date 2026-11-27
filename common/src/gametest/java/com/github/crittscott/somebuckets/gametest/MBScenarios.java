package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

final class MBScenarios {
    private MBScenarios() {}
    private static final BlockPos PLAYER_POS = new BlockPos(3, 2, 4);
    private static final BlockPos CLICKED = new BlockPos(5, 2, 4);
    private static final BlockPos SPAWN = CLICKED.east();
    /**
     * In game: right-click a named, injured pig with an empty Mob Bucket; the pig disappears and the
     * bucket holds one pig.
     */
    static void eligible_mob_capture_stores_snapshot_and_discards_entity(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.mob());
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        pig.setCustomName(Component.literal("Captured Pig"));
        pig.setHealth(7.0F);

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

        ItemStack bucket = player.getMainHandItem();
        GameTestSupport.check(result.consumesAction(), "Eligible pig capture did not succeed");
        GameTestSupport.check(!pig.isAlive(), "Captured pig remained alive");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1, "Mob Bucket did not store one snapshot");
        GameTestSupport.check(BucketState.getCurrentEntityType(bucket) == EntityType.PIG,
                "Mob Bucket stored the wrong entity type");
        helper.succeed();
    }

    /**
     * In game: right-click a cod swimming in a one-block water source with an empty Mob Bucket; the
     * cod and its water source are both taken, as a vanilla bucket takes them, and a nearby sculk
     * sensor hears one fluid pickup.
     */
    static void aquatic_capture_uses_native_water_pickup_observability(GameTestHelper helper) {
        BlockPos target = new BlockPos(4, 2, 4);
        helper.setBlock(target, Blocks.WATER);
        Cod cod = GameTestSupport.spawn(helper, EntityType.COD, target);
        Player player = playerWith(helper, GameTestSupport.mob());

        GameTestSupport.GameEventRecorder recorder = new GameTestSupport.GameEventRecorder(helper, target);
        recorder.add(helper.getLevel());
        InteractionResult result;
        try {
            result = GameTestSupport.interactWithEntity(player, cod, InteractionHand.MAIN_HAND);
        } finally {
            recorder.remove(helper.getLevel());
        }

        GameTestSupport.check(result.consumesAction(), "Aquatic Mob Bucket capture failed");
        GameTestSupport.check(!cod.isAlive(), "Captured cod remained alive");
        GameTestSupport.assertBlock(helper, target, Blocks.AIR);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Aquatic capture did not emit exactly one fluid-pickup game event");
        GameTestSupport.check(BucketState.getEntityCount(player.getMainHandItem()) == 1,
                "Aquatic capture did not store the cod snapshot");
        helper.succeed();
    }

    /**
     * In game: capturing a pig with a Mob Bucket counts as filling a bucket for advancements that
     * track it, and a failed capture (a cow offered to a Mob Bucket holding a pig) does not. The
     * automation half has no in-game form: it guards that a dispenser capture credits no player.
     */
    static void player_capture_fires_filled_bucket_criterion_but_automation_does_not(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.mob());
        Pig playerPig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));

        GameTestSupport.check(GameTestSupport.firesFilledBucket(player, () ->
                        GameTestSupport.check(GameTestSupport.interactWithEntity(player, playerPig,
                                InteractionHand.MAIN_HAND).consumesAction(), "Player Mob Bucket capture failed")),
                "Player capture did not fire the filled-bucket criterion");

        ServerPlayer observer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above());
        ItemStack automationBucket = GameTestSupport.mob();
        Pig automationPig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 5));
        GameTestSupport.check(!GameTestSupport.firesFilledBucket(observer, () ->
                        GameTestSupport.check(MBItem.capture(automationBucket, automationPig,
                                        GameTestSupport.dispenserAt(helper, helper.absolutePos(new BlockPos(3, 2, 5))),
                                        Direction.UP),
                                "Automation Mob Bucket capture failed")),
                "Automation capture fired a player filled-bucket criterion");

        ServerPlayer failedPlayer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above(2));
        failedPlayer.setItemInHand(InteractionHand.MAIN_HAND,
                GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null));
        Cow incompatibleCow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(5, 2, 5));
        GameTestSupport.check(!GameTestSupport.firesFilledBucket(failedPlayer, () ->
                        GameTestSupport.check(!GameTestSupport.interactWithEntity(failedPlayer, incompatibleCow,
                                        InteractionHand.MAIN_HAND).consumesAction(),
                                "Incompatible Mob Bucket capture unexpectedly succeeded")),
                "Failed capture fired the filled-bucket criterion");
        GameTestSupport.check(incompatibleCow.isAlive(), "Failed capture removed the incompatible cow");
        helper.succeed();
    }
    /**
     * A held Mob Bucket captures before the mob's own right-click reaction, which for a villager
     * would open trading or shake its head. In game: right-click a villager with an empty Mob Bucket;
     * the villager goes into the bucket instead of opening trades.
     */
    static void villager_capture_precedes_trading(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack bucket = GameTestSupport.mob();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        Villager villager = GameTestSupport.spawn(helper, EntityType.VILLAGER, CLICKED);

        InteractionResult result = GameTestSupport.interactWithEntity(player, villager, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Mob Bucket did not take the villager interaction");
        GameTestSupport.check(!villager.isAlive(), "Captured villager remained in the world");
        GameTestSupport.check(BucketState.getEntityCount(player.getMainHandItem()) == 1,
                "Mob Bucket did not store the villager");
        helper.succeed();
    }
    /**
     * A spectator's entity interaction never captures, on either loader, even where the loader's
     * interaction event fires before vanilla's spectator check. No ordinary in-game form: a
     * spectator's client does not send the interaction; this guards against a modified client.
     */
    static void spectator_mob_bucket_does_not_capture(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        GameType previous = player.gameMode.getGameModeForPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.mob());
        Villager villager = GameTestSupport.spawn(helper, EntityType.VILLAGER, CLICKED);
        player.setGameMode(GameType.SPECTATOR);
        try {
            GameTestSupport.interactWithEntity(player, villager, InteractionHand.MAIN_HAND);
        } finally {
            player.setGameMode(previous);
        }

        GameTestSupport.check(villager.isAlive(), "Spectator captured the villager");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }
    /**
     * In game: right-click a Wither with an empty Mob Bucket; nothing happens and the Wither stays,
     * because it is in the {@code somebuckets:uncapturable} entity tag.
     */
    static void tagged_boss_is_not_capturable(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.mob());
        WitherBoss wither = GameTestSupport.spawn(helper, EntityType.WITHER, new BlockPos(4, 2, 4));
        wither.setNoAi(true);

        InteractionResult result = GameTestSupport.interactWithEntity(player, wither, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Mob Bucket took the Wither interaction");
        GameTestSupport.check(wither.isAlive(), "Wither was captured despite the uncapturable tag");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: with a pig riding a cow (summoned with /summon and a Passengers tag), right-click
     * either one with an empty Mob Bucket; neither is captured.
     */
    static void passenger_and_vehicle_are_not_capturable(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.mob());
        Pig passenger = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        Cow vehicle = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(4, 2, 4));
        GameTestSupport.check(passenger.startRiding(vehicle, true), "Could not establish riding fixture");

        GameTestSupport.interactWithEntity(player, passenger, InteractionHand.MAIN_HAND);
        GameTestSupport.interactWithEntity(player, vehicle, InteractionHand.MAIN_HAND);

        GameTestSupport.check(passenger.isAlive(), "Passenger was captured");
        GameTestSupport.check(vehicle.isAlive(), "Vehicle was captured");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }
    /**
     * In game: right-click eight pigs in turn with one Mob Bucket; each is captured. A ninth pig is
     * not, and stays in the world.
     */
    static void bucket_accepts_eight_same_type_and_rejects_ninth(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.mob());
        GameTestSupport.check(MBItem.canAccept(player.getMainHandItem(), EntityType.PIG),
                "Empty Mob Bucket rejected its first entity type");

        for (int i = 0; i < 9; i++) {
            Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
            InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);
            ItemStack bucket = player.getMainHandItem();
            if (i < 8) {
                GameTestSupport.check(result.consumesAction(), "Capture " + (i + 1) + " did not succeed");
                GameTestSupport.check(!pig.isAlive(), "Captured pig " + (i + 1) + " remained alive");
                if (i == 6) {
                    GameTestSupport.check(BucketState.getEntityCount(bucket) == 7,
                            "Seventh capture did not establish the seven-mob boundary");
                    GameTestSupport.check(MBItem.canAccept(bucket, EntityType.PIG),
                            "Seven-mob bucket rejected its eighth matching mob");
                } else if (i == 7) {
                    GameTestSupport.check(BucketState.getEntityCount(bucket) == 8,
                            "Eighth capture did not fill the Mob Bucket");
                    GameTestSupport.check(!MBItem.canAccept(bucket, EntityType.PIG),
                            "Full Mob Bucket accepted a ninth matching mob");
                }
            } else {
                GameTestSupport.check(!result.consumesAction(), "Ninth capture succeeded");
                GameTestSupport.check(pig.isAlive(), "Rejected ninth pig was removed");
            }
        }

        GameTestSupport.check(BucketState.getEntityCount(player.getMainHandItem()) == 8,
                "Expected eight stored pigs, got " + BucketState.getEntityCount(player.getMainHandItem()));
        helper.succeed();
    }

    /**
     * In game: capture a pig with a Mob Bucket, then right-click a cow with it; the cow stays and
     * the bucket still holds only the pig.
     */
    static void bucket_rejects_different_entity_type(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.mob());
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(4, 2, 5));
        GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);
        ItemStack before = player.getMainHandItem().copy();

        InteractionResult result = GameTestSupport.interactWithEntity(player, cow, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Mob Bucket mixed entity types");
        GameTestSupport.check(cow.isAlive(), "Rejected cow was removed");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Rejected different-type capture mutated bucket");
        helper.succeed();
    }

    /**
     * In game: capture a pig named "Remember Me" that has lost some health, then sneak-use the Mob
     * Bucket on the side of a block; the same pig, with its name and health, appears in front of that
     * face, and the bucket is empty again.
     */
    static void release_restores_state_and_uuid_and_normalizes(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.mob());
        Pig original = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        original.setCustomName(Component.literal("Remember Me"));
        original.setHealth(6.0F);
        UUID originalUuid = original.getUUID();
        GameTestSupport.interactWithEntity(player, original, InteractionHand.MAIN_HAND);
        ItemStack bucket = player.getMainHandItem();
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Mob Bucket release did not succeed");
        List<Pig> pigs = GameTestSupport.entities(helper, Pig.class, SPAWN, 0.75D);
        GameTestSupport.check(pigs.size() == 1, "Expected one released pig, got " + pigs.size());
        Pig released = pigs.get(0);
        GameTestSupport.check(originalUuid.equals(released.getUUID()), "Released pig lost captured UUID");
        GameTestSupport.check(released.hasCustomName()
                        && "Remember Me".equals(released.getCustomName().getString()),
                "Released pig lost custom name");
        GameTestSupport.check(Math.abs(released.getHealth() - 6.0F) < 0.001F,
                "Released pig lost saved health");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: with statistics open, sneak-use a Mob Bucket holding a pig on the side of a block; its
     * "Times Used" rises by one. A release blocked by a solid block in front, or made in adventure
     * mode, releases nothing and does not count.
     */
    static void player_release_stat_is_awarded_only_after_success(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack successfulBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, successfulBucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(successfulBucket.getItem()));

        InteractionResult success = successfulBucket.useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        GameTestSupport.check(success.consumesAction(), "Valid player Mob Bucket release failed");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(successfulBucket.getItem()))
                        == statBefore + 1,
                "Successful player release did not award exactly one Mob Bucket use");
        GameTestSupport.entities(helper, Pig.class, SPAWN, 0.75D).forEach(Entity::discard);

        ItemStack collisionBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, collisionBucket);
        helper.setBlock(SPAWN, Blocks.STONE);
        InteractionResult collision = collisionBucket.useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        GameTestSupport.check(!collision.consumesAction(), "Colliding player release succeeded");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(collisionBucket.getItem()))
                        == statBefore + 1,
                "Collision failure awarded a Mob Bucket use");

        ItemStack deniedBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, deniedBucket);
        helper.setBlock(SPAWN, Blocks.AIR);
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        InteractionResult denied = deniedBucket.useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        GameTestSupport.check(!denied.consumesAction(), "Protection-denied player release succeeded");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(deniedBucket.getItem()))
                        == statBefore + 1,
                "Protection denial awarded a Mob Bucket use");
        helper.succeed();
    }

    /**
     * In game: capture a pig, then sneak-use the Mob Bucket on the side of a block whose
     * neighboring space the pig cannot fit into (for example a one-block gap under a low ceiling).
     * Nothing is released, the pig stays in the bucket, and the action bar reads
     * "No room to release".
     */
    static void blocked_player_release_reports_no_room(GameTestHelper helper) {
        GameTestSupport.RecordingServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        ItemStack before = bucket.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.STONE);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(!result.consumesAction(), "Blocked player release succeeded");
        GameTestSupport.assertSameStack(before, bucket, "Blocked player release mutated bucket");
        GameTestSupport.check(player.actionBarMessages.size() == 1
                        && player.actionBarMessages.get(0).getContents()
                        instanceof TranslatableContents contents
                        && "message.somebuckets.mob_bucket.no_room".equals(contents.getKey()),
                "Blocked player release did not report no room: " + player.actionBarMessages);
        helper.succeed();
    }

    /**
     * No in-game form: a player cannot copy a stored mob while its original is still in the world.
     * Guards that releasing a snapshot whose UUID is already in use gives the released mob a new UUID
     * and leaves the existing mob alone.
     */
    static void release_replaces_uuid_that_is_already_in_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Pig existing = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(2, 2, 2));
        UUID duplicateUuid = existing.getUUID();
        CompoundTag snapshot = new CompoundTag();
        existing.saveWithoutId(snapshot);
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", snapshot);
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Mob Bucket release did not succeed");
        GameTestSupport.check(existing.isAlive(), "Existing pig was disturbed by UUID collision handling");
        List<Pig> releasedPigs = GameTestSupport.entities(helper, Pig.class, SPAWN, 0.75D);
        GameTestSupport.check(releasedPigs.size() == 1,
                "Expected one released pig, got " + releasedPigs.size());
        GameTestSupport.check(!duplicateUuid.equals(releasedPigs.get(0).getUUID()),
                "Released pig retained a UUID that was already in use");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: capture two pigs, then sneak-use the Mob Bucket against a block with another solid
     * block in front of the clicked face; nothing is released, and a later release still gives the
     * first pig captured.
     */
    static void failed_collision_preserves_snapshot_and_fifo_order(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        CompoundTag first = GameTestSupport.mobSnapshot(helper.getLevel(), EntityType.PIG, "first");
        CompoundTag second = GameTestSupport.mobSnapshot(helper.getLevel(), EntityType.PIG, "second");
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", first);
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", second);
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.STONE);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(!result.consumesAction(), "Mob released into colliding block");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 2, "Failed release lost a snapshot");
        String firstMarker = bucket.getOrCreateTag().getList(BucketState.ENTITIES, Tag.TAG_COMPOUND)
                .getCompound(0).getString("TestMarker");
        GameTestSupport.check("first".equals(firstMarker),
                "Failed release changed FIFO order; first marker is " + firstMarker);
        helper.succeed();
    }

    /**
     * In game: sneak-use a Mob Bucket holding a cod on the side of a block with air in front; a
     * water source appears there with the cod in it, and the bucket is empty.
     */
    static void aquatic_release_creates_water(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Aquatic mob release did not succeed");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, 0.75D).size() == 1,
                "Released cod was not present in created water");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: sneak-use a Mob Bucket holding a cod on the side of a block with another solid block
     * in front of the clicked face; no water is placed and the cod stays in the bucket.
     */
    static void aquatic_collision_failure_precedes_water_placement(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(!result.consumesAction(), "Aquatic mob released into a colliding block");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.STONE);
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Aquatic collision failure consumed the stored snapshot");
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, 0.75D).isEmpty(),
                "Aquatic collision failure added the cod");
        helper.succeed();
    }

    /**
     * In game: sneak-use a Mob Bucket holding a cod on the side of a block with a bottom slab in
     * front of the clicked face; the slab becomes waterlogged and the cod swims in it.
     */
    static void aquatic_release_waterlogs_native_liquid_container(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.OAK_SLAB);
        player.setShiftKeyDown(true);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Aquatic release did not waterlog the slab");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.OAK_SLAB);
        GameTestSupport.check(helper.getBlockState(SPAWN).getValue(BlockStateProperties.WATERLOGGED),
                "Aquatic release left the slab dry");
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, 0.75D).size() == 1,
                "Released cod was not present at the waterlogged target");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: sneak-use a Mob Bucket holding a cod on the side of a block with water already in
     * front of the clicked face; the cod is released into that water, and a sculk sensor hears an
     * entity placement but no fluid placement.
     */
    static void aquatic_release_into_existing_water_emits_no_fluid_event(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.WATER);
        player.setShiftKeyDown(true);

        GameTestSupport.GameEventRecorder recorder = new GameTestSupport.GameEventRecorder(helper, SPAWN);
        recorder.add(helper.getLevel());
        InteractionResult result;
        try {
            result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                    player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        } finally {
            recorder.remove(helper.getLevel());
        }

        GameTestSupport.check(result.consumesAction(), "Aquatic release into existing water failed");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 0,
                "Existing water produced a redundant fluid-place game event");
        GameTestSupport.check(recorder.count(GameEvent.ENTITY_PLACE) == 1,
                "Successful aquatic release did not emit exactly one entity-place game event");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: capture a cod, then release it by sneak-using the Mob Bucket on the side of a block
     * next to a sculk sensor; the sensor activates.
     */
    static void aquatic_release_activates_sculk_sensor(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        Cod cod = GameTestSupport.spawn(helper, EntityType.COD, new BlockPos(4, 2, 4));
        GameTestSupport.interactWithEntity(player, cod, InteractionHand.MAIN_HAND);
        helper.setBlock(CLICKED, Blocks.STONE);
        BlockPos sensorPos = SPAWN.east();
        helper.setBlock(sensorPos, Blocks.SCULK_SENSOR);
        player.setShiftKeyDown(true);

        InteractionResult result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Aquatic mob release did not succeed");
        helper.runAfterDelay(6L, () -> {
            helper.assertBlockProperty(sensorPos, SculkSensorBlock.PHASE, SculkSensorPhase.ACTIVE);
            helper.succeed();
        });
    }

    /**
     * In game: capture a pig, then release it by sneak-using the Mob Bucket on the side of a block
     * next to a sculk sensor; the sensor activates.
     */
    static void land_release_activates_sculk_sensor(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);
        helper.setBlock(CLICKED, Blocks.STONE);
        BlockPos sensorPos = SPAWN.east();
        helper.setBlock(sensorPos, Blocks.SCULK_SENSOR);
        player.setShiftKeyDown(true);

        InteractionResult result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Land mob release did not succeed");
        helper.runAfterDelay(6L, () -> {
            helper.assertBlockProperty(sensorPos, SculkSensorBlock.PHASE, SculkSensorPhase.ACTIVE);
            helper.succeed();
        });
    }

    /**
     * In game: hold an empty Mob Bucket in the main hand and a blue Bucket of Axolotl named
     * "Held Axolotl" in the offhand, then use the Mob Bucket on air. The Mob Bucket now holds one
     * axolotl, the offhand holds a Water Bucket, and releasing the axolotl shows a blue axolotl
     * with that name.
     */
    static void vanilla_mob_bucket_empties_into_mob_bucket(GameTestHelper helper) {
        Axolotl source = EntityType.AXOLOTL.create(helper.getLevel());
        GameTestSupport.check(source != null, "Could not create axolotl fixture");
        source.setVariant(Axolotl.Variant.BLUE);
        source.setCustomName(Component.literal("Held Axolotl"));
        ItemStack axolotlBucket = source.getBucketItemStack();
        source.saveToBucketTag(axolotlBucket);
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        player.setItemInHand(InteractionHand.OFF_HAND, axolotlBucket);

        InteractionResultHolder<ItemStack> result = ((MBItem) bucket.getItem()).use(
                helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.getResult().consumesAction(), "Bucket of Axolotl did not empty into Mob Bucket");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1, "Mob Bucket did not store one axolotl");
        GameTestSupport.check(BucketState.getCurrentEntityType(bucket) == EntityType.AXOLOTL,
                "Mob Bucket stored the wrong entity type");
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET),
                "Emptied Bucket of Axolotl did not become a Water Bucket");
        Axolotl stored = EntityType.AXOLOTL.create(helper.getLevel());
        GameTestSupport.check(stored != null, "Could not recreate stored axolotl");
        stored.load(BucketState.copyFirstEntitySnapshot(bucket));
        GameTestSupport.check(stored.getVariant() == Axolotl.Variant.BLUE, "Stored axolotl lost its variant");
        GameTestSupport.check(stored.hasCustomName()
                        && "Held Axolotl".equals(stored.getCustomName().getString()),
                "Stored axolotl lost its name");
        helper.succeed();
    }

    /**
     * In game: fill a Mob Bucket with a blue axolotl named "First" and then a gold one named
     * "Second". Hold a Water Bucket in the offhand and use the Mob Bucket on air. The offhand
     * becomes a Bucket of Axolotl named "First" that releases a blue axolotl, and the Mob Bucket
     * still holds "Second".
     */
    static void mob_bucket_fills_water_bucket_with_oldest_mob(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "minecraft:axolotl",
                axolotlSnapshot(helper, "First", Axolotl.Variant.BLUE));
        BucketState.addEntitySnapshot(bucket, "minecraft:axolotl",
                axolotlSnapshot(helper, "Second", Axolotl.Variant.GOLD));
        Player player = playerWith(helper, bucket);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));

        InteractionResultHolder<ItemStack> result = ((MBItem) bucket.getItem()).use(
                helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.getResult().consumesAction(), "Mob Bucket did not fill the Water Bucket");
        ItemStack filled = player.getOffhandItem();
        GameTestSupport.check(filled.is(Items.AXOLOTL_BUCKET), "Water Bucket became " + filled);
        GameTestSupport.check("First".equals(filled.getHoverName().getString()),
                "Bucket of Axolotl did not receive the oldest mob");
        Axolotl bucketed = EntityType.AXOLOTL.create(helper.getLevel());
        GameTestSupport.check(bucketed != null, "Could not recreate bucketed axolotl");
        bucketed.loadFromBucketTag(filled.getOrCreateTag());
        GameTestSupport.check(bucketed.getVariant() == Axolotl.Variant.BLUE,
                "Bucket of Axolotl lost the variant");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1, "Mob Bucket did not keep the second axolotl");
        helper.succeed();
    }

    /**
     * In game: capture two pigs named "First" and "Second" in a Mob Bucket, hold an empty Mob Bucket
     * in the offhand, and use the full one on air. The offhand bucket now releases "First", and the
     * used bucket still holds "Second".
     */
    static void mob_bucket_gives_oldest_mob_to_other_mob_bucket(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", GameTestSupport.mobSnapshot(helper.getLevel(), EntityType.PIG, "First"));
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", GameTestSupport.mobSnapshot(helper.getLevel(), EntityType.PIG, "Second"));
        Player player = playerWith(helper, bucket);
        player.setItemInHand(InteractionHand.OFF_HAND, GameTestSupport.mob());

        InteractionResultHolder<ItemStack> result = ((MBItem) bucket.getItem()).use(
                helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.getResult().consumesAction(), "Mob Bucket did not give to the other Mob Bucket");
        ItemStack receiver = player.getOffhandItem();
        GameTestSupport.check(BucketState.getEntityCount(receiver) == 1
                        && "First".equals(BucketState.copyFirstEntitySnapshot(receiver).getString("TestMarker")),
                "Other Mob Bucket did not receive the oldest pig unchanged");
        ItemStack giver = player.getMainHandItem();
        GameTestSupport.check(BucketState.getEntityCount(giver) == 1
                        && "Second".equals(BucketState.copyFirstEntitySnapshot(giver).getString("TestMarker")),
                "Used Mob Bucket did not keep the second pig");
        helper.succeed();
    }

    /**
     * In game: hold an empty Mob Bucket in the main hand and a Mob Bucket holding a pig in the
     * offhand, then use the empty one on air. The pig moves into the main-hand bucket.
     */
    static void empty_mob_bucket_takes_mob_from_other_mob_bucket(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        ItemStack other = GameTestSupport.mob();
        BucketState.addEntitySnapshot(other, "minecraft:pig", GameTestSupport.mobSnapshot(helper.getLevel(), EntityType.PIG, "Taken"));
        player.setItemInHand(InteractionHand.OFF_HAND, other);

        InteractionResultHolder<ItemStack> result = ((MBItem) bucket.getItem()).use(
                helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.getResult().consumesAction(), "Empty Mob Bucket did not take the pig");
        ItemStack held = player.getMainHandItem();
        GameTestSupport.check(BucketState.getEntityCount(held) == 1
                        && "Taken".equals(BucketState.copyFirstEntitySnapshot(held).getString("TestMarker")),
                "Used Mob Bucket did not receive the pig");
        GameTestSupport.assertEmpty(player.getOffhandItem());
        helper.succeed();
    }

    /**
     * In game: hold a Mob Bucket holding a pig in the main hand and a stack of three empty Mob
     * Buckets in the offhand, then use the full one on air. One bucket holding the pig goes to your
     * inventory, two empties stay in the offhand, and the used bucket is empty.
     */
    static void mob_bucket_gives_into_one_of_stacked_empty_mob_buckets(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        Player player = playerWith(helper, bucket);
        ItemStack empties = GameTestSupport.mob();
        empties.setCount(3);
        player.setItemInHand(InteractionHand.OFF_HAND, empties);

        InteractionResultHolder<ItemStack> result = ((MBItem) bucket.getItem()).use(
                helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.getResult().consumesAction(), "Mob Bucket did not give to stacked empties");
        ItemStack offhand = player.getOffhandItem();
        GameTestSupport.check(offhand.getCount() == 2 && BucketState.isEmptyBucket(offhand),
                "Expected two empty Mob Buckets in the offhand, got " + offhand + " " + offhand.getTag());
        GameTestSupport.assertEmpty(player.getMainHandItem());
        ItemStack filled = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(bucket.getItem()) && BucketState.getEntityCount(stack) == 1) filled = stack;
        }
        GameTestSupport.check(filled.getCount() == 1, "The filled Mob Bucket did not reach the inventory");
        helper.succeed();
    }

    /**
     * In game: each of these held pairs does nothing when the Mob Bucket is used on air: a Mob
     * Bucket of pigs with a Bucket of Cod, a Mob Bucket holding eight cod with a Bucket of Cod,
     * without Upgrade Aquatic a Mob Bucket of squid with a Water Bucket, a Mob Bucket of cod with an
     * empty Bucket, an empty Mob Bucket with a Water Bucket, a Mob Bucket of pigs with a Mob Bucket
     * of cod, and two empty Mob Buckets.
     */
    static void mob_bucket_held_exchange_refuses_incompatible_pairs(GameTestHelper helper) {
        Level level = helper.getLevel();
        assertHeldExchangeRefused(helper, GameTestSupport.storedMob(level, EntityType.PIG, null), GameTestSupport.storedMob(level, EntityType.COD, null),
                "pigs with a Mob Bucket of cod");
        assertHeldExchangeRefused(helper, GameTestSupport.mob(), GameTestSupport.mob(),
                "two empty Mob Buckets");
        assertHeldExchangeRefused(helper, GameTestSupport.storedMob(level, EntityType.PIG, null), new ItemStack(Items.COD_BUCKET),
                "pigs with a Bucket of Cod");
        ItemStack full = GameTestSupport.mob();
        for (int i = 0; i < 8; i++) GameTestSupport.addSnapshot(full, level, EntityType.COD);
        assertHeldExchangeRefused(helper, full, new ItemStack(Items.COD_BUCKET),
                "a full bucket of cod with a Bucket of Cod");
        assertHeldExchangeRefused(helper, GameTestSupport.storedMob(level, EntityType.SQUID, null), new ItemStack(Items.WATER_BUCKET),
                "squid with a Water Bucket");
        assertHeldExchangeRefused(helper, GameTestSupport.storedMob(level, EntityType.COD, null), new ItemStack(Items.BUCKET),
                "cod with an empty Bucket");
        assertHeldExchangeRefused(helper, GameTestSupport.mob(), new ItemStack(Items.WATER_BUCKET),
                "an empty Mob Bucket with a Water Bucket");
        helper.succeed();
    }

    /**
     * In game: tie a pig to a fence post with a lead and right-click it with an empty Mob Bucket; the
     * pig is not captured and stays tied to the post.
     */
    static void leashed_mob_is_not_capturable(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.mob());
        BlockPos fence = new BlockPos(4, 2, 6);
        helper.setBlock(fence, Blocks.OAK_FENCE);
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), helper.absolutePos(fence));
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        pig.setLeashedTo(knot, true);

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Mob Bucket took the leashed pig interaction");
        GameTestSupport.check(pig.isAlive(), "Leashed pig was captured");
        GameTestSupport.check(pig.getLeashHolder() == knot, "Leashed pig left its fence post");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: right-click someone else's tamed wolf with an empty Mob Bucket and nothing happens;
     * the wolf's owner can capture it.
     */
    static void owned_mob_is_capturable_only_by_its_owner(GameTestHelper helper) {
        Player stranger = playerWith(helper, GameTestSupport.mob());
        Player owner = playerWith(helper, GameTestSupport.mob());
        Wolf wolf = GameTestSupport.spawn(helper, EntityType.WOLF, new BlockPos(4, 2, 4));
        wolf.tame(owner);

        InteractionResult refused = GameTestSupport.interactWithEntity(stranger, wolf, InteractionHand.MAIN_HAND);
        GameTestSupport.check(!refused.consumesAction() && wolf.isAlive(), "A stranger captured an owned wolf");
        GameTestSupport.assertEmpty(stranger.getMainHandItem());

        InteractionResult captured = GameTestSupport.interactWithEntity(owner, wolf, InteractionHand.MAIN_HAND);
        GameTestSupport.check(captured.consumesAction() && !wolf.isAlive(), "The owner could not capture their wolf");
        GameTestSupport.check(BucketState.getEntityCount(owner.getMainHandItem()) == 1, "Owner capture stored nothing");
        helper.succeed();
    }

    /**
     * In game: hold a stack of three empty Mob Buckets and capture a pig; one bucket holds the pig and
     * goes to your inventory, and two empties stay in your hand.
     */
    static void stack_of_empty_mob_buckets_fills_one(GameTestHelper helper) {
        ItemStack empties = GameTestSupport.mob();
        empties.setCount(3);
        Player player = playerWith(helper, empties);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction() && !pig.isAlive(), "Stacked Mob Buckets did not capture");
        ItemStack held = player.getMainHandItem();
        GameTestSupport.check(held.getCount() == 2 && BucketState.isEmptyBucket(held),
                "Expected two empty Mob Buckets in hand, got " + held + " " + held.getTag());
        ItemStack filled = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(held.getItem()) && BucketState.getEntityCount(stack) == 1) filled = stack;
        }
        GameTestSupport.check(filled.getCount() == 1, "The filled Mob Bucket did not reach the inventory");
        helper.succeed();
    }

    /**
     * A released mob that has no mob bucket of its own is made persistent, as vanilla does for a
     * bucketed fish. In game: capture a natural zombie, release it, walk more than 128 blocks away
     * and back: it is still there.
     */
    static void released_non_bucketable_mob_does_not_despawn(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        GameTestSupport.check(zombie != null, "Could not create zombie fixture");
        CompoundTag snapshot = new CompoundTag();
        zombie.saveWithoutId(snapshot);
        GameTestSupport.check(!snapshot.getBoolean("PersistenceRequired"),
                "Zombie fixture was already persistent");
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "minecraft:zombie", snapshot);
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(player,
                InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Zombie release did not succeed");
        List<Zombie> zombies = GameTestSupport.entities(helper, Zombie.class, SPAWN, 0.75D);
        GameTestSupport.check(zombies.size() == 1, "Expected one released zombie, got " + zombies.size());
        GameTestSupport.check(zombies.get(0).isPersistenceRequired(), "Released zombie can despawn");
        helper.succeed();
    }

    /**
     * A mob whose saved data is too large for clients to receive in an item is not captured. In game:
     * load a chested donkey with long written books and try to bucket it: the action bar says it
     * carries too much and the donkey stays.
     */
    static void oversized_mob_is_not_captured(GameTestHelper helper) {
        GameTestSupport.RecordingServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack bucket = GameTestSupport.mob();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        Donkey donkey = chestedDonkey(helper, longBook(), longBook());

        GameTestSupport.interactWithEntity(player, donkey, InteractionHand.MAIN_HAND);

        GameTestSupport.check(donkey.isAlive() && !donkey.isRemoved(), "Oversized donkey was captured");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.check(player.actionBarMessages.stream().anyMatch(message ->
                        message.getContents() instanceof TranslatableContents contents
                                && "message.somebuckets.mob_bucket.too_large".equals(contents.getKey())),
                "Oversized capture did not report its size: " + player.actionBarMessages);
        helper.succeed();
    }

    /**
     * Control for {@link #oversized_mob_is_not_captured}. In game: a chested donkey carrying a few
     * stacks of dirt goes into a Mob Bucket.
     */
    static void lightly_loaded_chested_donkey_is_captured(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.mob());
        Donkey donkey = chestedDonkey(helper, new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64));

        GameTestSupport.interactWithEntity(player, donkey, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!donkey.isAlive(), "Lightly loaded donkey was not captured");
        GameTestSupport.check(BucketState.getEntityCount(player.getMainHandItem()) == 1,
                "Mob Bucket did not store the donkey");
        helper.succeed();
    }

    /**
     * A tropical fish keeps its bucket's pattern through a held intake and a release. In game: hold
     * a Mob Bucket and, in the other hand, a Bucket of Tropical Fish caught in the world; use the
     * Mob Bucket on air, then release the fish: it has the same pattern and colors.
     */
    static void tropical_fish_bucket_variant_survives_intake_and_release(GameTestHelper helper) {
        int variant = (3 << 16) | (9 << 24) | 1;
        ItemStack fishBucket = new ItemStack(Items.TROPICAL_FISH_BUCKET);
        fishBucket.getOrCreateTag().putInt("BucketVariantTag", variant);
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        player.setItemInHand(InteractionHand.OFF_HAND, fishBucket);

        InteractionResultHolder<ItemStack> intake = ((MBItem) bucket.getItem()).use(
                helper.getLevel(), player, InteractionHand.MAIN_HAND);
        GameTestSupport.check(intake.getResult().consumesAction(), "Bucket of Tropical Fish did not empty");
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);
        InteractionResult release = ((MBItem) bucket.getItem()).useOn(new UseOnContext(player,
                InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(release.consumesAction(), "Tropical fish release did not succeed");
        List<TropicalFish> fish = GameTestSupport.entities(helper, TropicalFish.class, SPAWN, 0.75D);
        GameTestSupport.check(fish.size() == 1, "Expected one released tropical fish, got " + fish.size());
        ItemStack rebucketed = new ItemStack(Items.TROPICAL_FISH_BUCKET);
        fish.get(0).saveToBucketTag(rebucketed);
        GameTestSupport.check(rebucketed.getOrCreateTag().getInt("BucketVariantTag") == variant,
                "Released tropical fish lost its bucket's pattern");
        helper.succeed();
    }

    private static Donkey chestedDonkey(GameTestHelper helper, ItemStack... cargo) {
        Donkey donkey = EntityType.DONKEY.create(helper.getLevel());
        GameTestSupport.check(donkey != null, "Could not create donkey fixture");
        CompoundTag tag = new CompoundTag();
        donkey.saveWithoutId(tag);
        tag.putBoolean("ChestedHorse", true);
        ListTag items = new ListTag();
        for (int i = 0; i < cargo.length; i++) {
            CompoundTag slot = cargo[i].save(new CompoundTag());
            // Slots 0 and 1 are the saddle and armor; the chest starts at 2.
            slot.putByte("Slot", (byte) (2 + i));
            items.add(slot);
        }
        tag.put("Items", items);
        donkey.load(tag);
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(CLICKED));
        donkey.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        GameTestSupport.check(helper.getLevel().addFreshEntity(donkey), "Could not add donkey fixture");
        return donkey;
    }

    /* A book and quill whose pages alone exceed the Mob Bucket's snapshot size limit. */
    private static ItemStack longBook() {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        ListTag pages = new ListTag();
        String page = "x".repeat(1000);
        for (int i = 0; i < MBItem.MAX_SNAPSHOT_BYTES / page.length() + 1; i++) pages.add(StringTag.valueOf(page));
        book.getOrCreateTag().put("pages", pages);
        return book;
    }

    private static void assertHeldExchangeRefused(GameTestHelper helper, ItemStack bucket, ItemStack other,
                                                  String pair) {
        Player player = playerWith(helper, bucket);
        player.setItemInHand(InteractionHand.OFF_HAND, other);
        ItemStack bucketBefore = bucket.copy();
        ItemStack otherBefore = other.copy();

        InteractionResultHolder<ItemStack> result = ((MBItem) bucket.getItem()).use(
                helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.getResult().consumesAction(), "Held exchange accepted " + pair);
        GameTestSupport.assertSameStack(bucketBefore, player.getMainHandItem(),
                "Refused exchange of " + pair + " changed the Mob Bucket");
        GameTestSupport.assertSameStack(otherBefore, player.getOffhandItem(),
                "Refused exchange of " + pair + " changed the other hand");
    }

    private static CompoundTag axolotlSnapshot(GameTestHelper helper, String name, Axolotl.Variant variant) {
        Axolotl axolotl = EntityType.AXOLOTL.create(helper.getLevel());
        GameTestSupport.check(axolotl != null, "Could not create axolotl snapshot fixture");
        axolotl.setVariant(variant);
        axolotl.setCustomName(Component.literal(name));
        CompoundTag tag = new CompoundTag();
        axolotl.saveWithoutId(tag);
        return tag;
    }

    private static Player playerWith(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }
}
