package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
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

/**
 * Source Bucket world, cauldron, cow, milk, and allowlist scenarios shared by every loader. Player
 * scenarios right-click through {@link GameTestSupport#useItem}.
 */
final class SBScenarios {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private SBScenarios() {}

    /**
     * In game: right-click a lava source with an empty Source Bucket; the lava disappears and the
     * bucket becomes a lava Source Bucket.
     */
    static void empty_source_acquires_world_fluid(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.LAVA);
        Player player = playerAbove(helper, GameTestSupport.source());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Empty Source Bucket did not acquire lava");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: right-click a lava source with an empty Source Bucket; it counts as filling a bucket
     * for advancements, and the Source Bucket's "used" statistic rises by exactly one.
     */
    static void player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(
            GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.LAVA);
        ServerPlayer player = (ServerPlayer) playerAbove(helper, GameTestSupport.source());
        ItemStack bucket = player.getMainHandItem();
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult[] result = new InteractionResult[1];
        boolean filled = GameTestSupport.firesFilledBucket(player,
                () -> result[0] = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(result[0].consumesAction(), "Player Source Bucket world pickup failed");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player Source Bucket world pickup did not award exactly one item-use statistic");
        GameTestSupport.check(filled, "Player Source Bucket world pickup did not fire the filled-bucket criterion");
        helper.succeed();
    }

