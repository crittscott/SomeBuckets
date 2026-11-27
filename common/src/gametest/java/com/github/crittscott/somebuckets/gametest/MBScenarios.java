package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.MBItem;
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
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cross-loader Mob Bucket scenarios; each loader wraps every method as a GameTest. Captures are a
 * player's right-click on the mob ({@link GameTestSupport#interactWithEntity}); releases are a sneaking player's
 * right-click on the side of {@link #CLICKED} ({@link GameTestSupport#clickBlock}).
 */
final class MBScenarios {
    private MBScenarios() {}
    private static final BlockPos PLAYER_POS = new BlockPos(3, 2, 4);
    private static final BlockPos MOB_POS = new BlockPos(4, 2, 4);
    private static final BlockPos CLICKED = new BlockPos(5, 2, 4);
    private static final BlockPos SPAWN = CLICKED.east();
    private static final double AT_BLOCK = 0.75D;

    @FunctionalInterface
    interface SpawnVeto {
        void run(UUID storedUuid, Runnable action);
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

    /**
     * No in-game form: vanilla never refuses an entity joining the world, only another mod does. Guards
     * that when a sneaking player's cod release is refused after its water is placed, the water stays,
     * the cod stays in the Mob Bucket, and no use is counted.
     */
    static void rejected_aquatic_spawn_preserves_committed_water_and_snapshot(
            GameTestHelper helper, SpawnVeto vetoSpawn) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        UUID storedUuid = BucketState.copyFirstEntitySnapshot(bucket).getUUID("UUID");
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult[] result = new InteractionResult[1];
        vetoSpawn.run(storedUuid, () -> result[0] = releaseAgainstClicked(helper, player));

        GameTestSupport.check(!result[0].consumesAction(), "Rejected cod insertion reported success");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Rejected cod insertion consumed the stored snapshot");
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, AT_BLOCK).isEmpty(),
                "Entity-join cancellation still added the cod");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem())) == statBefore,
                "Rejected entity insertion awarded a Mob Bucket use");
        helper.succeed();
    }
    /**
     * In game: configure a protection mod to deny fluid placement, then sneak-use a cod-filled Mob Bucket
     * against a block there; no water appears, no cod is released, and the cod stays in the bucket.
     * Automation: sneak-clicks the block as a player, cancels the loader's place event, and verifies
     * one event reporting water, then an unchanged world and bucket.
     */
    static void cancelled_place_check_keeps_aquatic_mob_in_bucket(
            GameTestHelper helper, ProtectionScenarios.ScopedPlaceDenial denyingPlacements) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        ItemStack before = bucket.copy();
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);
        List<ProtectionScenarios.DeniedPlacement> denied = new ArrayList<>();

        InteractionResult[] result = new InteractionResult[1];
        denyingPlacements.run(denied, () -> result[0] = releaseAgainstClicked(helper, player));

        GameTestSupport.check(!result[0].consumesAction(), "A cancelled place check allowed the release");
        GameTestSupport.check(denied.equals(List.of(new ProtectionScenarios.DeniedPlacement(
                        helper.absolutePos(SPAWN), Blocks.WATER.defaultBlockState()))),
                "Expected one place check reporting release water, got " + denied);
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.AIR);
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, AT_BLOCK).isEmpty(),
                "A cancelled place check still released the cod");
        GameTestSupport.assertSameStack(before, bucket, "A cancelled release changed the bucket");
        helper.succeed();
    }
    /**
     * In game: use an empty Mob Bucket on an eligible mob; the mob vanishes and its type and state appear
     * in the bucket.
     */
    static void eligible_mob_capture_stores_snapshot_and_discards_entity(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);
        pig.setCustomName(Component.literal("Captured Pig"));
        pig.setHealth(7.0F);

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Eligible pig capture did not succeed");
        GameTestSupport.check(!pig.isAlive(), "Captured pig remained alive");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1, "Mob Bucket did not store one snapshot");
        GameTestSupport.check(BucketState.getCurrentEntityType(bucket) == EntityType.PIG,
                "Mob Bucket stored the wrong entity type");
        CompoundTag snapshot = BucketState.copyFirstEntitySnapshot(bucket);
        GameTestSupport.check(snapshot.contains("CustomName"), "Snapshot did not keep the pig's name");
        GameTestSupport.check(snapshot.getFloat("Health") == 7.0F,
                "Snapshot did not keep the pig's health: " + snapshot.getFloat("Health"));
        helper.succeed();
    }
    /**
     * A held Mob Bucket captures before the mob's own right-click reaction, which for a villager
     * would open trading. In game: right-click a villager with an empty Mob Bucket; the villager goes
     * into the bucket instead of opening trades.
     */
    static void villager_capture_precedes_trading(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Villager villager = GameTestSupport.spawn(helper, EntityType.VILLAGER, MOB_POS);

        InteractionResult result = GameTestSupport.interactWithEntity(player, villager, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Mob Bucket did not take the villager interaction");
        GameTestSupport.check(!villager.isAlive(), "Captured villager remained in the world");
        GameTestSupport.check(BucketState.getEntityCount(player.getMainHandItem()) == 1,
                "Mob Bucket did not store the villager");
        helper.succeed();
    }
    /**
     * A held Mob Bucket captures its owner's pet before the pet's own right-click reaction, which for
     * a tamed wolf would toggle sitting. In game: right-click your tamed wolf with an empty Mob
     * Bucket; the wolf goes into the bucket instead of sitting down.
     */
    static void owned_pet_capture_precedes_sitting(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Wolf wolf = GameTestSupport.spawn(helper, EntityType.WOLF, MOB_POS);
        wolf.tame(player);

        InteractionResult result = GameTestSupport.interactWithEntity(player, wolf, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Mob Bucket did not take the click on its owner's wolf");
        GameTestSupport.check(!wolf.isAlive(), "Owner's tamed wolf was not captured");
        GameTestSupport.check(BucketState.getEntityCount(player.getMainHandItem()) == 1,
                "Mob Bucket did not store the tamed wolf");
        helper.succeed();
    }
    /**
     * A spectator's entity interaction never captures, even where the loader's interaction event
     * fires before vanilla's spectator check. No in-game form: a spectator's client does not send the
     * interaction; this guards against a modified client.
     */
    static void spectator_mob_bucket_does_not_capture(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.mob());
        Villager villager = GameTestSupport.spawn(helper, EntityType.VILLAGER, MOB_POS);
        player.setGameMode(GameType.SPECTATOR);

        GameTestSupport.interactWithEntity(player, villager, InteractionHand.MAIN_HAND);

        GameTestSupport.check(villager.isAlive(), "Spectator captured the villager");
        GameTestSupport.check(BucketState.getEntityCount(player.getMainHandItem()) == 0,
                "Spectator's Mob Bucket stored a mob");
        helper.succeed();
    }
    /**
     * In game: right-click a cod swimming in a one-block water source with an empty Mob Bucket next to a
     * sculk sensor; the cod and its water source are both taken, and the sensor hears one fluid pickup.
     */
    static void aquatic_capture_uses_native_water_pickup_observability(GameTestHelper helper) {
        helper.setBlock(MOB_POS, Blocks.WATER);
        Cod cod = GameTestSupport.spawn(helper, EntityType.COD, MOB_POS);
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);

        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, MOB_POS);
        InteractionResult result = recorder.during(() -> GameTestSupport.interactWithEntity(player, cod, InteractionHand.MAIN_HAND));

        GameTestSupport.check(result.consumesAction(), "Aquatic Mob Bucket capture failed");
        GameTestSupport.check(!cod.isAlive(), "Captured cod remained alive");
        GameTestSupport.assertBlock(helper, MOB_POS, Blocks.AIR);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Aquatic capture did not emit exactly one fluid-pickup game event");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Aquatic capture did not store the cod snapshot");
        helper.succeed();
    }
    /**
     * In game: capturing a pig with a Mob Bucket counts as filling a bucket for advancements that track
     * it, and a failed capture (a cow offered to a Mob Bucket holding a pig) does not. Automation: a
     * dispenser capture beside a watching player credits no player.
     */
    static void player_capture_fires_filled_bucket_criterion_but_automation_does_not(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.mob());
        Pig playerPig = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);
        var playerFilled = GameTestSupport.filledBucketProbe(player);

        GameTestSupport.check(playerFilled.during(() ->
                        GameTestSupport.interactWithEntity(player, playerPig, InteractionHand.MAIN_HAND)).consumesAction(),
                "Player Mob Bucket capture failed");

        ServerPlayer observer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above());
        BlockPos dispenserPos = new BlockPos(3, 2, 5);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, dispenserPos, Direction.EAST, GameTestSupport.mob());
        Pig automationPig = GameTestSupport.spawn(helper, EntityType.PIG, dispenserPos.east());
        var observerFilled = GameTestSupport.filledBucketProbe(observer);

        observerFilled.during(() -> {
            GameTestSupport.dispenseNow(helper, dispenserPos);
            return null;
        });
        GameTestSupport.check(!automationPig.isAlive()
                        && BucketState.getEntityCount(dispenser.getItem(0)) == 1,
                "Automation Mob Bucket capture failed");

        GameTestSupport.check(playerFilled.fired(), "Player capture did not fire the filled-bucket criterion");
        GameTestSupport.check(!observerFilled.fired(), "Automation capture fired a player filled-bucket criterion");

        ServerPlayer failedPlayer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above(2));
        failedPlayer.setItemInHand(InteractionHand.MAIN_HAND,
                GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null));
        Cow incompatibleCow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(5, 2, 5));
        var failedFilled = GameTestSupport.filledBucketProbe(failedPlayer);

        GameTestSupport.check(!failedFilled.during(() ->
                        GameTestSupport.interactWithEntity(failedPlayer, incompatibleCow, InteractionHand.MAIN_HAND)).consumesAction(),
                "Incompatible Mob Bucket capture unexpectedly succeeded");
        GameTestSupport.check(!failedFilled.fired(), "Failed capture fired the filled-bucket criterion");
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
        WitherBoss wither = GameTestSupport.spawn(helper, EntityType.WITHER, MOB_POS);
        wither.setNoAi(true);

        InteractionResult result = GameTestSupport.interactWithEntity(player, wither, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Mob Bucket took the Wither interaction");
        GameTestSupport.check(wither.isAlive(), "Wither was captured despite the blacklist tag");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * In game: with a pig riding a cow (summoned with /summon and a Passengers tag), right-click either
     * one with an empty Mob Bucket; neither is captured and the bucket stays empty.
     */
    static void passenger_and_vehicle_are_not_capturable(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Pig passenger = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);
        Cow vehicle = GameTestSupport.spawn(helper, EntityType.COW, MOB_POS);
        GameTestSupport.check(passenger.startRiding(vehicle, true), "Could not establish riding fixture");

        InteractionResult passengerResult = GameTestSupport.interactWithEntity(player, passenger, InteractionHand.MAIN_HAND);
        InteractionResult vehicleResult = GameTestSupport.interactWithEntity(player, vehicle, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!passengerResult.consumesAction() && passenger.isAlive(), "Passenger was captured");
        GameTestSupport.check(!vehicleResult.consumesAction() && vehicle.isAlive(), "Vehicle was captured");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * In game: tie a pig to a fence post with a lead, then right-click the pig with an empty Mob
     * Bucket; the pig stays tied to the post and the bucket stays empty.
     */
    static void mob_bucket_refuses_leashed_mob(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        BlockPos fencePos = MOB_POS.south();
        helper.setBlock(fencePos, Blocks.OAK_FENCE);
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(
                helper.getLevel(), helper.absolutePos(fencePos));
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);
        pig.setLeashedTo(knot, true);

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Mob Bucket took the leashed pig interaction");
        GameTestSupport.check(pig.isAlive(), "A leashed pig was captured");
        GameTestSupport.check(pig.getLeashHolder() == knot, "The refused capture untied the pig");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * In game: capture eight mobs of one type, then try a ninth; the bucket keeps eight and the ninth
     * remains in the world.
     */
    static void bucket_accepts_eight_same_type_and_rejects_ninth(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        GameTestSupport.check(MBItem.canAccept(bucket, EntityType.PIG),
                "Empty Mob Bucket rejected its first entity type");

        for (int i = 0; i <= BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS; i++) {
            Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);
            InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);
            if (i < BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS) {
                GameTestSupport.check(result.consumesAction(), "Capture " + (i + 1) + " did not succeed");
                GameTestSupport.check(!pig.isAlive(), "Captured pig " + (i + 1) + " remained alive");
                if (i == BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS - 2) {
                    GameTestSupport.check(BucketState.getEntityCount(bucket) == BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS - 1,
                            "Seventh capture did not establish the seven-mob boundary");
                    GameTestSupport.check(MBItem.canAccept(bucket, EntityType.PIG),
                            "Seven-mob bucket rejected its eighth matching mob");
                } else if (i == BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS - 1) {
                    GameTestSupport.check(BucketState.getEntityCount(bucket) == BucketDefinitions.MOB_BUCKET_CAPACITY_MOBS,
                            "Eighth capture did not fill the Mob Bucket");
                    GameTestSupport.check(!MBItem.canAccept(bucket, EntityType.PIG),
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
     * In game: after capturing one mob, try a different entity type; it remains in the world and stored
     * contents are unchanged.
     */
    static void bucket_rejects_different_entity_type(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(4, 2, 5));
        GameTestSupport.check(GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND).consumesAction(),
                "Could not capture the fixture pig");
        ItemStack before = bucket.copy();

        InteractionResult result = GameTestSupport.interactWithEntity(player, cow, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Mob Bucket mixed entity types");
        GameTestSupport.check(cow.isAlive(), "Rejected cow was removed");
        GameTestSupport.assertSameStack(before, bucket, "Rejected different-type capture mutated bucket");
        helper.succeed();
    }
    /**
     * In game: capture a named or damaged mob and release it; its state and UUID return, and the final
     * removal empties the bucket.
     */
    static void release_restores_state_and_uuid_and_normalizes(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Pig original = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);
        original.setCustomName(Component.literal("Remember Me"));
        original.setHealth(6.0F);
        UUID originalUuid = original.getUUID();
        GameTestSupport.check(GameTestSupport.interactWithEntity(player, original, InteractionHand.MAIN_HAND).consumesAction(),
                "Could not capture the fixture pig");
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = releaseAgainstClicked(helper, player);

        GameTestSupport.check(result.consumesAction(), "Mob Bucket release did not succeed");
        List<Pig> pigs = GameTestSupport.entities(helper, Pig.class, SPAWN, AT_BLOCK);
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
     * In game: capture a naturally spawned zombie, release it, walk more than 128 blocks away and back;
     * it is still there. A released mob that has no mob bucket of its own is made persistent, as vanilla
     * does for a bucketed fish.
     */
    static void released_zombie_does_not_despawn(GameTestHelper helper) {
        CompoundTag snapshot = GameTestSupport.mobSnapshot(helper.getLevel(), EntityType.ZOMBIE, null);
        GameTestSupport.check(!snapshot.getBoolean("PersistenceRequired"),
                "Zombie fixture was already persistent");
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "minecraft:zombie", snapshot);
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);

        InteractionResult result = releaseAgainstClicked(helper, player);

        GameTestSupport.check(result.consumesAction(), "Zombie release did not succeed");
        List<Zombie> zombies = GameTestSupport.entities(helper, Zombie.class, SPAWN, AT_BLOCK);
        GameTestSupport.check(zombies.size() == 1, "Expected one released zombie, got " + zombies.size());
        GameTestSupport.check(zombies.get(0).isPersistenceRequired(), "Released zombie can despawn");
        helper.succeed();
    }
    /**
     * In game: load a chested donkey with stacks of long written books and right-click it with an empty
     * Mob Bucket; the action bar says it carries too much and the donkey stays. The fixture weighs a pig
     * down with one heavy held item in place of a loaded inventory, since a mob whose saved data is too
     * large for clients to receive in an item is not captured.
     */
    static void oversized_mob_is_not_captured(GameTestHelper helper) {
        RecordingPlayer player = new RecordingPlayer(helper);
        ItemStack bucket = GameTestSupport.mob();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);
        ItemStack ballast = new ItemStack(Items.DIRT);
        CompoundTag data = new CompoundTag();
        data.putString("Ballast", "x".repeat(MBItem.MAX_SNAPSHOT_BYTES));
        ballast.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        pig.setItemSlot(EquipmentSlot.MAINHAND, ballast);

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Oversized pig capture succeeded");
        GameTestSupport.check(pig.isAlive(), "Oversized pig was captured");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(player.actionBar.stream().anyMatch(message ->
                        message.getContents() instanceof TranslatableContents contents
                                && "message.somebuckets.mob_bucket.too_large".equals(contents.getKey())),
                "Oversized capture did not report its size: " + player.actionBar);
        helper.succeed();
    }
    /**
     * In game: right-click a chested donkey carrying a few stacks of dirt with an empty Mob Bucket; it
     * is captured. Control for {@link #oversized_mob_is_not_captured}, with a pig holding an ordinary
     * item.
     */
    static void lightly_loaded_mob_is_captured(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);
        pig.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, 64));

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction() && !pig.isAlive(), "Lightly loaded pig was not captured");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1, "Mob Bucket did not store the pig");
        helper.succeed();
    }
    /**
     * In game: with statistics open, sneak-use a Mob Bucket holding a pig on the side of a block; its
     * "Times Used" rises by one. A release blocked by a solid block in front, or refused because the
     * player may not build there, releases nothing and does not count.
     */
    static void player_release_stat_is_awarded_only_after_success(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack successfulBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, successfulBucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(successfulBucket.getItem()));

        InteractionResult success = releaseAgainstClicked(helper, player);
        GameTestSupport.check(success.consumesAction(), "Valid player Mob Bucket release failed");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(successfulBucket.getItem()))
                        == statBefore + 1,
                "Successful player release did not award exactly one Mob Bucket use");
        GameTestSupport.entities(helper, Pig.class, SPAWN, AT_BLOCK).forEach(Entity::discard);

        ItemStack collisionBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, collisionBucket);
        helper.setBlock(SPAWN, Blocks.STONE);
        InteractionResult collision = releaseAgainstClicked(helper, player);
        GameTestSupport.check(!collision.consumesAction(), "Colliding player release succeeded");
        GameTestSupport.check(BucketState.getEntityCount(collisionBucket) == 1,
                "Colliding player release consumed the stored pig");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(collisionBucket.getItem()))
                        == statBefore + 1,
                "Collision failure awarded a Mob Bucket use");

        ItemStack deniedBucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, deniedBucket);
        helper.setBlock(SPAWN, Blocks.AIR);
        InteractionResult[] denied = new InteractionResult[1];
        ProtectionScenarios.withoutBuildPermission(player, () -> {
            denied[0] = releaseAgainstClicked(helper, player);
            return denied[0].consumesAction();
        });
        GameTestSupport.check(!denied[0].consumesAction(), "Protection-denied player release succeeded");
        GameTestSupport.check(BucketState.getEntityCount(deniedBucket) == 1
                        && GameTestSupport.entities(helper, Pig.class, SPAWN, AT_BLOCK).isEmpty(),
                "Protection-denied player release released the pig");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(deniedBucket.getItem()))
                        == statBefore + 1,
                "Protection denial awarded a Mob Bucket use");
        helper.succeed();
    }
    /**
     * No in-game form: occupies the saved UUID before release and verifies the released mob receives a
     * different unique UUID.
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

        InteractionResult result = releaseAgainstClicked(helper, player);

        GameTestSupport.check(result.consumesAction(), "Mob Bucket release did not succeed");
        GameTestSupport.check(existing.isAlive(), "Existing pig was disturbed by UUID collision handling");
        List<Pig> releasedPigs = GameTestSupport.entities(helper, Pig.class, SPAWN, AT_BLOCK);
        GameTestSupport.check(releasedPigs.size() == 1,
                "Expected one released pig, got " + releasedPigs.size());
        GameTestSupport.check(!duplicateUuid.equals(releasedPigs.get(0).getUUID()),
                "Released pig retained a UUID that was already in use");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * In game: capture two pigs, named "first" and "second" in that order, then sneak-use the Mob Bucket
     * on a block whose clicked face has a solid block in front of it; nothing is released. Clear the
     * space and release again; "first" comes out.
     */
    static void failed_collision_preserves_snapshot_and_fifo_order(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, "first");
        BucketState.addEntitySnapshot(bucket, "minecraft:pig",
                GameTestSupport.mobSnapshot(helper.getLevel(), EntityType.PIG, "second"));
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.STONE);

        InteractionResult result = releaseAgainstClicked(helper, player);

        GameTestSupport.check(!result.consumesAction(), "Mob released into colliding block");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 2, "Failed release lost a snapshot");
        String firstMarker = BucketState.copyFirstEntitySnapshot(bucket).getString("TestMarker");
        GameTestSupport.check("first".equals(firstMarker),
                "Failed release changed FIFO order; first marker is " + firstMarker);
        helper.succeed();
    }
    /**
     * In game: release an aquatic mob into an empty valid space; a water source is created, a nearby sculk
     * sensor hears the fluid placement, and the mob enters the water.
     */
    static void aquatic_release_creates_water(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, SPAWN);
        InteractionResult result = recorder.during(() -> releaseAgainstClicked(helper, player));

        GameTestSupport.check(result.consumesAction(), "Aquatic mob release did not succeed");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Created release water did not emit exactly one fluid-place game event");
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, AT_BLOCK).size() == 1,
                "Released cod was not present in created water");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * In game: pulse a dispenser holding a cod-filled Mob Bucket into open air beside a sculk shrieker;
     * the water appears and the cod is released, but the shrieker stays silent, as for a vanilla
     * dispenser, because the water placement has no player source.
     */
    static void automation_aquatic_release_water_has_no_source_entity(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, CLICKED, Direction.EAST,
                GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null));

        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, SPAWN);
        recorder.during(() -> {
            GameTestSupport.dispenseNow(helper, CLICKED);
            return null;
        });

        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, AT_BLOCK).size() == 1,
                "Automation release did not release the cod");
        GameTestSupport.assertEmpty(dispenser.getItem(0));
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Automation release water did not emit exactly one fluid-place game event");
        GameTestSupport.check(recorder.contexts().stream().allMatch(event -> event.sourceEntity() == null),
                "Automation release attributed a game event to its automation player");
        helper.succeed();
    }
    /**
     * In game: obstruct an aquatic mob's release space; release fails before water is placed and the
     * snapshot remains stored.
     */
    static void aquatic_collision_failure_precedes_water_placement(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = releaseAgainstClicked(helper, player);

        GameTestSupport.check(!result.consumesAction(), "Aquatic mob released into a colliding block");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.STONE);
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Aquatic collision failure consumed the stored snapshot");
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, AT_BLOCK).isEmpty(),
                "Aquatic collision failure added the cod");
        helper.succeed();
    }
    /** In game: release an aquatic mob into a dry waterloggable block; the block waterlogs and the mob is released. */
    static void aquatic_release_waterlogs_native_liquid_container(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.OAK_SLAB);
        player.setShiftKeyDown(true);

        InteractionResult result = releaseAgainstClicked(helper, player);

        GameTestSupport.check(result.consumesAction(), "Aquatic release did not waterlog the slab");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.OAK_SLAB);
        GameTestSupport.check(helper.getBlockState(SPAWN).getValue(BlockStateProperties.WATERLOGGED),
                "Aquatic release left the slab dry");
        GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, SPAWN, AT_BLOCK).size() == 1,
                "Released cod was not present at the waterlogged target");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * In game: sneak-use a Mob Bucket holding a cod on the side of a block with water already in front
     * of the clicked face, next to a sculk sensor; the cod is released into that water, and the sensor
     * hears an entity placement but no fluid placement.
     */
    static void aquatic_release_into_existing_water_emits_no_fluid_event(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null);
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.WATER);
        player.setShiftKeyDown(true);

        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, SPAWN);
        InteractionResult result = recorder.during(() -> releaseAgainstClicked(helper, player));

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
     * In game: capture a pig while holding three empty Mob Buckets; two empties stay in hand and one
     * bucket holding the pig goes into the inventory.
     */
    static void stacked_empty_capture_moves_one_filled_bucket_to_inventory(GameTestHelper helper) {
        Player player = playerWith(helper, GameTestSupport.mob().copyWithCount(3));
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, MOB_POS);

        InteractionResult result = GameTestSupport.interactWithEntity(player, pig, InteractionHand.MAIN_HAND);

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
                        && BucketState.getCurrentEntityType(filled.get(0)) == EntityType.PIG,
                "Filled Mob Bucket did not hold exactly the captured pig");
        helper.succeed();
    }

    /**
     * In game: use an empty Mob Bucket on a cod in kelp, then in adventure mode without an applicable
     * {@code can_place_on} component on a cod in source water; both captures fail and leave the cod,
     * block, and bucket unchanged.
     */
    static void aquatic_capture_fails_when_water_pickup_is_refused(GameTestHelper helper) {
        BlockPos waterPos = new BlockPos(4, 2, 6);
        helper.setBlock(MOB_POS.below(), Blocks.STONE);
        helper.setBlock(MOB_POS, Blocks.KELP);
        helper.setBlock(waterPos, Blocks.WATER);
        Cod kelpCod = GameTestSupport.spawn(helper, EntityType.COD, MOB_POS);
        Cod waterCod = GameTestSupport.spawn(helper, EntityType.COD, waterPos);
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);

        boolean kelpCaptured = GameTestSupport.interactWithEntity(player, kelpCod, InteractionHand.MAIN_HAND).consumesAction();
        boolean waterCaptured = ProtectionScenarios.withoutBuildPermission(player, () ->
                GameTestSupport.interactWithEntity(player, waterCod, InteractionHand.MAIN_HAND).consumesAction());

        GameTestSupport.check(!kelpCaptured, "Captured a cod whose kelp refused water pickup");
        GameTestSupport.check(!waterCaptured, "Captured a cod whose water removal was not permitted");
        GameTestSupport.check(kelpCod.isAlive() && waterCod.isAlive(), "A refused capture removed the cod");
        GameTestSupport.assertBlock(helper, MOB_POS, Blocks.KELP);
        GameTestSupport.assertBlock(helper, waterPos, Blocks.WATER);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * In game: capture a modded mob, close the world, remove that mob's mod, reopen the world, and
     * sneak-use the Mob Bucket on a block; nothing is released and the stored entry remains.
     * Automation: stores an unregistered entity id directly before attempting release.
     */
    static void release_with_unresolved_entity_type_keeps_entry(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        BucketState.addEntitySnapshot(bucket, "missingmod:temporarily_absent",
                GameTestSupport.mobSnapshot(helper.getLevel(), EntityType.PIG, null));
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = releaseAgainstClicked(helper, player);

        GameTestSupport.check(!result.consumesAction(), "Release of an unresolved entity type succeeded");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Unresolved entity type release consumed the stored entry");
        GameTestSupport.check(GameTestSupport.entities(helper, Entity.class, SPAWN, AT_BLOCK).isEmpty(),
                "Unresolved entity type release added an entity");
        helper.succeed();
    }
    /**
     * In game: capture an allowed mob, add its type to {@code somebuckets:mb_blacklist}, reload data
     * packs, and sneak-use the bucket on a block; release is refused and the entry remains stored.
     * Automation: uses a synthetic stored Wither snapshot to exercise the same post-load rejection.
     */
    static void release_of_blacklisted_stored_type_is_refused(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.storedMob(helper.getLevel(), EntityType.WITHER, null);
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = releaseAgainstClicked(helper, player);

        GameTestSupport.check(!result.consumesAction(), "A blacklisted stored type was released");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Refused blacklisted release consumed the stored entry");
        GameTestSupport.check(GameTestSupport.entities(helper, WitherBoss.class, SPAWN, AT_BLOCK).isEmpty(),
                "Refused blacklisted release added a Wither");
        helper.succeed();
    }
    /** In game: use an empty Mob Bucket on an armor stand and on another player; neither is captured. */
    static void players_and_non_mob_entities_are_not_capturable(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);
        ArmorStand armorStand = GameTestSupport.spawn(helper, EntityType.ARMOR_STAND, MOB_POS);
        Player otherPlayer = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 2, 5));

        InteractionResult standResult = GameTestSupport.interactWithEntity(player, armorStand, InteractionHand.MAIN_HAND);
        InteractionResult playerResult = GameTestSupport.interactWithEntity(player, otherPlayer, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!standResult.consumesAction(), "Armor stand capture succeeded");
        GameTestSupport.check(!playerResult.consumesAction(), "Player capture succeeded");
        GameTestSupport.check(armorStand.isAlive(), "Refused capture removed the armor stand");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    private static Player playerWith(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }

    /** The player's right-click on the east face of {@link #CLICKED}; a release needs the player sneaking. */
    private static InteractionResult releaseAgainstClicked(GameTestHelper helper, Player player) {
        return GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CLICKED, Direction.EAST));
    }
}
