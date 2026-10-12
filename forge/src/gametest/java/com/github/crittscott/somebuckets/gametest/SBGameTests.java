package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.ForgeServerConfig;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.List;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class SBGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private SBGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_source_acquires_world_fluid(GameTestHelper helper) {
        SBScenarios.empty_source_acquires_world_fluid(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(GameTestHelper helper) {
        SBScenarios.player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void waterlogged_block_assigns_source_and_survives(GameTestHelper helper) {
        SBScenarios.waterlogged_block_assigns_source_and_survives(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void assigned_source_refuses_reassignment(GameTestHelper helper) {
        SBScenarios.assigned_source_refuses_reassignment(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void assigned_source_sneak_right_click_takes_matching_world_source(GameTestHelper helper) {
        SBScenarios.assigned_source_sneak_right_click_takes_matching_world_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void assigned_source_sneak_right_click_ignores_different_world_fluid(GameTestHelper helper) {
        SBScenarios.assigned_source_sneak_right_click_ignores_different_world_fluid(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void assigned_source_normal_right_click_places_without_consumption(GameTestHelper helper) {
        SBScenarios.assigned_source_normal_right_click_places_without_consumption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void assigned_source_takes_matching_waterlogged_source(GameTestHelper helper) {
        SBScenarios.assigned_source_takes_matching_waterlogged_source(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_places_repeatedly_without_consumption(GameTestHelper helper) {
        SBScenarios.source_places_repeatedly_without_consumption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_source_acquires_full_water_cauldron(GameTestHelper helper) {
        SBScenarios.empty_source_acquires_full_water_cauldron(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_fills_empty_cauldron_without_consumption(GameTestHelper helper) {
        SBScenarios.source_fills_empty_cauldron_without_consumption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adult_cow_assigns_milk_but_baby_does_not(GameTestHelper helper) {
        SBScenarios.adult_cow_assigns_milk_but_baby_does_not(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_milk_is_not_consumed_by_drinking(GameTestHelper helper) {
        SBScenarios.source_milk_is_not_consumed_by_drinking(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void normal_use_in_air_preserves_source_assignment(GameTestHelper helper) {
        SBScenarios.normal_use_in_air_preserves_source_assignment(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void sneak_use_in_air_clears_source_assignment(GameTestHelper helper) {
        SBScenarios.sneak_use_in_air_clears_source_assignment(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_does_not_support_powder_snow(GameTestHelper helper) {
        SBScenarios.source_does_not_support_powder_snow(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_allow_list_blocks_input_output_and_fuel_without_affecting_big_buckets(
            GameTestHelper helper) {
        List<? extends String> original = List.copyOf(ForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get());
        ForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.set(List.of("minecraft:water"));
        SBPolicy.refresh(ForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), "SBGameTests", true);

        try {
            ItemStack emptySource = GameTestSupport.source();
            helper.setBlock(TARGET, Blocks.LAVA);
            boolean tookLava = FluidTransactions.tryTakeSource(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), emptySource,
                    GameTestSupport.automation(helper));

            GameTestSupport.check(!tookLava, "Disabled lava assigned an empty Source Bucket");
            GameTestSupport.assertEmpty(emptySource);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);

            helper.setBlock(TARGET, Blocks.LAVA_CAULDRON);
            boolean tookLavaCauldron = FluidTransactions.tryTakeSource(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), emptySource,
                    GameTestSupport.automation(helper));

            GameTestSupport.check(!tookLavaCauldron,
                    "Disabled lava assigned an empty Source Bucket from a cauldron");
            GameTestSupport.assertEmpty(emptySource);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA_CAULDRON);

            ItemStack lavaSource = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
            IFluidHandlerItem sourceHandler = lavaSource.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                    .orElseThrow(() -> new IllegalStateException("Source Bucket exposed no fluid capability"));
            FluidStack drained = sourceHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            BlockPos placeTarget = TARGET.offset(1, 0, 0);
            boolean placed = FluidTransactions.tryPlaceSource(
                    helper.getLevel(), GameTestSupport.hit(helper, placeTarget, Direction.UP), lavaSource,
                    GameTestSupport.automation(helper), true);
            BlockPos cauldronTarget = TARGET.offset(2, 0, 0);
            helper.setBlock(cauldronTarget, Blocks.CAULDRON);
            boolean filledCauldron = FluidTransactions.tryPlaceSource(
                    helper.getLevel(), GameTestSupport.hit(helper, cauldronTarget, Direction.UP), lavaSource,
                    GameTestSupport.automation(helper), true);

            GameTestSupport.check(drained.isEmpty(), "Disabled Source Bucket supplied fluid capability output");
            GameTestSupport.check(!placed, "Disabled Source Bucket placed world fluid");
            GameTestSupport.check(!filledCauldron, "Disabled Source Bucket filled a cauldron");
            GameTestSupport.assertBlock(helper, placeTarget, Blocks.AIR);
            GameTestSupport.assertBlock(helper, cauldronTarget, Blocks.CAULDRON);
            GameTestSupport.check(ForgeHooks.getBurnTime(lavaSource, RecipeType.SMELTING) == 0,
                    "Disabled lava Source Bucket remained furnace fuel");
            GameTestSupport.check(!SBPolicy.allowsMilk(), "Milk remained allowed after removal");
            GameTestSupport.assertFluid(lavaSource, Fluids.LAVA, 1000);

            ItemStack bigMilk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
            ItemStack sourceMilk = GameTestSupport.milk(GameTestSupport.source(), 1000);
            Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
            boolean sankMilk = HeldTransfers.tryTransfer(
                    helper.getLevel(), player,
                    InteractionHand.MAIN_HAND, bigMilk,
                    InteractionHand.OFF_HAND, sourceMilk);

            GameTestSupport.check(!sankMilk, "Disabled milk Source Bucket remained an infinite sink");
            GameTestSupport.assertMilk(bigMilk, 1000);
            GameTestSupport.assertMilk(sourceMilk, 1000);

            ItemStack big = GameTestSupport.big8();
            IFluidHandlerItem bigHandler = big.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                    .orElseThrow(() -> new IllegalStateException("Big Bucket exposed no fluid capability"));
            int filled = bigHandler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);

            GameTestSupport.check(filled == 1000, "Source allow list restricted a Big Bucket");
            GameTestSupport.check(ForgeHooks.getBurnTime(big, RecipeType.SMELTING)
                            == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                    "Source allow list disabled Big Bucket lava fuel");

            ForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.set(List.of(
                    "minecraft:lava", "missingmod:removed_fluid", "somebuckets:milk"));
            GameTestSupport.check(SBPolicy.allows(Fluids.WATER),
                    "Policy cache changed before an explicit config refresh");

            SBPolicy.refresh(ForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), "SBGameTests", true);

            GameTestSupport.check(SBPolicy.allows(Fluids.LAVA),
                    "Reloaded policy did not allow its registered fluid");
            GameTestSupport.check(!SBPolicy.allows(Fluids.WATER),
                    "Reloaded policy retained a removed fluid");
            GameTestSupport.check(SBPolicy.allowsMilk(),
                    "Reloaded policy did not allow milk alongside an unknown fluid");
            helper.succeed();
        } finally {
            ForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.set(original);
            SBPolicy.refresh(ForgeServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), "SBGameTests cleanup", true);
        }
    }
}
