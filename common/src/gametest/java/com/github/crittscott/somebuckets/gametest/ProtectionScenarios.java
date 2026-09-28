package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
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

    static void cancelled_break_check_denies_player_but_not_automation(
            GameTestHelper helper, ScopedDenial denyingBreaks) {
        BlockPos automationTarget = TARGET.east(2);
        helper.setBlock(TARGET, Blocks.WATER);
        helper.setBlock(automationTarget, Blocks.WATER);
        ItemStack playerBucket = GameTestSupport.big8();
        ItemStack automationBucket = GameTestSupport.big8();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        ProtectionContext automation = ProtectionContext.dispenser(
                BucketOperations.get().automationPlayer(helper.getLevel()));
        List<BlockPos> denied = new java.util.ArrayList<>();

        boolean[] acted = new boolean[2];
        denyingBreaks.run(denied, () -> {
            acted[0] = FluidTransactions.tryTakeFinite(helper.getLevel(),
                    GameTestSupport.hit(helper, TARGET, Direction.UP), playerBucket, player,
                    InteractionHand.MAIN_HAND);
            acted[1] = FluidTransactions.tryTakeFinite(helper.getLevel(),
                    GameTestSupport.hit(helper, automationTarget, Direction.UP), automationBucket,
                    automation);
        });

        GameTestSupport.check(!acted[0], "A cancelled break check did not deny the player's pickup");
        GameTestSupport.check(denied.equals(List.of(helper.absolutePos(TARGET))),
                "Expected one break check at the player's target, got " + denied);
        GameTestSupport.assertEmpty(playerBucket);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        GameTestSupport.check(acted[1], "Automation was denied by a player break check");
        GameTestSupport.assertFluid(automationBucket, Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB);
        GameTestSupport.assertBlock(helper, automationTarget, Blocks.AIR);
        helper.succeed();
    }

    static void cancelled_place_check_denies_player_fluid_place(
            GameTestHelper helper, ScopedDenial denyingPlacements) {
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(TARGET.above(), Blocks.AIR);
        ItemStack bucket = GameTestSupport.fluid(
                GameTestSupport.big8(), Fluids.WATER, 8 * FluidBucketItem.BUCKET_VOLUME_MB);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        List<BlockPos> denied = new java.util.ArrayList<>();

        boolean[] acted = new boolean[1];
        denyingPlacements.run(denied, () -> acted[0] = FluidTransactions.tryPlaceFinite(helper.getLevel(),
                GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, player,
                InteractionHand.MAIN_HAND));

        GameTestSupport.check(!acted[0], "A cancelled place check did not deny the player's placement");
        GameTestSupport.check(denied.equals(List.of(helper.absolutePos(TARGET.above()))),
                "Expected one place check at the resolved target, got " + denied);
        GameTestSupport.assertSameStack(before, bucket, "Denied placement drained the bucket");
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        helper.succeed();
    }
    /**
     * Automation-only: withdraws the automation player's build permission and verifies a pickup still
     * completes, as a vanilla dispenser ignores player build permission.
     */
    static void automation_ignores_build_permission(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        helper.setBlock(TARGET, Blocks.WATER);
        ProtectionContext context = automationContext(helper);

        boolean acted = withoutBuildPermission(context.actor(), () -> FluidTransactions.tryTakeFinite(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context));

        GameTestSupport.check(acted, "Automation was denied by the automation player's build permission");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    /**
     * Manual: build a dispenser aimed at source water, move the world border so both are outside it,
     * and pulse the dispenser; the water and empty Big Bucket remain unchanged.
     */
    static void automation_outside_world_border_cannot_take_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER);
        ProtectionContext context = automationContext(helper);

        boolean acted = outsideWorldBorder(helper, () -> FluidTransactions.tryTakeFinite(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context));

        GameTestSupport.check(!acted, "Automation took fluid outside the world border");
        GameTestSupport.assertSameStack(before, bucket, "Denied fluid edit mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }
    /**
     * Manual: build a dispenser with an empty Source Bucket aimed at a full water cauldron, move the
     * world border so both are outside it, and pulse it; cauldron and bucket remain unchanged.
     */
    static void automation_outside_world_border_cannot_use_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        ProtectionContext context = automationContext(helper);

        boolean acted = outsideWorldBorder(helper, () -> FluidTransactions.tryTakeSource(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context));

        GameTestSupport.check(!acted, "Automation used a cauldron outside the world border");
        GameTestSupport.assertSameStack(before, bucket, "Denied cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(TARGET).getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL,
                "Denied cauldron interaction changed fill level");
        helper.succeed();
    }
    /**
     * Manual: place dispensers holding pig- and cod-filled Mob Buckets, move the world border so their
     * targets are outside it, and pulse them; neither mob is released, no water appears, and both
     * buckets remain filled.
     */
    static void automation_outside_world_border_cannot_release(GameTestHelper helper) {
        ItemStack pigBucket = storedMob(helper, EntityType.PIG, "minecraft:pig");
        ItemStack codBucket = storedMob(helper, EntityType.COD, "minecraft:cod");
        BlockPos codTarget = TARGET.east(2);
        ProtectionContext context = automationContext(helper);

        boolean pigActed = outsideWorldBorder(helper, () -> MBItem.releaseOldest(
                helper.getLevel(), helper.absolutePos(TARGET), pigBucket, context, Direction.UP));
        boolean codActed = outsideWorldBorder(helper, () -> MBItem.releaseOldest(
                helper.getLevel(), helper.absolutePos(codTarget), codBucket, context, Direction.UP));

        GameTestSupport.check(!pigActed && !codActed, "Automation released a mob outside the world border");
        GameTestSupport.check(BucketState.getEntityCount(pigBucket) == 1
                        && BucketState.getEntityCount(codBucket) == 1,
                "Denied release consumed a stored snapshot");
        GameTestSupport.check(GameTestSupport.entities(helper, Pig.class, TARGET, 0.75D).isEmpty(),
                "Denied release added a mob to the world");
        GameTestSupport.assertBlock(helper, codTarget, Blocks.AIR);
        helper.succeed();
    }
    /**
     * Manual: in adventure mode, use a water-filled Big Bucket without an applicable
     * {@code can_place_on} component on short grass; the grass and bucket remain unchanged.
     */
    static void player_without_build_permission_cannot_place_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.SHORT_GRASS);

        boolean acted = withoutBuildPermission(player, () -> FluidTransactions.tryPlaceFinite(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, player,
                InteractionHand.MAIN_HAND));

        GameTestSupport.check(!acted, "Player without build permission placed fluid");
        GameTestSupport.assertSameStack(before, bucket, "Denied placement drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.SHORT_GRASS);
        helper.succeed();
    }
    /**
     * Manual: in adventure mode, sneak-use a filled Junk Bucket without an applicable
     * {@code can_place_on} component on stone; no item is ejected and the oldest entry remains stored.
     */
    static void player_without_build_permission_cannot_eject(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 3);
        BucketState.setStoredItems(bucket, List.of(food));
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.STONE);

        InteractionResult[] result = new InteractionResult[1];
        withoutBuildPermission(player, () -> {
            result[0] = ((JBItem) bucket.getItem()).useOn(new UseOnContext(
                    player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, TARGET, Direction.EAST)));
            return result[0].consumesAction();
        });

        GameTestSupport.check(!result[0].consumesAction(), "Player without build permission ejected an item");
        GameTestSupport.assertStored(helper, bucket, food);
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, TARGET.east(), 0.6D).isEmpty(),
                "Denied ejection dropped an item entity");
        helper.succeed();
    }
    /**
     * Automation-only: fires a dispenser at source water and verifies the pickup completes, the level's
     * automation player is stable and named, and it earns no statistic.
     */
    static void dispenser_acts_as_stable_automation_player(GameTestHelper helper) {
        ServerPlayer automationPlayer = BucketOperations.get().automationPlayer(helper.getLevel());
        GameTestSupport.check(automationPlayer == BucketOperations.get().automationPlayer(helper.getLevel()),
                "Automation player is not stable across lookups");
        GameTestSupport.check("[SomeBuckets]".equals(automationPlayer.getGameProfile().getName()),
                "Automation player has the wrong name: " + automationPlayer.getGameProfile().getName());
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
     * Manual: in adventure mode, use an empty Big Bucket without an applicable {@code can_place_on}
     * component on source water; world and bucket remain unchanged.
     */
    static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        Player player = adventurePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = FluidTransactions.tryTakeFinite(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, player,
                InteractionHand.MAIN_HAND);

        GameTestSupport.check(!acted, "Adventure player collected fluid without CanPlaceOn permission");
        GameTestSupport.assertSameStack(before, bucket, "Denied pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    /**
     * Manual: move the world border so a nearby player and pig are outside it and use an empty Mob
     * Bucket on the pig; capture fails there but succeeds after restoring the border.
     */
    static void player_outside_world_border_cannot_capture_mob(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        ProtectionContext context = ProtectionContext.player(player, InteractionHand.MAIN_HAND);

        boolean denied = outsideWorldBorder(helper, () -> MBItem.capture(bucket, pig, context, Direction.UP));

        GameTestSupport.check(!denied, "Player captured a mob outside the world border");
        GameTestSupport.check(pig.isAlive(), "Denied capture removed the pig");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(MBItem.capture(bucket, pig, context, Direction.UP),
                "Player could not capture the pig inside the world border");
        helper.succeed();
    }
    /**
     * Manual: move the world border so a nearby player and adult cow are outside it; neither an empty
     * Big nor Source Bucket milks the cow there, while the Big Bucket does after restoring the border.
     */
    static void player_outside_world_border_cannot_milk(GameTestHelper helper) {
        ItemStack big = GameTestSupport.big8();
        ItemStack source = GameTestSupport.source();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, TARGET);

        InteractionResult bigDenied = outsideWorldBorder(helper, () -> ((BBItem) big.getItem())
                .interactLivingEntity(big, player, cow, InteractionHand.MAIN_HAND));
        InteractionResult sourceDenied = outsideWorldBorder(helper, () -> ((SBItem) source.getItem())
                .interactLivingEntity(source, player, cow, InteractionHand.MAIN_HAND));

        GameTestSupport.check(!bigDenied.consumesAction(), "Big Bucket milked a cow outside the world border");
        GameTestSupport.check(!sourceDenied.consumesAction(), "Source Bucket milked a cow outside the world border");
        GameTestSupport.assertEmpty(big);
        GameTestSupport.assertEmpty(source);
        GameTestSupport.check(((BBItem) big.getItem())
                        .interactLivingEntity(big, player, cow, InteractionHand.MAIN_HAND).consumesAction(),
                "Big Bucket could not milk the cow inside the world border");
        GameTestSupport.assertMilk(big, FluidBucketItem.BUCKET_VOLUME_MB);
        helper.succeed();
    }
    /**
     * Manual: aim a dispenser containing an empty Source Bucket at an adult cow, move the world border
     * so they are outside it, and pulse it; the bucket stays empty there but assigns milk after the
     * border is restored.
     */
    static void automation_outside_world_border_cannot_milk(GameTestHelper helper) {
        ItemStack source = GameTestSupport.source();
        GameTestSupport.spawn(helper, EntityType.COW, TARGET);
        ProtectionContext context = automationContext(helper);
        BlockPos front = helper.absolutePos(TARGET);

        boolean denied = outsideWorldBorder(helper, () ->
                FluidTransactions.tryMilkSourceDispenser(helper.getLevel(), front, source, context));

        GameTestSupport.check(!denied, "Automation milked a cow outside the world border");
        GameTestSupport.assertEmpty(source);
        GameTestSupport.check(FluidTransactions.tryMilkSourceDispenser(helper.getLevel(), front, source, context),
                "Automation could not milk the cow inside the world border");
        GameTestSupport.assertMilk(source, FluidBucketItem.BUCKET_VOLUME_MB);
        helper.succeed();
    }
    /**
     * Manual: move the world border so a player and dropped item are outside it and use Junk and Trash
     * Buckets nearby; neither collects there, while each collects after the border is restored.
     */
    static void player_outside_world_border_cannot_vacuum_items(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        ProtectionContext context = ProtectionContext.player(player, InteractionHand.MAIN_HAND);

        for (ItemStack bucket : List.of(GameTestSupport.junk(), GameTestSupport.trash())) {
            JBItem item = (JBItem) bucket.getItem();
            ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), TARGET);

            boolean denied = outsideWorldBorder(helper, () ->
                    item.absorbItemEntities(helper.getLevel(), bucket, List.of(entity), context));

            GameTestSupport.check(!denied, item + " collected an item outside the world border");
            GameTestSupport.check(entity.isAlive() && entity.getItem().getCount() == 2,
                    item + " changed an item entity outside the world border");
            GameTestSupport.assertStored(helper, bucket);
            GameTestSupport.check(item.absorbItemEntities(helper.getLevel(), bucket, List.of(entity), context),
                    item + " could not collect the item inside the world border");
        }
        helper.succeed();
    }
    /**
     * Manual: move the world border so a player and adult pig are outside it and use a Junk Bucket
     * containing carrots on the pig; it is not fed there but enters love mode after the border is restored.
     */
    static void player_outside_world_border_cannot_feed_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3)));
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        JBItem item = (JBItem) bucket.getItem();

        InteractionResult denied = outsideWorldBorder(helper, () ->
                item.interactLivingEntity(bucket, player, pig, InteractionHand.MAIN_HAND));

        GameTestSupport.check(!denied.consumesAction(), "Junk Bucket fed an animal outside the world border");
        GameTestSupport.check(!pig.isInLove(), "Denied feeding put the pig in love mode");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.CARROT, 3));
        GameTestSupport.check(item.interactLivingEntity(bucket, player, pig, InteractionHand.MAIN_HAND)
                        .consumesAction(),
                "Junk Bucket could not feed the pig inside the world border");
        GameTestSupport.check(pig.isInLove(), "Fed pig did not enter love mode");
        helper.succeed();
    }

    /**
     * Manual: have player A tame a wolf, then have player B use an empty Mob Bucket on it; nothing
     * happens, while player A can capture it. Automation: also invokes the shared capture entry point
     * directly to verify the ownership gate is not limited to item interaction dispatch.
     */
    static void player_cannot_capture_another_players_pet(GameTestHelper helper) {
        Player owner = GameTestSupport.survivalPlayer(helper, TARGET.west());
        Player other = GameTestSupport.survivalPlayer(helper, TARGET.east());
        Wolf wolf = GameTestSupport.spawn(helper, EntityType.WOLF, TARGET);
        wolf.tame(owner);
        ItemStack bucket = GameTestSupport.mob();
        other.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult byHand = ((MBItem) bucket.getItem())
                .interactLivingEntity(bucket, other, wolf, InteractionHand.MAIN_HAND);
        boolean captured = MBItem.capture(bucket, wolf,
                ProtectionContext.player(other, InteractionHand.MAIN_HAND), Direction.UP);

        GameTestSupport.check(!byHand.consumesAction() && !captured, "A player captured another player's pet");
        GameTestSupport.check(wolf.isAlive(), "Denied capture removed the pet");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(MBItem.capture(bucket, wolf,
                        ProtectionContext.player(owner, InteractionHand.MAIN_HAND), Direction.UP),
                "The owner could not capture their own pet");
        helper.succeed();
    }
    /** Manual: aim a dispenser with an empty Mob Bucket at a tamed wolf and pulse it; the wolf is not captured. */
    static void automation_cannot_capture_owned_pet(GameTestHelper helper) {
        Player owner = GameTestSupport.survivalPlayer(helper, TARGET.west());
        Wolf wolf = GameTestSupport.spawn(helper, EntityType.WOLF, TARGET);
        wolf.tame(owner);
        ItemStack bucket = GameTestSupport.mob();

        boolean captured = MBItem.capture(bucket, wolf, automationContext(helper), Direction.UP);

        GameTestSupport.check(!captured, "Automation captured an owned pet");
        GameTestSupport.check(wolf.isAlive(), "Denied capture removed the pet");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /** Manual: leash a pig, then use an empty Mob Bucket on it; the pig remains in the world and the bucket stays empty. */
    static void mob_bucket_refuses_leashed_mob(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        pig.setLeashedTo(player, true);
        ItemStack bucket = GameTestSupport.mob();

        GameTestSupport.check(!MBItem.canCapture(pig), "A leashed mob was reported capturable");
        GameTestSupport.check(!MBItem.capture(bucket, pig,
                        ProtectionContext.player(player, InteractionHand.MAIN_HAND), Direction.UP),
                "A leashed mob was captured");
        GameTestSupport.check(pig.isAlive(), "Denied capture removed the pig");
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * Manual: aim a dispenser containing a Junk Bucket of cod at an untamed cat and pulse it; the cat
     * is not fed or tamed and the cod remains stored.
     */
    static void automation_does_not_feed_untamed_cat(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.COD, 3)));
        Cat cat = GameTestSupport.spawn(helper, EntityType.CAT, TARGET);
        ProtectionContext context = automationContext(helper);

        boolean fed = ((JBItem) bucket.getItem())
                .feedAnimal(bucket, cat, context.actor(), InteractionHand.MAIN_HAND, context);

        GameTestSupport.check(!fed, "Automation fed an untamed cat");
        GameTestSupport.check(!cat.isTame(), "Automation tamed a cat");
        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.COD, 3));
        helper.succeed();
    }

    private static ProtectionContext automationContext(GameTestHelper helper) {
        return ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel()));
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

    private static ItemStack storedMob(GameTestHelper helper, EntityType<?> type, String id) {
        ItemStack bucket = GameTestSupport.mob();
        Entity stored = type.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(stored != null, "Could not create stored " + id + " fixture");
        CompoundTag snapshot = new CompoundTag();
        stored.saveWithoutId(snapshot);
        BucketState.addEntitySnapshot(bucket, id, snapshot);
        return bucket;
    }

    private static Player adventurePlayer(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        return player;
    }
}
