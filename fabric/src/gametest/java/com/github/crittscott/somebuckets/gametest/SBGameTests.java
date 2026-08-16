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
        SBScenarios.empty_source_acquires_world_fluid(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(GameTestHelper helper) {
        SBScenarios.player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void waterlogged_block_assigns_source_and_survives(GameTestHelper helper) {
        SBScenarios.waterlogged_block_assigns_source_and_survives(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_refuses_reassignment(GameTestHelper helper) {
        SBScenarios.assigned_source_refuses_reassignment(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_places_repeatedly_without_consumption(GameTestHelper helper) {
        SBScenarios.source_places_repeatedly_without_consumption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_source_acquires_full_water_cauldron(GameTestHelper helper) {
        SBScenarios.empty_source_acquires_full_water_cauldron(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_fills_empty_cauldron_without_consumption(GameTestHelper helper) {
        SBScenarios.source_fills_empty_cauldron_without_consumption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void adult_cow_assigns_milk_but_baby_does_not(GameTestHelper helper) {
        SBScenarios.adult_cow_assigns_milk_but_baby_does_not(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_milk_is_not_consumed_by_drinking(GameTestHelper helper) {
        SBScenarios.source_milk_is_not_consumed_by_drinking(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void shift_use_in_air_clears_source_assignment(GameTestHelper helper) {
        SBScenarios.shift_use_in_air_clears_source_assignment(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_does_not_support_powder_snow(GameTestHelper helper) {
        SBScenarios.source_does_not_support_powder_snow(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_allow_list_blocks_input_and_output_without_affecting_big_buckets(
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


