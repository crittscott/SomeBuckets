package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.protection.AutomationPlayers;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

final class ProtectionScenarios {
    private ProtectionScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);
    /** Dispenser facing east into {@link #TARGET}. */
    private static final BlockPos DISPENSER = TARGET.west();
    private static final long DISPENSER_SETTLE_TICKS = 8L;

    /**
     * No in-game form: guards the protection rule itself, that automation with no claim mod
     * installed may act inside the world border and a dispenser may not edit outside it.
     */
    static void unowned_automation_is_permitted_without_providers(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();

        GameTestSupport.check(Protections.mayAct(helper.getLevel(), ProtectionContext.unownedAutomation(),
                        ProtectionAction.BLOCK_EDIT, helper.absolutePos(TARGET), Direction.UP, bucket, null),
                "Unowned automation was denied without a denying provider");
        BlockPos outsideBorder = new BlockPos(30_000_000, helper.absolutePos(TARGET).getY(), 0);
        GameTestSupport.check(!Protections.mayAct(helper.getLevel(),
                        ProtectionContext.dispenser(helper.absolutePos(TARGET)), ProtectionAction.BLOCK_EDIT,
                        outsideBorder, Direction.UP, bucket, null),
                "Dispenser was allowed to edit outside the world border");
        helper.succeed();
    }

    /**
     * In game: in a claim you may not edit, hold an empty Big Bucket in each hand, look down at a
     * water source, and right-click; neither bucket collects the water, and the claim mod is asked
     * once for each hand.
     */
    static void player_fluid_context_preserves_main_and_offhand(GameTestHelper helper) {
        ItemStack mainBucket = GameTestSupport.big8();
        ItemStack offBucket = GameTestSupport.big8();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, mainBucket);
        player.setItemInHand(InteractionHand.OFF_HAND, offBucket);
        helper.setBlock(TARGET, Blocks.WATER);

        for (InteractionHand hand : InteractionHand.values()) {
            int[] providerCalls = {0};
            InteractionResult result;
            try (Protections.Registration ignored = Protections.register(
                    (level, actor, action, target, face, held, entity) -> {
                        if (action != ProtectionAction.FLUID_EDIT || actor.player() != player) return true;
                        providerCalls[0]++;
                        GameTestSupport.check(actor.hand() == hand,
                                "Provider received " + actor.hand() + " instead of " + hand);
                        return false;
                    })) {
                result = GameTestSupport.useItem(player, hand);
            }
            GameTestSupport.check(!result.consumesAction(), "Provider denial did not stop the " + hand + " use");
            GameTestSupport.check(providerCalls[0] > 0, "Provider did not see the " + hand + " use");
        }

        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertEmpty(player.getOffhandItem());
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    /**
     * In game: in a claim where the dispenser's owner may not interact with entities, power a
     * dispenser holding an empty Junk Bucket with two diamonds lying in front of it; the diamonds
     * stay on the ground and the bucket stays empty.
     */
    static void registered_provider_denies_storage_absorption_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), TARGET);

        Protections.Registration registration = refuseDispenser(helper, ProtectionAction.ENTITY_INTERACT);
        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(DISPENSER_SETTLE_TICKS, () -> {
            try {
                GameTestSupport.assertStored(helper, dispenser.getItem(0));
                GameTestSupport.check(input.isAlive() && input.getItem().getCount() == 2,
                        "Denied absorption mutated the item entity");
            } finally {
                registration.close();
            }
            helper.succeed();
        });
    }

    /**
     * In game: in a claim where the dispenser's owner may not place fluids, power a dispenser holding
     * a Mob Bucket with a cod facing open air; no cod and no water appear, and the cod stays in the
     * bucket.
     */
    static void aquatic_release_requires_entity_and_fluid_permissions(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST,
                GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null));

        Protections.Registration registration = refuseDispenser(helper, ProtectionAction.FLUID_EDIT);
        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(DISPENSER_SETTLE_TICKS, () -> {
            try {
                GameTestSupport.check(BucketState.getEntityCount(dispenser.getItem(0)) == 1,
                        "Denied aquatic release consumed stored snapshot");
                GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
                GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, TARGET, 0.75D).isEmpty(),
                        "Denied aquatic release added a cod");
            } finally {
                registration.close();
            }
            helper.succeed();
        });
    }

    /**
     * In game: in a claim where the dispenser's owner may not break or place blocks, power a
     * dispenser holding a Mob Bucket with a cod facing short grass; the grass stays, and the cod
     * stays in the bucket.
     */
    static void blockedit_denial_stops_replaceable_fluid_destruction(GameTestHelper helper) {
        // Grass needs soil, or the dispenser's own block updates pop it before the release.
        helper.setBlock(TARGET.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(TARGET, Blocks.SHORT_GRASS);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST,
                GameTestSupport.storedMob(helper.getLevel(), EntityType.COD, null));

        Protections.Registration registration = refuseDispenser(helper, ProtectionAction.BLOCK_EDIT);
        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(DISPENSER_SETTLE_TICKS, () -> {
            try {
                GameTestSupport.check(BucketState.getEntityCount(dispenser.getItem(0)) == 1,
                        "Denied block edit consumed stored snapshot");
                GameTestSupport.assertBlock(helper, TARGET, Blocks.SHORT_GRASS);
            } finally {
                registration.close();
            }
            helper.succeed();
        });
    }

    /**
     * In game: in a claim where you may not break or place blocks, hold a full Big Bucket of water
     * and right-click short grass; the grass stays and the bucket stays full.
     */
    static void blockedit_denial_stops_arbitrary_fluid_placement(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.SHORT_GRASS);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult result;
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, entity) ->
                        action != ProtectionAction.BLOCK_EDIT || actor.player() != player)) {
            result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.consumesAction(),
                "Arbitrary fluid placement ignored denied block edit at the replaceable block");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Denied block edit drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.SHORT_GRASS);
        helper.succeed();
    }

    /**
     * No in-game form: guards that the loader installed the fake player dispensers act as, which
     * the FTB Chunks claim check asks about. Wrapped only on loaders with that integration.
     */
    static void automation_player_provider_is_installed(GameTestHelper helper) {
        GameTestSupport.check(AutomationPlayers.get(helper.getLevel()) != null,
                "Loader did not install the dispenser automation player provider");
        helper.succeed();
    }

    /**
     * In game: in adventure mode, hold an empty Big Bucket and right-click a water source; the bucket
     * stays empty and the water stays, as with a vanilla bucket.
     */
    static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.WATER);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Adventure player collected fluid without CanPlaceOn permission");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Denied pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    /**
     * In game: stand outside a claim and, with a Big Bucket of water, right-click the side of a
     * stone block whose neighbor on that side is inside a claim you may not edit; no water appears
     * there and the bucket keeps its water.
     */
    static void fallthrough_neighbor_requires_its_own_permission(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack before = bucket.copy();
        BlockPos neighbor = TARGET.east();
        BlockPos expectedTarget = helper.absolutePos(neighbor);
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(neighbor, Blocks.AIR);
        helper.setBlock(TARGET.east(2), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.east(3), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult result;
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.FLUID_EDIT || actor.player() != player) return true;
                    GameTestSupport.check(expectedTarget.equals(target),
                            "Provider received clicked block instead of fall-through destination");
                    return false;
                })) {
            result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.consumesAction(), "Claim denial at fall-through destination was ignored");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Denied neighbor placement drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: in a claim where you may not pick up items, hold an empty Junk Bucket next to two
     * dropped diamonds and right-click; the diamonds stay on the ground.
     */
    static void registered_provider_denies_player_storage_absorption_without_mutation(GameTestHelper helper) {
        assertPlayerAbsorptionDenied(helper, GameTestSupport.junk(), new ItemStack(Items.DIAMOND, 2));
    }

    /**
     * In game: in a claim where you may not pick up items, hold an empty Trash Bucket next to five
     * dropped dirt and right-click; the dirt stays on the ground.
     */
    static void registered_provider_denies_player_trash_absorption_without_mutation(GameTestHelper helper) {
        assertPlayerAbsorptionDenied(helper, GameTestSupport.trash(), new ItemStack(Items.DIRT, 5));
    }

    /**
     * In game: in a claim where you may not drop items, sneak and right-click the side of a block with
     * a Junk Bucket holding carrots; no carrots come out. The claim mod is asked first about the
     * space beside the clicked face.
     */
    static void registered_provider_denies_player_ejection_at_drop_pos(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 3);
        BucketState.setStoredItems(bucket, List.of(food));
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos expectedDropPos = helper.absolutePos(TARGET.east());

        List<BlockPos> releaseTargets = new ArrayList<>();
        InteractionResult result;
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.ENTITY_RELEASE || actor.player() != player) return true;
                    GameTestSupport.check(!actor.isAutomation(), "Provider saw an automation actor");
                    releaseTargets.add(target);
                    return false;
                })) {
            result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET, Direction.EAST));
        }

        GameTestSupport.check(!result.consumesAction(), "Claim provider did not deny player ejection");
        GameTestSupport.check(!releaseTargets.isEmpty() && releaseTargets.get(0).equals(expectedDropPos),
                "Provider was not first asked about the drop position: " + releaseTargets);
        GameTestSupport.assertStored(helper, player.getMainHandItem(), food);
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, TARGET, 3.0D).isEmpty(),
                "Denied ejection dropped an item entity");
        helper.succeed();
    }

    /**
     * In game: in a claim where you may not interact with animals, right-click a pig with a Junk
     * Bucket holding carrots; the pig is not fed and the carrots stay in the bucket.
     */
    static void registered_provider_denies_player_feeding_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 2);
        BucketState.setStoredItems(bucket, List.of(food));
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);

        InteractionResult result;
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.ENTITY_INTERACT) return true;
                    GameTestSupport.check(!actor.isAutomation() && actor.player() == player,
                            "Provider received the wrong feeding player");
                    return false;
                })) {
            result = bucket.getItem().interactLivingEntity(bucket, player, pig, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.consumesAction(), "Claim provider did not deny player feeding");
        GameTestSupport.check(!pig.isInLove(), "Denied feeding changed the animal");
        GameTestSupport.assertStored(helper, bucket, food);
        helper.succeed();
    }

    /* Right-clicks a held storage bucket next to {@code input} with every pickup by that player refused. */
    private static void assertPlayerAbsorptionDenied(GameTestHelper helper, ItemStack bucket, ItemStack input) {
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        int count = input.getCount();
        ItemEntity entity = GameTestSupport.spawnItem(helper, input, TARGET);

        InteractionResult result;
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, e) -> {
                    if (action != ProtectionAction.ENTITY_INTERACT || actor.player() != player) return true;
                    GameTestSupport.check(!actor.isAutomation(),
                            "Provider saw an automation actor for a player vacuum");
                    return false;
                })) {
            result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.consumesAction(), "Claim provider did not deny player absorption");
        GameTestSupport.assertStored(helper, player.getMainHandItem());
        GameTestSupport.check(entity.isAlive() && entity.getItem().getCount() == count,
                "Denied absorption mutated the item entity");
        helper.succeed();
    }

    /*
     * Refuses {@code refused} for the dispenser at DISPENSER until closed; other actors and actions
     * are allowed, so concurrent tests are unaffected.
     */
    private static Protections.Registration refuseDispenser(GameTestHelper helper, ProtectionAction refused) {
        BlockPos source = helper.absolutePos(DISPENSER);
        return Protections.register((level, actor, action, target, face, held, entity) ->
                level != helper.getLevel() || action != refused || !source.equals(actor.automationSource()));
    }
}
