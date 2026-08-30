package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.AutomationPlayers;
import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.protection.Protections;
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

final class ProtectionScenarios {
    private ProtectionScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);
    static void unowned_automation_is_permitted_without_providers(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();

        GameTestSupport.check(Protections.mayAct(helper.getLevel(), ProtectionContext.unownedAutomation(),
                        ProtectionAction.BLOCK_EDIT, helper.absolutePos(TARGET), Direction.UP, bucket, null),
                "Unowned automation was denied without a denying provider");
        helper.succeed();
    }
    static void player_fluid_context_preserves_main_and_offhand(GameTestHelper helper) {
        BlockPos mainTarget = TARGET;
        BlockPos offTarget = TARGET.east();
        ItemStack mainBucket = GameTestSupport.big8();
        ItemStack offBucket = GameTestSupport.big8();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, mainBucket);
        player.setItemInHand(InteractionHand.OFF_HAND, offBucket);
        helper.setBlock(mainTarget, Blocks.WATER);
        helper.setBlock(offTarget, Blocks.WATER);

        int[] providerCalls = {0};
        boolean mainActed;
        boolean offActed;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.FLUID_EDIT) return true;
                    InteractionHand expected = providerCalls[0]++ == 0
                            ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    GameTestSupport.check(actor.player() == player,
                            "Provider received the wrong player context");
                    GameTestSupport.check(actor.hand() == expected,
                            "Provider received " + actor.hand() + " instead of " + expected);
                    return false;
                })) {
            mainActed = BucketOperations.get().tryBigTake(
                    helper.getLevel(), GameTestSupport.hit(helper, mainTarget, Direction.UP), mainBucket,
                    player, InteractionHand.MAIN_HAND);
            offActed = BucketOperations.get().tryBigTake(
                    helper.getLevel(), GameTestSupport.hit(helper, offTarget, Direction.UP), offBucket,
                    player, InteractionHand.OFF_HAND);
        }

        GameTestSupport.check(!mainActed && !offActed, "Provider denial did not stop both hand paths");
        GameTestSupport.check(providerCalls[0] == 2, "Provider did not see both hand paths");
        GameTestSupport.assertEmpty(mainBucket);
        GameTestSupport.assertEmpty(offBucket);
        GameTestSupport.assertBlock(helper, mainTarget, Blocks.WATER);
        GameTestSupport.assertBlock(helper, offTarget, Blocks.WATER);
        helper.succeed();
    }
    static void registered_provider_denies_fluid_edit_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER);
        BlockPos expectedSource = helper.absolutePos(TARGET.west());
        BlockPos expectedTarget = helper.absolutePos(TARGET);
        ProtectionContext context = ProtectionContext.dispenser(expectedSource);

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.FLUID_EDIT) return true;
                    GameTestSupport.check(expectedSource.equals(actor.automationSource()),
                            "Provider received wrong automation source");
                    GameTestSupport.check(expectedTarget.equals(target),
                            "Provider received wrong mutation target");
                    return false;
                })) {
            acted = GameTestSupport.tryBigTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny fluid edit");
        GameTestSupport.assertSameStack(before, bucket, "Denied fluid edit mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }
    static void registered_provider_denies_mob_capture_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.ENTITY_INTERACT)) {
            acted = MBItem.capture(bucket, pig, context, Direction.UP);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny mob capture");
        GameTestSupport.check(pig.isAlive(), "Denied capture removed mob");
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 0, "Denied capture mutated Mob Bucket");
        helper.succeed();
    }
    static void registered_provider_denies_storage_absorption_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), TARGET);
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.ENTITY_INTERACT)) {
            acted = ((JBItem) bucket.getItem()).absorbItemEntities(helper.getLevel(), bucket,
                    java.util.List.of(input), context, Direction.EAST);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny storage-bucket absorption");
        GameTestSupport.assertStored(helper, bucket);
        GameTestSupport.check(input.isAlive() && input.getItem().getCount() == 2,
                "Denied absorption mutated the item entity");
        helper.succeed();
    }
    static void registered_provider_denies_automated_feeding_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 2);
        NBTUtil.setStoredItems(bucket, java.util.List.of(food), helper.getLevel().registryAccess());
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.ENTITY_INTERACT)) {
            acted = ((JBItem) bucket.getItem()).feedAnimal(
                    bucket, pig, null, InteractionHand.MAIN_HAND, context, Direction.EAST);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny automated feeding");
        GameTestSupport.check(!pig.isInLove(), "Denied feeding changed the animal");
        GameTestSupport.assertStored(helper, bucket, food);
        helper.succeed();
    }
    static void registered_provider_denies_cauldron_interaction_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.BLOCK_INTERACT)) {
            acted = GameTestSupport.trySourceTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny cauldron interaction");
        GameTestSupport.assertSameStack(before, bucket, "Denied cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(TARGET).getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL,
                "Denied cauldron interaction changed fill level");
        helper.succeed();
    }
    static void registered_provider_denies_entity_release_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Entity storedPig = EntityType.PIG.create(helper.getLevel());
        GameTestSupport.check(storedPig != null, "Could not create stored pig fixture");
        CompoundTag snapshot = new CompoundTag();
        storedPig.saveWithoutId(snapshot);
        NBTUtil.addEntitySnapshot(bucket, "minecraft:pig", snapshot);
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.ENTITY_RELEASE)) {
            acted = MBItem.releaseOldest(helper.getLevel(), helper.absolutePos(TARGET), bucket,
                    context, Direction.UP);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny entity release");
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 1,
                "Denied entity release consumed stored snapshot");
        GameTestSupport.check(GameTestSupport.entities(helper, Pig.class, TARGET, 0.75D).isEmpty(),
                "Denied entity release added mob to world");
        helper.succeed();
    }
    static void aquatic_release_requires_entity_and_fluid_permissions(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Entity storedCod = EntityType.COD.create(helper.getLevel());
        GameTestSupport.check(storedCod != null, "Could not create stored cod fixture");
        CompoundTag snapshot = new CompoundTag();
        storedCod.saveWithoutId(snapshot);
        NBTUtil.addEntitySnapshot(bucket, "minecraft:cod", snapshot);
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.FLUID_EDIT)) {
            acted = MBItem.releaseOldest(helper.getLevel(), helper.absolutePos(TARGET), bucket,
                    context, Direction.UP);
        }

        GameTestSupport.check(!acted, "Aquatic release ignored denied water edit");
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 1,
                "Denied aquatic release consumed stored snapshot");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    static void blockedit_denial_stops_replaceable_fluid_destruction(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Entity storedCod = EntityType.COD.create(helper.getLevel());
        GameTestSupport.check(storedCod != null, "Could not create stored cod fixture");
        CompoundTag snapshot = new CompoundTag();
        storedCod.saveWithoutId(snapshot);
        NBTUtil.addEntitySnapshot(bucket, "minecraft:cod", snapshot);
        helper.setBlock(TARGET, Blocks.SHORT_GRASS);
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.BLOCK_EDIT)) {
            acted = MBItem.releaseOldest(helper.getLevel(), helper.absolutePos(TARGET), bucket,
                    context, Direction.UP);
        }

        GameTestSupport.check(!acted, "Aquatic release ignored denied block edit at the replaceable block");
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 1,
                "Denied block edit consumed stored snapshot");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.SHORT_GRASS);
        helper.succeed();
    }
    static void automation_player_provider_is_installed(GameTestHelper helper) {
        GameTestSupport.check(AutomationPlayers.get(helper.getLevel()) != null,
                "Loader did not install the dispenser automation player provider");
        helper.succeed();
    }
    static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        Player player = adventurePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = BucketOperations.get().tryBigTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, player,
                InteractionHand.MAIN_HAND);

        GameTestSupport.check(!acted, "Adventure player collected fluid without CanPlaceOn permission");
        GameTestSupport.assertSameStack(before, bucket, "Denied pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }
    static void fallthrough_neighbor_requires_its_own_permission(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        BlockPos neighbor = TARGET.east();
        BlockPos expectedTarget = helper.absolutePos(neighbor);
        helper.setBlock(TARGET, Blocks.STONE);

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.FLUID_EDIT) return true;
                    GameTestSupport.check(expectedTarget.equals(target),
                            "Provider received clicked block instead of fall-through destination");
                    return false;
                })) {
            acted = BucketOperations.get().tryBigPlace(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.EAST), bucket, player,
                    InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!acted, "Claim denial at fall-through destination was ignored");
        GameTestSupport.assertSameStack(before, bucket, "Denied neighbor placement drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.AIR);
        helper.succeed();
    }
    static void registered_provider_denies_player_storage_absorption_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), TARGET);

        InteractionResultHolder<ItemStack> result;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.ENTITY_INTERACT) return true;
                    GameTestSupport.check(!actor.isAutomation(),
                            "Provider saw an automation actor for a player vacuum");
                    GameTestSupport.check(actor.player() == player,
                            "Provider received the wrong acting player");
                    return false;
                })) {
            result = bucket.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.getResult().consumesAction(),
                "Claim provider did not deny player Junk Bucket absorption");
        GameTestSupport.assertStored(helper, bucket);
        GameTestSupport.check(input.isAlive() && input.getItem().getCount() == 2,
                "Denied absorption mutated the item entity");
        helper.succeed();
    }
    static void registered_provider_denies_player_trash_absorption_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT, 5), TARGET);

        InteractionResultHolder<ItemStack> result;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.ENTITY_INTERACT) return true;
                    GameTestSupport.check(!actor.isAutomation(),
                            "Provider saw an automation actor for a player vacuum");
                    GameTestSupport.check(actor.player() == player,
                            "Provider received the wrong acting player");
                    return false;
                })) {
            result = bucket.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.getResult().consumesAction(),
                "Claim provider did not deny player Trash Bucket absorption");
        GameTestSupport.assertStored(helper, bucket);
        GameTestSupport.check(input.isAlive() && input.getItem().getCount() == 5,
                "Denied absorption mutated the item entity");
        helper.succeed();
    }
    static void registered_provider_denies_player_ejection_at_drop_pos(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 3);
        NBTUtil.setStoredItems(bucket, java.util.List.of(food), helper.getLevel().registryAccess());
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos expectedDropPos = helper.absolutePos(TARGET.east());

        InteractionResult result;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.ENTITY_RELEASE) return true;
                    GameTestSupport.check(expectedDropPos.equals(target),
                            "Provider received the clicked block instead of the drop position");
                    GameTestSupport.check(!actor.isAutomation() && actor.player() == player,
                            "Provider received the wrong acting player");
                    return false;
                })) {
            result = ((JBItem) bucket.getItem()).useOn(new UseOnContext(
                    player, InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, TARGET, Direction.EAST)));
        }

        GameTestSupport.check(!result.consumesAction(), "Claim provider did not deny player ejection");
        GameTestSupport.assertStored(helper, bucket, food);
        GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, TARGET.east(), 0.6D).isEmpty(),
                "Denied ejection dropped an item entity");
        helper.succeed();
    }
    static void registered_provider_denies_player_feeding_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 2);
        NBTUtil.setStoredItems(bucket, java.util.List.of(food), helper.getLevel().registryAccess());
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.west());
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);

        InteractionResult result;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.ENTITY_INTERACT) return true;
                    GameTestSupport.check(!actor.isAutomation() && actor.player() == player,
                            "Provider received the wrong feeding player");
                    return false;
                })) {
            result = ((JBItem) bucket.getItem()).interactLivingEntity(bucket, player, pig, InteractionHand.MAIN_HAND);
        }

        GameTestSupport.check(!result.consumesAction(), "Claim provider did not deny player feeding");
        GameTestSupport.check(!pig.isInLove(), "Denied feeding changed the animal");
        GameTestSupport.assertStored(helper, bucket, food);
        helper.succeed();
    }

    private static Player adventurePlayer(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, TARGET);
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        return player;
    }
}

