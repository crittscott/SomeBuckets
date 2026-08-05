package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private ProtectionGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void unowned_automation_is_permitted_without_providers(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();

        GameTestSupport.check(Protections.mayAct(helper.getLevel(), ProtectionContext.unownedAutomation(),
                        ProtectionAction.BLOCK_EDIT, helper.absolutePos(TARGET), Direction.UP, bucket, null),
                "Unowned automation was denied without a denying provider");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_fluid_edit_without_mutation(GameTestHelper helper) {
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
            acted = BBFluidLogic.getInstance().tryTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny fluid edit");
        GameTestSupport.assertSameStack(before, bucket, "Denied fluid edit mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_mob_capture_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.ENTITY_INTERACT)) {
            acted = MBItem.capture(bucket, pig, context);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny mob capture");
        GameTestSupport.check(pig.isAlive(), "Denied capture removed mob");
        GameTestSupport.check(NBTUtil.getEntityCount(bucket) == 0, "Denied capture mutated Mob Bucket");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_storage_absorption_without_mutation(GameTestHelper helper) {
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
        GameTestSupport.assertStored(bucket);
        GameTestSupport.check(input.isAlive() && input.getItem().getCount() == 2,
                "Denied absorption mutated the item entity");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_automated_feeding_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack food = new ItemStack(Items.CARROT, 2);
        NBTUtil.setStoredItems(bucket, java.util.List.of(food));
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, TARGET);
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.ENTITY_INTERACT)) {
            acted = ((JBItem) bucket.getItem()).feedAnimal(
                    bucket, pig, null, true, context, Direction.EAST);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny automated feeding");
        GameTestSupport.check(!pig.isInLove(), "Denied feeding changed the animal");
        GameTestSupport.assertStored(bucket, food);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_cauldron_interaction_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));
        ProtectionContext context = ProtectionContext.dispenser(helper.absolutePos(TARGET.west()));

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> action != ProtectionAction.BLOCK_INTERACT)) {
            acted = SBFluidLogic.getInstance().tryTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, context);
        }

        GameTestSupport.check(!acted, "Claim provider did not deny cauldron interaction");
        GameTestSupport.assertSameStack(before, bucket, "Denied cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(TARGET).getValue(LayeredCauldronBlock.LEVEL) == 3,
                "Denied cauldron interaction changed fill level");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_entity_release_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Entity storedPig = EntityType.PIG.create(helper.getLevel());
        GameTestSupport.check(storedPig != null, "Could not create stored pig fixture");
        CompoundTag snapshot = new CompoundTag();
        storedPig.saveWithoutId(snapshot);
        NBTUtil.setEntityHeader(bucket, "minecraft:pig");
        NBTUtil.addEntitySnapshot(bucket, snapshot);
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void aquatic_release_requires_entity_and_fluid_permissions(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        Entity storedCod = EntityType.COD.create(helper.getLevel());
        GameTestSupport.check(storedCod != null, "Could not create stored cod fixture");
        CompoundTag snapshot = new CompoundTag();
        storedCod.saveWithoutId(snapshot);
        NBTUtil.setEntityHeader(bucket, "minecraft:cod");
        NBTUtil.addEntitySnapshot(bucket, snapshot);
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        Player player = adventurePlayer(helper);
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = BBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, player);

        GameTestSupport.check(!acted, "Adventure player collected fluid without CanPlaceOn permission");
        GameTestSupport.assertSameStack(before, bucket, "Denied pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void fallthrough_neighbor_requires_its_own_permission(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack before = bucket.copy();
        Player player = helper.makeMockSurvivalPlayer();
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
            acted = BBFluidLogic.getInstance().tryPlace(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.EAST), bucket, player);
        }

        GameTestSupport.check(!acted, "Claim denial at fall-through destination was ignored");
        GameTestSupport.assertSameStack(before, bucket, "Denied neighbor placement drained bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.AIR);
        helper.succeed();
    }

    private static Player adventurePlayer(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
        return player;
    }
}
