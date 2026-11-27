package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.GameEventRecorder;
import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.ProtectionEvent;
import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.RefusalScope;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** Big and Huge Bucket world, milk and gesture scenarios shared by both loaders. */
final class BBScenarios {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private BBScenarios() {}

    /**
     * In game: hold an empty Big Bucket and right-click a cow, then a calf; the cow fills the bucket
     * with one unit of milk and the calf does nothing.
     */
    static void adult_cow_adds_milk_but_baby_does_not(GameTestHelper helper) {
        ItemStack adultBucket = GameTestSupport.big8();
        ItemStack babyBucket = GameTestSupport.big8();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        Cow adult = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(3, 2, 2));
        Cow baby = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(5, 2, 2));
        baby.setAge(-24000);

        player.setItemInHand(InteractionHand.MAIN_HAND, adultBucket);
        InteractionResult adultResult = GameTestSupport.interactWithEntity(player, adult, InteractionHand.MAIN_HAND);
        ItemStack milked = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, babyBucket);
        InteractionResult babyResult = GameTestSupport.interactWithEntity(player, baby, InteractionHand.MAIN_HAND);

        GameTestSupport.check(adultResult.consumesAction(), "Adult cow milking did not succeed");
        GameTestSupport.check(!babyResult.consumesAction(), "Baby cow was milked");
        GameTestSupport.assertMilk(milked, 1000);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * No in-game form: a player's use of a partly filled water Big Bucket on lava falls back to
     * placing water, so a player never sees the refusal alone. Guards the take rule that use relies
     * on: a Big Bucket holding one fluid never takes another, and the refused take changes neither
     * the bucket nor the source.
     */
    static void bucket_refuses_different_source_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = FluidTransactions.tryTakeFinite(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                GameTestSupport.automation(helper));

        GameTestSupport.check(!acted, "Big Bucket mixed water and lava");
        GameTestSupport.assertSameStack(before, bucket, "Rejected source pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of milk, add Poison to yourself, and right-click the
     * air; you start drinking, and when you finish the Poison is gone and the bucket holds one unit.
     * The drink is completed directly rather than by waiting out the use duration.
     */
    static void drinking_milk_removes_effect_and_consumes_one_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), 2000);
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setXRot(-90.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
        GameTestSupport.check(player.isUsingItem(), "Using a milk Big Bucket did not start drinking");
        player.getUseItem().finishUsingItem(helper.getLevel(), player);

        GameTestSupport.check(!player.hasEffect(MobEffects.POISON), "Milk did not remove status effect");
        GameTestSupport.assertMilk(player.getMainHandItem(), 1000);
        helper.succeed();
    }

    /**
     * In game: hold an empty Big Bucket and right-click a water source; the source disappears and the
     * bucket holds one unit of water.
     */
    static void empty_bucket_collects_source(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Empty Big Bucket use did not succeed");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: hold an empty Big Bucket and right-click flowing water that is not a source; nothing
     * happens.
     */
    static void flowing_fluid_is_not_collected(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
        Player player = playerAbove(helper, GameTestSupport.big8());
        ItemStack before = player.getMainHandItem().copy();

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Big Bucket use on flowing water consumed the action");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Rejected flowing pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    /**
     * No in-game form: a player's use of a full Big Bucket at a source skips taking and places
     * instead, so a player never sees the refusal alone. Guards the take rule that use relies on: a
     * full bucket takes nothing, and the refused take changes neither the bucket nor the source.
     */
    static void full_bucket_refuses_another_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = FluidTransactions.tryTakeFinite(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                GameTestSupport.automation(helper));

        GameTestSupport.check(!acted, "Full Big Bucket collected another source");
        GameTestSupport.assertSameStack(before, bucket, "Rejected full pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with one unit of water and right-click a water source; it collects
     * the source (two units) instead of placing.
     */
    static void partial_bucket_collects_matching_source(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Partial Big Bucket use did not succeed");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 2000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with one unit of water and right-click the top of a stone block; a
     * water source appears on the stone and the bucket is empty, with no leftover content.
     */
    static void placement_consumes_one_unit_and_final_unit_normalizes(GameTestHelper helper) {
        helper.setBlock(TARGET.below(), Blocks.STONE);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket placement did not succeed");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of water and right-click the side of a stone block;
     * the water appears in front of that side, the stone stays, and the bucket keeps one unit.
     */
    static void placement_falls_through_solid_clicked_block(GameTestHelper helper) {
        BlockPos neighbor = TARGET.east();
        helper.setBlock(TARGET, Blocks.STONE);
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.east(2));
        Vec3 eastFace = Vec3.atCenterOf(helper.absolutePos(TARGET)).add(0.5D, 0.0D, 0.0D);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, eastFace);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket placement against a stone side did not succeed");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.WATER);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of water and right-click an oak fence; the fence
     * becomes waterlogged and the bucket keeps one unit.
     */
    static void placement_waterlogs_liquid_container(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.OAK_FENCE);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not waterlog fence");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isSource(),
                "Waterlogged fence did not contain a source fluid state");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket full of powder snow (eight blocks) and right-click a powder-snow
     * block; the bucket cannot take another, so a powder-snow block is placed on top instead and the
     * bucket holds seven.
     */
    static void powder_snow_capacity_is_enforced(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.powder(GameTestSupport.big8(), 8));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Full powder-snow bucket did not build on the block");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.POWDER_SNOW);
        GameTestSupport.assertPowder(player.getMainHandItem(), 7);
        helper.succeed();
    }

    /**
     * In game: hold an empty Big Bucket, right-click a powder-snow block, then right-click the top of
     * the block it stood on; the first click collects the powder snow, the second puts it back and
     * leaves the bucket empty.
     */
    static void powder_snow_collects_and_places_one_block(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        helper.setBlock(TARGET.below(), Blocks.STONE);
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult collected = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));
        GameTestSupport.assertPowder(player.getMainHandItem(), 1);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        InteractionResult placed = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET.below(), Direction.UP));

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect powder snow");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not place powder snow");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of powder snow and right-click a powder-snow block
     * without sneaking; the bucket collects it and holds three units.
     */
    static void powder_snow_use_at_powder_snow_collects_it(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.powder(GameTestSupport.big8(), 2));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Powder-snow Big Bucket did not collect powder snow");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        GameTestSupport.assertPowder(player.getMainHandItem(), 3);
        helper.succeed();
    }

    /**
     * In game, Forge only: put a Big Bucket of powder snow in a dispenser facing the side of a stone
     * block inside a claim that refuses fake players building, and pulse it; nothing is placed and
     * the bucket keeps its powder snow. On Fabric the place check is posted only to Architectury and
     * Common Protection API, which the test environment lacks. The refused place event is posted at
     * the position beside the clicked face, where the native placement would have gone.
     */
    static void powder_snow_protection_denial_precedes_native_placement(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        ItemStack before = bucket.copy();
        BlockPos placeTarget = TARGET.east();
        helper.setBlock(TARGET, Blocks.STONE);

        boolean acted;
        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) -> {
            if (event != ProtectionEvent.PLACE) return false;
            GameTestSupport.check(pos.equals(helper.absolutePos(placeTarget)),
                    "Place event did not use the native adjacent placement target");
            return true;
        })) {
            acted = FluidTransactions.tryPlacePowder(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.EAST), bucket,
                    GameTestSupport.automation(helper), true);
        }

        GameTestSupport.check(!acted, "Protection denial allowed powder placement");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, placeTarget, Blocks.AIR);
        GameTestSupport.assertSameStack(before, bucket, "Protection denial debited the Big Bucket");
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of powder snow and right-click the top of a stone
     * block; a powder-snow block appears, a nearby sculk sensor detects a block placement (not a fluid
     * placement), the bucket keeps one unit, and the Big Bucket's "times used" statistic and the
     * placed-block advancement trigger each advance once.
     */
    static void powder_snow_player_placement_emits_native_observability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(TARGET.below(), Blocks.STONE);
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        player.setXRot(90.0F);
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        ItemUsedOnLocationTrigger.TriggerInstance criterion =
                ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.POWDER_SNOW);
        Advancement advancement = Advancement.Builder.advancement()
                .addCriterion("placed", criterion)
                .build(new ResourceLocation(SomeBuckets.MODID, "gametest/powder_snow_placed"));
        CriterionTrigger.Listener<ItemUsedOnLocationTrigger.TriggerInstance> criterionListener =
                new CriterionTrigger.Listener<>(criterion, advancement, "placed");
        GameEventRecorder recorder = new GameEventRecorder(helper, TARGET);

        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        InteractionResult result;
        CriteriaTriggers.PLACED_BLOCK.addPlayerListener(player.getAdvancements(), criterionListener);
        recorder.add(level);
        try {
            result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET.below(), Direction.UP));
        } finally {
            recorder.remove(level);
            CriteriaTriggers.PLACED_BLOCK.removePlayerListener(player.getAdvancements(), criterionListener);
        }

        GameTestSupport.check(result.consumesAction(), "Player powder placement did not succeed");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertPowder(player.getMainHandItem(), 1);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem())) == statBefore + 1,
                "Successful powder placement did not award exactly one Big Bucket use");
        GameTestSupport.check(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "Successful powder placement did not fire the placed-block criterion");
        GameTestSupport.check(recorder.count(GameEvent.BLOCK_PLACE) == 1,
                "Successful powder placement did not emit exactly one block-place game event");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 0,
                "Powder placement emitted a fluid-place game event");
        GameTestSupport.check(recorder.affected(GameEvent.BLOCK_PLACE, Blocks.POWDER_SNOW),
                "Block-place game event did not carry the placed powder-snow state");
        helper.succeed();
    }

    /**
     * In game: hold an empty Big Bucket and right-click a water source; besides collecting it, the
     * Big Bucket's "times used" statistic rises by one and vanilla's filled-bucket advancement
     * trigger fires, as it does for a vanilla bucket.
     */
    static void player_big_world_pickup_awards_one_use_and_filled_bucket_criterion(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        player.setXRot(90.0F);
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult[] result = new InteractionResult[1];
        boolean fired = GameTestSupport.firesFilledBucket(player,
                () -> result[0] = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(result[0].consumesAction(), "Player Big Bucket world pickup failed");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player Big Bucket world pickup did not award exactly one item-use statistic");
        GameTestSupport.check(fired, "Player Big Bucket world pickup did not fire the filled-bucket criterion");
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket of lava, sneak, look at the sky, and right-click; the bucket empties.
     */
    static void shift_use_in_air_discards_contents(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 3000));
        player.setShiftKeyDown(true);
        player.setXRot(-90.0F);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneak-use in air did not succeed");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with two units of powder snow, sneak, and right-click the top of a
     * powder-snow block; a new powder-snow block appears on top instead of the bucket collecting the
     * one you aimed at, and the bucket keeps one unit.
     */
    static void sneaking_with_powder_snow_at_powder_snow_places_outward(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.powder(GameTestSupport.big8(), 2));
        player.setShiftKeyDown(true);

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Sneaking powder-snow use did not place a block");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.POWDER_SNOW);
        GameTestSupport.assertPowder(player.getMainHandItem(), 1);
        helper.succeed();
    }

    /**
     * In game: hold a stack of three empty Big Buckets and use them on a water source; one bucket
     * fills and goes to your inventory, and two empties stay in your hand.
     */
    static void stack_of_empty_big_buckets_fills_one(GameTestHelper helper) {
        ItemStack empties = GameTestSupport.big8();
        empties.setCount(3);
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = playerAbove(helper, empties);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        ItemStack held = player.getMainHandItem();
        GameTestSupport.check(held.getCount() == 2 && BucketState.isEmptyBucket(held),
                "Expected two empty Big Buckets in hand, got " + held + " " + held.getTag());
        ItemStack filled = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(held.getItem()) && !BucketState.isEmptyBucket(stack)) filled = stack;
        }
        GameTestSupport.assertFluid(filled, Fluids.WATER, 1000);
        GameTestSupport.check(filled.getCount() == 1, "More than one Big Bucket filled from one source");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: hold an empty Big Bucket and right-click the top of a waterlogged oak fence; the bucket
     * takes one unit of water and the fence stays, no longer waterlogged.
     */
    static void waterlogged_block_gives_up_only_its_water(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not collect water from a waterlogged block");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Waterlogged block kept its water after pickup");
        helper.succeed();
    }

    /* A survival player directly above TARGET, looking straight down at it, holding bucket. */
    private static Player playerAbove(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }
}
