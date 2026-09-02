package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.ServerConfig;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
    public static void sneak_use_in_air_clears_source_milk_assignment(GameTestHelper helper) {
        SBScenarios.sneak_use_in_air_clears_source_milk_assignment(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void normal_use_in_air_on_source_milk_preserves_assignment(GameTestHelper helper) {
        SBScenarios.normal_use_in_air_on_source_milk_preserves_assignment(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_does_not_support_powder_snow(GameTestHelper helper) {
        SBScenarios.source_does_not_support_powder_snow(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_allow_list_blocks_input_output_and_fuel_without_affecting_big_buckets(
            GameTestHelper helper) {
        List<? extends String> original = List.copyOf(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get());
        ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.set(List.of("minecraft:water"));
        SBPolicy.refresh(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), "SBGameTests");

        try {
            ItemStack emptySource = GameTestSupport.source();
            helper.setBlock(TARGET, Blocks.LAVA);
            boolean tookLava = SBFluidLogic.tryTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), emptySource,
                    ProtectionContext.unownedAutomation());

            GameTestSupport.check(!tookLava, "Disabled lava assigned an empty Source Bucket");
            GameTestSupport.assertEmpty(emptySource);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);

            helper.setBlock(TARGET, Blocks.LAVA_CAULDRON);
            boolean tookLavaCauldron = SBFluidLogic.tryTakeWithContext(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), emptySource,
                    ProtectionContext.unownedAutomation());

            GameTestSupport.check(!tookLavaCauldron,
                    "Disabled lava assigned an empty Source Bucket from a cauldron");
            GameTestSupport.assertEmpty(emptySource);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA_CAULDRON);

            ItemStack lavaSource = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
            IFluidHandlerItem sourceHandler = lavaSource.getCapability(Capabilities.FluidHandler.ITEM);
            if (sourceHandler == null) {
                throw new IllegalStateException("Source Bucket exposed no fluid capability");
            }
            FluidStack drained = sourceHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            BlockPos placeTarget = TARGET.offset(1, 0, 0);
            boolean placed = SBFluidLogic.tryPlace(
                    helper.getLevel(), GameTestSupport.hit(helper, placeTarget, Direction.UP), lavaSource,
                    ProtectionContext.unownedAutomation(), true);
            BlockPos cauldronTarget = TARGET.offset(2, 0, 0);
            helper.setBlock(cauldronTarget, Blocks.CAULDRON);
            boolean filledCauldron = SBFluidLogic.tryPlace(
                    helper.getLevel(), GameTestSupport.hit(helper, cauldronTarget, Direction.UP), lavaSource,
                    ProtectionContext.unownedAutomation(), true);

            GameTestSupport.check(drained.isEmpty(), "Disabled Source Bucket supplied fluid capability output");
            GameTestSupport.check(!placed, "Disabled Source Bucket placed world fluid");
            GameTestSupport.check(!filledCauldron, "Disabled Source Bucket filled a cauldron");
            GameTestSupport.assertBlock(helper, placeTarget, Blocks.AIR);
            GameTestSupport.assertBlock(helper, cauldronTarget, Blocks.CAULDRON);
            GameTestSupport.check(lavaSource.getBurnTime(RecipeType.SMELTING) == 0,
                    "Disabled lava Source Bucket remained furnace fuel");
            GameTestSupport.check(!SBPolicy.allowsMilk(), "Milk remained allowed after removal");
            GameTestSupport.assertFluid(lavaSource, Fluids.LAVA, 1000);

            ItemStack bigMilk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
            ItemStack sourceMilk = GameTestSupport.milk(GameTestSupport.source(), 1000);
            Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
            boolean sankMilk = Transfers.tryTransferOne(
                    helper.getLevel(), player,
                    InteractionHand.MAIN_HAND, bigMilk,
                    InteractionHand.OFF_HAND, sourceMilk);

            GameTestSupport.check(!sankMilk, "Disabled milk Source Bucket remained an infinite sink");
            GameTestSupport.assertMilk(bigMilk, 1000);
            GameTestSupport.assertMilk(sourceMilk, 1000);

            ItemStack big = GameTestSupport.big8();
            IFluidHandlerItem bigHandler = big.getCapability(Capabilities.FluidHandler.ITEM);
            if (bigHandler == null) {
                throw new IllegalStateException("Big Bucket exposed no fluid capability");
            }
            int filled = bigHandler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);

            GameTestSupport.check(filled == 1000, "Source allow list restricted a Big Bucket");
            GameTestSupport.check(big.getBurnTime(RecipeType.SMELTING)
                            == FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS,
                    "Source allow list disabled Big Bucket lava fuel");

            ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.set(List.of(
                    "minecraft:lava", "missingmod:removed_fluid", "somebuckets:milk"));
            GameTestSupport.check(SBPolicy.allows(Fluids.WATER),
                    "Policy cache changed before an explicit config refresh");

            SBPolicy.refresh(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), "SBGameTests");

            GameTestSupport.check(SBPolicy.allows(Fluids.LAVA),
                    "Reloaded policy did not allow its registered fluid");
            GameTestSupport.check(!SBPolicy.allows(Fluids.WATER),
                    "Reloaded policy retained a removed fluid");
            GameTestSupport.check(SBPolicy.allowsMilk(),
                    "Reloaded policy did not allow milk alongside an unknown fluid");
            helper.succeed();
        } finally {
            ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.set(original);
            SBPolicy.refresh(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get(), "SBGameTests cleanup");
        }
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void empty_allow_list_disables_all_source_contents(GameTestHelper helper) {
        SBScenarios.empty_allow_list_disables_all_source_contents(helper);
    }
}
