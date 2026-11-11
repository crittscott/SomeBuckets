package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Cross-loader Mob Bucket scenarios; each loader wraps every method as a GameTest. */
final class MBScenarios {
    private MBScenarios() {}
    private static final BlockPos PLAYER_POS = new BlockPos(3, 2, 4);
    private static final BlockPos CLICKED = new BlockPos(5, 2, 4);
    private static final BlockPos SPAWN = CLICKED.east();

    @FunctionalInterface
    interface SpawnVeto {
        void run(UUID storedUuid, Runnable action);
    }

    static void rejected_aquatic_spawn_preserves_committed_water_and_snapshot(
            GameTestHelper helper, SpawnVeto vetoSpawn) {
        ItemStack bucket = storedCod(helper.getLevel());
        UUID storedUuid = BucketState.copyFirstEntitySnapshot(bucket).read("UUID", UUIDUtil.CODEC).orElseThrow();
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult[] result = new InteractionResult[1];
        vetoSpawn.run(storedUuid, () -> result[0] = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST))));

        GameTestSupport.check(!result[0].consumesAction(), "Rejected cod insertion reported success");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Rejected cod insertion consumed the stored snapshot");
        GameTestSupport.check(entitiesAt(helper, Cod.class, SPAWN).isEmpty(),
                "Entity-join cancellation still added the cod");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem())) == statBefore,
                "Rejected entity insertion awarded a Mob Bucket use");
        helper.succeed();
    }
    /**
     * Manual: configure a protection mod to deny fluid placement, then sneak-use a cod-filled Mob Bucket
     * against a block there; no water appears, no cod is released, and the cod stays in the bucket.
     * Automation: drives the release through the loader's item-use hook, cancels its place event, and
     * verifies one event reporting water, then an unchanged world and bucket.
     */
    static void cancelled_place_check_keeps_aquatic_mob_in_bucket(
            GameTestHelper helper, ProtectionScenarios.ScopedPlaceDenial denyingPlacements) {
        ItemStack bucket = storedCod(helper.getLevel());
        ItemStack before = bucket.copy();
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);
        List<ProtectionScenarios.DeniedPlacement> denied = new java.util.ArrayList<>();

        InteractionResult[] result = new InteractionResult[1];
        denyingPlacements.run(denied, () -> result[0] = bucket.useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST))));

        GameTestSupport.check(!result[0].consumesAction(), "A cancelled place check allowed the release");
        GameTestSupport.check(denied.equals(List.of(new ProtectionScenarios.DeniedPlacement(
                        helper.absolutePos(SPAWN), Blocks.WATER.defaultBlockState()))),
                "Expected one place check reporting release water, got " + denied);
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.AIR);
        GameTestSupport.check(entitiesAt(helper, Cod.class, SPAWN).isEmpty(),
                "A cancelled place check still released the cod");
        GameTestSupport.assertSameStack(before, bucket, "A cancelled release changed the bucket");
        helper.succeed();
    }
    /**
     * Manual: use an empty Mob Bucket on an eligible mob; the mob vanishes and its type and state appear
     * in the bucket.
     */
    static void eligible_mob_capture_stores_snapshot_and_discards_entity(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityTypes.PIG, new BlockPos(4, 2, 4));
        pig.setCustomName(Component.literal("Captured Pig"));
        pig.setHealth(7.0F);

        InteractionResult result = ((MBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Eligible pig capture did not succeed");
        GameTestSupport.check(!pig.isAlive(), "Captured pig remained alive");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1, "Mob Bucket did not store one snapshot");
        GameTestSupport.check(BucketState.getCurrentEntityType(bucket) == EntityTypes.PIG,
                "Mob Bucket stored the wrong entity type");
        CompoundTag snapshot = BucketState.copyFirstEntitySnapshot(bucket);
        GameTestSupport.check(snapshot.contains("CustomName"), "Snapshot did not keep the pig's name");
        GameTestSupport.check(snapshot.getFloat("Health").filter(health -> health == 7.0F).isPresent(),
                "Snapshot did not keep the pig's health: " + snapshot.getFloat("Health"));
        helper.succeed();
    }

    /**
     * Manual: try a Mob Bucket on a small sulfur cube. The bucket stays empty. Grow the cube
     * with a slime ball, feed it stone, and capture it on dry land. Save and reopen the
     * world, then release it. No water appears, and shearing it returns the stone.
     */
    static void sulfur_cube_round_trips_on_dry_land_with_absorbed_block(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        BlockPos target = new BlockPos(4, 2, 4);
        SulfurCube cube = GameTestSupport.spawn(helper, EntityTypes.SULFUR_CUBE, target);
        cube.setBaby(true);
        InteractionResult rejected = ((MBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, cube, InteractionHand.MAIN_HAND);
        GameTestSupport.check(!rejected.consumesAction() && cube.isAlive(),
                "Mob Bucket captured a small sulfur cube");
        GameTestSupport.assertEmpty(bucket);
        cube.setBaby(false);
        GameTestSupport.check(HeldTransfers.interactHolding(player, InteractionHand.MAIN_HAND,
                        new ItemStack(Items.STONE), cube).result().consumesAction(),
                "Sulfur cube did not absorb the stone");

        InteractionResult captured = ((MBItem) bucket.getItem()).interactLivingEntity(
                bucket, player, cube, InteractionHand.MAIN_HAND);
        GameTestSupport.check(captured.consumesAction() && !cube.isAlive(),
                "Mob Bucket could not capture a sulfur cube on dry land");
        GameTestSupport.check(BucketState.getCurrentEntityType(bucket) == EntityTypes.SULFUR_CUBE,
                "Mob Bucket did not store a sulfur cube");
        GameTestSupport.assertBlock(helper, target, Blocks.AIR);

        bucket = GameTestSupport.loadStack(helper, GameTestSupport.saveStack(helper, bucket));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(SPAWN.below(), Blocks.STONE);
        player.setShiftKeyDown(true);
        InteractionResult released = bucket.useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, SPAWN.below(), Direction.UP)));
        GameTestSupport.check(released.consumesAction(), "Mob Bucket could not release the sulfur cube");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.AIR);
        List<SulfurCube> cubes = entitiesAt(helper, SulfurCube.class, SPAWN);
        GameTestSupport.check(cubes.size() == 1, "Expected one released sulfur cube");
        player.setShiftKeyDown(false);
        HeldTransfers.interactHolding(player, InteractionHand.MAIN_HAND,
                new ItemStack(Items.SHEARS), cubes.getFirst());
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, SPAWN, 2.0).stream()
                        .anyMatch(drop -> drop.getItem().is(Items.STONE)),
                "Shearing the released sulfur cube did not return the absorbed stone");
        helper.succeed();
    }

    /**
     * Manual: feed TNT to a large sulfur cube and light it with a dispenser holding flint and
     * steel. Try a Mob Bucket by hand and from a dispenser while the TNT is lit. The cube remains
     * in the world and neither bucket gains a mob.
     */
    static void primed_sulfur_cube_refuses_player_and_dispenser_capture(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        helper.setBlock(SPAWN.below(), Blocks.STONE);
        SulfurCube cube = GameTestSupport.spawn(helper, EntityTypes.SULFUR_CUBE, SPAWN);
        cube.setBaby(false);
        GameTestSupport.check(HeldTransfers.interactHolding(player, InteractionHand.MAIN_HAND,
                        new ItemStack(Items.TNT), cube).result().consumesAction(),
                "Sulfur cube did not absorb the TNT");
        var dispenser = GameTestSupport.dispenser(helper, CLICKED, Direction.EAST,
                new ItemStack(Items.FLINT_AND_STEEL));
        GameTestSupport.triggerDispenser(helper, CLICKED);

        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(cube.isPrimed(), "Flint and steel did not prime the sulfur cube");
            InteractionResult result = ((MBItem) bucket.getItem()).interactLivingEntity(
                    bucket, player, cube, InteractionHand.MAIN_HAND);
            GameTestSupport.check(!result.consumesAction() && cube.isAlive(),
                    "Player captured a sulfur cube with primed TNT");
            GameTestSupport.assertEmpty(bucket);
            GameTestSupport.check(entitiesAt(helper, SulfurCube.class, SPAWN).contains(cube),
                    "Primed sulfur cube moved away from the dispenser");
            dispenser.setItem(0, GameTestSupport.mob());
            GameTestSupport.triggerDispenser(helper, CLICKED);

            helper.runAfterDelay(8L, () -> {
                GameTestSupport.check(cube.isAlive() && cube.isPrimed(),
                        "Dispenser removed or defused a sulfur cube with primed TNT");
                GameTestSupport.check(dispenser.getItem(0).is(bucket.getItem()),
                        "Dispenser ejected the empty Mob Bucket");
                GameTestSupport.assertEmpty(dispenser.getItem(0));
                helper.succeed();
            });
        });
    }

    /**
     * Manual: name and harness an adult Happy Ghast, capture it, save and reload the world, then
     * release it into open space. Its name, health, UUID, and harness survive; the bucket empties.
     */
    static void happy_ghast_survives_bucket_persistence_and_release(GameTestHelper helper) {
        BlockPos target = new BlockPos(4, 2, 4);
        var original = GameTestSupport.spawn(helper, EntityTypes.HAPPY_GHAST, target);
        original.setCustomName(Component.literal("Cloud Rider"));
        original.setHealth(6.0F);
        Item blueHarness = GameTestSupport.requireVanillaItem("blue_harness");
        original.setItemSlot(EquipmentSlot.BODY, new ItemStack(blueHarness));
        UUID uuid = original.getUUID();
        ItemStack bucket = GameTestSupport.mob();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(0, 2, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        ProtectionContext context = ProtectionContext.player(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(MBItem.capture(bucket, original, context, Direction.UP),
                "Happy Ghast capture failed");
        GameTestSupport.check(!original.isAlive(), "Captured Happy Ghast remained alive");
        ItemStack restoredBucket = GameTestSupport.loadStack(helper, GameTestSupport.saveStack(helper, bucket));
        player.setItemInHand(InteractionHand.MAIN_HAND, restoredBucket);
        GameTestSupport.check(MBItem.releaseOldest(helper.getLevel(), helper.absolutePos(target),
                        restoredBucket, context, Direction.UP),
                "Persisted Happy Ghast could not be released");

        var released = entitiesAt(helper, original.getClass(), target);
        GameTestSupport.check(released.size() == 1, "Expected one released Happy Ghast");
        var ghast = released.get(0);
        GameTestSupport.check(uuid.equals(ghast.getUUID()), "Happy Ghast lost its UUID");
        GameTestSupport.check(ghast.hasCustomName() && "Cloud Rider".equals(ghast.getCustomName().getString()),
                "Happy Ghast lost its name");
        GameTestSupport.check(Math.abs(ghast.getHealth() - 6.0F) < 0.001F, "Happy Ghast lost its health");
        GameTestSupport.check(ghast.getItemBySlot(EquipmentSlot.BODY).is(blueHarness),
                "Happy Ghast lost its harness");
        GameTestSupport.assertEmpty(restoredBucket);
        helper.succeed();
    }

    /**
     * Manual: while another player rides a harnessed Happy Ghast, try capturing it with an empty
     * Mob Bucket. The ghast and its rider remain together and the bucket stays empty.
     */
    static void ridden_happy_ghast_capture_preserves_ghast_rider_and_bucket(GameTestHelper helper) {
        var ghast = GameTestSupport.spawn(helper, EntityTypes.HAPPY_GHAST, new BlockPos(4, 2, 4));
        ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(GameTestSupport.requireVanillaItem("blue_harness")));
        Player rider = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 2, 4));
        GameTestSupport.check(rider.startRiding(ghast, true, false), "Could not mount Happy Ghast fixture");
        ItemStack bucket = GameTestSupport.mob();
        Player actor = GameTestSupport.survivalPlayer(helper, new BlockPos(0, 2, 0));
        actor.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult result = ((MBItem) bucket.getItem()).interactLivingEntity(
                bucket, actor, ghast, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Ridden Happy Ghast was captured");
        GameTestSupport.check(ghast.isAlive() && rider.getVehicle() == ghast,
                "Refused capture changed the Happy Ghast or its rider");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * Automation-only: gives a stored cod a malformed health field and tries releasing it into air.
     * A reported entity-load error preserves the complete snapshot and places no water or entity.
     */
    static void entity_load_error_preserves_snapshot_and_destination(GameTestHelper helper) {
        Cod cod = EntityTypes.COD.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(cod != null, "Could not create cod serialization fixture");
        CompoundTag snapshot = GameTestSupport.snapshot(cod);
        snapshot.putString("Health", "invalid");
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "minecraft:cod", snapshot);
        ItemStack before = bucket.copy();
        Player player = playerWith(helper, bucket);

        boolean released = MBItem.releaseOldest(helper.getLevel(), helper.absolutePos(SPAWN), bucket,
                ProtectionContext.player(player, InteractionHand.MAIN_HAND), Direction.UP);

        GameTestSupport.check(!released, "Mob Bucket released an entity with an unreadable health field");
        GameTestSupport.assertSameStack(before, bucket, "Entity-load error consumed or rewrote the snapshot");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.AIR);
        GameTestSupport.check(entitiesAt(helper, Cod.class, SPAWN).isEmpty(),
                "Entity-load error still inserted the cod");
        helper.succeed();
    }
    /**
     * Automation-only: captures an aquatic mob from source water and verifies native pickup sound
     * and game-event observability as well as removal of both mob and source.
     */
    static void aquatic_capture_uses_native_water_pickup_observability(GameTestHelper helper) {
        BlockPos target = new BlockPos(4, 2, 4);
        helper.setBlock(target, Blocks.WATER);
        Cod cod = GameTestSupport.spawn(helper, EntityTypes.COD, target);
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);

        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, target);
        boolean captured = recorder.during(() -> MBItem.capture(bucket, cod,
                ProtectionContext.player(player, InteractionHand.MAIN_HAND), Direction.UP));

        GameTestSupport.check(captured, "Aquatic Mob Bucket capture failed");
        GameTestSupport.check(!cod.isAlive(), "Captured cod remained alive");
        GameTestSupport.assertBlock(helper, target, Blocks.AIR);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Aquatic capture did not emit exactly one fluid-pickup game event");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Aquatic capture did not store the cod snapshot");
        helper.succeed();
    }
    /**
     * Automation-only: compares player and automation capture and verifies only the player path
     * triggers the filled-bucket criterion.
     */
    static void player_capture_fires_filled_bucket_criterion_but_automation_does_not(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack playerBucket = GameTestSupport.mob();
        player.setItemInHand(InteractionHand.MAIN_HAND, playerBucket);
        Pig playerPig = GameTestSupport.spawn(helper, EntityTypes.PIG, new BlockPos(4, 2, 4));
        var playerFilled = new GameTestSupport.CriterionProbe(player, "filled_bucket");

        GameTestSupport.check(playerFilled.during(() -> MBItem.capture(playerBucket, playerPig,
                        ProtectionContext.player(player, InteractionHand.MAIN_HAND), Direction.UP)),
                "Player Mob Bucket capture failed");

        ServerPlayer observer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above());
        ItemStack automationBucket = GameTestSupport.mob();
        Pig automationPig = GameTestSupport.spawn(helper, EntityTypes.PIG, new BlockPos(4, 2, 5));
        var observerFilled = new GameTestSupport.CriterionProbe(observer, "filled_bucket");

        GameTestSupport.check(observerFilled.during(() -> MBItem.capture(automationBucket, automationPig,
                        ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel())), Direction.UP)),
                "Automation Mob Bucket capture failed");

        GameTestSupport.check(playerFilled.fired(), "Player capture did not fire the filled-bucket criterion");
        GameTestSupport.check(!observerFilled.fired(), "Automation capture fired a player filled-bucket criterion");

        ServerPlayer failedPlayer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above(2));
        ItemStack incompatibleBucket = storedPig(helper.getLevel());
        Cow incompatibleCow = GameTestSupport.spawn(helper, EntityTypes.COW, new BlockPos(5, 2, 5));
        var failedFilled = new GameTestSupport.CriterionProbe(failedPlayer, "filled_bucket");

        GameTestSupport.check(!failedFilled.during(() -> MBItem.capture(incompatibleBucket, incompatibleCow,
                        ProtectionContext.player(failedPlayer, InteractionHand.MAIN_HAND), Direction.UP)),
                "Incompatible Mob Bucket capture unexpectedly succeeded");
        GameTestSupport.check(!failedFilled.fired(), "Failed capture fired the filled-bucket criterion");
        GameTestSupport.check(incompatibleCow.isAlive(), "Failed capture removed the incompatible cow");
        helper.succeed();
    }
    /** Manual: use an empty Mob Bucket on a blacklisted boss; the entity and bucket remain unchanged. */
    static void blacklisted_boss_is_not_capturable(GameTestHelper helper) {
        WitherBoss wither = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(wither != null, "Could not create Wither fixture");

        GameTestSupport.check(!MBItem.canCapture(wither), "Wither was capturable despite blacklist tag");
        helper.succeed();
    }
    /** Manual: try capturing a passenger and a vehicle carrying one; both are refused without changing the bucket. */
    static void passenger_and_vehicle_are_not_capturable(GameTestHelper helper) {
        Pig passenger = GameTestSupport.spawn(helper, EntityTypes.PIG, new BlockPos(4, 2, 4));
        Cow vehicle = GameTestSupport.spawn(helper, EntityTypes.COW, new BlockPos(4, 2, 4));
        GameTestSupport.check(passenger.startRiding(vehicle, true, false), "Could not establish riding fixture");

        GameTestSupport.check(!MBItem.canCapture(passenger), "Passenger was capturable");
        GameTestSupport.check(!MBItem.canCapture(vehicle), "Vehicle was capturable");
        helper.succeed();
    }
    /**
     * Manual: capture eight mobs of one type, then try a ninth; the bucket keeps eight and the ninth
     * remains in the world.
     */
    static void bucket_accepts_eight_same_type_and_rejects_ninth(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        GameTestSupport.check(MBItem.canAccept(bucket, EntityTypes.PIG),
                "Empty Mob Bucket rejected its first entity type");

        for (int i = 0; i <= BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS; i++) {
            Pig pig = GameTestSupport.spawn(helper, EntityTypes.PIG, new BlockPos(4, 2, 4));
            InteractionResult result = item.interactLivingEntity(bucket, player, pig, InteractionHand.MAIN_HAND);
            if (i < BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS) {
                GameTestSupport.check(result.consumesAction(), "Capture " + (i + 1) + " did not succeed");
                GameTestSupport.check(!pig.isAlive(), "Captured pig " + (i + 1) + " remained alive");
                if (i == BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS - 2) {
                    GameTestSupport.check(BucketState.getEntityCount(bucket) == BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS - 1,
                            "Seventh capture did not establish the seven-mob boundary");
                    GameTestSupport.check(MBItem.canAccept(bucket, EntityTypes.PIG),
                            "Seven-mob bucket rejected its eighth matching mob");
                } else if (i == BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS - 1) {
                    GameTestSupport.check(BucketState.getEntityCount(bucket) == BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS,
                            "Eighth capture did not fill the Mob Bucket");
                    GameTestSupport.check(!MBItem.canAccept(bucket, EntityTypes.PIG),
                            "Full Mob Bucket accepted a ninth matching mob");
                }
            } else {
                GameTestSupport.check(!result.consumesAction(), "Ninth capture succeeded");
                GameTestSupport.check(pig.isAlive(), "Rejected ninth pig was removed");
            }
        }

        GameTestSupport.check(BucketState.getEntityCount(bucket) == BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS,
                "Expected eight stored pigs, got " + BucketState.getEntityCount(bucket));
        helper.succeed();
    }
    /**
     * Manual: after capturing one mob, try a different entity type; it remains in the world and stored
     * contents are unchanged.
     */
    static void bucket_rejects_different_entity_type(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityTypes.PIG, new BlockPos(4, 2, 4));
        Cow cow = GameTestSupport.spawn(helper, EntityTypes.COW, new BlockPos(4, 2, 5));
        item.interactLivingEntity(bucket, player, pig, InteractionHand.MAIN_HAND);
        ItemStack before = bucket.copy();

        InteractionResult result = item.interactLivingEntity(bucket, player, cow, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Mob Bucket mixed entity types");
        GameTestSupport.check(cow.isAlive(), "Rejected cow was removed");
        GameTestSupport.assertSameStack(before, bucket, "Rejected different-type capture mutated bucket");
        helper.succeed();
    }
    /**
     * Manual: capture a named or damaged mob and release it; its state and UUID return, and the final
     * removal empties the bucket.
     */
    static void release_restores_state_and_uuid_and_normalizes(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        Pig original = GameTestSupport.spawn(helper, EntityTypes.PIG, new BlockPos(4, 2, 4));
        original.setCustomName(Component.literal("Remember Me"));
        original.setHealth(6.0F);
        UUID originalUuid = original.getUUID();
        item.interactLivingEntity(bucket, player, original, InteractionHand.MAIN_HAND);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Mob Bucket release did not succeed");
        List<Pig> pigs = entitiesAt(helper, Pig.class, SPAWN);
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
     * Automation-only: compares failed and successful player release and verifies the item-use statistic
     * increments only on success.
     */
    static void player_release_stat_is_awarded_only_after_success(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack successfulBucket = storedPig(helper.getLevel());
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
        entitiesAt(helper, Pig.class, SPAWN).forEach(Entity::discard);

        ItemStack collisionBucket = storedPig(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, collisionBucket);
        helper.setBlock(SPAWN, Blocks.STONE);
        InteractionResult collision = collisionBucket.useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        GameTestSupport.check(!collision.consumesAction(), "Colliding player release succeeded");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(collisionBucket.getItem()))
                        == statBefore + 1,
                "Collision failure awarded a Mob Bucket use");

        ItemStack deniedBucket = storedPig(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, deniedBucket);
        helper.setBlock(SPAWN, Blocks.AIR);
        InteractionResult[] denied = new InteractionResult[1];
        ProtectionScenarios.withoutBuildPermission(player, () -> {
            denied[0] = deniedBucket.useOn(new UseOnContext(
                    player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
            return denied[0].consumesAction();
        });
        GameTestSupport.check(!denied[0].consumesAction(), "Protection-denied player release succeeded");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(deniedBucket.getItem()))
                        == statBefore + 1,
                "Protection denial awarded a Mob Bucket use");
        helper.succeed();
    }
    /**
     * Automation-only: occupies the saved UUID before release and verifies the released mob receives a
     * different unique UUID.
     */
    static void release_replaces_uuid_that_is_already_in_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Pig existing = GameTestSupport.spawn(helper, EntityTypes.PIG, new BlockPos(2, 2, 2));
        UUID duplicateUuid = existing.getUUID();
        CompoundTag snapshot = GameTestSupport.snapshot(existing);
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", snapshot);
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Mob Bucket release did not succeed");
        GameTestSupport.check(existing.isAlive(), "Existing pig was disturbed by UUID collision handling");
        List<Pig> releasedPigs = entitiesAt(helper, Pig.class, SPAWN);
        GameTestSupport.check(releasedPigs.size() == 1,
                "Expected one released pig, got " + releasedPigs.size());
        GameTestSupport.check(!duplicateUuid.equals(releasedPigs.get(0).getUUID()),
                "Released pig retained a UUID that was already in use");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /** Manual: block the release space of a multi-mob bucket; release fails and the oldest stored mob remains first. */
    static void failed_collision_preserves_snapshot_and_fifo_order(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        CompoundTag first = pigSnapshot(helper, "first");
        CompoundTag second = pigSnapshot(helper, "second");
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
        String firstMarker = BucketState.copyFirstEntitySnapshot(bucket).getStringOr("TestMarker", "");
        GameTestSupport.check("first".equals(firstMarker),
                "Failed release changed FIFO order; first marker is " + firstMarker);
        helper.succeed();
    }
    /**
     * Manual: release an aquatic mob into an empty valid space; a water source is created, a nearby sculk
     * sensor hears the fluid placement, and the mob enters the water.
     */
    static void aquatic_release_creates_water(GameTestHelper helper) {
        ItemStack bucket = storedCod(helper.getLevel());
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, SPAWN);
        InteractionResult result = recorder.during(() -> item.useOn(new UseOnContext(player,
                InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST))));

        GameTestSupport.check(result.consumesAction(), "Aquatic mob release did not succeed");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Created release water did not emit exactly one fluid-place game event");
        GameTestSupport.check(entitiesAt(helper, Cod.class, SPAWN).size() == 1,
                "Released cod was not present in created water");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * Manual: pulse a dispenser holding a cod-filled Mob Bucket into open air beside a sculk shrieker;
     * the water appears and the cod is released, but the shrieker stays silent, as for a vanilla
     * dispenser, because the water placement has no player source.
     */
    static void automation_aquatic_release_water_has_no_source_entity(GameTestHelper helper) {
        ItemStack bucket = storedCod(helper.getLevel());
        ProtectionContext context = ProtectionContext.dispenser(
                BucketOperations.get().automationPlayer(helper.getLevel()));

        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, SPAWN);
        boolean released = recorder.during(() -> MBItem.releaseOldest(
                helper.getLevel(), helper.absolutePos(SPAWN), bucket, context, Direction.EAST));

        GameTestSupport.check(released, "Automation aquatic release did not succeed");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Automation release water did not emit exactly one fluid-place game event");
        GameTestSupport.check(recorder.contexts().stream().allMatch(event -> event.sourceEntity() == null),
                "Automation release attributed a game event to its automation player");
        helper.succeed();
    }
    /**
     * Manual: obstruct an aquatic mob's release space; release fails before water is placed and the
     * snapshot remains stored.
     */
    static void aquatic_collision_failure_precedes_water_placement(GameTestHelper helper) {
        ItemStack bucket = storedCod(helper.getLevel());
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
        GameTestSupport.check(entitiesAt(helper, Cod.class, SPAWN).isEmpty(),
                "Aquatic collision failure added the cod");
        helper.succeed();
    }
    /** Manual: release an aquatic mob into a dry waterloggable block; the block waterlogs and the mob is released. */
    static void aquatic_release_waterlogs_native_liquid_container(GameTestHelper helper) {
        ItemStack bucket = storedCod(helper.getLevel());
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
        GameTestSupport.check(entitiesAt(helper, Cod.class, SPAWN).size() == 1,
                "Released cod was not present at the waterlogged target");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * Automation-only: releases an aquatic mob into existing source water and verifies no redundant
     * fluid-placement game event is emitted.
     */
    static void aquatic_release_into_existing_water_emits_no_fluid_event(GameTestHelper helper) {
        ItemStack bucket = storedCod(helper.getLevel());
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.WATER);
        player.setShiftKeyDown(true);

        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, SPAWN);
        InteractionResult result = recorder.during(() -> ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST))));

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
     * Manual: capture a pig while holding three empty Mob Buckets; two empties stay in hand and one
     * bucket holding the pig goes into the inventory.
     */
    static void stacked_empty_capture_moves_one_filled_bucket_to_inventory(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        ItemStack held = GameTestSupport.mob();
        held.setCount(3);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        Pig pig = GameTestSupport.spawn(helper, EntityTypes.PIG, new BlockPos(4, 2, 4));

        InteractionResult result = ((MBItem) held.getItem())
                .interactLivingEntity(held, player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Mob Bucket did not capture the pig");
        GameTestSupport.check(!pig.isAlive(), "Captured pig remained alive");
        ItemStack hand = player.getMainHandItem();
        GameTestSupport.check(hand.getItem() instanceof MBItem && hand.getCount() == 2,
                "Expected two empty Mob Buckets in hand, got " + hand);
        GameTestSupport.assertEmpty(hand);
        List<ItemStack> filled = GameTestSupport.inventoryStacks(player, stack ->
                stack.getItem() instanceof MBItem && BucketState.getEntityCount(stack) > 0);
        GameTestSupport.check(filled.size() == 1, "Expected one filled Mob Bucket in inventory, got " + filled);
        GameTestSupport.check(BucketState.getEntityCount(filled.get(0)) == 1
                        && BucketState.getCurrentEntityType(filled.get(0)) == EntityTypes.PIG,
                "Filled Mob Bucket did not hold exactly the captured pig");
        helper.succeed();
    }

    /**
     * Manual: use an empty Mob Bucket on a cod in kelp, then in adventure mode without an applicable
     * {@code can_place_on} component on a cod in source water; both captures fail and leave the cod,
     * block, and bucket unchanged.
     */
    static void aquatic_capture_fails_when_water_pickup_is_refused(GameTestHelper helper) {
        BlockPos kelpPos = new BlockPos(4, 2, 4);
        BlockPos waterPos = new BlockPos(4, 2, 6);
        helper.setBlock(kelpPos.below(), Blocks.STONE);
        helper.setBlock(kelpPos, Blocks.KELP);
        helper.setBlock(waterPos, Blocks.WATER);
        Cod kelpCod = GameTestSupport.spawn(helper, EntityTypes.COD, kelpPos);
        Cod waterCod = GameTestSupport.spawn(helper, EntityTypes.COD, waterPos);
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        ProtectionContext context = ProtectionContext.player(player, InteractionHand.MAIN_HAND);

        boolean kelpCaptured = MBItem.capture(bucket, kelpCod, context, Direction.UP);
        boolean waterCaptured = ProtectionScenarios.withoutBuildPermission(player, () ->
                MBItem.capture(bucket, waterCod, context, Direction.UP));

        GameTestSupport.check(!kelpCaptured, "Captured a cod whose kelp refused water pickup");
        GameTestSupport.check(!waterCaptured, "Captured a cod whose water removal was not permitted");
        GameTestSupport.check(kelpCod.isAlive() && waterCod.isAlive(), "A refused capture removed the cod");
        GameTestSupport.assertBlock(helper, kelpPos, Blocks.KELP);
        GameTestSupport.assertBlock(helper, waterPos, Blocks.WATER);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * Manual: capture a modded mob, close the world, remove that mob's mod, reopen the world, and
     * sneak-use the Mob Bucket on a block; nothing is released and the stored entry remains.
     * Automation: stores an unregistered entity id directly before attempting release.
     */
    static void release_with_unresolved_entity_type_keeps_entry(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "missingmod:temporarily_absent", pigSnapshot(helper.getLevel()));
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(!result.consumesAction(), "Release of an unresolved entity type succeeded");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Unresolved entity type release consumed the stored entry");
        GameTestSupport.check(entitiesAt(helper, Entity.class, SPAWN).isEmpty(),
                "Unresolved entity type release added an entity");
        helper.succeed();
    }
    /**
     * Manual: capture an allowed mob, add its type to {@code somebuckets:mb_blacklist}, reload data
     * packs, and sneak-use the bucket on a block; release is refused and the entry remains stored.
     * Automation: uses a synthetic stored Wither snapshot to exercise the same post-load rejection.
     */
    static void release_of_blacklisted_stored_type_is_refused(GameTestHelper helper) {
        WitherBoss wither = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(wither != null, "Could not create stored Wither fixture");
        CompoundTag snapshot = GameTestSupport.snapshot(wither);
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "minecraft:wither", snapshot);
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(!result.consumesAction(), "A blacklisted stored type was released");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Refused blacklisted release consumed the stored entry");
        GameTestSupport.check(entitiesAt(helper, WitherBoss.class, SPAWN).isEmpty(),
                "Refused blacklisted release added a Wither");
        helper.succeed();
    }
    /** Manual: use an empty Mob Bucket on an armor stand and on another player; neither is captured. */
    static void players_and_non_mob_entities_are_not_capturable(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        ArmorStand armorStand = GameTestSupport.spawn(helper, EntityTypes.ARMOR_STAND, new BlockPos(4, 2, 4));
        Player otherPlayer = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 2, 5));
        MBItem item = (MBItem) bucket.getItem();

        InteractionResult standResult = item.interactLivingEntity(
                bucket, player, armorStand, InteractionHand.MAIN_HAND);
        InteractionResult playerResult = item.interactLivingEntity(
                bucket, player, otherPlayer, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!MBItem.canCapture(armorStand), "An armor stand was reported capturable");
        GameTestSupport.check(!MBItem.canCapture(otherPlayer), "A player was reported capturable");
        GameTestSupport.check(!standResult.consumesAction(), "Armor stand capture succeeded");
        GameTestSupport.check(!playerResult.consumesAction(), "Player capture succeeded");
        GameTestSupport.check(armorStand.isAlive(), "Refused capture removed the armor stand");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    private static CompoundTag pigSnapshot(Level level) {
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(pig != null, "Could not create pig snapshot fixture");
        CompoundTag snapshot = GameTestSupport.snapshot(pig);
        return snapshot;
    }

    private static Player playerWith(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }

    private static CompoundTag pigSnapshot(GameTestHelper helper, String marker) {
        Pig pig = EntityTypes.PIG.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(pig != null, "Could not create pig snapshot fixture");
        CompoundTag tag = GameTestSupport.snapshot(pig);
        tag.putString("TestMarker", marker);
        return tag;
    }

    private static ItemStack storedCod(Level level) {
        Cod cod = EntityTypes.COD.create(level, EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(cod != null, "Could not create stored cod fixture");
        CompoundTag snapshot = GameTestSupport.snapshot(cod);
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "minecraft:cod", snapshot);
        return bucket;
    }

    private static ItemStack storedPig(Level level) {
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", pigSnapshot(level));
        return bucket;
    }

    private static <T extends Entity> List<T> entitiesAt(
            GameTestHelper helper, Class<T> type, BlockPos relative) {
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(relative));
        return helper.getLevel().getEntitiesOfClass(type, new AABB(center, center).inflate(0.75D),
                entity -> entity.isAlive());
    }
}
