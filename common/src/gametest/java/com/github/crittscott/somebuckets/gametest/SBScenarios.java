package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.FilledBucketTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

final class SBScenarios {
    private SBScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);
    /**
     * Manual: use an empty Source Bucket on a lava source; the source disappears and the bucket is
     * assigned to lava.
     */
    static void empty_source_acquires_world_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = GameTestSupport.trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Empty Source Bucket did not acquire lava");
        GameTestSupport.assertFluid(bucket, Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    /**
     * Automation-only: assigns a Source Bucket from world water and verifies one item-use statistic
     * and one filled-bucket criterion trigger as well as the visible state changes.
     */
    static void player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        ItemStack bucket = GameTestSupport.source();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.LAVA);

        Criterion<FilledBucketTrigger.TriggerInstance> criterion =
                FilledBucketTrigger.TriggerInstance.filledBucket(ItemPredicate.Builder.item());
        ResourceLocation advancementId = ResourceLocation.fromNamespaceAndPath(
                SomeBuckets.MODID, "gametest/source_world_pickup_filled");
        AdvancementHolder advancement = Advancement.Builder.advancement()
                .addCriterion("filled", criterion)
                .build(advancementId);
        CriterionTrigger.Listener<FilledBucketTrigger.TriggerInstance> listener =
                new CriterionTrigger.Listener<>(criterion.triggerInstance(), advancement, "filled");
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        boolean acted;
        CriteriaTriggers.FILLED_BUCKET.addPlayerListener(player.getAdvancements(), listener);
        try {
            acted = SBFluidLogic.tryTake(
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
    /**
     * Manual: collect from a waterlogged block with an empty Source Bucket; the block survives dry and the
     * bucket assigns water.
     */
    static void waterlogged_block_assigns_source_and_survives(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));

        boolean acted = GameTestSupport.trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Source Bucket did not take water from a waterlogged block");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Waterlogged block kept its water after pickup");
        helper.succeed();
    }
    /** Manual: use a water-assigned Source Bucket on lava; its assignment and the lava remain unchanged. */
    static void assigned_source_refuses_reassignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = GameTestSupport.trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!acted, "Assigned Source Bucket changed fluid");
        GameTestSupport.assertSameStack(before, bucket, "Rejected reassignment mutated Source Bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }
    /**
     * Manual: sneak-use a water-assigned Source Bucket on source water; the water is removed and the
     * assignment remains.
     */
    static void assigned_source_sneak_right_click_takes_matching_world_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.WATER);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, bucket,
                "Matching pickup changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    /** Manual: sneak-use a water-assigned Source Bucket on lava; neither world fluid nor assignment changes. */
    static void assigned_source_sneak_right_click_ignores_different_world_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.LAVA);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, bucket,
                "Different-fluid target changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }
    /**
     * Manual: normally use a water-assigned Source Bucket on a placeable target; water appears and
     * assignment is unchanged.
     */
    static void assigned_source_normal_right_click_places_without_consumption(GameTestHelper helper) {
        BlockPos placeTarget = TARGET.north();
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        SBItem item = (SBItem) bucket.getItem();
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(placeTarget, Blocks.AIR);
        helper.setBlock(TARGET.north(2), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingAt(
                helper, TARGET.north(3), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, bucket,
                "Normal placement changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, placeTarget, Blocks.WATER);
        helper.succeed();
    }
    /** Manual: sneak-use a water-assigned Source Bucket on a waterlogged block; only its water is removed. */
    static void assigned_source_takes_matching_waterlogged_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));

        boolean acted = GameTestSupport.trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Assigned Source Bucket did not take waterlogged source");
        GameTestSupport.assertSameStack(before, bucket,
                "Waterlogged pickup changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Matching waterlogged source remained after pickup");
        helper.succeed();
    }
    /**
     * Manual: place water repeatedly from an assigned Source Bucket; every placement succeeds without
     * changing assignment.
     */
    static void source_places_repeatedly_without_consumption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        BlockPos first = new BlockPos(3, 2, 4);
        BlockPos second = new BlockPos(5, 2, 4);

        boolean firstActed = GameTestSupport.trySourcePlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, first, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);
        boolean secondActed = GameTestSupport.trySourcePlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, second, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(firstActed && secondActed, "Source Bucket did not place repeatedly");
        GameTestSupport.assertBlock(helper, first, Blocks.WATER);
        GameTestSupport.assertBlock(helper, second, Blocks.WATER);
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        helper.succeed();
    }
    /** Manual: use an empty Source Bucket on a full water cauldron; it assigns water and empties the cauldron. */
    static void empty_source_acquires_full_water_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));

        boolean acted = GameTestSupport.trySourceTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Source Bucket did not acquire full water cauldron");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.CAULDRON);
        helper.succeed();
    }
    /** Manual: use a lava-assigned Source Bucket on an empty cauldron; it becomes full and assignment is unchanged. */
    static void source_fills_empty_cauldron_without_consumption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        helper.setBlock(TARGET, Blocks.CAULDRON);

        boolean acted = GameTestSupport.trySourcePlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(acted, "Lava Source Bucket did not fill cauldron");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertFluid(bucket, Fluids.LAVA, 1000);
        helper.succeed();
    }
    /** Manual: use an empty Source Bucket on an adult cow and a calf; only the adult assigns infinite milk. */
    static void adult_cow_assigns_milk_but_baby_does_not(GameTestHelper helper) {
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
    /**
     * Manual: drink from a milk-assigned Source Bucket while affected by a potion; effects clear and milk
     * remains assigned.
     */
    static void source_milk_is_not_consumed_by_drinking(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.source(), 1000);
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.serverPlayer(helper, new BlockPos(2, 2, 2));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));

        item.finishUsingItem(bucket, helper.getLevel(), player);

        GameTestSupport.check(!player.hasEffect(MobEffects.POISON), "Source milk did not remove effect");
        GameTestSupport.assertMilk(bucket, 1000);
        helper.succeed();
    }
    /** Manual: normally use an assigned fluid Source Bucket while targeting air; its assignment remains unchanged. */
    static void normal_use_in_air_preserves_source_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        ItemStack before = bucket.copy();
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setXRot(-90.0F);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, bucket,
                "Normal air use changed Source Bucket assignment");
        helper.succeed();
    }
    /** Manual: sneak-use an assigned fluid Source Bucket while targeting air; it becomes unassigned. */
    static void sneak_use_in_air_clears_source_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /**
     * Manual: sneak-use a milk-assigned Source Bucket while targeting air; it becomes unassigned instead
     * of drinking.
     */
    static void sneak_use_in_air_clears_source_milk_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.source(), 1000);
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    /** Manual: normally drink from a milk-assigned Source Bucket while targeting air; milk remains assigned. */
    static void normal_use_in_air_on_source_milk_preserves_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.source(), 1000);
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertMilk(bucket, 1000);
        helper.succeed();
    }
    /** Manual: use an empty Source Bucket on powder snow; neither block nor bucket changes. */
    static void source_does_not_support_powder_snow(GameTestHelper helper) {
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
     * Manual: set the Source Bucket allowlist empty and reload, then try water, lava, and milk; all
     * assignments are refused.
     */
    static void empty_allow_list_disables_all_source_contents(GameTestHelper helper) {
        BlockPos waterPos = TARGET;
        BlockPos lavaPos = TARGET.east(2);
        helper.setBlock(waterPos, Blocks.WATER);
        helper.setBlock(lavaPos, Blocks.LAVA);
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(3, 2, 2));
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        ItemStack waterBucket = GameTestSupport.source();
        ItemStack lavaBucket = GameTestSupport.source();
        ItemStack milkBucket = GameTestSupport.source();
        try {
            SBPolicy.refresh(List.<String>of(), "SBScenarios");

            boolean tookWater = GameTestSupport.trySourceTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, waterPos, Direction.UP), waterBucket,
                    ProtectionContext.unownedAutomation());
            boolean tookLava = GameTestSupport.trySourceTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, lavaPos, Direction.UP), lavaBucket,
                    ProtectionContext.unownedAutomation());
            InteractionResult milked = ((SBItem) milkBucket.getItem()).interactLivingEntity(
                    milkBucket, player, cow, InteractionHand.MAIN_HAND);

            GameTestSupport.check(!tookWater, "Empty allowlist still assigned water");
            GameTestSupport.check(!tookLava, "Empty allowlist still assigned lava");
            GameTestSupport.check(!milked.consumesAction(), "Empty allowlist still assigned milk");
            GameTestSupport.assertEmpty(waterBucket);
            GameTestSupport.assertEmpty(lavaBucket);
            GameTestSupport.assertEmpty(milkBucket);
            GameTestSupport.assertBlock(helper, waterPos, Blocks.WATER);
            GameTestSupport.assertBlock(helper, lavaPos, Blocks.LAVA);
            helper.succeed();
        } finally {
            SBPolicy.refresh(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS, "SBScenarios cleanup");
        }
    }

}
