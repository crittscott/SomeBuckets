package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.advancements.criterion.ItemUsedOnLocationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-loader Big and Huge Bucket scenarios; each loader wraps every method as a GameTest. Player
 * scenarios right-click through {@link GameTestSupport#useItem}, {@link GameTestSupport#clickBlock},
 * or {@link GameTestSupport#interactWithEntity}.
 */
final class BBScenarios {
    private BBScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    @FunctionalInterface
    interface ScopedPlaceCancellation {
        void run(BlockPos absoluteTarget, Runnable onEvent, Runnable action);
    }

    /**
     * No in-game form: vanilla never cancels the loader's block-place event, only a protection mod does.
     * Guards that a player's powder snow placement posts the event once and, when it is canceled, places
     * nothing and leaves the Big Bucket unchanged.
     */
    static void powder_snow_place_event_cancellation_is_atomic(
            GameTestHelper helper, ScopedPlaceCancellation cancelPlacement) {
        helper.setBlock(TARGET.below(), Blocks.STONE);
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        ItemStack before = bucket.copy();
        Player player = playerAbove(helper, bucket);

        int[] eventCalls = {0};
        InteractionResult[] result = new InteractionResult[1];
        cancelPlacement.run(helper.absolutePos(TARGET), () -> eventCalls[0]++,
                () -> result[0] = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                        GameTestSupport.hit(helper, TARGET.below(), Direction.UP)));

        GameTestSupport.check(eventCalls[0] == 1, "Powder placement did not post one place event");
        GameTestSupport.check(!result[0].consumesAction(),
                "Canceled place event reported successful powder placement");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertSameStack(before, player.getItemInHand(InteractionHand.MAIN_HAND),
                "Canceled powder placement debited the Big Bucket");
        helper.succeed();
    }
    /** In game: right-click a water source with an empty Big Bucket; the source disappears and the bucket holds one unit. */
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
        ServerPlayer player = serverPlayerAbove(helper, TARGET.above(), GameTestSupport.big8());
        ItemStack bucket = player.getMainHandItem();

        var filled = GameTestSupport.filledBucketProbe(player);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult result = filled.during(() ->
                GameTestSupport.useItem(player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(result.consumesAction(), "Player Big Bucket world pickup failed");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player Big Bucket world pickup did not award exactly one item-use statistic");
        GameTestSupport.check(filled.fired(),
                "Player Big Bucket world pickup did not fire the filled-bucket criterion");
        helper.succeed();
    }
    /**
     * In game: right-click a water source with a Big Bucket holding one unit of water; it collects the
     * source and holds two units instead of placing.
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
     * player's right-click on lava goes on to place water instead.
     */
    static void bucket_refuses_different_source_without_mutation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = FluidTransactions.tryTakeFinite(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel())));

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

        boolean acted = FluidTransactions.tryTakeFinite(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel())));

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

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not collect water from a waterlogged block");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Waterlogged block kept its water after pickup");
        helper.succeed();
    }
    /** In game: right-click flowing water (not a source) with an empty Big Bucket; the flow and bucket remain unchanged. */
    static void flowing_fluid_is_not_collected(GameTestHelper helper) {
        helper.setBlock(TARGET.below(), Blocks.STONE);
        helper.setBlock(TARGET, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
        ItemStack bucket = GameTestSupport.big8();
        ItemStack before = bucket.copy();
        Player player = playerAbove(helper, bucket);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Big Bucket collected flowing water");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Rejected flowing pickup mutated bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }
    /**
     * In game: right-click the top of a stone block with a Big Bucket holding one unit of water; one
     * source appears on top and the bucket becomes canonical empty.
     */
    static void placement_consumes_one_unit_and_final_unit_normalizes(GameTestHelper helper) {
        helper.setBlock(TARGET.below(), Blocks.STONE);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET.below(), Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not place water");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }
    /**
     * In game: right-click the east side of a stone block with a Big Bucket holding two units of water;
     * water appears beside the stone, on the face you clicked, and the bucket has one unit left.
     */
    static void placement_falls_through_solid_clicked_block(GameTestHelper helper) {
        BlockPos neighbor = TARGET.east();
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(neighbor, Blocks.AIR);
        helper.setBlock(TARGET.east(2), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.east(3), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.EAST));

        GameTestSupport.check(result.consumesAction(), "Fluid did not fall through to clicked-face neighbor");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.WATER);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }
    /**
     * In game: right-click a dry oak fence with a Big Bucket holding two units of water; the fence
     * becomes waterlogged and the bucket has one unit left.
     */
    static void placement_waterlogs_liquid_container(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.OAK_FENCE);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

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
     * In game: put a Big Bucket holding one powder snow in a dispenser facing a stone block and pulse
     * it; the stone stays, no powder snow appears, and the bucket keeps its unit.
     */
    static void failed_powder_snow_placement_is_atomic(GameTestHelper helper) {
        BlockPos dispenserPos = TARGET.west();
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.STONE);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, dispenserPos, Direction.EAST, bucket);

        GameTestSupport.dispenseNow(helper, dispenserPos);

        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        GameTestSupport.assertSameStack(before, dispenser.getItem(0), "Failed powder placement debited the Big Bucket");
        helper.succeed();
    }
    /**
     * In game: right-click the side of a stone block whose open face lies beyond the world border, with
     * a Big Bucket holding one powder snow; no powder snow appears and the bucket keeps its unit. The
     * protection check refuses before the native placement runs.
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

        InteractionResult result = ProtectionScenarios.outsideWorldBorder(helper, () ->
                GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                        GameTestSupport.hit(helper, TARGET, Direction.EAST)));

        GameTestSupport.check(!result.consumesAction(), "Protection denial allowed powder placement");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, placeTarget, Blocks.AIR);
        GameTestSupport.assertSameStack(before, player.getMainHandItem(), "Protection denial debited the Big Bucket");
        helper.succeed();
    }
    /**
     * In game: right-click the top of a stone block with a Big Bucket holding two powder snow; a powder
     * snow block appears, the bucket keeps one unit, the Big Bucket's "used" statistic rises by one, a
     * nearby sculk sensor hears a block being placed (not a fluid), and placed-block advancements fire.
     */
    static void powder_snow_player_placement_emits_native_observability(GameTestHelper helper) {
        helper.setBlock(TARGET.below(), Blocks.STONE);
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 2);
        ServerPlayer player = serverPlayerAbove(helper, TARGET.above(), bucket);

        var placedCriterion = new GameTestSupport.CriterionProbe<>(player,
                ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.POWDER_SNOW));
        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, TARGET);

        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        InteractionResult result = placedCriterion.during(() -> recorder.during(() ->
                GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                        GameTestSupport.hit(helper, TARGET.below(), Direction.UP))));

        GameTestSupport.check(result.consumesAction(), "Player powder placement did not succeed");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        GameTestSupport.assertPowder(player.getMainHandItem(), 1);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem())) == statBefore + 1,
                "Successful powder placement did not award exactly one Big Bucket use");
        GameTestSupport.check(placedCriterion.fired(),
                "Successful powder placement did not fire the placed-block criterion");
        GameTestSupport.check(recorder.count(GameEvent.BLOCK_PLACE) == 1,
                "Successful powder placement did not emit exactly one block-place game event");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 0,
                "Powder placement emitted a fluid-place game event");
        GameTestSupport.check(recorder.contexts().stream().anyMatch(context ->
                        context.affectedState() != null && context.affectedState().is(Blocks.POWDER_SNOW)),
                "Block-place game event did not carry the placed powder-snow state");
        helper.succeed();
    }
    /**
     * In game: right-click the top of a stone block with a Big Bucket holding two units of water; water
     * appears on top, the bucket has one unit left, the Big Bucket's "used" statistic rises by one, a
     * nearby sculk sensor hears a fluid being placed, and placed-block advancements fire.
     */
    static void fluid_player_placement_emits_native_observability(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        BlockPos placed = TARGET.above();
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(placed, Blocks.AIR);
        ServerPlayer player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.above().east(2), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        var placedCriterion = new GameTestSupport.CriterionProbe<>(player,
                ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(Blocks.WATER));
        GameTestSupport.EventRecorder recorder = new GameTestSupport.EventRecorder(helper, placed);

        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        InteractionResult result = placedCriterion.during(() -> recorder.during(() ->
                GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                        GameTestSupport.hit(helper, TARGET, Direction.UP))));

        GameTestSupport.check(result.consumesAction(), "Player fluid placement did not succeed");
        GameTestSupport.assertBlock(helper, placed, Blocks.WATER);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem())) == statBefore + 1,
                "Successful fluid placement did not award exactly one Big Bucket use");
        GameTestSupport.check(placedCriterion.fired(),
                "Successful fluid placement did not fire the placed-block criterion");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Successful fluid placement did not emit exactly one fluid-place game event");
        helper.succeed();
    }
    /**
     * In game: right-click the top of a powder snow block with a Big Bucket already holding eight powder
     * snow; the bucket cannot take a ninth, so it builds a powder snow block on top instead, as a
     * vanilla powder snow bucket does, and holds seven.
     */
    static void powder_snow_capacity_is_enforced(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        helper.setBlock(TARGET.above(), Blocks.AIR);
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
     * In game: sneak and right-click the top of a powder snow block with a Big Bucket holding two powder
     * snow; another block is placed on top instead of the targeted one being collected. Right-click
     * that new block without sneaking; the bucket collects it.
     */
    static void powder_snow_sneak_use_places_on_existing_block(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        helper.setBlock(TARGET.above(), Blocks.AIR);
        helper.setBlock(TARGET.above().east(), Blocks.AIR);
        // Looking down and in from the east: the ray reaches the target's top through the air above it,
        // and once a block is placed there, that block's east face.
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.above().east(2), TARGET);
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
    /** In game: right-click an adult cow and then a calf with an empty Big Bucket; only the adult adds one milk unit. */
    static void adult_cow_adds_milk_but_baby_does_not(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        Cow adult = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(3, 2, 2));
        Cow baby = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(5, 2, 2));
        baby.setAge(-24000);

        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.big8());
        InteractionResult adultResult = GameTestSupport.interactWithEntity(player, adult, InteractionHand.MAIN_HAND);
        GameTestSupport.check(adultResult.consumesAction(), "Adult cow milking did not succeed");
        GameTestSupport.assertMilk(player.getMainHandItem(), 1000);

        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.big8());
        InteractionResult babyResult = GameTestSupport.interactWithEntity(player, baby, InteractionHand.MAIN_HAND);
        GameTestSupport.check(!babyResult.consumesAction(), "Baby cow was milked");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }
    /**
     * In game: drink from a milk-filled Big Bucket while affected by a potion; effects clear and one unit
     * is consumed. The bucket stays drinkable until its last unit is gone.
     */
    static void drinking_milk_removes_effect_and_consumes_one_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), 2000);
        BBItem item = (BBItem) bucket.getItem();
        Player player = GameTestSupport.serverPlayer(helper, new BlockPos(2, 2, 2));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        GameTestSupport.check(bucket.has(DataComponents.CONSUMABLE), "Milk-filled bucket is not consumable");

        item.finishUsingItem(bucket, helper.getLevel(), player);

        GameTestSupport.check(!player.hasEffect(MobEffects.POISON), "Milk did not remove status effect");
        GameTestSupport.assertMilk(bucket, 1000);
        GameTestSupport.check(bucket.has(DataComponents.CONSUMABLE), "Partly drunk bucket lost its consumable");

        item.finishUsingItem(bucket, helper.getLevel(), player);

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(!bucket.has(DataComponents.CONSUMABLE), "Emptied bucket is still consumable");
        helper.succeed();
    }
    /** In game: sneak, look at the sky, and right-click with a nonempty Big Bucket; all contents are discarded. */
    static void shift_use_in_air_discards_contents(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 3000));
        player.setShiftKeyDown(true);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneak air use did not discard the contents");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }
    /**
     * In game: hold three empty Big Buckets and right-click a water source; two empties stay in hand
     * and one bucket holding one unit goes into the inventory.
     */
    static void stacked_empty_pickup_moves_one_filled_bucket_to_inventory(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        heldEmptyStack(player, 3);
        helper.setBlock(TARGET, Blocks.WATER);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Big Bucket did not collect water");
        assertHandEmpties(player, 2);
        List<ItemStack> filled = filledBuckets(player);
        GameTestSupport.check(filled.size() == 1, "Expected one filled bucket in inventory, got " + filled);
        GameTestSupport.assertFluid(filled.get(0), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    /**
     * In game: with every other inventory slot full, right-click a water source with a stack of empty
     * Big Buckets; the filled bucket drops at the player's feet.
     */
    static void stacked_empty_pickup_drops_filled_bucket_when_inventory_is_full(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        ItemStack held = heldEmptyStack(player, 3);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot != inventory.getSelectedSlot()) inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        helper.setBlock(TARGET, Blocks.WATER);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Big Bucket did not collect water");
        assertHandEmpties(player, 2);
        GameTestSupport.check(filledBuckets(player).isEmpty(), "Filled bucket entered a full inventory");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(4.0D), entity -> entity.getItem().getItem() == held.getItem());
        GameTestSupport.check(drops.size() == 1, "Expected one dropped filled bucket, got " + drops.size());
        GameTestSupport.assertFluid(drops.get(0).getItem(), Fluids.WATER, 1000);
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }
    /**
     * In game: right-click an adult cow while holding three empty Big Buckets; two empties stay in hand
     * and one bucket holding one milk unit goes into the inventory.
     */
    static void stacked_empty_milking_moves_one_filled_bucket_to_inventory(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        heldEmptyStack(player, 3);
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(3, 2, 2));

        InteractionResult result = GameTestSupport.interactWithEntity(player, cow, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Big Bucket did not milk the cow");
        assertHandEmpties(player, 2);
        List<ItemStack> filled = filledBuckets(player);
        GameTestSupport.check(filled.size() == 1, "Expected one milk bucket in inventory, got " + filled);
        GameTestSupport.assertMilk(filled.get(0), 1000);
        helper.succeed();
    }
    /**
     * In game: in creative mode, right-click a water source with a stack of empty Big Buckets; the
     * empties stay in hand and one filled bucket is added, as with vanilla buckets.
     */
    static void creative_stacked_empty_pickup_keeps_empties(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.getAbilities().instabuild = true;
        heldEmptyStack(player, 3);
        helper.setBlock(TARGET, Blocks.WATER);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Creative stacked Big Bucket did not collect water");
        assertHandEmpties(player, 3);
        List<ItemStack> filled = filledBuckets(player);
        GameTestSupport.check(filled.size() == 1, "Expected one filled bucket in inventory, got " + filled);
        GameTestSupport.assertFluid(filled.get(0), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * In game: right-click the side of a stone block with a full Big Bucket; it places water in front of
     * that face and loses one unit.
     */
    static void full_bucket_use_places_one_unit(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.north(2), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.NORTH));

        GameTestSupport.check(result.consumesAction(), "Full Big Bucket use did not place water");
        GameTestSupport.assertBlock(helper, TARGET.north(), Blocks.WATER);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 7000);
        helper.succeed();
    }
    /**
     * In game: right-click a water source with a partly water-filled Big Bucket; it collects the source
     * rather than placing.
     */
    static void partial_bucket_use_collects_before_placing(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = playerAbove(helper, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Partial Big Bucket use did not collect the source");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 2000);
        helper.succeed();
    }
    /**
     * In game: right-click the side of a stone block with a partly water-filled Big Bucket; with nothing
     * to collect it places one unit in front of that face.
     */
    static void partial_bucket_use_places_when_nothing_to_collect(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        Player player = GameTestSupport.survivalPlayerLookingAt(helper, TARGET.north(2), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.NORTH));

        GameTestSupport.check(result.consumesAction(), "Partial Big Bucket use did not place water");
        GameTestSupport.assertBlock(helper, TARGET.north(), Blocks.WATER);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }
    /** In game: right-click powder snow with an empty Big Bucket; it collects the block. */
    static void empty_bucket_use_collects_powder_snow(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = playerAbove(helper, GameTestSupport.big8());

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Empty Big Bucket use did not collect powder snow");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertPowder(player.getMainHandItem(), 1);
        helper.succeed();
    }
    /**
     * In game: without sneaking, right-click powder snow with a partly filled powder-snow Big Bucket; it
     * collects the block rather than placing another.
     */
    static void partial_powder_bucket_use_collects_powder_snow(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = playerAbove(helper, GameTestSupport.powder(GameTestSupport.big8(), 1));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Partial powder Big Bucket use did not collect");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.AIR);
        GameTestSupport.assertPowder(player.getMainHandItem(), 2);
        helper.succeed();
    }

    private static void assertHandEmpties(Player player, int count) {
        ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
        GameTestSupport.check(hand.getItem() instanceof BBItem && hand.getCount() == count,
                "Expected " + count + " empty Big Buckets in hand, got " + hand);
        GameTestSupport.assertEmpty(hand);
    }

    private static List<ItemStack> filledBuckets(Player player) {
        List<ItemStack> filled = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof BBItem && !BucketState.isEmptyBucket(stack)) filled.add(stack);
        }
        return filled;
    }

    private static ItemStack heldEmptyStack(Player player, int count) {
        ItemStack held = GameTestSupport.big8();
        held.setCount(count);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return held;
    }

    /** A survival player standing on the block above {@code TARGET}, looking down, holding {@code bucket}. */
    private static Player playerAbove(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }

    /** A connected server player at {@code position}, looking straight down, holding {@code bucket}. */
    private static ServerPlayer serverPlayerAbove(GameTestHelper helper, BlockPos position, ItemStack bucket) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, position);
        player.setXRot(90.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }
}
