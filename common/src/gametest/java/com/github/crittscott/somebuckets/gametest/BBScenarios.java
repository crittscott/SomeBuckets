package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTrigger;
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
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;

/**
 * Big Bucket world, cauldron, cow, and milk scenarios shared by every loader. Player scenarios stand
 * a player above {@code TARGET} looking down and right-click through {@link GameTestSupport#useItem}
 * or {@link GameTestSupport#clickBlock}.
 */
final class BBScenarios {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private BBScenarios() {}

    /**
     * In game: right-click a water source with an empty Big Bucket; the source disappears and the
     * bucket holds one unit of water.
     */
    static void empty_bucket_collects_source(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Empty Big Bucket did not collect water source");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: right-click a water source with an empty Big Bucket; it counts as filling a bucket for
     * advancements, and the Big Bucket's "used" statistic rises by exactly one.
     */
    static void player_big_world_pickup_awards_one_use_and_filled_bucket_criterion(
            GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        ServerPlayer player = (ServerPlayer) playerAbove(helper, GameTestSupport.big8());
        ItemStack bucket = player.getMainHandItem();
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult[] result = new InteractionResult[1];
        boolean filled = GameTestSupport.firesFilledBucket(player,
                () -> result[0] = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(result[0].consumesAction(), "Player Big Bucket world pickup failed");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player Big Bucket world pickup did not award exactly one item-use statistic");
        GameTestSupport.check(filled, "Player Big Bucket world pickup did not fire the filled-bucket criterion");
        helper.succeed();
    }

    /**
     * In game: right-click a water source with a Big Bucket holding one unit of water; it collects
     * the source and holds two units instead of placing.
     */
    static void partial_bucket_collects_matching_source(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Partial Big Bucket did not collect matching source");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 2000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * No in-game form: guards the take rule alone, that a Big Bucket of water never collects lava. A
     * player's right-click on lava goes on to place water instead, and a dispenser takes the same rule
     * before placing.
     */
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

    /**
     * No in-game form: guards the take rule alone, that a full Big Bucket collects nothing more. A
     * player's right-click on water with a full bucket places instead.
     */
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

    /**
     * In game: right-click a waterlogged fence with an empty Big Bucket; the fence stays, loses its
     * water, and the bucket holds one unit of water.
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

    /**
     * In game: right-click flowing water (not a source) with an empty Big Bucket; nothing is
     * collected.
     */
    static void flowing_fluid_is_not_collected(GameTestHelper helper) {
        helper.setBlock(TARGET.below(), Blocks.STONE);
        helper.setBlock(TARGET, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        Player player = playerAbove(helper, bucket);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Flowing-water use mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    /**
     * In game: right-click the top of a stone block with a Big Bucket holding one unit of water; water
     * appears on top and the bucket is empty.
     */
    static void placement_consumes_one_unit_and_final_unit_normalizes(GameTestHelper helper) {
        helper.setBlock(TARGET.below(), Blocks.STONE);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not place water");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: right-click the side of a stone block with a Big Bucket holding two units of water;
     * water appears beside the stone, on the face you clicked, and the bucket has one unit left.
     */
    static void placement_falls_through_solid_clicked_block(GameTestHelper helper) {
        BlockPos neighbor = TARGET.east();
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(neighbor, Blocks.AIR);
        helper.setBlock(TARGET.east(2), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.east(3), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Fluid did not fall through to clicked-face neighbor");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.WATER);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: right-click an oak fence with a Big Bucket holding two units of water; the fence
     * becomes waterlogged and the bucket has one unit left.
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
     * In game: right-click a powder snow block with an empty Big Bucket; the block is collected.
     * Right-click the stone beneath where it stood; the powder snow is put back and the bucket is
     * empty.
     */
    static void powder_snow_collects_and_places_one_block(GameTestHelper helper) {
        helper.setBlock(TARGET.below(), Blocks.STONE);
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult collected = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));
        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect powder snow");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertPowder(player.getMainHandItem(), 1);

        InteractionResult placed = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET.below(), Direction.UP));

        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not place powder snow");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: with a claim mod that forbids you from building there, right-click the side of a stone
     * block with a Big Bucket of powder snow; no powder snow appears beside the stone and the bucket
     * keeps its unit. The claim is asked about the block beside the stone.
     */
    static void powder_snow_protection_denial_precedes_native_placement(GameTestHelper helper) {
        BlockPos placeTarget = TARGET.east();
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(placeTarget, Blocks.AIR);
        helper.setBlock(TARGET.east(2), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.east(3), TARGET);
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        ItemStack before = bucket.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult result;
        try (Protections.Registration ignored = Protections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (actor.player() != player || action != ProtectionAction.BLOCK_EDIT) return true;
                    GameTestSupport.check(target.equals(helper.absolutePos(placeTarget)),
                            "Protection check did not use the native adjacent placement target");
                    return false;
                })) {
            result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET, Direction.EAST));
        }

        GameTestSupport.check(!result.consumesAction(), "Protection denial allowed powder placement");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, placeTarget, Blocks.AIR);
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Protection denial debited the Big Bucket");
        helper.succeed();
    }

