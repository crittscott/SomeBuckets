package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.FilledBucketTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

public final class SBGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_source_acquires_world_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = GameTestSupport.fabricOps().trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Empty Source Bucket did not acquire lava");
        GameTestSupport.assertFluid(bucket, Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        ItemStack bucket = GameTestSupport.source();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.LAVA);

        FilledBucketTrigger.TriggerInstance criterion =
                FilledBucketTrigger.TriggerInstance.filledBucket(ItemPredicate.ANY);
        Advancement advancement = Advancement.Builder.advancement()
                .addCriterion("filled", criterion)
                .build(new ResourceLocation(SomeBuckets.MODID, "gametest/source_world_pickup_filled"));
        CriterionTrigger.Listener<FilledBucketTrigger.TriggerInstance> listener =
                new CriterionTrigger.Listener<>(criterion, advancement, "filled");
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        boolean acted;
        CriteriaTriggers.FILLED_BUCKET.addPlayerListener(player.getAdvancements(), listener);
        try {
            acted = BucketOperations.get().trySourceTake(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                    player, InteractionHand.MAIN_HAND);
        } finally {
            CriteriaTriggers.FILLED_BUCKET.removePlayerListener(player.getAdvancements(), listener);
        }

        GameTestSupport.check(acted, "Player Source Bucket world pickup failed");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player Source Bucket world pickup did not award exactly one item-use statistic");
        GameTestSupport.check(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "Player Source Bucket world pickup did not fire the filled-bucket criterion");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void waterlogged_block_assigns_source_and_survives(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));

        boolean acted = GameTestSupport.fabricOps().trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Source Bucket did not take water from a waterlogged block");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Waterlogged block kept its water after pickup");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_refuses_reassignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = GameTestSupport.fabricOps().trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!acted, "Assigned Source Bucket changed fluid");
        GameTestSupport.assertSameStack(before, bucket, "Rejected reassignment mutated Source Bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_places_repeatedly_without_consumption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        BlockPos first = new BlockPos(3, 2, 4);
        BlockPos second = new BlockPos(5, 2, 4);

        boolean firstActed = GameTestSupport.fabricOps().trySourcePlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, first, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);
        boolean secondActed = GameTestSupport.fabricOps().trySourcePlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, second, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(firstActed && secondActed, "Source Bucket did not place repeatedly");
        GameTestSupport.assertBlock(helper, first, Blocks.WATER);
        GameTestSupport.assertBlock(helper, second, Blocks.WATER);
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_source_acquires_full_water_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));

        boolean acted = GameTestSupport.fabricOps().trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Source Bucket did not acquire full water cauldron");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.CAULDRON);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_fills_empty_cauldron_without_consumption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        helper.setBlock(TARGET, Blocks.CAULDRON);

        boolean acted = GameTestSupport.fabricOps().trySourcePlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(acted, "Lava Source Bucket did not fill cauldron");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertFluid(bucket, Fluids.LAVA, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void adult_cow_assigns_milk_but_baby_does_not(GameTestHelper helper) {
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
    public void source_milk_is_not_consumed_by_drinking(GameTestHelper helper) {
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
    public void shift_use_in_air_clears_source_assignment(GameTestHelper helper) {
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
    public void source_does_not_support_powder_snow(GameTestHelper helper) {
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

    /**
     * Fabric has no in-memory config object to mutate like Forge's {@code ServerConfig}; the allowlist
     * only ever lives in {@link SBPolicy}, refreshed directly here instead of through
     * {@link FabricServerConfig}, which only loads it once from disk at startup. The fuel checks Forge
     * runs through {@code ForgeHooks.getBurnTime} are dropped, matching {@code RecipeAndFuelGameTests}.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_allow_list_blocks_input_output_and_fuel_without_affecting_big_buckets(
            GameTestHelper helper) {
        SBPolicy.refresh(List.of("minecraft:water"), "SBGameTests");

        try {
            ItemStack emptySource = GameTestSupport.source();
            helper.setBlock(TARGET, Blocks.LAVA);
            boolean tookLava = GameTestSupport.fabricOps().trySourceTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), emptySource,
                    ProtectionContext.unownedAutomation());

            GameTestSupport.check(!tookLava, "Disabled lava assigned an empty Source Bucket");
            GameTestSupport.assertEmpty(emptySource);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);

            helper.setBlock(TARGET, Blocks.LAVA_CAULDRON);
            boolean tookLavaCauldron = GameTestSupport.fabricOps().trySourceTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), emptySource,
                    ProtectionContext.unownedAutomation());

            GameTestSupport.check(!tookLavaCauldron,
                    "Disabled lava assigned an empty Source Bucket from a cauldron");
            GameTestSupport.assertEmpty(emptySource);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA_CAULDRON);

            ItemStack lavaSource = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
            long drained = GameTestSupport.extract(
                    GameTestSupport.fluidStorage(GameTestSupport.containerOf(lavaSource)),
                    FluidVariant.of(Fluids.LAVA), 1000L * GameTestSupport.DROPLETS_PER_MB, true);
            BlockPos placeTarget = TARGET.offset(1, 0, 0);
            boolean placed = GameTestSupport.fabricOps().trySourcePlaceWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, placeTarget, Direction.UP), lavaSource,
                    ProtectionContext.unownedAutomation(), true);
            BlockPos cauldronTarget = TARGET.offset(2, 0, 0);
            helper.setBlock(cauldronTarget, Blocks.CAULDRON);
            boolean filledCauldron = GameTestSupport.fabricOps().trySourcePlaceWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, cauldronTarget, Direction.UP), lavaSource,
                    ProtectionContext.unownedAutomation(), true);

            GameTestSupport.check(drained == 0, "Disabled Source Bucket supplied fluid storage output");
            GameTestSupport.check(!placed, "Disabled Source Bucket placed world fluid");
            GameTestSupport.check(!filledCauldron, "Disabled Source Bucket filled a cauldron");
            GameTestSupport.assertBlock(helper, placeTarget, Blocks.AIR);
            GameTestSupport.assertBlock(helper, cauldronTarget, Blocks.CAULDRON);
            GameTestSupport.check(!SBPolicy.allowsMilk(), "Milk remained allowed after removal");
            GameTestSupport.assertFluid(lavaSource, Fluids.LAVA, 1000);

            ItemStack bigMilk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
            ItemStack sourceMilk = GameTestSupport.milk(GameTestSupport.source(), 1000);
            Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
            player.setItemInHand(InteractionHand.MAIN_HAND, bigMilk);
            player.setItemInHand(InteractionHand.OFF_HAND, sourceMilk);
            boolean sankMilk = BucketOperations.get().tryHeldTransfer(
                    helper.getLevel(), player,
                    InteractionHand.MAIN_HAND, bigMilk,
                    InteractionHand.OFF_HAND, sourceMilk);

            GameTestSupport.check(!sankMilk, "Disabled milk Source Bucket remained an infinite sink");
            GameTestSupport.assertMilk(player.getMainHandItem(), 1000);
            GameTestSupport.assertMilk(player.getOffhandItem(), 1000);

            SimpleContainer bigContainer = GameTestSupport.containerOf(GameTestSupport.big8());
            long filled = GameTestSupport.insert(GameTestSupport.fluidStorage(bigContainer),
                    FluidVariant.of(Fluids.LAVA), 1000L * GameTestSupport.DROPLETS_PER_MB, true);

            GameTestSupport.check(filled == 1000L * GameTestSupport.DROPLETS_PER_MB,
                    "Source allow list restricted a Big Bucket");

            List<String> reloaded = List.of("minecraft:lava", "missingmod:removed_fluid", "somebuckets:milk");
            GameTestSupport.check(SBPolicy.allows(Fluids.WATER),
                    "Policy cache changed before an explicit config refresh");

            SBPolicy.refresh(reloaded, "SBGameTests");

            GameTestSupport.check(SBPolicy.allows(Fluids.LAVA),
                    "Reloaded policy did not allow its registered fluid");
            GameTestSupport.check(!SBPolicy.allows(Fluids.WATER),
                    "Reloaded policy retained a removed fluid");
            GameTestSupport.check(SBPolicy.allowsMilk(),
                    "Reloaded policy did not allow milk alongside an unknown fluid");
            helper.succeed();
        } finally {
            SBPolicy.refresh(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS, "SBGameTests cleanup");
        }
    }
}