    /**
     * In game: right-click a waterlogged fence with an empty Source Bucket; the fence stays, loses its
     * water, and the bucket becomes a water Source Bucket.
     */
    static void waterlogged_block_assigns_source_and_survives(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        Player player = playerAbove(helper, GameTestSupport.source());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Source Bucket did not take water from a waterlogged block");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Waterlogged block kept its water after pickup");
        helper.succeed();
    }

    /**
     * No in-game form: guards the take rule alone, that a water Source Bucket never takes lava. A
     * player's sneak-use on lava is covered by
     * {@link #assigned_source_sneak_right_click_ignores_different_world_fluid}.
     */
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
     * In game: sneak and right-click a water source with a water Source Bucket; the source disappears
     * and the bucket stays a water Source Bucket.
     */
    static void assigned_source_sneak_right_click_takes_matching_world_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.WATER);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, bucket,
                "Matching pickup changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: sneak and right-click a lava source with a water Source Bucket; the lava stays and the
     * bucket stays a water Source Bucket.
     */
    static void assigned_source_sneak_right_click_ignores_different_world_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.LAVA);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, bucket,
                "Different-fluid target changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }

    /**
     * In game: right-click the side of a stone block with a water Source Bucket; water appears beside
     * the stone and the bucket stays a water Source Bucket.
     */
    static void assigned_source_normal_right_click_places_without_consumption(GameTestHelper helper) {
        BlockPos placeTarget = TARGET.north();
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(placeTarget, Blocks.AIR);
        helper.setBlock(TARGET.north(2), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingAt(
                helper, TARGET.north(3), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, bucket,
                "Normal placement changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, placeTarget, Blocks.WATER);
        helper.succeed();
    }

    /**
     * In game: sneak and right-click a waterlogged fence with a water Source Bucket; the fence loses its
     * water and the bucket stays a water Source Bucket.
     */
    static void assigned_source_takes_matching_waterlogged_source(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        Player player = playerAbove(helper, bucket);
        player.setShiftKeyDown(true);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Assigned Source Bucket did not take waterlogged source");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Waterlogged pickup changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Matching waterlogged source remained after pickup");
        helper.succeed();
    }

    /**
     * In game: right-click the floor in two places with a water Source Bucket; water appears in both
     * and the bucket stays a water Source Bucket.
     */
    static void source_places_repeatedly_without_consumption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        BlockPos first = new BlockPos(3, 2, 4);
        BlockPos second = new BlockPos(5, 2, 4);
        helper.setBlock(first.below(), Blocks.STONE);
        helper.setBlock(second.below(), Blocks.STONE);

        Player firstPlayer = GameTestSupport.survivalPlayerLookingDown(helper, first.above());
        firstPlayer.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        InteractionResult firstResult = GameTestSupport.useItem(firstPlayer, InteractionHand.MAIN_HAND);
        Player secondPlayer = GameTestSupport.survivalPlayerLookingDown(helper, second.above());
        secondPlayer.setItemInHand(InteractionHand.MAIN_HAND, firstPlayer.getMainHandItem());
        InteractionResult secondResult = GameTestSupport.useItem(secondPlayer, InteractionHand.MAIN_HAND);

        GameTestSupport.check(firstResult.consumesAction() && secondResult.consumesAction(),
                "Source Bucket did not place repeatedly");
        GameTestSupport.assertBlock(helper, first, Blocks.WATER);
        GameTestSupport.assertBlock(helper, second, Blocks.WATER);
        GameTestSupport.assertFluid(secondPlayer.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }

    /**
     * No in-game form: guards the Source Bucket's cauldron take rule that player clicks and dispensers
     * share: a full water cauldron empties and assigns water. Each loader's {@code CauldronGameTests}
     * clicks a cauldron as a player.
     */
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

    /**
     * No in-game form: guards the Source Bucket's cauldron fill rule that player clicks and dispensers
     * share: an empty cauldron becomes a lava cauldron and the bucket keeps its lava. Each loader's
     * {@code CauldronGameTests} clicks a cauldron as a player.
     */
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

    /**
     * In game: right-click an adult cow with an empty Source Bucket; it becomes a milk Source Bucket.
     * Right-click a calf; nothing happens.
     */
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
     * In game: while poisoned, drink from a milk Source Bucket; the poison ends and the bucket is still
     * a milk Source Bucket.
     */
    static void source_milk_is_not_consumed_by_drinking(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.source(), 1000);
        SBItem item = (SBItem) bucket.getItem();
        Player player = GameTestSupport.connectedServerPlayer(helper, new BlockPos(2, 2, 2));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));

        item.finishUsingItem(bucket, helper.getLevel(), player);

        GameTestSupport.check(!player.hasEffect(MobEffects.POISON), "Source milk did not remove effect");
        GameTestSupport.assertMilk(bucket, 1000);
        helper.succeed();
    }

    /**
     * In game: look at the sky and right-click with a lava Source Bucket; it stays a lava Source
     * Bucket.
     */
    static void normal_use_in_air_preserves_source_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setXRot(-90.0F);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, bucket,
                "Normal air use changed Source Bucket assignment");
        helper.succeed();
    }

    /**
     * In game: sneak, look at the sky, and right-click with a lava Source Bucket; it becomes an empty
     * Source Bucket.
     */
    static void sneak_use_in_air_clears_source_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: sneak, look at the sky, and right-click with a milk Source Bucket; it becomes an empty
     * Source Bucket.
     */
    static void sneak_use_in_air_clears_source_milk_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.source(), 1000);
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: look at the sky and right-click with a milk Source Bucket without sneaking; you start
     * drinking and it stays a milk Source Bucket.
     */
    static void normal_use_in_air_on_source_milk_preserves_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.source(), 1000);
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertMilk(bucket, 1000);
        helper.succeed();
    }

    /**
     * In game: right-click a powder snow block with an empty Source Bucket; nothing is collected.
     */
    static void source_does_not_support_powder_snow(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        helper.succeed();
    }

    /**
     * No in-game form: guards that a server config whose Source Bucket allowlist is empty allows no
     * fluid and no milk.
     */
    static void empty_allow_list_disables_all_source_contents(GameTestHelper helper) {
        try {
            SBPolicy.refresh(List.<String>of(), "SBScenarios", true);

            GameTestSupport.check(!SBPolicy.allows(Fluids.WATER), "Empty allowlist still permitted water");
            GameTestSupport.check(!SBPolicy.allows(Fluids.LAVA), "Empty allowlist still permitted lava");
            GameTestSupport.check(!SBPolicy.allowsMilk(), "Empty allowlist still permitted milk");
            helper.succeed();
        } finally {
            SBPolicy.refresh(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS, "SBScenarios cleanup", true);
        }
    }

    /**
     * In game: hold a stack of 16 empty Source Buckets and right-click a lava source; the lava
     * disappears, 15 empty Source Buckets stay in hand, and one lava Source Bucket appears in the
     * inventory.
     */
    static void stacked_empty_source_buckets_assign_one(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.LAVA);
        Player player = playerAbove(helper, GameTestSupport.source().copyWithCount(GameTestSupport.STACKED_EMPTIES));

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Stacked empty Source Buckets did not acquire lava");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertFluid(GameTestSupport.assertOneFilledFromStack(
                player, InteractionHand.MAIN_HAND, GameTestSupport.source().getItem()), Fluids.LAVA, 1000);
        helper.succeed();
    }

    /** A survival player standing on the block above {@code TARGET}, looking down, holding {@code bucket}. */
    private static Player playerAbove(GameTestHelper helper, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }
}
