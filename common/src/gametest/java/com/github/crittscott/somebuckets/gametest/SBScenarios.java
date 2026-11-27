package com.github.crittscott.somebuckets.gametest;

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

/** Source Bucket world, cauldron, milk and gesture scenarios shared by both loaders. */
final class SBScenarios {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private SBScenarios() {}

    /**
     * In game: hold an empty Source Bucket and right-click a cow, then a calf; the cow assigns the
     * bucket to milk and the calf does nothing.
     */
    static void adult_cow_assigns_milk_but_baby_does_not(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
        Cow adult = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(3, 2, 2));
        Cow baby = GameTestSupport.spawn(helper, EntityType.COW, new BlockPos(5, 2, 2));
        baby.setAge(-24000);

        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());
        InteractionResult adultResult = GameTestSupport.interactWithEntity(player, adult, InteractionHand.MAIN_HAND);
        ItemStack milked = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());
        InteractionResult babyResult = GameTestSupport.interactWithEntity(player, baby, InteractionHand.MAIN_HAND);

        GameTestSupport.check(adultResult.consumesAction(), "Adult cow did not assign Source Bucket milk");
        GameTestSupport.check(!babyResult.consumesAction(), "Baby cow assigned Source Bucket milk");
        GameTestSupport.assertMilk(milked, 1000);
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: hold a water Source Bucket and right-click the top of a stone block; water appears on
     * the stone and the bucket stays assigned to water.
     */
    static void assigned_source_normal_right_click_places_without_consumption(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        helper.setBlock(TARGET, Blocks.STONE);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Source Bucket placement did not succeed");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Normal placement changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET.above(), Blocks.WATER);
        helper.succeed();
    }

    /**
     * In game: hold a water Source Bucket, sneak, and right-click a lava source; nothing happens: the
     * lava stays and the bucket stays assigned to water.
     */
    static void assigned_source_sneak_right_click_ignores_different_world_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.LAVA);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Sneak-use on a different fluid consumed the action");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Different-fluid target changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        helper.succeed();
    }

    /**
     * In game: hold a water Source Bucket, sneak, and right-click a water source; the source
     * disappears and the bucket stays assigned to water.
     */
    static void assigned_source_sneak_right_click_takes_matching_world_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setShiftKeyDown(true);
        helper.setBlock(TARGET, Blocks.WATER);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneak-use on a matching source did not succeed");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Matching pickup changed Source Bucket assignment");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: hold a water Source Bucket, sneak, and right-click a waterlogged oak fence; the fence
     * stays but is no longer waterlogged, and the bucket stays assigned to water.
     */
    static void assigned_source_takes_matching_waterlogged_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
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
     * In game: hold an empty Source Bucket and right-click a full water cauldron; the cauldron empties
     * and the bucket is assigned to water. The cauldron's own block use, which has no Source Bucket
     * entry, is not run.
     */
    static void empty_source_acquires_full_water_cauldron(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Source Bucket did not acquire full water cauldron");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: hold an empty Source Bucket and right-click a lava source; the lava disappears and the
     * bucket is assigned to lava.
     */
    static void empty_source_acquires_world_fluid(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.LAVA);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Empty Source Bucket did not acquire lava");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    /**
     * In game: hold a lava Source Bucket, look at the sky, and right-click; nothing happens and the
     * bucket stays assigned to lava.
     */
    static void normal_use_in_air_preserves_source_assignment(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        player.setXRot(-90.0F);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!result.consumesAction(), "Normal air use consumed the action");
        GameTestSupport.assertSameStack(before, player.getMainHandItem(),
                "Normal air use changed Source Bucket assignment");
        helper.succeed();
    }

    /**
     * In game: hold an empty Source Bucket and right-click a lava source; besides assigning the
     * bucket, the Source Bucket's "times used" statistic rises by one and vanilla's filled-bucket
     * advancement trigger fires, as it does for a vanilla bucket.
     */
    static void player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.LAVA);
        ServerPlayer player = GameTestSupport.serverPlayer(helper, TARGET.above());
        player.setXRot(90.0F);
        ItemStack bucket = GameTestSupport.source();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        int statBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));

        InteractionResult[] result = new InteractionResult[1];
        boolean fired = GameTestSupport.firesFilledBucket(player,
                () -> result[0] = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(result[0].consumesAction(), "Player Source Bucket world pickup failed");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 1000);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == statBefore + 1,
                "Player Source Bucket world pickup did not award exactly one item-use statistic");
        GameTestSupport.check(fired, "Player Source Bucket world pickup did not fire the filled-bucket criterion");
        helper.succeed();
    }

    /**
     * In game: hold a milk Source Bucket, sneak, look at the sky, and right-click; the bucket becomes
     * an empty Source Bucket instead of being drunk.
     */
    static void sneak_use_in_air_clears_milk_source_assignment(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.milk(GameTestSupport.source(), 1000));
        player.setShiftKeyDown(true);
        player.setXRot(-90.0F);

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(!player.isUsingItem(), "Sneak-use in air started drinking Source Bucket milk");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: hold a lava Source Bucket, sneak, look at the sky, and right-click; the bucket becomes
     * an empty Source Bucket.
     */
    static void sneak_use_in_air_clears_source_assignment(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000));
        player.setShiftKeyDown(true);
        player.setXRot(-90.0F);

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Sneak-use in air did not succeed");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        helper.succeed();
    }

    /**
     * In game: hold an empty Source Bucket and right-click a powder-snow block; nothing happens and
     * the bucket stays empty.
     */
    static void source_does_not_support_powder_snow(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.POWDER_SNOW);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(!result.consumesAction(), "Source Bucket use on powder snow consumed the action");
        GameTestSupport.assertEmpty(player.getMainHandItem());
        GameTestSupport.assertBlock(helper, TARGET, Blocks.POWDER_SNOW);
        helper.succeed();
    }

    /**
     * In game: hold a lava Source Bucket and right-click an empty cauldron; it becomes a lava
     * cauldron and the bucket stays assigned to lava. The cauldron's own block use, which has no
     * Source Bucket entry, is not run.
     */
    static void source_fills_empty_cauldron_without_consumption(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.CAULDRON);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000));

        InteractionResult result = GameTestSupport.clickBlock(player, InteractionHand.MAIN_HAND,
                GameTestSupport.hit(helper, TARGET, Direction.UP));

        GameTestSupport.check(result.consumesAction(), "Lava Source Bucket did not fill cauldron");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, 1000);
        helper.succeed();
    }

    /**
     * In game: hold a milk Source Bucket, add Poison to yourself, and right-click the air; you start
     * drinking, and when you finish the Poison is gone and the bucket is still assigned to milk. The
     * drink is completed directly rather than by waiting out the use duration.
     */
    static void source_milk_is_not_consumed_by_drinking(GameTestHelper helper) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 3, 4));
        player.setXRot(-90.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.milk(GameTestSupport.source(), 1000));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));

        GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);
        GameTestSupport.check(player.isUsingItem(), "Using a milk Source Bucket did not start drinking");
        player.getUseItem().finishUsingItem(helper.getLevel(), player);

        GameTestSupport.check(!player.hasEffect(MobEffects.POISON), "Source milk did not remove effect");
        GameTestSupport.assertMilk(player.getMainHandItem(), 1000);
        helper.succeed();
    }

    /**
     * In game: hold a water Source Bucket and right-click the tops of two stone blocks in turn; water
     * appears on both and the bucket stays assigned to water.
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
     * In game: hold an empty Source Bucket and right-click the top of a waterlogged oak fence; the
     * bucket is assigned to water and the fence stays, no longer waterlogged.
     */
    static void waterlogged_block_assigns_source_and_survives(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.OAK_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());

        InteractionResult result = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND);

        GameTestSupport.check(result.consumesAction(), "Source Bucket did not take water from a waterlogged block");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Waterlogged block kept its water after pickup");
        helper.succeed();
    }
}
