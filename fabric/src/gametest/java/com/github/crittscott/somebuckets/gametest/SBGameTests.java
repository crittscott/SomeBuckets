package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.config.FabricServerConfig;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fabric Source Bucket GameTests, including shared scenarios and server-policy persistence. */
public final class SBGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    /** See {@link SBScenarios#empty_source_acquires_world_fluid}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_source_acquires_world_fluid(GameTestHelper helper) {
        SBScenarios.empty_source_acquires_world_fluid(helper);
    }

    /** See {@link SBScenarios#player_source_world_pickup_awards_one_use_and_filled_bucket_criterion}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(GameTestHelper helper) {
        SBScenarios.player_source_world_pickup_awards_one_use_and_filled_bucket_criterion(helper);
    }

    /** See {@link SBScenarios#waterlogged_block_assigns_source_and_survives}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void waterlogged_block_assigns_source_and_survives(GameTestHelper helper) {
        SBScenarios.waterlogged_block_assigns_source_and_survives(helper);
    }

    /** See {@link SBScenarios#assigned_source_refuses_reassignment}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_refuses_reassignment(GameTestHelper helper) {
        SBScenarios.assigned_source_refuses_reassignment(helper);
    }

    /** See {@link SBScenarios#assigned_source_sneak_right_click_takes_matching_world_source}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_sneak_right_click_takes_matching_world_source(GameTestHelper helper) {
        SBScenarios.assigned_source_sneak_right_click_takes_matching_world_source(helper);
    }

    /** See {@link SBScenarios#assigned_source_sneak_right_click_ignores_different_world_fluid}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_sneak_right_click_ignores_different_world_fluid(GameTestHelper helper) {
        SBScenarios.assigned_source_sneak_right_click_ignores_different_world_fluid(helper);
    }

    /** See {@link SBScenarios#assigned_source_normal_right_click_places_without_consumption}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_normal_right_click_places_without_consumption(GameTestHelper helper) {
        SBScenarios.assigned_source_normal_right_click_places_without_consumption(helper);
    }

    /** See {@link SBScenarios#assigned_source_takes_matching_waterlogged_source}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_takes_matching_waterlogged_source(GameTestHelper helper) {
        SBScenarios.assigned_source_takes_matching_waterlogged_source(helper);
    }

    /** See {@link SBScenarios#source_places_repeatedly_without_consumption}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_places_repeatedly_without_consumption(GameTestHelper helper) {
        SBScenarios.source_places_repeatedly_without_consumption(helper);
    }

    /** See {@link SBScenarios#source_serves_cauldron_from_block_use}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_serves_cauldron_from_block_use(GameTestHelper helper) {
        SBScenarios.source_serves_cauldron_from_block_use(helper);
    }

    /** See {@link SBScenarios#empty_source_acquires_full_water_cauldron}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_source_acquires_full_water_cauldron(GameTestHelper helper) {
        SBScenarios.empty_source_acquires_full_water_cauldron(helper);
    }

    /** See {@link SBScenarios#source_fills_empty_cauldron_without_consumption}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_fills_empty_cauldron_without_consumption(GameTestHelper helper) {
        SBScenarios.source_fills_empty_cauldron_without_consumption(helper);
    }

    /** See {@link SBScenarios#adult_cow_assigns_milk_but_baby_does_not}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void adult_cow_assigns_milk_but_baby_does_not(GameTestHelper helper) {
        SBScenarios.adult_cow_assigns_milk_but_baby_does_not(helper);
    }

    /** See {@link SBScenarios#source_milk_is_not_consumed_by_drinking}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_milk_is_not_consumed_by_drinking(GameTestHelper helper) {
        SBScenarios.source_milk_is_not_consumed_by_drinking(helper);
    }

    /** See {@link SBScenarios#normal_use_in_air_preserves_source_assignment}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void normal_use_in_air_preserves_source_assignment(GameTestHelper helper) {
        SBScenarios.normal_use_in_air_preserves_source_assignment(helper);
    }

    /** See {@link SBScenarios#sneak_use_in_air_clears_source_assignment}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void sneak_use_in_air_clears_source_assignment(GameTestHelper helper) {
        SBScenarios.sneak_use_in_air_clears_source_assignment(helper);
    }

    /** See {@link SBScenarios#sneak_use_in_air_clears_source_milk_assignment}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void sneak_use_in_air_clears_source_milk_assignment(GameTestHelper helper) {
        SBScenarios.sneak_use_in_air_clears_source_milk_assignment(helper);
    }

    /** See {@link SBScenarios#normal_use_in_air_on_source_milk_preserves_assignment}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void normal_use_in_air_on_source_milk_preserves_assignment(GameTestHelper helper) {
        SBScenarios.normal_use_in_air_on_source_milk_preserves_assignment(helper);
    }

    /** See {@link SBScenarios#source_does_not_support_powder_snow}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_does_not_support_powder_snow(GameTestHelper helper) {
        SBScenarios.source_does_not_support_powder_snow(helper);
    }

    /**
     * Manual: restrict {@code config/somebuckets-server.json} to water and run {@code /reload}; Source
     * Buckets refuse lava input and output while Big Buckets still handle lava. Repeat with milk and an
     * unknown id to check assignment and reset. Automation: applies each resolved list directly and
     * checks every path in one scenario.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_allow_list_blocks_input_and_output_without_affecting_big_buckets(
            GameTestHelper helper) {
        Runnable restorePolicy = GameTestSupport.sourcePolicyRestorer();
        SBPolicy.refresh(List.of("minecraft:water"), "SBGameTests", false);

        try {
            ItemStack emptySource = GameTestSupport.source();
            helper.setBlock(TARGET, Blocks.LAVA);
            boolean tookLava = FluidTransactions.tryTakeSource(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), emptySource,
                    ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel())));

            GameTestSupport.check(!tookLava, "Disabled lava assigned an empty Source Bucket");
            GameTestSupport.assertEmpty(emptySource);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);

            helper.setBlock(TARGET, Blocks.LAVA_CAULDRON);
            boolean tookLavaCauldron = FluidTransactions.tryTakeSource(
                    helper.getLevel(), GameTestSupport.hit(helper, TARGET, Direction.UP), emptySource,
                    ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel())));

            GameTestSupport.check(!tookLavaCauldron,
                    "Disabled lava assigned an empty Source Bucket from a cauldron");
            GameTestSupport.assertEmpty(emptySource);
            GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA_CAULDRON);

            ItemStack lavaSource = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
            long drained = GameTestSupport.extract(
                    GameTestSupport.fluidStorage(GameTestSupport.containerOf(lavaSource)),
                    FluidVariant.of(Fluids.LAVA), 1000L * GameTestSupport.DROPLETS_PER_MB, true);
            BlockPos placeTarget = TARGET.offset(1, 0, 0);
            boolean placed = FluidTransactions.tryPlaceSource(
                    helper.getLevel(), GameTestSupport.hit(helper, placeTarget, Direction.UP), lavaSource,
                    ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel())), true);
            BlockPos cauldronTarget = TARGET.offset(2, 0, 0);
            helper.setBlock(cauldronTarget, Blocks.CAULDRON);
            boolean filledCauldron = FluidTransactions.tryPlaceSource(
                    helper.getLevel(), GameTestSupport.hit(helper, cauldronTarget, Direction.UP), lavaSource,
                    ProtectionContext.dispenser(BucketOperations.get().automationPlayer(helper.getLevel())), true);

            GameTestSupport.check(drained == 0, "Disabled Source Bucket supplied fluid storage output");
            GameTestSupport.check(!placed, "Disabled Source Bucket placed world fluid");
            GameTestSupport.check(!filledCauldron, "Disabled Source Bucket filled a cauldron");
            GameTestSupport.assertBlock(helper, placeTarget, Blocks.AIR);
            GameTestSupport.assertBlock(helper, cauldronTarget, Blocks.CAULDRON);
            GameTestSupport.check(!helper.getLevel().fuelValues().isFuel(lavaSource),
                    "Disabled lava Source Bucket remained furnace fuel");
            GameTestSupport.check(!SBPolicy.allowsMilk(), "Milk remained allowed after removal");
            GameTestSupport.assertFluid(lavaSource, Fluids.LAVA, 1000);

            ItemStack bigMilk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
            ItemStack sourceMilk = GameTestSupport.milk(GameTestSupport.source(), 1000);
            Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(2, 2, 2));
            player.setItemInHand(InteractionHand.MAIN_HAND, bigMilk);
            player.setItemInHand(InteractionHand.OFF_HAND, sourceMilk);
            boolean sankMilk = HeldTransfers.tryTransfer(
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
            SBPolicy.refresh(reloaded, "SBGameTests", false);

            GameTestSupport.check(SBPolicy.allows(Fluids.LAVA),
                    "Reloaded policy did not allow its registered fluid");
            GameTestSupport.check(!SBPolicy.allows(Fluids.WATER),
                    "Reloaded policy retained a removed fluid");
            GameTestSupport.check(SBPolicy.allowsMilk(),
                    "Reloaded policy did not allow milk alongside an unknown fluid");
            helper.succeed();
        } finally {
            restorePolicy.run();
        }
    }

    /** See {@link SBScenarios#empty_allow_list_disables_all_source_contents}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_allow_list_disables_all_source_contents(GameTestHelper helper) {
        SBScenarios.empty_allow_list_disables_all_source_contents(helper);
    }

    /**
     * Manual: stop the server, add lava, an unknown id, and malformed entries to
     * {@code config/somebuckets-server.json}, restart, and verify only lava is allowed; then stop the
     * server, delete the file, restart, and verify it is recreated with the shipped defaults.
     * Automation: restores the original file and resolved policy after exercising both loads.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void server_config_file_loads_entries_and_recreates_defaults(GameTestHelper helper) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("somebuckets-server.json");
        Runnable restorePolicy = GameTestSupport.sourcePolicyRestorer();
        try {
            byte[] original = Files.exists(path) ? Files.readAllBytes(path) : null;
            try {
                Files.writeString(path, """
                        {"allowedContents": ["minecraft:lava", "missingmod:removed_fluid", "Not A Valid Id", 7]}
                        """);
                FabricServerConfig.load(true);

                GameTestSupport.check(SBPolicy.allows(Fluids.LAVA), "Configured lava was not allowed");
                GameTestSupport.check(!SBPolicy.allows(Fluids.WATER), "Unconfigured water was allowed");
                GameTestSupport.check(!SBPolicy.allowsMilk(), "Unconfigured milk was allowed");

                Files.delete(path);
                FabricServerConfig.load(false);

                GameTestSupport.check(Files.exists(path), "Loading without a config file did not create one");
                JsonArray written = JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                        .getAsJsonArray(SBPolicy.ALLOWED_CONTENTS_KEY);
                List<String> writtenIds = new ArrayList<>();
                written.forEach(element -> writtenIds.add(element.getAsString()));
                GameTestSupport.check(writtenIds.equals(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS),
                        "Recreated config did not hold the shipped defaults: " + writtenIds);
                GameTestSupport.check(SBPolicy.allows(Fluids.WATER) && SBPolicy.allows(Fluids.LAVA)
                                && SBPolicy.allowsMilk(),
                        "Recreated config did not resolve to the shipped policy");
                helper.succeed();
            } finally {
                if (original == null) {
                    Files.deleteIfExists(path);
                } else {
                    Files.write(path, original);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            restorePolicy.run();
        }
    }
}
