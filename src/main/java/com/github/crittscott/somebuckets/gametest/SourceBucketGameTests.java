package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.ServerConfig;
import com.github.crittscott.somebuckets.config.SourceBucketPolicy;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.item.SBItem;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.List;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class SourceBucketGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private SourceBucketGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_source_acquires_world_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = SBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(acted, "Empty Source Bucket did not acquire lava");
        GameTestSupport.assertFluid(bucket, Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void waterlogged_block_assigns_source_and_survives(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));

        boolean acted = SBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(acted, "Source Bucket did not take water from a waterlogged block");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Waterlogged block kept its water after pickup");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void assigned_source_refuses_reassignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = SBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(!acted, "Assigned Source Bucket changed fluid");
        GameTestSupport.assertSameStack(before, bucket, "Rejected reassignment mutated Source Bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_places_repeatedly_without_consumption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        BlockPos first = new BlockPos(3, 2, 4);
        BlockPos second = new BlockPos(5, 2, 4);

        boolean firstActed = SBFluidLogic.getInstance().tryPlace(
                helper.getLevel(), GameTestSupport.hit(helper, first, Direction.UP), bucket, null);
        boolean secondActed = SBFluidLogic.getInstance().tryPlace(
                helper.getLevel(), GameTestSupport.hit(helper, second, Direction.UP), bucket, null);

        GameTestSupport.check(firstActed && secondActed, "Source Bucket did not place repeatedly");
        GameTestSupport.assertBlock(helper, first, Blocks.WATER);
        GameTestSupport.assertBlock(helper, second, Blocks.WATER);
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_source_acquires_full_water_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));

        boolean acted = SBFluidLogic.getInstance().tryTake(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(acted, "Source Bucket did not acquire full water cauldron");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.CAULDRON);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_fills_empty_cauldron_without_consumption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        helper.setBlock(TARGET, Blocks.CAULDRON);

        boolean acted = SBFluidLogic.getInstance().tryPlace(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket, null);

        GameTestSupport.check(acted, "Lava Source Bucket did not fill cauldron");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertFluid(bucket, Fluids.LAVA, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adult_cow_assigns_milk_but_baby_does_not(GameTestHelper helper) {
        ItemStack adultBucket = GameTestSupport.source();
        ItemStack babyBucket = GameTestSupport.source();
        SBItem item = (SBItem) adultBucket.getItem();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        Cow adult = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(3, 2, 2));
        Cow baby = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(5, 2, 2));
        baby.setAge(-24000);

        InteractionResult adultResult = item.interactLivingEntity(
                adultBucket, player, adult, InteractionHand.MAIN_HAND);
        InteractionResult babyResult = item.interactLivingEntity(
                babyBucket, player, baby, InteractionHand.MAIN_HAND);

        GameTestSupport.check(adultResult.consumesAction(), "Adult cow did not assign Source Bucket milk");
        GameTestSupport.check(!babyResult.consumesAction(), "Baby cow assigned Source Bucket milk");
        GameTestSupport.assertMilk(adultBucket, 1000);
        GameTestSupport.assertEmpty(babyBucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_milk_is_not_consumed_by_drinking(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.source(), 1000);
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));

        item.finishUsingItem(bucket, helper.getLevel(), player);

        GameTestSupport.check(!player.hasEffect(MobEffects.POISON), "Source milk did not remove effect");
        GameTestSupport.assertMilk(bucket, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void shift_use_in_air_clears_source_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        player.setXRot(-90.0F);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_does_not_support_powder_snow(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        SBItem item = (SBItem) bucket.getItem();
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_allow_list_blocks_input_output_and_fuel_without_affecting_big_buckets(
            GameTestHelper helper) {
        List<? extends String> original = List.copyOf(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get());
        ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.set(List.of("minecraft:water"));
        SourceBucketPolicy.refresh("SourceBucketGameTests");

        try {
            ItemStack emptySource = GameTestSupport.source();
            helper.setBlock(TARGET, Blocks.LAVA);
            boolean tookLava = SBFluidLogic.getInstance().tryTake(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), emptySource, null);

            GameTestSupport.check(!tookLava, "Disabled lava assigned an empty Source Bucket");
            GameTestSupport.assertEmpty(emptySource);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);

            ItemStack lavaSource = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
            IFluidHandlerItem sourceHandler = lavaSource.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                    .orElseThrow(() -> new IllegalStateException("Source Bucket exposed no fluid capability"));
            FluidStack drained = sourceHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            BlockPos placeTarget = TARGET.offset(1, 0, 0);
            boolean placed = SBFluidLogic.getInstance().tryPlace(
                    helper.getLevel(), GameTestSupport.hit(helper, placeTarget, Direction.UP), lavaSource, null);

            GameTestSupport.check(drained.isEmpty(), "Disabled Source Bucket supplied fluid capability output");
            GameTestSupport.check(!placed, "Disabled Source Bucket placed world fluid");
            GameTestSupport.assertBlock(helper, placeTarget, Blocks.AIR);
            GameTestSupport.check(ForgeHooks.getBurnTime(lavaSource, RecipeType.SMELTING) == 0,
                    "Disabled lava Source Bucket remained furnace fuel");
            GameTestSupport.check(!SourceBucketPolicy.allowsMilk(), "Milk remained allowed after removal");
            GameTestSupport.assertFluid(lavaSource, Fluids.LAVA, 1000);

            ItemStack bigMilk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
            ItemStack sourceMilk = GameTestSupport.milk(GameTestSupport.source(), 1000);
            Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
            boolean sankMilk = Transfers.tryTransferOne(
                    helper.getLevel(), player,
                    InteractionHand.MAIN_HAND, bigMilk,
                    InteractionHand.OFF_HAND, sourceMilk);

            GameTestSupport.check(!sankMilk, "Disabled milk Source Bucket remained an infinite sink");
            GameTestSupport.assertMilk(bigMilk, 1000);
            GameTestSupport.assertMilk(sourceMilk, 1000);

            ItemStack big = GameTestSupport.big8();
            IFluidHandlerItem bigHandler = big.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                    .orElseThrow(() -> new IllegalStateException("Big Bucket exposed no fluid capability"));
            int filled = bigHandler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);

            GameTestSupport.check(filled == 1000, "Source allow list restricted a Big Bucket");
            GameTestSupport.check(ForgeHooks.getBurnTime(big, RecipeType.SMELTING) == 20000,
                    "Source allow list disabled Big Bucket lava fuel");

            ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.set(List.of(
                    "minecraft:lava", "missingmod:removed_fluid", "somebuckets:milk"));
            GameTestSupport.check(SourceBucketPolicy.allows(Fluids.WATER),
                    "Policy cache changed before an explicit config refresh");

            SourceBucketPolicy.refresh("SourceBucketGameTests");

            GameTestSupport.check(SourceBucketPolicy.allows(Fluids.LAVA),
                    "Reloaded policy did not allow its registered fluid");
            GameTestSupport.check(!SourceBucketPolicy.allows(Fluids.WATER),
                    "Reloaded policy retained a removed fluid");
            GameTestSupport.check(SourceBucketPolicy.allowsMilk(),
                    "Reloaded policy did not allow milk alongside an unknown fluid");
            helper.succeed();
        } finally {
            ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.set(original);
            SourceBucketPolicy.refresh("SourceBucketGameTests cleanup");
        }
    }
}
