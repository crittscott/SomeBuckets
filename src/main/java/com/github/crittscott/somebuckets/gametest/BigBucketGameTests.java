package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class BigBucketGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private BigBucketGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_bucket_collects_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = BBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(acted, "Empty Big Bucket did not collect water source");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_bucket_collects_matching_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = BBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(acted, "Partial Big Bucket did not collect matching source");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 2000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void bucket_refuses_different_source_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = BBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(!acted, "Big Bucket mixed water and lava");
        GameTestSupport.assertSameStack(before, bucket, "Rejected source pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void full_bucket_refuses_another_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = BBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(!acted, "Full Big Bucket collected another source");
        GameTestSupport.assertSameStack(before, bucket, "Rejected full pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void placement_consumes_one_unit_and_final_unit_normalizes(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);

        boolean acted = BBFluidLogic.getInstance().tryPlace(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(acted, "Big Bucket did not place water");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void placement_falls_through_solid_clicked_block(GameTestHelper helper) {
        BlockPos neighbor = TARGET.east();
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        helper.setBlock(TARGET, Blocks.STONE);

        boolean acted = BBFluidLogic.getInstance().tryPlace(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.EAST), bucket, null);

        GameTestSupport.check(acted, "Fluid did not fall through to clicked-face neighbor");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.WATER);
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void placement_waterlogs_liquid_container(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        helper.setBlock(TARGET, Blocks.OAK_FENCE);

        boolean acted = BBFluidLogic.getInstance().tryPlace(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(acted, "Big Bucket did not waterlog fence");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isSource(),
                "Waterlogged fence did not contain a source fluid state");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_collects_and_places_one_block(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);

        boolean collected = BBFluidLogic.getInstance().tryTakePowder(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);
        boolean placed = BBFluidLogic.getInstance().tryPlacePowder(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(collected, "Big Bucket did not collect powder snow");
        GameTestSupport.check(placed, "Big Bucket did not place powder snow");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_capacity_is_enforced(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 8);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);

        boolean acted = BBFluidLogic.getInstance().tryTakePowder(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(!acted, "Full powder-snow bucket collected another block");
        GameTestSupport.assertSameStack(before, bucket, "Rejected powder pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adult_cow_adds_milk_but_baby_does_not(GameTestHelper helper) {
        BBItem item = (BBItem) GameTestSupport.big8().getItem();
        ItemStack adultBucket = GameTestSupport.big8();
        ItemStack babyBucket = GameTestSupport.big8();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        Cow adult = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(3, 2, 2));
        Cow baby = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(5, 2, 2));
        baby.setAge(-24000);

        InteractionResult adultResult = item.interactLivingEntity(
                adultBucket, player, adult, InteractionHand.MAIN_HAND);
        InteractionResult babyResult = item.interactLivingEntity(
                babyBucket, player, baby, InteractionHand.MAIN_HAND);

        GameTestSupport.check(adultResult.consumesAction(), "Adult cow milking did not succeed");
        GameTestSupport.check(!babyResult.consumesAction(), "Baby cow was milked");
        GameTestSupport.assertMilk(adultBucket, 1000);
        GameTestSupport.assertEmpty(babyBucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void drinking_milk_removes_effect_and_consumes_one_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), 2000);
        BBItem item = (BBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));

        item.finishUsingItem(bucket, helper.getLevel(), player);

        GameTestSupport.check(!player.hasEffect(MobEffects.POISON), "Milk did not remove status effect");
        GameTestSupport.assertMilk(bucket, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void shift_use_in_air_discards_contents(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 3000);
        BBItem item = (BBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        player.setXRot(-90.0F);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
}