    /**
     * In game: right-click the floor with a Big Bucket holding two powder snow; a powder snow block
     * appears, the bucket keeps one unit, the Big Bucket's "used" statistic rises by one, a nearby
     * sculk sensor hears a block being placed (not a fluid), and placed-block advancements fire.
     */
    static void powder_snow_player_placement_emits_native_observability(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        Criterion<ItemUsedOnLocationTrigger.TriggerInstance> criterion =
                ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.POWDER_SNOW);
        AdvancementHolder advancement = Advancement.Builder.advancement()
                .addCriterion("placed", criterion)
                .build(ResourceLocation.fromNamespaceAndPath(
                        SomeBuckets.MODID, "gametest/powder_snow_placed"));
        CriterionTrigger.Listener<ItemUsedOnLocationTrigger.TriggerInstance> criterionListener =
                new CriterionTrigger.Listener<>(criterion.triggerInstance(), advancement, "placed");
        GameTestSupport.GameEventRecorder recorder = new GameTestSupport.GameEventRecorder(helper, TARGET);

        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        InteractionResult result;
        CriteriaTriggers.PLACED_BLOCK.addPlayerListener(player.getAdvancements(), criterionListener);
        recorder.add(level);
        try {
            result = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET, Direction.UP)));
        } finally {
            recorder.remove(level);
            CriteriaTriggers.PLACED_BLOCK.removePlayerListener(player.getAdvancements(), criterionListener);
        }

        GameTestSupport.check(result.consumesAction(), "Player powder placement did not succeed");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertPowder(bucket, 1);
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
     * In game: right-click the top of a powder snow block with a Big Bucket already holding eight
     * powder snow; the bucket cannot take more, so it builds a powder snow block on top, as a vanilla
     * powder snow bucket does, and holds seven.
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
     * In game: hold a Big Bucket with some powder snow, sneak, and right-click the top of a powder
     * snow block; a new powder snow block appears on top instead of the bucket collecting the one you
     * aimed at. Right-click that new block without sneaking; the bucket collects it.
     */
    static void powder_snow_sneak_use_places_on_existing_block(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        helper.setBlock(TARGET.above(), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.powder(GameTestSupport.big8(), 2));

        player.setShiftKeyDown(true);
        InteractionResult placed = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(placed.consumesAction(), "Sneak-use on powder snow did not place a block");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.POWDER_SNOW);
        GameTestSupport.assertPowder(player.getMainHandItem(), 1);

        player.setShiftKeyDown(false);
        InteractionResult collected = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET.above(), Direction.UP));

        GameTestSupport.check(collected.consumesAction(), "Normal use on powder snow did not collect it");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        GameTestSupport.assertPowder(player.getMainHandItem(), 2);
        helper.succeed();
    }

    /**
     * In game: right-click an adult cow with an empty Big Bucket; it holds one unit of milk.
     * Right-click a calf; nothing happens.
     */
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

    /**
     * In game: while poisoned, drink from a Big Bucket holding two units of milk; the poison ends and
     * the bucket has one unit left.
     */
    static void drinking_milk_removes_effect_and_consumes_one_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), 2000);
        BBItem item = (BBItem) bucket.getItem();
        Player player = GameTestSupport.connectedServerPlayer(helper, new BlockPos(2, 2, 2));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));

        item.finishUsingItem(bucket, helper.getLevel(), player);

        GameTestSupport.check(!player.hasEffect(MobEffects.POISON), "Milk did not remove status effect");
        GameTestSupport.assertMilk(bucket, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a Big Bucket with three units of lava, sneak, look at the sky, and right-click;
     * the bucket is emptied.
     */
    static void shift_use_in_air_discards_contents(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 3000));
        player.setShiftKeyDown(true);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * A refused cauldron interaction does not pour beside the cauldron. In game: hold a Big Bucket
     * with two buckets of water and right-click a two-thirds full water cauldron without sneaking:
     * nothing is poured and the bucket keeps its water.
     */
    static void big_bucket_use_on_partial_water_cauldron_pours_nothing(GameTestHelper helper) {
        BlockState cauldron = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 2);
        assertFluidUseOnCauldronPoursNothing(helper, cauldron);
    }

    /**
     * A fluid Big Bucket does not pour beside a powder-snow cauldron it cannot use. In game: hold a
     * Big Bucket with two buckets of water and right-click a full powder snow cauldron without
     * sneaking: nothing is poured and the bucket keeps its water.
     */
    static void big_bucket_use_on_powder_snow_cauldron_pours_nothing(GameTestHelper helper) {
        BlockState cauldron = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        assertFluidUseOnCauldronPoursNothing(helper, cauldron);
    }

    /**
     * A powder-snow Big Bucket does not build on a cauldron it cannot use. In game: hold a Big
     * Bucket with two powder snow and right-click a full water cauldron without sneaking: no powder
     * snow appears on top and the bucket keeps both units.
     */
    static void powder_big_bucket_use_on_water_cauldron_places_nothing(GameTestHelper helper) {
        BlockState cauldron = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(TARGET, cauldron);
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 2);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.above(2), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        InteractionResult onBlock = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP)));
        bucket.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!onBlock.consumesAction(), "Powder Big Bucket acted on a water cauldron");
        GameTestSupport.check(helper.getBlockState(TARGET).equals(cauldron), "Water cauldron changed");
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        GameTestSupport.assertPowder(bucket, 2);
        helper.succeed();
    }

    /* Drives a non-sneaking item use of a two-unit water Big Bucket at a cauldron the map refused. */
    private static void assertFluidUseOnCauldronPoursNothing(GameTestHelper helper, BlockState cauldron) {
        helper.setBlock(TARGET, cauldron);
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.above(2), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        bucket.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(helper.getBlockState(TARGET).equals(cauldron),
                "Cauldron changed: " + helper.getBlockState(TARGET));
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 2000);
        helper.succeed();
    }

    /**
     * In game: hold a stack of 16 empty Big Buckets and right-click a water source; the source
     * disappears, 15 empty Big Buckets stay in hand, and one Big Bucket holding one unit of water
     * appears in the inventory.
     */
    static void stacked_empty_big_buckets_fill_one_from_source(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = playerAbove(helper, GameTestSupport.big8().copyWithCount(GameTestSupport.STACKED_EMPTIES));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Big Buckets did not collect water source");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertFluid(GameTestSupport.assertOneFilledFromStack(
                player, InteractionHand.MAIN_HAND, GameTestSupport.big8().getItem()), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a stack of 16 empty Big Buckets and right-click an adult cow; 15 empty Big
     * Buckets stay in hand and one Big Bucket holding one unit of milk appears in the inventory.
     */
    static void stacked_empty_big_buckets_milk_one_from_cow(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND,
                GameTestSupport.big8().copyWithCount(GameTestSupport.STACKED_EMPTIES));
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(3, 2, 2));

        InteractionResult result = player.interactOn(cow, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Big Buckets did not milk the cow");
        GameTestSupport.assertMilk(GameTestSupport.assertOneFilledFromStack(
                player, InteractionHand.MAIN_HAND, GameTestSupport.big8().getItem()), 1000);
        helper.succeed();
    }

    /**
     * In game: hold a stack of 16 empty Big Buckets and right-click a full powder snow cauldron; the
     * cauldron empties, 15 empty Big Buckets stay in hand, and one Big Bucket holding one powder snow
     * appears in the inventory.
     */
    static void stacked_empty_big_buckets_take_one_powder_from_cauldron(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        Player player = playerAbove(helper, GameTestSupport.big8().copyWithCount(GameTestSupport.STACKED_EMPTIES));

        ItemInteractionResult result = helper.getBlockState(TARGET).useItemOn(player.getMainHandItem(),
                helper.getLevel(), player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Stacked empty Big Buckets did not take cauldron powder snow");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.CAULDRON);
        GameTestSupport.assertPowder(GameTestSupport.assertOneFilledFromStack(
                player, InteractionHand.MAIN_HAND, GameTestSupport.big8().getItem()), 1);
        helper.succeed();
    }

    /** A survival player standing on the block above {@code TARGET}, looking down, holding {@code bucket}. */
    private static Player playerAbove(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }
}
