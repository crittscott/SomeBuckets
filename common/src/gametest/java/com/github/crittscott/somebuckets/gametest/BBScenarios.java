package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.FilledBucketTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger;
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
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class BBScenarios {
    private BBScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);
    static void empty_bucket_collects_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = GameTestSupport.tryBigTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Empty Big Bucket did not collect water source");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    static void player_big_world_pickup_awards_one_use_and_filled_bucket_criterion(
            GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.WATER);

        FilledBucketTrigger.TriggerInstance criterion =
                FilledBucketTrigger.TriggerInstance.filledBucket(ItemPredicate.ANY);
        Advancement advancement = Advancement.Builder.advancement()
                .addCriterion("filled", criterion)
                .build(new ResourceLocation(SomeBuckets.MODID, "gametest/big_world_pickup_filled"));
        CriterionTrigger.Listener<FilledBucketTrigger.TriggerInstance> listener =
                new CriterionTrigger.Listener<>(criterion, advancement, "filled");
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        boolean acted;
        CriteriaTriggers.FILLED_BUCKET.addPlayerListener(player.getAdvancements(), listener);
        try {
            acted = BucketOperations.get().tryBigTake(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                    player, InteractionHand.MAIN_HAND);
        } finally {
            CriteriaTriggers.FILLED_BUCKET.removePlayerListener(player.getAdvancements(), listener);
        }

        GameTestSupport.check(acted, "Player Big Bucket world pickup failed");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player Big Bucket world pickup did not award exactly one item-use statistic");
        GameTestSupport.check(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "Player Big Bucket world pickup did not fire the filled-bucket criterion");
        helper.succeed();
    }
    static void partial_bucket_collects_matching_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = GameTestSupport.tryBigTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Partial Big Bucket did not collect matching source");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 2000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    static void bucket_refuses_different_source_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = GameTestSupport.tryBigTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!acted, "Big Bucket mixed water and lava");
        GameTestSupport.assertSameStack(before, bucket, "Rejected source pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }
    static void full_bucket_refuses_another_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER);

        boolean acted = GameTestSupport.tryBigTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!acted, "Full Big Bucket collected another source");
        GameTestSupport.assertSameStack(before, bucket, "Rejected full pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }
    static void waterlogged_block_gives_up_only_its_water(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));

        boolean acted = GameTestSupport.tryBigTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(acted, "Big Bucket did not collect water from a waterlogged block");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Waterlogged block kept its water after pickup");
        helper.succeed();
    }
    static void flowing_fluid_is_not_collected(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));

        boolean acted = GameTestSupport.tryBigTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!acted, "Big Bucket collected flowing water");
        GameTestSupport.assertSameStack(before, bucket, "Rejected flowing pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }
    static void placement_consumes_one_unit_and_final_unit_normalizes(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);

        boolean acted = GameTestSupport.tryBigPlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(acted, "Big Bucket did not place water");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    static void placement_falls_through_solid_clicked_block(GameTestHelper helper) {
        BlockPos neighbor = TARGET.east();
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        helper.setBlock(TARGET, Blocks.STONE);

        boolean acted = GameTestSupport.tryBigPlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.EAST), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(acted, "Fluid did not fall through to clicked-face neighbor");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.WATER);
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        helper.succeed();
    }
    static void placement_waterlogs_liquid_container(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        helper.setBlock(TARGET, Blocks.OAK_FENCE);

        boolean acted = GameTestSupport.tryBigPlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(acted, "Big Bucket did not waterlog fence");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isSource(),
                "Waterlogged fence did not contain a source fluid state");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        helper.succeed();
    }
    static void powder_snow_collects_and_places_one_block(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);

        boolean collected = GameTestSupport.tryPowderTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());
        boolean placed = GameTestSupport.tryPowderPlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), true);

        GameTestSupport.check(collected, "Big Bucket did not collect powder snow");
        GameTestSupport.check(placed, "Big Bucket did not place powder snow");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }
    static void failed_powder_snow_placement_is_atomic(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.STONE);

        boolean acted = GameTestSupport.tryPowderPlaceWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation(), false);

        GameTestSupport.check(!acted, "Powder snow replaced a solid direct-only target");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        GameTestSupport.assertSameStack(before, bucket, "Failed powder placement debited the Big Bucket");
        helper.succeed();
    }
    static void powder_snow_protection_denial_precedes_native_placement(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        ItemStack before = bucket.copy();
        BlockPos placeTarget = TARGET.east();
        helper.setBlock(TARGET, Blocks.STONE);

        boolean acted;
        try (ClaimProtections.Registration ignored = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (action != ProtectionAction.BLOCK_EDIT) return true;
                    GameTestSupport.check(target.equals(helper.absolutePos(placeTarget)),
                            "Protection check did not use the native adjacent placement target");
                    return false;
                })) {
            acted = GameTestSupport.tryPowderPlaceWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.EAST), bucket,
                    ProtectionContext.unownedAutomation(), true);
        }

        GameTestSupport.check(!acted, "Protection denial allowed powder placement");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, placeTarget, Blocks.AIR);
        GameTestSupport.assertSameStack(before, bucket, "Protection denial debited the Big Bucket");
        helper.succeed();
    }
    static void powder_snow_player_placement_emits_native_observability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        ItemUsedOnLocationTrigger.TriggerInstance criterion =
                ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.POWDER_SNOW);
        Advancement advancement = Advancement.Builder.advancement()
                .addCriterion("placed", criterion)
                .build(new ResourceLocation(SomeBuckets.MODID, "gametest/powder_snow_placed"));
        CriterionTrigger.Listener<ItemUsedOnLocationTrigger.TriggerInstance> criterionListener =
                new CriterionTrigger.Listener<>(criterion, advancement, "placed");

        List<GameEvent> gameEvents = new ArrayList<>();
        List<GameEvent.Context> gameEventContexts = new ArrayList<>();
        GameEventListener gameEventListener = new GameEventListener() {
            @Override
            public BlockPositionSource getListenerSource() {
                return new BlockPositionSource(helper.absolutePos(TARGET));
            }

            @Override
            public int getListenerRadius() {
                return 16;
            }

            @Override
            public boolean handleGameEvent(ServerLevel serverLevel, GameEvent event,
                                           GameEvent.Context context, Vec3 pos) {
                if (BlockPos.containing(pos).equals(helper.absolutePos(TARGET))) {
                    gameEvents.add(event);
                    gameEventContexts.add(context);
                }
                return true;
            }
        };
        DynamicGameEventListener<GameEventListener> dynamicListener =
                new DynamicGameEventListener<>(gameEventListener);

        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        boolean acted;
        CriteriaTriggers.PLACED_BLOCK.addPlayerListener(player.getAdvancements(), criterionListener);
        dynamicListener.add(level);
        try {
            acted = BucketOperations.get().tryPowderPlace(
                    level, GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                    player, InteractionHand.MAIN_HAND);
        } finally {
            dynamicListener.remove(level);
            CriteriaTriggers.PLACED_BLOCK.removePlayerListener(player.getAdvancements(), criterionListener);
        }

        GameTestSupport.check(acted, "Player powder placement did not succeed");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertPowder(bucket, 1);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem())) == statBefore + 1,
                "Successful powder placement did not award exactly one Big Bucket use");
        GameTestSupport.check(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "Successful powder placement did not fire the placed-block criterion");
        GameTestSupport.check(gameEvents.stream().filter(event -> event == GameEvent.BLOCK_PLACE).count() == 1,
                "Successful powder placement did not emit exactly one block-place game event");
        GameTestSupport.check(gameEvents.stream().noneMatch(event -> event == GameEvent.FLUID_PLACE),
                "Powder placement emitted a fluid-place game event");
        GameTestSupport.check(gameEventContexts.stream().anyMatch(context ->
                        context.affectedState() != null && context.affectedState().is(Blocks.POWDER_SNOW)),
                "Block-place game event did not carry the placed powder-snow state");
        helper.succeed();
    }
    static void powder_snow_capacity_is_enforced(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 8);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);

        boolean acted = GameTestSupport.tryPowderTakeWithContext(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.unownedAutomation());

        GameTestSupport.check(!acted, "Full powder-snow bucket collected another block");
        GameTestSupport.assertSameStack(before, bucket, "Rejected powder pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        helper.succeed();
    }
    static void adult_cow_adds_milk_but_baby_does_not(GameTestHelper helper) {
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
    static void drinking_milk_removes_effect_and_consumes_one_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), 2000);
        BBItem item = (BBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));

        item.finishUsingItem(bucket, helper.getLevel(), player);

        GameTestSupport.check(!player.hasEffect(MobEffects.POISON), "Milk did not remove status effect");
        GameTestSupport.assertMilk(bucket, 1000);
        helper.succeed();
    }
    static void shift_use_in_air_discards_contents(GameTestHelper helper) {
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


