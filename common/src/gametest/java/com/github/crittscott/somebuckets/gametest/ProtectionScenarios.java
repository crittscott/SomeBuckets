package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.ProtectionEvent;
import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.RefusalScope;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Protection scenarios. A test listener on the loader's protection events stands in for a claim
 * mod: in game, the same denials come from claiming the target area with a claim mod (for example
 * FTB Chunks or Open Parties and Claims) and acting there as a non-member, or, for dispensers, with
 * the claim mod's fake-player access turned off.
 */
final class ProtectionScenarios {
    private static final long DISPENSER_SETTLE_TICKS = 8L;
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private ProtectionScenarios() {}

    /**
     * In game: in adventure mode, sneak and right-click the side of a block with a Junk Bucket holding
     * carrots; no carrots appear beside the clicked face and the bucket keeps them.
     */
    static void adventure_player_cannot_eject_stored_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 3);
        BucketState.setStoredItems(bucket, List.of(food));
        Player player = adventurePlayer(helper, TARGET.west(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.STONE);

        InteractionResult result = player.getMainHandItem().useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, TARGET, Direction.EAST)));

        GameTestSupport.check(!result.consumesAction(), "Adventure player ejected stored items");
        GameTestSupport.assertStored(player.getMainHandItem(), food);
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, TARGET.east(), 0.6D).isEmpty(),
                "Denied ejection dropped an item entity");
        helper.succeed();
    }

    /**
     * In game: in adventure mode, look down at a water source holding an empty Big Bucket and
     * right-click; the water stays and the bucket stays empty.
     */
    static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        Player player = adventurePlayer(helper, TARGET.above());
        player.setXRot(90.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.WATER);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Adventure player collected fluid without CanPlaceOn permission");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Denied pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    /**
     * A dispenser treats another mod taking the click at its target as refusal, since vanilla
     * dispatch would not pass that click on to the item. In game: with a mod that acts on
     * right-clicks at a cauldron, a dispenser holding a Source Bucket aimed at it does nothing extra.
     */
    static void handled_block_interaction_denies_dispenser_cauldron_fill(GameTestHelper helper) {
        dispenseSourceIntoEmptyCauldron(helper, true);
    }

    /**
     * A player's world-fluid pickup posts the block-break event as that player, from either hand.
     * In game: in someone else's claim, look down at water and right-click with an empty Big Bucket
     * in the main hand, then in the off hand; the water stays and both buckets stay empty.
     */
    static void player_fluid_pickup_posts_break_for_each_hand(GameTestHelper helper) {
        BlockPos mainTarget = TARGET;
        BlockPos offTarget = TARGET.east();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, mainTarget.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.big8());
        player.setItemInHand(InteractionHand.OFF_HAND, GameTestSupport.big8());
        helper.setBlock(mainTarget, Blocks.WATER);
        helper.setBlock(offTarget, Blocks.WATER);

        int[] refusals = {0};
        InteractionResult mainResult;
        InteractionResult offResult;
        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) -> {
            if (event != ProtectionEvent.BREAK || actor != player) return false;
            refusals[0]++;
            return true;
        })) {
            mainResult = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
            Vec3 aboveOff = Vec3.atCenterOf(helper.absolutePos(offTarget.above()));
            player.setPos(aboveOff.x, aboveOff.y, aboveOff.z);
            offResult = GameTestSupport.useItem(player, InteractionHand.OFF_HAND);
        }

        GameTestSupport.check(!mainResult.consumesAction() && !offResult.consumesAction(),
                "Refused break did not stop both hand paths");
        GameTestSupport.check(refusals[0] == 2, "Break event was not posted once for each hand, got " + refusals[0]);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertEmpty(player.getOffhandItem());
        GameTestSupport.assertBlock(helper, mainTarget, Blocks.WATER);
        GameTestSupport.assertBlock(helper, offTarget, Blocks.WATER);
        helper.succeed();
    }

    /**
     * Forge only, as for {@link #refused_place_denies_aquatic_release_water}. A placement that falls
     * through to the neighbor of the clicked block posts the place event for that neighbor. In game:
     * standing outside a claim, right-click the side of a block with a Big Bucket of water where the
     * space beside that side is claimed; no water appears and the bucket keeps both units.
     */
    static void refused_place_checks_fallthrough_destination(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack before = bucket.copy();
        BlockPos neighbor = TARGET.east();
        BlockPos expectedTarget = helper.absolutePos(neighbor);
        helper.setBlock(TARGET, Blocks.STONE);
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.east(2));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(TARGET)));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult result;
        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) -> {
            if (event != ProtectionEvent.PLACE || actor != player) return false;
            GameTestSupport.check(expectedTarget.equals(pos),
                    "Place event carried the clicked block instead of the fall-through destination");
            return true;
        })) {
            result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.consumesAction(), "Refused placement at the fall-through destination was ignored");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Denied neighbor placement drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.AIR);
        helper.succeed();
    }

    /**
     * Forge only: on Fabric, placement posts only Architectury's and Common Protection API's checks,
     * which the test environment lacks. In game: a dispenser holding a Mob Bucket with a cod, facing
     * dry air inside a claim that denies fake players, places no water and keeps the cod.
     */
    static void refused_place_denies_aquatic_release_water(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        ItemStack bucket = GameTestSupport.mob();
        GameTestSupport.addSnapshot(bucket, helper.getLevel(), EntityType.COD);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, dispenserPos, Direction.EAST, bucket);
        BlockPos source = helper.absolutePos(dispenserPos);
        RefusalScope scope = GameTestSupport.refuseProtection((event, actor, pos) ->
                event == ProtectionEvent.PLACE && GameTestSupport.isDispenserActor(actor, source));

        GameTestSupport.triggerDispenser(helper, dispenserPos);
        helper.runAfterDelay(DISPENSER_SETTLE_TICKS, () -> {
            try {
                GameTestSupport.check(BucketState.getEntityCount(dispenser.getItem(0)) == 1,
                        "Denied aquatic release consumed the stored cod");
                GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
                GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, TARGET, 1.5D).isEmpty(),
                        "Denied aquatic release added a cod to the world");
            } finally {
                scope.close();
            }
            helper.succeed();
        });
    }

    /**
     * Forge only: Fabric API has no item-pickup event. In game: a dispenser with an empty Junk Bucket
     * facing two dropped diamonds, inside a claim that denies fake-player pickup, leaves them on the
     * ground.
     */
    static void refused_pickup_denies_dispenser_absorption(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, dispenserPos, Direction.EAST,
                GameTestSupport.junk());
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), TARGET);
        BlockPos source = helper.absolutePos(dispenserPos);
        RefusalScope scope = GameTestSupport.refuseProtection((event, actor, pos) ->
                event == ProtectionEvent.PICKUP && GameTestSupport.isDispenserActor(actor, source));

        GameTestSupport.triggerDispenser(helper, dispenserPos);
        helper.runAfterDelay(DISPENSER_SETTLE_TICKS, () -> {
            try {
                GameTestSupport.assertStored(dispenser.getItem(0));
                GameTestSupport.check(input.isAlive() && input.getItem().getCount() == 2,
                        "Denied absorption mutated the item entity");
            } finally {
                scope.close();
            }
            helper.succeed();
        });
    }

    /**
     * Forge only: Fabric API has no item-pickup event. In game: in a claim that denies item pickup,
     * right-click with a Junk Bucket next to two dropped diamonds; they stay on the ground.
     */
    static void refused_pickup_denies_player_junk_absorption(GameTestHelper helper) {
        assertPlayerAbsorptionRefused(helper, GameTestSupport.junk(), new ItemStack(Items.DIAMOND, 2));
    }

    /**
     * Forge only: Fabric API has no item-pickup event. In game: in a claim that denies item pickup,
     * right-click with a Trash Bucket next to five dropped dirt; they stay on the ground.
     */
    static void refused_pickup_denies_player_trash_absorption(GameTestHelper helper) {
        assertPlayerAbsorptionRefused(helper, GameTestSupport.trash(), new ItemStack(Items.DIRT, 5));
    }

    /**
     * Control for {@link #handled_block_interaction_denies_dispenser_cauldron_fill}: with no mod
     * taking the click, the dispenser's water Source Bucket fills the empty cauldron and stays
     * assigned. In game: a dispenser holding a water Source Bucket facing an empty cauldron fills it.
     */
    static void unhandled_block_interaction_lets_dispenser_fill_cauldron(GameTestHelper helper) {
        dispenseSourceIntoEmptyCauldron(helper, false);
    }

    /* A mock player in adventure mode standing at relative. */
    private static Player adventurePlayer(GameTestHelper helper, BlockPos relative) {
        Player player = GameTestSupport.survivalPlayer(helper, relative);
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        return player;
    }

    private static void assertPlayerAbsorptionRefused(GameTestHelper helper, ItemStack bucket, ItemStack dropped) {
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        ItemEntity input = GameTestSupport.spawnItem(helper, dropped.copy(), TARGET);

        InteractionResult result;
        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) ->
                event == ProtectionEvent.PICKUP && actor == player)) {
            result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.consumesAction(), "Refused pickup did not deny the bucket's absorption");
        GameTestSupport.assertStored(player.getMainHandItem());
        GameTestSupport.check(input.isAlive() && input.getItem().getCount() == dropped.getCount(),
                "Denied absorption mutated the item entity");
        helper.succeed();
    }

    private static void dispenseSourceIntoEmptyCauldron(GameTestHelper helper, boolean handled) {
        BlockPos dispenserPos = TARGET.west();
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, dispenserPos, Direction.EAST, bucket);
        helper.setBlock(TARGET, Blocks.CAULDRON);
        BlockPos cauldron = helper.absolutePos(TARGET);
        RefusalScope scope = GameTestSupport.refuseProtection((event, actor, pos) ->
                handled && event == ProtectionEvent.BLOCK_INTERACT_HANDLED && cauldron.equals(pos));

        GameTestSupport.triggerDispenser(helper, dispenserPos);
        helper.runAfterDelay(DISPENSER_SETTLE_TICKS, () -> {
            try {
                GameTestSupport.assertSameStack(before, dispenser.getItem(0),
                        "Dispenser changed the Source Bucket assignment");
                GameTestSupport.assertBlock(helper, TARGET, handled ? Blocks.CAULDRON : Blocks.WATER_CAULDRON);
            } finally {
                scope.close();
            }
            helper.succeed();
        });
    }
}
