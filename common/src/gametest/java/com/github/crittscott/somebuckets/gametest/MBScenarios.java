package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
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
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        pig.setCustomName(Component.literal("Captured Pig"));
        pig.setHealth(7.0F);

        InteractionResult result = ((MBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

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
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);

        GameTestSupport.GameEventRecorder recorder = new GameTestSupport.GameEventRecorder(helper, target);
        recorder.add(helper.getLevel());
        InteractionResult result;
        try {
            result = ((MBItem) bucket.getItem()).interactLivingEntity(
                    bucket, player, cod, InteractionHand.MAIN_HAND);
        } finally {
            recorder.remove(helper.getLevel());
        }

        GameTestSupport.check(result.consumesAction(), "Aquatic Mob Bucket capture failed");
        GameTestSupport.check(!cod.isAlive(), "Captured cod remained alive");
        GameTestSupport.assertBlock(helper, target, Blocks.AIR);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Aquatic capture did not emit exactly one fluid-pickup game event");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
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
        ItemStack playerBucket = GameTestSupport.mob();
        player.setItemInHand(InteractionHand.MAIN_HAND, playerBucket);
        Pig playerPig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));

        GameTestSupport.check(GameTestSupport.firesFilledBucket(player, () ->
                        GameTestSupport.check(((MBItem) playerBucket.getItem()).interactLivingEntity(
                                        playerBucket, player, playerPig, InteractionHand.MAIN_HAND).consumesAction(),
                                "Player Mob Bucket capture failed")),
                "Player capture did not fire the filled-bucket criterion");

        ServerPlayer observer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above());
        ItemStack automationBucket = GameTestSupport.mob();
        Pig automationPig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 5));
        GameTestSupport.check(!GameTestSupport.firesFilledBucket(observer, () ->
                        GameTestSupport.check(MBItem.capture(automationBucket, automationPig,
                                        ProtectionContext.dispenser(helper.absolutePos(new BlockPos(3, 2, 5))),
                                        Direction.UP),
                                "Automation Mob Bucket capture failed")),
                "Automation capture fired a player filled-bucket criterion");

        ServerPlayer failedPlayer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above(2));
        ItemStack incompatibleBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        failedPlayer.setItemInHand(InteractionHand.MAIN_HAND, incompatibleBucket);
        Cow incompatibleCow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(5, 2, 5));
        GameTestSupport.check(!GameTestSupport.firesFilledBucket(failedPlayer, () ->
                        GameTestSupport.check(!((MBItem) incompatibleBucket.getItem()).interactLivingEntity(
                                        incompatibleBucket, failedPlayer, incompatibleCow, InteractionHand.MAIN_HAND)
                                        .consumesAction(),
                                "Incompatible Mob Bucket capture unexpectedly succeeded")),
                "Failed capture fired the filled-bucket criterion");
        GameTestSupport.check(incompatibleCow.isAlive(), "Failed capture removed the incompatible cow");
        helper.succeed();
    }

    /**
     * In game: right-click a Wither with an empty Mob Bucket; nothing happens and the Wither stays,
     * because it is in the {@code somebuckets:mb_blacklist} entity tag.
     */
    static void blacklisted_boss_is_not_capturable(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        WitherBoss wither = GameTestSupport.spawn(helper, EntityType.WITHER, new BlockPos(4, 2, 4));
        wither.setNoAi(true);

        InteractionResult result = ((MBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, wither, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Mob Bucket took the Wither interaction");
        GameTestSupport.check(wither.isAlive(), "Wither was captured despite the blacklist tag");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: with a pig riding a cow (summoned with /summon and a Passengers tag), right-click
     * either one with an empty Mob Bucket; neither is captured.
     */
    static void passenger_and_vehicle_are_not_capturable(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        Pig passenger = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        Cow vehicle = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(4, 2, 4));
        GameTestSupport.check(passenger.startRiding(vehicle, true), "Could not establish riding fixture");

        InteractionResult passengerResult = item.interactLivingEntity(
                bucket, player, passenger, InteractionHand.MAIN_HAND);
        InteractionResult vehicleResult = item.interactLivingEntity(
                bucket, player, vehicle, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!passengerResult.consumesAction() && passenger.isAlive(), "Passenger was captured");
        GameTestSupport.check(!vehicleResult.consumesAction() && vehicle.isAlive(), "Vehicle was captured");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: right-click eight pigs in turn with one Mob Bucket; each is captured. A ninth pig is
     * not, and stays in the world.
     */
    static void bucket_accepts_eight_same_type_and_rejects_ninth(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        GameTestSupport.check(MBItem.canAccept(bucket, EntityType.PIG),
                "Empty Mob Bucket rejected its first entity type");

        for (int i = 0; i <= MBItem.MAX_MOBS; i++) {
            Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
            InteractionResult result = item.interactLivingEntity(bucket, player, pig, InteractionHand.MAIN_HAND);
            if (i < MBItem.MAX_MOBS) {
                GameTestSupport.check(result.consumesAction(), "Capture " + (i + 1) + " did not succeed");
                GameTestSupport.check(!pig.isAlive(), "Captured pig " + (i + 1) + " remained alive");
                if (i == MBItem.MAX_MOBS - 2) {
                    GameTestSupport.check(BucketState.getEntityCount(bucket) == MBItem.MAX_MOBS - 1,
                            "Seventh capture did not establish the seven-mob boundary");
                    GameTestSupport.check(MBItem.canAccept(bucket, EntityType.PIG),
                            "Seven-mob bucket rejected its eighth matching mob");
                } else if (i == MBItem.MAX_MOBS - 1) {
                    GameTestSupport.check(BucketState.getEntityCount(bucket) == MBItem.MAX_MOBS,
                            "Eighth capture did not fill the Mob Bucket");
                    GameTestSupport.check(!MBItem.canAccept(bucket, EntityType.PIG),
                            "Full Mob Bucket accepted a ninth matching mob");
                }
            } else {
                GameTestSupport.check(!result.consumesAction(), "Ninth capture succeeded");
                GameTestSupport.check(pig.isAlive(), "Rejected ninth pig was removed");
            }
        }

        GameTestSupport.check(BucketState.getEntityCount(bucket) == MBItem.MAX_MOBS,
                "Expected eight stored pigs, got " + BucketState.getEntityCount(bucket));
        helper.succeed();
    }

    /**
     * In game: capture a pig with a Mob Bucket, then right-click a cow with it; the cow stays and
     * the bucket still holds only the pig.
     */
    static void bucket_rejects_different_entity_type(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(4, 2, 5));
        item.interactLivingEntity(bucket, player, pig, InteractionHand.MAIN_HAND);
        ItemStack before = bucket.copy();

        InteractionResult result = item.interactLivingEntity(bucket, player, cow, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Mob Bucket mixed entity types");
        GameTestSupport.check(cow.isAlive(), "Rejected cow was removed");
        GameTestSupport.assertSameStack(before, bucket, "Rejected different-type capture mutated bucket");
        helper.succeed();
    }

    /**
     * In game: capture a pig named "Remember Me" that has lost some health, then sneak-use the Mob
     * Bucket on the side of a block; the same pig, with its name and health, appears in front of that
     * face, and the bucket is empty again.
     */
    static void release_restores_state_and_uuid_and_normalizes(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        Pig original = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        original.setCustomName(Component.literal("Remember Me"));
        original.setHealth(6.0F);
        UUID originalUuid = original.getUUID();
        item.interactLivingEntity(bucket, player, original, InteractionHand.MAIN_HAND);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
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
     * "Times Used" rises by one. A release blocked by a solid block in front, or refused by a claim,
     * releases nothing and does not count.
     */
    static void player_release_stat_is_awarded_only_after_success(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack successfulBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, successfulBucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(successfulBucket.getItem()));

        InteractionResult success = ((MBItem) successfulBucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        GameTestSupport.check(success.consumesAction(), "Valid player Mob Bucket release failed");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(successfulBucket.getItem()))
                        == statBefore + 1,
                "Successful player release did not award exactly one Mob Bucket use");
        GameTestSupport.entities(helper, Pig.class, SPAWN, 0.75D).forEach(Entity::discard);

        ItemStack collisionBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, collisionBucket);
        helper.setBlock(SPAWN, Blocks.STONE);
        InteractionResult collision = ((MBItem) collisionBucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        GameTestSupport.check(!collision.consumesAction(), "Colliding player release succeeded");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(collisionBucket.getItem()))
                        == statBefore + 1,
                "Collision failure awarded a Mob Bucket use");

        ItemStack deniedBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, deniedBucket);
        helper.setBlock(SPAWN, Blocks.AIR);
        InteractionResult denied;
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, entity) ->
                        action != ProtectionAction.ENTITY_RELEASE)) {
            denied = ((MBItem) deniedBucket.getItem()).useOn(new UseOnContext(
                    player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        }
        GameTestSupport.check(!denied.consumesAction(), "Protection-denied player release succeeded");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(deniedBucket.getItem()))
                        == statBefore + 1,
                "Protection denial awarded a Mob Bucket use");
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
        String firstMarker = BucketState.copyFirstEntitySnapshot(bucket).getString("TestMarker");
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
        item.interactLivingEntity(bucket, player, cod, InteractionHand.MAIN_HAND);
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
     * A released mob that has no mob bucket of its own is made persistent, as vanilla does for a
     * bucketed fish. In game: capture a naturally spawned zombie, release it, walk more than 128
     * blocks away and back: it is still there.
     */
    static void released_non_bucketable_mob_does_not_despawn(GameTestHelper helper) {
        CompoundTag snapshot = GameTestSupport.mobSnapshot(helper.getLevel(), EntityType.ZOMBIE, null);
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
     * A mob whose saved data is too large for clients to receive in an item is not captured; the
     * fixture weighs a pig down with one heavy held item in place of a loaded inventory. In game:
     * load a chested donkey with stacks of long written books and try to bucket it: the action bar
     * says it carries too much and the donkey stays.
     */
    static void oversized_mob_is_not_captured(GameTestHelper helper) {
        RecordingPlayer player = new RecordingPlayer(helper);
        ItemStack bucket = GameTestSupport.mob();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        ItemStack ballast = new ItemStack(Items.DIRT);
        CompoundTag data = new CompoundTag();
        data.putString("Ballast", "x".repeat(MBItem.MAX_SNAPSHOT_BYTES));
        ballast.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        pig.setItemSlot(EquipmentSlot.MAINHAND, ballast);

        InteractionResult result = ((MBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Oversized pig capture succeeded");
        GameTestSupport.check(pig.isAlive() && !pig.isRemoved(), "Oversized pig was captured");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(player.actionBar.stream().anyMatch(message ->
                        message.getContents() instanceof TranslatableContents contents
                                && "message.somebuckets.mob_bucket.too_large".equals(contents.getKey())),
                "Oversized capture did not report its size: " + player.actionBar);
        helper.succeed();
    }

    /**
     * Control for {@link #oversized_mob_is_not_captured}: a pig holding an ordinary item is captured.
     * In game: a chested donkey carrying a few stacks of dirt goes into a Mob Bucket.
     */
    static void lightly_loaded_mob_is_captured(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        pig.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, 64));

        InteractionResult result = ((MBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction() && !pig.isAlive(), "Lightly loaded pig was not captured");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1, "Mob Bucket did not store the pig");
        helper.succeed();
    }

    /**
     * In game: hold a stack of 16 empty Mob Buckets and right-click a pig; the pig disappears, 15
     * empty Mob Buckets stay in hand, and one Mob Bucket holding the pig appears in the inventory.
     */
    static void stacked_empty_mob_buckets_capture_into_one(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.mob().copyWithCount(GameTestSupport.STACKED_EMPTIES));
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));

        InteractionResult result = player.interactOn(pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Mob Buckets did not capture the pig");
        GameTestSupport.check(!pig.isAlive(), "Captured pig remained alive");
        ItemStack filled = GameTestSupport.assertOneFilledFromStack(
                player, InteractionHand.MAIN_HAND, GameTestSupport.mob().getItem());
        GameTestSupport.check(BucketState.getEntityCount(filled) == 1, "Filled Mob Bucket did not hold one pig");
        helper.succeed();
    }

    /* A server player that records action-bar messages instead of sending them to a connection. */
    private static final class RecordingPlayer extends ServerPlayer {
        private final List<Component> actionBar = new ArrayList<>();

        private RecordingPlayer(GameTestHelper helper) {
            super(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "sb-recording-gametest"),
                    ClientInformation.createDefault());
            Vec3 position = Vec3.atCenterOf(helper.absolutePos(PLAYER_POS));
            setPos(position.x, position.y, position.z);
        }

        @Override
        public void displayClientMessage(Component message, boolean overlay) {
            if (overlay) actionBar.add(message);
        }
    }

    private static Player playerWith(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }
}
