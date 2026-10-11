package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.FilledBucketTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class MBScenarios {
    private MBScenarios() {}
    private static final BlockPos PLAYER_POS = new BlockPos(3, 2, 4);
    private static final BlockPos CLICKED = new BlockPos(5, 2, 4);
    private static final BlockPos SPAWN = CLICKED.east();
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
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 1, "Mob Bucket did not store one snapshot");
        GameTestSupport.check(NBTUtil.getCurrentEntityType(bucket) == EntityType.PIG,
                "Mob Bucket stored the wrong entity type");
        helper.succeed();
    }
    static void aquatic_capture_uses_native_water_pickup_observability(GameTestHelper helper) {
        BlockPos target = new BlockPos(4, 2, 4);
        helper.setBlock(target, Blocks.WATER);
        Cod cod = GameTestSupport.spawn(helper, EntityType.COD, target);
        ItemStack bucket = GameTestSupport.mob();
        Player player = playerWith(helper, bucket);

        List<GameEvent> events = new ArrayList<>();
        DynamicGameEventListener<GameEventListener> dynamicListener =
                new DynamicGameEventListener<>(eventListener(helper, target, events));
        dynamicListener.add(helper.getLevel());
        boolean captured;
        try {
            captured = MBItem.capture(bucket, cod,
                    ProtectionContext.player(player, InteractionHand.MAIN_HAND), Direction.UP);
        } finally {
            dynamicListener.remove(helper.getLevel());
        }

        GameTestSupport.check(captured, "Aquatic Mob Bucket capture failed");
        GameTestSupport.check(!cod.isAlive(), "Captured cod remained alive");
        GameTestSupport.assertBlock(helper, target, Blocks.AIR);
        GameTestSupport.check(events.stream().filter(event -> event == GameEvent.FLUID_PICKUP).count() == 1,
                "Aquatic capture did not emit exactly one fluid-pickup game event");
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 1,
                "Aquatic capture did not store the cod snapshot");
        helper.succeed();
    }
    static void player_capture_fires_filled_bucket_criterion_but_automation_does_not(
            GameTestHelper helper) {
        FilledBucketTrigger.TriggerInstance playerCriterion =
                FilledBucketTrigger.TriggerInstance.filledBucket(ItemPredicate.ANY);
        Advancement playerAdvancement = Advancement.Builder.advancement()
                .addCriterion("filled", playerCriterion)
                .build(new ResourceLocation(SomeBuckets.MODID, "gametest/mob_player_filled"));
        CriterionTrigger.Listener<FilledBucketTrigger.TriggerInstance> playerListener =
                new CriterionTrigger.Listener<>(playerCriterion, playerAdvancement, "filled");
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack playerBucket = GameTestSupport.mob();
        player.setItemInHand(InteractionHand.MAIN_HAND, playerBucket);
        Pig playerPig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));

        CriteriaTriggers.FILLED_BUCKET.addPlayerListener(player.getAdvancements(), playerListener);
        try {
            GameTestSupport.check(MBItem.capture(playerBucket, playerPig,
                            ProtectionContext.player(player, InteractionHand.MAIN_HAND), Direction.UP),
                    "Player Mob Bucket capture failed");
        } finally {
            CriteriaTriggers.FILLED_BUCKET.removePlayerListener(player.getAdvancements(), playerListener);
        }

        FilledBucketTrigger.TriggerInstance automationCriterion =
                FilledBucketTrigger.TriggerInstance.filledBucket(ItemPredicate.ANY);
        Advancement automationAdvancement = Advancement.Builder.advancement()
                .addCriterion("filled", automationCriterion)
                .build(new ResourceLocation(SomeBuckets.MODID, "gametest/mob_automation_not_filled"));
        CriterionTrigger.Listener<FilledBucketTrigger.TriggerInstance> automationListener =
                new CriterionTrigger.Listener<>(automationCriterion, automationAdvancement, "filled");
        ServerPlayer observer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above());
        ItemStack automationBucket = GameTestSupport.mob();
        Pig automationPig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 5));

        CriteriaTriggers.FILLED_BUCKET.addPlayerListener(observer.getAdvancements(), automationListener);
        try {
            GameTestSupport.check(MBItem.capture(automationBucket, automationPig,
                            ProtectionContext.dispenser(helper.absolutePos(new BlockPos(3, 2, 5))),
                            Direction.UP),
                    "Automation Mob Bucket capture failed");
        } finally {
            CriteriaTriggers.FILLED_BUCKET.removePlayerListener(observer.getAdvancements(), automationListener);
        }

        GameTestSupport.check(player.getAdvancements().getOrStartProgress(playerAdvancement).isDone(),
                "Player capture did not fire the filled-bucket criterion");
        GameTestSupport.check(!observer.getAdvancements().getOrStartProgress(automationAdvancement).isDone(),
                "Automation capture fired a player filled-bucket criterion");

        FilledBucketTrigger.TriggerInstance failedCriterion =
                FilledBucketTrigger.TriggerInstance.filledBucket(ItemPredicate.ANY);
        Advancement failedAdvancement = Advancement.Builder.advancement()
                .addCriterion("filled", failedCriterion)
                .build(new ResourceLocation(SomeBuckets.MODID, "gametest/mob_failed_not_filled"));
        CriterionTrigger.Listener<FilledBucketTrigger.TriggerInstance> failedListener =
                new CriterionTrigger.Listener<>(failedCriterion, failedAdvancement, "filled");
        ServerPlayer failedPlayer = GameTestSupport.serverPlayer(helper, PLAYER_POS.above(2));
        ItemStack incompatibleBucket = storedPig(helper.getLevel());
        Cow incompatibleCow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(5, 2, 5));

        CriteriaTriggers.FILLED_BUCKET.addPlayerListener(failedPlayer.getAdvancements(), failedListener);
        try {
            GameTestSupport.check(!MBItem.capture(incompatibleBucket, incompatibleCow,
                            ProtectionContext.player(failedPlayer, InteractionHand.MAIN_HAND), Direction.UP),
                    "Incompatible Mob Bucket capture unexpectedly succeeded");
        } finally {
            CriteriaTriggers.FILLED_BUCKET.removePlayerListener(failedPlayer.getAdvancements(), failedListener);
        }
        GameTestSupport.check(!failedPlayer.getAdvancements().getOrStartProgress(failedAdvancement).isDone(),
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
        GameTestSupport.check(NBTUtil.getEntityCount(player.getMainHandItem()) == 1,
                "Mob Bucket did not store the villager");
        helper.succeed();
    }
    static void tagged_boss_is_not_capturable(GameTestHelper helper) {
        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        GameTestSupport.check(wither != null, "Could not create Wither fixture");

        GameTestSupport.check(!MBItem.canCapture(wither), "Wither was capturable despite uncapturable tag");
        helper.succeed();
    }
    static void passenger_and_vehicle_are_not_capturable(GameTestHelper helper) {
        Pig passenger = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        Cow vehicle = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(4, 2, 4));
        GameTestSupport.check(passenger.startRiding(vehicle, true), "Could not establish riding fixture");

        GameTestSupport.check(!MBItem.canCapture(passenger), "Passenger was capturable");
        GameTestSupport.check(!MBItem.canCapture(vehicle), "Vehicle was capturable");
        helper.succeed();
    }
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
                    GameTestSupport.check(NBTUtil.getEntityCount(bucket) == MBItem.MAX_MOBS - 1,
                            "Seventh capture did not establish the seven-mob boundary");
                    GameTestSupport.check(MBItem.canAccept(bucket, EntityType.PIG),
                            "Seven-mob bucket rejected its eighth matching mob");
                } else if (i == MBItem.MAX_MOBS - 1) {
                    GameTestSupport.check(NBTUtil.getEntityCount(bucket) == MBItem.MAX_MOBS,
                            "Eighth capture did not fill the Mob Bucket");
                    GameTestSupport.check(!MBItem.canAccept(bucket, EntityType.PIG),
                            "Full Mob Bucket accepted a ninth matching mob");
                }
            } else {
                GameTestSupport.check(!result.consumesAction(), "Ninth capture succeeded");
                GameTestSupport.check(pig.isAlive(), "Rejected ninth pig was removed");
            }
        }

        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == MBItem.MAX_MOBS,
                "Expected eight stored pigs, got " + NBTUtil.getEntityCount(bucket));
        helper.succeed();
    }
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
    static void player_release_stat_is_awarded_only_after_success(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, PLAYER_POS);
        ItemStack successfulBucket = storedPig(helper.getLevel());
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
        entitiesAt(helper, Pig.class, SPAWN).forEach(Entity::discard);

        ItemStack collisionBucket = storedPig(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, collisionBucket);
        helper.setBlock(SPAWN, Blocks.STONE);
        InteractionResult collision = ((MBItem) collisionBucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        GameTestSupport.check(!collision.consumesAction(), "Colliding player release succeeded");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(collisionBucket.getItem()))
                        == statBefore + 1,
                "Collision failure awarded a Mob Bucket use");

        ItemStack deniedBucket = storedPig(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, deniedBucket);
        helper.setBlock(SPAWN, Blocks.AIR);
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        InteractionResult denied = ((MBItem) deniedBucket.getItem()).useOn(new UseOnContext(
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
        ItemStack bucket = storedPig(helper.getLevel());
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
    static void release_replaces_uuid_that_is_already_in_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Pig existing = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(2, 2, 2));
        UUID duplicateUuid = existing.getUUID();
        CompoundTag snapshot = new CompoundTag();
        existing.saveWithoutId(snapshot);
        NBTUtil.addEntitySnapshot(bucket, "minecraft:pig", snapshot);
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
    static void failed_collision_preserves_snapshot_and_fifo_order(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        CompoundTag first = pigSnapshot(helper, "first");
        CompoundTag second = pigSnapshot(helper, "second");
        NBTUtil.addEntitySnapshot(bucket, "minecraft:pig", first);
        NBTUtil.addEntitySnapshot(bucket, "minecraft:pig", second);
        Player player = playerWith(helper, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.STONE);

        InteractionResult result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(!result.consumesAction(), "Mob released into colliding block");
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 2, "Failed release lost a snapshot");
        String firstMarker = bucket.getOrCreateTag().getList(NBTUtil.ENTITIES, Tag.TAG_COMPOUND)
                .getCompound(0).getString("TestMarker");
        GameTestSupport.check("first".equals(firstMarker),
                "Failed release changed FIFO order; first marker is " + firstMarker);
        helper.succeed();
    }
    static void aquatic_release_creates_water(GameTestHelper helper) {
        ItemStack bucket = storedCod(helper.getLevel());
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        player.setShiftKeyDown(true);

        InteractionResult result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, CLICKED, Direction.EAST)));

        GameTestSupport.check(result.consumesAction(), "Aquatic mob release did not succeed");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(entitiesAt(helper, Cod.class, SPAWN).size() == 1,
                "Released cod was not present in created water");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
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
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 1,
                "Aquatic collision failure consumed the stored snapshot");
        GameTestSupport.check(entitiesAt(helper, Cod.class, SPAWN).isEmpty(),
                "Aquatic collision failure added the cod");
        helper.succeed();
    }
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
    static void aquatic_release_into_existing_water_emits_no_fluid_event(GameTestHelper helper) {
        ItemStack bucket = storedCod(helper.getLevel());
        Player player = playerWith(helper, bucket);
        helper.setBlock(CLICKED, Blocks.STONE);
        helper.setBlock(SPAWN, Blocks.WATER);
        player.setShiftKeyDown(true);

        List<GameEvent> events = new ArrayList<>();
        GameEventListener listener = eventListener(helper, SPAWN, events);
        DynamicGameEventListener<GameEventListener> dynamicListener = new DynamicGameEventListener<>(listener);
        dynamicListener.add(helper.getLevel());
        InteractionResult result;
        try {
            result = ((MBItem) bucket.getItem()).useOn(new UseOnContext(
                    player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, CLICKED, Direction.EAST)));
        } finally {
            dynamicListener.remove(helper.getLevel());
        }

        GameTestSupport.check(result.consumesAction(), "Aquatic release into existing water failed");
        GameTestSupport.assertBlock(helper, SPAWN, Blocks.WATER);
        GameTestSupport.check(events.stream().noneMatch(event -> event == GameEvent.FLUID_PLACE),
                "Existing water produced a redundant fluid-place game event");
        GameTestSupport.check(events.stream().filter(event -> event == GameEvent.ENTITY_PLACE).count() == 1,
                "Successful aquatic release did not emit exactly one entity-place game event");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
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
    static void land_release_activates_sculk_sensor(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        MBItem item = (MBItem) bucket.getItem();
        Player player = playerWith(helper, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, new BlockPos(4, 2, 4));
        item.interactLivingEntity(bucket, player, pig, InteractionHand.MAIN_HAND);
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
     * In-game: hold an empty Mob Bucket in the main hand and a blue Bucket of Axolotl named
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
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 1, "Mob Bucket did not store one axolotl");
        GameTestSupport.check(NBTUtil.getCurrentEntityType(bucket) == EntityType.AXOLOTL,
                "Mob Bucket stored the wrong entity type");
        GameTestSupport.check(player.getOffhandItem().is(Items.WATER_BUCKET),
                "Emptied Bucket of Axolotl did not become a Water Bucket");
        Axolotl stored = EntityType.AXOLOTL.create(helper.getLevel());
        GameTestSupport.check(stored != null, "Could not recreate stored axolotl");
        stored.load(NBTUtil.copyFirstEntitySnapshot(bucket));
        GameTestSupport.check(stored.getVariant() == Axolotl.Variant.BLUE, "Stored axolotl lost its variant");
        GameTestSupport.check(stored.hasCustomName()
                        && "Held Axolotl".equals(stored.getCustomName().getString()),
                "Stored axolotl lost its name");
        helper.succeed();
    }

    /**
     * In-game: fill a Mob Bucket with a blue axolotl named "First" and then a gold one named
     * "Second". Hold a Water Bucket in the offhand and use the Mob Bucket on air. The offhand
     * becomes a Bucket of Axolotl named "First" that releases a blue axolotl, and the Mob Bucket
     * still holds "Second".
     */
    static void mob_bucket_fills_water_bucket_with_oldest_mob(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        NBTUtil.addEntitySnapshot(bucket, "minecraft:axolotl",
                axolotlSnapshot(helper, "First", Axolotl.Variant.BLUE));
        NBTUtil.addEntitySnapshot(bucket, "minecraft:axolotl",
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
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 1, "Mob Bucket did not keep the second axolotl");
        helper.succeed();
    }

    /**
     * In-game: each of these held pairs does nothing when the Mob Bucket is used on air: a Mob
     * Bucket of pigs with a Bucket of Cod, a Mob Bucket holding eight cod with a Bucket of Cod, a
     * Mob Bucket of squid with a Water Bucket, a Mob Bucket of cod with an empty Bucket, and an
     * empty Mob Bucket with a Water Bucket.
     */
    static void mob_bucket_held_exchange_refuses_incompatible_pairs(GameTestHelper helper) {
        Level level = helper.getLevel();
        assertHeldExchangeRefused(helper, storedPig(level), new ItemStack(Items.COD_BUCKET),
                "pigs with a Bucket of Cod");
        ItemStack full = GameTestSupport.mob();
        for (int i = 0; i < MBItem.MAX_MOBS; i++) {
            NBTUtil.addEntitySnapshot(full, "minecraft:cod", NBTUtil.copyFirstEntitySnapshot(storedCod(level)));
        }
        assertHeldExchangeRefused(helper, full, new ItemStack(Items.COD_BUCKET),
                "a full bucket of cod with a Bucket of Cod");
        assertHeldExchangeRefused(helper, storedSquid(level), new ItemStack(Items.WATER_BUCKET),
                "squid with a Water Bucket");
        assertHeldExchangeRefused(helper, storedCod(level), new ItemStack(Items.BUCKET),
                "cod with an empty Bucket");
        assertHeldExchangeRefused(helper, GameTestSupport.mob(), new ItemStack(Items.WATER_BUCKET),
                "an empty Mob Bucket with a Water Bucket");
        helper.succeed();
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

    private static ItemStack storedSquid(Level level) {
        Squid squid = EntityType.SQUID.create(level);
        GameTestSupport.check(squid != null, "Could not create stored squid fixture");
        CompoundTag snapshot = new CompoundTag();
        squid.saveWithoutId(snapshot);
        ItemStack bucket = GameTestSupport.mob();
        NBTUtil.addEntitySnapshot(bucket, "minecraft:squid", snapshot);
        return bucket;
    }

    private static Player playerWith(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayer(helper, PLAYER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }

    private static CompoundTag pigSnapshot(GameTestHelper helper, String marker) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        GameTestSupport.check(pig != null, "Could not create pig snapshot fixture");
        CompoundTag tag = new CompoundTag();
        pig.saveWithoutId(tag);
        tag.putString("TestMarker", marker);
        return tag;
    }

    private static ItemStack storedCod(Level level) {
        Cod cod = EntityType.COD.create(level);
        GameTestSupport.check(cod != null, "Could not create stored cod fixture");
        CompoundTag snapshot = new CompoundTag();
        cod.saveWithoutId(snapshot);
        ItemStack bucket = GameTestSupport.mob();
        NBTUtil.addEntitySnapshot(bucket, "minecraft:cod", snapshot);
        return bucket;
    }

    private static ItemStack storedPig(Level level) {
        Pig pig = EntityType.PIG.create(level);
        GameTestSupport.check(pig != null, "Could not create stored pig fixture");
        CompoundTag snapshot = new CompoundTag();
        pig.saveWithoutId(snapshot);
        ItemStack bucket = GameTestSupport.mob();
        NBTUtil.addEntitySnapshot(bucket, "minecraft:pig", snapshot);
        return bucket;
    }

    private static GameEventListener eventListener(GameTestHelper helper, BlockPos relative,
                                                   List<GameEvent> events) {
        BlockPos absolute = helper.absolutePos(relative);
        return new GameEventListener() {
            @Override
            public BlockPositionSource getListenerSource() {
                return new BlockPositionSource(absolute);
            }

            @Override
            public int getListenerRadius() {
                return 16;
            }

            @Override
            public boolean handleGameEvent(ServerLevel level, GameEvent event,
                                           GameEvent.Context context, Vec3 pos) {
                if (BlockPos.containing(pos).equals(absolute)) events.add(event);
                return true;
            }
        };
    }

    private static <T extends net.minecraft.world.entity.Entity> List<T> entitiesAt(
            GameTestHelper helper, Class<T> type, BlockPos relative) {
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(relative));
        return helper.getLevel().getEntitiesOfClass(type, new AABB(center, center).inflate(0.75D),
                entity -> entity.isAlive());
    }
}
