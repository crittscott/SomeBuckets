package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.ProtectionEvent;
import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.RefusalScope;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluids;

/**
 * Protection scenarios. A test listener on the loader's protection events stands in for a claim
 * mod: in game, the same denials come from claiming the target area with a claim mod (for example
 * FTB Chunks or Open Parties and Claims) and acting there as a non-member, or, for dispensers, with
 * the claim mod's fake-player access turned off.
 */
final class ProtectionScenarios {
    private ProtectionScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    /** Automation with no refusing listener may act; the posted events reach no denial. */
    static void automation_is_permitted_without_refusal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();

        GameTestSupport.check(Protections.mayRemove(helper.getLevel(), GameTestSupport.automation(helper),
                        helper.absolutePos(TARGET), Direction.UP, bucket),
                "Unowned automation was denied without a refusing listener");
        helper.succeed();
    }

    /**
     * A player's world-fluid pickup posts the block-break event as that player, from either hand.
     * In game: in someone else's claim, a Big Bucket in either hand cannot collect water.
     */
    static void player_fluid_pickup_posts_break_for_each_hand(GameTestHelper helper) {
        BlockPos mainTarget = TARGET;
        BlockPos offTarget = TARGET.east();
        ItemStack mainBucket = GameTestSupport.big8();
        ItemStack offBucket = GameTestSupport.big8();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, mainBucket);
        player.setItemInHand(InteractionHand.OFF_HAND, offBucket);
        helper.setBlock(mainTarget, Blocks.WATER);
        helper.setBlock(offTarget, Blocks.WATER);

        int[] refusals = {0};
        boolean mainActed;
        boolean offActed;
        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) -> {
            if (event != ProtectionEvent.BREAK) return false;
            GameTestSupport.check(actor == player, "Break event carried the wrong player");
            refusals[0]++;
            return true;
        })) {
            mainActed = FluidTransactions.tryTakeFinite(
                    helper.getLevel(), GameTestSupport.hit(helper, mainTarget, Direction.UP), mainBucket,
                    player, InteractionHand.MAIN_HAND);
            offActed = FluidTransactions.tryTakeFinite(
                    helper.getLevel(), GameTestSupport.hit(helper, offTarget, Direction.UP), offBucket,
                    player, InteractionHand.OFF_HAND);
        }

        GameTestSupport.check(!mainActed && !offActed, "Refused break did not stop both hand paths");
        GameTestSupport.check(refusals[0] == 2, "Break event was not posted for both hand paths");
        GameTestSupport.assertEmpty(mainBucket);
        GameTestSupport.assertEmpty(offBucket);
        GameTestSupport.assertBlock(helper, mainTarget, Blocks.WATER);
        GameTestSupport.assertBlock(helper, offTarget, Blocks.WATER);
        helper.succeed();
    }

    /**
     * A dispenser's fluid pickup posts the block-break event as the {@code [SomeBuckets]} automation
     * player standing at the dispenser. In game: a dispenser with a Big Bucket facing water inside a
     * claim that denies fake players leaves the water and bucket alone.
     */
    static void refused_break_denies_dispenser_fluid_pickup(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER);
        BlockPos source = helper.absolutePos(TARGET.west());
        BlockPos target = helper.absolutePos(TARGET);
        ProtectionContext context = GameTestSupport.dispenserAt(helper, source);

        boolean acted;
        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) -> {
            if (event != ProtectionEvent.BREAK) return false;
            GameTestSupport.check(GameTestSupport.isDispenserActor(actor, source),
                    "Break event was not posted as the automation player at the dispenser");
            GameTestSupport.check(target.equals(pos), "Break event carried the wrong position");
            return true;
        })) {
            acted = FluidTransactions.tryTakeFinite(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context);
        }

        GameTestSupport.check(!acted, "Refused break did not deny dispenser fluid pickup");
        GameTestSupport.assertSameStack(before, bucket, "Denied fluid pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    /** In game: a dispenser with a Mob Bucket inside a claim that denies fake players captures nothing. */
    static void refused_entity_interaction_denies_dispenser_capture(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        ProtectionContext context = GameTestSupport.dispenserAt(helper, helper.absolutePos(TARGET.west()));

        boolean acted;
        try (RefusalScope ignored = GameTestSupport.refuseProtection(
                (event, actor, pos) -> event == ProtectionEvent.ENTITY_INTERACT)) {
            acted = MBItem.capture(bucket, pig, context, Direction.UP);
        }

        GameTestSupport.check(!acted, "Refused entity interaction did not deny mob capture");
        GameTestSupport.check(pig.isAlive(), "Denied capture removed mob");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 0, "Denied capture mutated Mob Bucket");
        helper.succeed();
    }

    /**
     * Forge only: Fabric API has no item-pickup event. In game: a dispenser with a Junk Bucket
     * inside a claim that denies fake-player pickup leaves dropped items in front of it.
     */
    static void refused_pickup_denies_dispenser_absorption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), TARGET);
        ProtectionContext context = GameTestSupport.dispenserAt(helper, helper.absolutePos(TARGET.west()));

        boolean acted;
        try (RefusalScope ignored = GameTestSupport.refuseProtection(
                (event, actor, pos) -> event == ProtectionEvent.PICKUP)) {
            acted = ((JBItem) bucket.getItem()).absorbItemEntities(helper.getLevel(), bucket,
                    java.util.List.of(input), context);
        }

        GameTestSupport.check(!acted, "Refused pickup did not deny storage-bucket absorption");
        GameTestSupport.assertStored(bucket);
        GameTestSupport.check(input.isAlive() && input.getItem().getCount() == 2,
                "Denied absorption mutated the item entity");
        helper.succeed();
    }

    /** In game: a dispenser with a carrot-filled Junk Bucket in a fake-player-denying claim feeds no pig. */
    static void refused_entity_interaction_denies_automated_feeding(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 2);
        BucketState.setStoredItems(bucket, java.util.List.of(food));
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        ProtectionContext context = GameTestSupport.dispenserAt(helper, helper.absolutePos(TARGET.west()));

        boolean acted;
        try (RefusalScope ignored = GameTestSupport.refuseProtection(
                (event, actor, pos) -> event == ProtectionEvent.ENTITY_INTERACT)) {
            acted = ((JBItem) bucket.getItem()).feedAnimal(
                    bucket, pig, context.actor(), InteractionHand.MAIN_HAND, context);
        }

        GameTestSupport.check(!acted, "Refused entity interaction did not deny automated feeding");
        GameTestSupport.check(!pig.isInLove(), "Denied feeding changed the animal");
        GameTestSupport.assertStored(bucket, food);
        helper.succeed();
    }

    /** In game: a dispenser with a Source Bucket facing a water cauldron in a fake-player-denying claim. */
    static void refused_block_interaction_denies_dispenser_cauldron_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        ProtectionContext context = GameTestSupport.dispenserAt(helper, helper.absolutePos(TARGET.west()));

        boolean acted;
        try (RefusalScope ignored = GameTestSupport.refuseProtection(
                (event, actor, pos) -> event == ProtectionEvent.BLOCK_INTERACT)) {
            acted = FluidTransactions.tryTakeSource(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context);
        }

        GameTestSupport.check(!acted, "Refused block interaction did not deny cauldron use");
        GameTestSupport.assertSameStack(before, bucket, "Denied cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(TARGET).getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL,
                "Denied cauldron interaction changed fill level");
        helper.succeed();
    }

    /** In game: a dispenser with a full Mob Bucket in a fake-player-denying claim releases nothing. */
    static void refused_block_interaction_denies_dispenser_release(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addSnapshot(helper, bucket, EntityType.PIG, "minecraft:pig");
        ProtectionContext context = GameTestSupport.dispenserAt(helper, helper.absolutePos(TARGET.west()));

        boolean acted;
        try (RefusalScope ignored = GameTestSupport.refuseProtection(
                (event, actor, pos) -> event == ProtectionEvent.BLOCK_INTERACT)) {
            acted = MBItem.releaseOldest(helper.getLevel(), helper.absolutePos(TARGET), bucket,
                    context, Direction.UP);
        }

        GameTestSupport.check(!acted, "Refused block interaction did not deny entity release");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Denied entity release consumed stored snapshot");
        GameTestSupport.check(GameTestSupport.entities(helper, Pig.class, TARGET, 0.75D).isEmpty(),
                "Denied entity release added mob to world");
        helper.succeed();
    }

    /**
     * Forge only: on Fabric, placement posts only Architectury's and Common Protection API's checks,
     * which the test environment lacks. In game: a dispenser releasing a stored cod into dry air in
     * a fake-player-denying claim places no water and keeps the cod.
     */
    static void refused_place_denies_aquatic_release_water(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addSnapshot(helper, bucket, EntityType.COD, "minecraft:cod");
        ProtectionContext context = GameTestSupport.dispenserAt(helper, helper.absolutePos(TARGET.west()));

        boolean acted;
        try (RefusalScope ignored = GameTestSupport.refuseProtection(
                (event, actor, pos) -> event == ProtectionEvent.PLACE)) {
            acted = MBItem.releaseOldest(helper.getLevel(), helper.absolutePos(TARGET), bucket,
                    context, Direction.UP);
        }

        GameTestSupport.check(!acted, "Aquatic release ignored the refused water placement");
        GameTestSupport.check(BucketState.getEntityCount(bucket) == 1,
                "Denied aquatic release consumed stored snapshot");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /** In game: in adventure mode, a Big Bucket cannot collect water. */
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
     * Forge only, as for {@link #refused_place_denies_aquatic_release_water}. A placement that falls
     * through to the neighbor of the clicked block posts the place event for that neighbor. In game:
     * standing outside a claim, pour water against a block whose neighboring space is claimed.
     */
    static void refused_place_checks_fallthrough_destination(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack before = bucket.copy();
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        BlockPos neighbor = TARGET.east();
        BlockPos expectedTarget = helper.absolutePos(neighbor);
        helper.setBlock(TARGET, Blocks.STONE);

        boolean acted;
        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) -> {
            if (event != ProtectionEvent.PLACE) return false;
            GameTestSupport.check(expectedTarget.equals(pos),
                    "Place event carried the clicked block instead of the fall-through destination");
            return true;
        })) {
            acted = FluidTransactions.tryPlaceFinite(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.EAST), bucket, player,
                    InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!acted, "Refused placement at the fall-through destination was ignored");
        GameTestSupport.assertSameStack(before, bucket, "Denied neighbor placement drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.AIR);
        helper.succeed();
    }

    /**
     * Forge only: Fabric API has no item-pickup event. In game: in a claim that denies item pickup,
     * using a Junk Bucket near dropped items collects nothing.
     */
    static void refused_pickup_denies_player_junk_absorption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), TARGET);

        InteractionResultHolder<ItemStack> result;
        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) -> {
            if (event != ProtectionEvent.PICKUP) return false;
            GameTestSupport.check(actor == player, "Pickup event carried the wrong player");
            return true;
        })) {
            result = bucket.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.getResult().consumesAction(),
                "Refused pickup did not deny player Junk Bucket absorption");
        GameTestSupport.assertStored(bucket);
        GameTestSupport.check(input.isAlive() && input.getItem().getCount() == 2,
                "Denied absorption mutated the item entity");
        helper.succeed();
    }

    /** Forge only, as for {@link #refused_pickup_denies_player_junk_absorption}, with a Trash Bucket. */
    static void refused_pickup_denies_player_trash_absorption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT, 5), TARGET);

        InteractionResultHolder<ItemStack> result;
        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) -> {
            if (event != ProtectionEvent.PICKUP) return false;
            GameTestSupport.check(actor == player, "Pickup event carried the wrong player");
            return true;
        })) {
            result = bucket.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.getResult().consumesAction(),
                "Refused pickup did not deny player Trash Bucket absorption");
        GameTestSupport.assertStored(bucket);
        GameTestSupport.check(input.isAlive() && input.getItem().getCount() == 5,
                "Denied absorption mutated the item entity");
        helper.succeed();
    }

    /** In game: in adventure mode, sneak-using a Junk Bucket on a block ejects nothing. */
    static void adventure_player_cannot_eject_stored_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 3);
        BucketState.setStoredItems(bucket, java.util.List.of(food));
        Player player = adventurePlayer(helper);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.STONE);

        InteractionResult result = ((JBItem) bucket.getItem()).useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, TARGET, Direction.EAST)));

        GameTestSupport.check(!result.consumesAction(), "Adventure player ejected stored items");
        GameTestSupport.assertStored(bucket, food);
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, TARGET.east(), 0.6D).isEmpty(),
                "Denied ejection dropped an item entity");
        helper.succeed();
    }

    private static void addSnapshot(GameTestHelper helper, ItemStack bucket, EntityType<?> type, String id) {
        Entity stored = type.create(helper.getLevel());
        GameTestSupport.check(stored != null, "Could not create stored " + id + " fixture");
        CompoundTag snapshot = new CompoundTag();
        stored.saveWithoutId(snapshot);
        BucketState.addEntitySnapshot(bucket, id, snapshot);
    }

    private static Player adventurePlayer(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        return player;
    }
}
