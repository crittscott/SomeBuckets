package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/**
 * Cross-loader Source Bucket scenarios; each loader wraps every method as a GameTest. Player
 * scenarios right-click through {@link GameTestSupport#useItem}, {@link GameTestSupport#clickBlock},
 * or {@link GameTestSupport#interactWithEntity}.
 */
final class SBScenarios {
    private SBScenarios() {}
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);
    /**
     * In game: right-click a lava source with an empty Source Bucket; the source disappears and the
     * bucket is assigned to lava.
     */
    static void empty_source_acquires_world_fluid(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.LAVA);
        Player player = playerAbove(helper, TARGET, GameTestSupport.source());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Empty Source Bucket did not acquire lava");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    /**
     * In game: right-click a lava source with an empty Source Bucket; it counts as filling a bucket for
     * advancements, and the Source Bucket's "used" statistic rises by exactly one.
     */
    static void player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(
            GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.LAVA);
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        player.setXRot(90.0F);
        ItemStack bucket = GameTestSupport.source();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        var filled = new GameTestSupport.CriterionProbe(player, "filled_bucket");
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult result = filled.during(() ->
                GameTestSupport.useItem(player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(result.consumesAction(), "Player Source Bucket world pickup failed");
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player Source Bucket world pickup did not award exactly one item-use statistic");
        GameTestSupport.check(filled.fired(),
                "Player Source Bucket world pickup did not fire the filled-bucket criterion");
        helper.succeed();
    }
    /**
     * In game: right-click a waterlogged fence with an empty Source Bucket; the fence survives dry and
     * the bucket is assigned to water.
     */
    static void waterlogged_block_assigns_source_and_survives(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        Player player = playerAbove(helper, TARGET, GameTestSupport.source());

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Source Bucket did not take water from a waterlogged block");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Waterlogged block kept its water after pickup");
        helper.succeed();
    }
    /**
     * No in-game form: guards the take rule alone, that a water Source Bucket never takes lava. A
     * player's normal right-click on lava places water instead; the sneak-use is covered by
     * {@link #assigned_source_sneak_right_click_ignores_different_world_fluid}.
     */
    static void assigned_source_refuses_reassignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.LAVA);

        boolean acted = FluidTransactions.tryTakeSource(
                helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), bucket,
                ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel())));

        GameTestSupport.check(!acted, "Assigned Source Bucket changed fluid");
        GameTestSupport.assertSameStack(before, bucket, "Rejected reassignment mutated Source Bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }
    /**
     * In game: sneak and right-click source water with a water-assigned Source Bucket; the water is
     * removed and the assignment remains.
     */
    static void assigned_source_sneak_right_click_takes_matching_world_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = playerAbove(helper, TARGET, bucket);
        player.setShiftKeyDown(true);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneak-use did not take the matching source");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Matching pickup changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }
    /**
     * In game: sneak and right-click lava with a water-assigned Source Bucket; neither the lava nor the
     * assignment changes.
     */
    static void assigned_source_sneak_right_click_ignores_different_world_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.LAVA);
        Player player = playerAbove(helper, TARGET, bucket);
        player.setShiftKeyDown(true);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Sneak-use acted on a different world fluid");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Different-fluid target changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }
    /**
     * In game: right-click the side of a stone block with a water-assigned Source Bucket; water appears
     * beside the stone and the assignment is unchanged.
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

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.NORTH));

        GameTestSupport.check(result.consumesAction(), "Source Bucket did not place water");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Normal placement changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, placeTarget, Blocks.WATER);
        helper.succeed();
    }
    /**
     * In game: sneak and right-click a waterlogged fence with a water-assigned Source Bucket; only its
     * water is removed and the assignment remains.
     */
    static void assigned_source_takes_matching_waterlogged_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        Player player = playerAbove(helper, TARGET, bucket);
        player.setShiftKeyDown(true);

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Assigned Source Bucket did not take waterlogged source");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Waterlogged pickup changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Matching waterlogged source remained after pickup");
        helper.succeed();
    }
    /**
     * In game: right-click the floor in two places with a water-assigned Source Bucket; water appears in
     * both and the assignment is unchanged.
     */
    static void source_places_repeatedly_without_consumption(GameTestHelper helper) {
        BlockPos first = new BlockPos(3, 2, 4);
        BlockPos second = new BlockPos(5, 2, 4);
        helper.setBlock(first.below(), Blocks.STONE);
        helper.setBlock(second.below(), Blocks.STONE);

        Player firstPlayer = playerAbove(helper, first,
                GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000));
        InteractionResult firstResult = GameTestSupport.clickBlock(firstPlayer, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, first.below(), Direction.UP));
        Player secondPlayer = playerAbove(helper, second, firstPlayer.getMainHandItem());
        InteractionResult secondResult = GameTestSupport.clickBlock(secondPlayer, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, second.below(), Direction.UP));

        GameTestSupport.check(firstResult.consumesAction() && secondResult.consumesAction(),
                "Source Bucket did not place repeatedly");
        GameTestSupport.assertBlock(helper, first, Blocks.WATER);
        GameTestSupport.assertBlock(helper, second, Blocks.WATER);
        GameTestSupport.assertFluid(secondPlayer.getMainHandItem(), Fluids.WATER, 1000);
        helper.succeed();
    }
    /**
     * In game: right-click a full water cauldron with an empty Source Bucket; it is assigned to water
     * and the cauldron empties. Automation also checks the air-use dispatch alone leaves the cauldron
     * to the block click.
     */
    static void source_serves_cauldron_from_block_use(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        Player player = playerAbove(helper, TARGET, GameTestSupport.source());

        InteractionResult airUse = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
        GameTestSupport.check(!airUse.consumesAction(), "Source Bucket item use acted on the cauldron");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER_CAULDRON);

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Source Bucket block use did not take from the cauldron");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.CAULDRON);
        helper.succeed();
    }
    /**
     * In game: right-click a full water cauldron with an empty Source Bucket; it is assigned to water
     * and the cauldron empties.
     */
    static void empty_source_acquires_full_water_cauldron(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        Player player = playerAbove(helper, TARGET, GameTestSupport.source());

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Source Bucket did not acquire full water cauldron");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.CAULDRON);
        helper.succeed();
    }
    /**
     * In game: right-click an empty cauldron with a lava-assigned Source Bucket; it becomes a full lava
     * cauldron and the assignment is unchanged.
     */
    static void source_fills_empty_cauldron_without_consumption(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.CAULDRON);
        Player player = playerAbove(helper, TARGET, GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Lava Source Bucket did not fill cauldron");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 1000);
        helper.succeed();
    }
    /** In game: right-click an adult cow and a calf with an empty Source Bucket; only the adult assigns infinite milk. */
    static void adult_cow_assigns_milk_but_baby_does_not(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        Cow adult = GameTestSupport.spawn(helper, EntityTypes.COW, new BlockPos(3, 2, 2));
        Cow baby = GameTestSupport.spawn(helper, EntityTypes.COW, new BlockPos(5, 2, 2));
        baby.setAge(-24000);

        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());
        InteractionResult adultResult = GameTestSupport.interactWithEntity(player, adult, InteractionHand.MAIN_HAND);
        GameTestSupport.check(adultResult.consumesAction(), "Adult cow did not assign Source Bucket milk");
        GameTestSupport.assertMilk(player.getMainHandItem(), 1000);

        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());
        InteractionResult babyResult = GameTestSupport.interactWithEntity(player, baby, InteractionHand.MAIN_HAND);
        GameTestSupport.check(!babyResult.consumesAction(), "Baby cow assigned Source Bucket milk");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }
    /**
     * In game: drink from a milk-assigned Source Bucket while affected by a potion; effects clear and milk
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
    /** In game: look at the sky and right-click with a lava-assigned Source Bucket; its assignment remains unchanged. */
    static void normal_use_in_air_preserves_source_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayerLookingAtAir(helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Normal air use changed Source Bucket assignment");
        helper.succeed();
    }
    /** In game: sneak, look at the sky, and right-click with a lava-assigned Source Bucket; it becomes unassigned. */
    static void sneak_use_in_air_clears_source_assignment(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND,
                GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000));
        player.setShiftKeyDown(true);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneak air use did not clear the assignment");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }
    /**
     * In game: sneak, look at the sky, and right-click with a milk-assigned Source Bucket; it becomes
     * unassigned instead of drinking.
     */
    static void sneak_use_in_air_clears_source_milk_assignment(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.milk(GameTestSupport.source(), 1000));
        player.setShiftKeyDown(true);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneak air use did not clear the milk assignment");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }
    /**
     * In game: look at the sky and right-click with a milk-assigned Source Bucket without sneaking; you
     * start drinking and milk remains assigned.
     */
    static void normal_use_in_air_on_source_milk_preserves_assignment(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayerLookingAtAir(
                helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.milk(GameTestSupport.source(), 1000));

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.assertMilk(player.getMainHandItem(), 1000);
        helper.succeed();
    }
    /** In game: right-click powder snow with an empty Source Bucket; neither block nor bucket changes. */
    static void source_does_not_support_powder_snow(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = playerAbove(helper, TARGET, GameTestSupport.source());

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(!result.consumesAction(), "Source Bucket acted on powder snow");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        helper.succeed();
    }
    /**
     * In game: set the Source Bucket allowlist empty and reload, then right-click water, lava, and an
     * adult cow with empty Source Buckets; every assignment is refused and the world is unchanged.
     */
    static void empty_allow_list_disables_all_source_contents(GameTestHelper helper) {
        Runnable restorePolicy = GameTestSupport.sourcePolicyRestorer();
        BlockPos waterPos = TARGET;
        BlockPos lavaPos = TARGET.east(2);
        helper.setBlock(waterPos, Blocks.WATER);
        helper.setBlock(lavaPos, Blocks.LAVA);
        Cow cow = GameTestSupport.spawn(helper, EntityTypes.COW, new BlockPos(3, 2, 2));
        Player waterPlayer = playerAbove(helper, waterPos, GameTestSupport.source());
        Player lavaPlayer = playerAbove(helper, lavaPos, GameTestSupport.source());
        Player milkPlayer = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        milkPlayer.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());
        try {
            SBPolicy.refresh(List.<String>of(), "SBScenarios", false);

            InteractionResult tookWater = GameTestSupport.useItem(waterPlayer, InteractionHand.MAIN_HAND);
            InteractionResult tookLava = GameTestSupport.useItem(lavaPlayer, InteractionHand.MAIN_HAND);
            InteractionResult milked = GameTestSupport.interactWithEntity(milkPlayer, cow, InteractionHand.MAIN_HAND);

            GameTestSupport.check(!tookWater.consumesAction(), "Empty allowlist still assigned water");
            GameTestSupport.check(!tookLava.consumesAction(), "Empty allowlist still assigned lava");
            GameTestSupport.check(!milked.consumesAction(), "Empty allowlist still assigned milk");
            GameTestSupport.assertEmpty(waterPlayer.getMainHandItem());
            GameTestSupport.assertEmpty(lavaPlayer.getMainHandItem());
            GameTestSupport.assertEmpty(milkPlayer.getMainHandItem());
            GameTestSupport.assertBlock(helper, waterPos, Blocks.WATER);
            GameTestSupport.assertBlock(helper, lavaPos, Blocks.LAVA);
            helper.succeed();
        } finally {
            restorePolicy.run();
        }
    }

    /** A survival player standing on the block above {@code target}, looking down, holding {@code bucket}. */
    private static Player playerAbove(GameTestHelper helper, BlockPos target, ItemStack bucket) {
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, target.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        return player;
    }
}
