package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.register.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class CauldronGameTests {
    private static final BlockPos CAULDRON = new BlockPos(4, 2, 4);

    private CauldronGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void both_big_bucket_tiers_are_registered(GameTestHelper helper) {
        assertRegistered(ModItems.BIG_BUCKET_8.get());
        assertRegistered(ModItems.BIG_BUCKET_64.get());
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void full_water_cauldron_fills_empty_big_bucket(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, CauldronInteraction.WATER, state, bucket);

        GameTestSupport.check(result.consumesAction(), "Full water cauldron interaction did not succeed");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_water_cauldron_is_not_collected(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, CauldronInteraction.WATER, state, bucket);

        GameTestSupport.check(!result.consumesAction(), "Partial water cauldron was collected");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Rejected interaction changed cauldron");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        BlockState state = Blocks.CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, CauldronInteraction.EMPTY, state, bucket);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not fill empty water cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(CAULDRON).getValue(LayeredCauldronBlock.LEVEL) == 3,
                "Water cauldron was not filled to level 3");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState full = Blocks.LAVA_CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, full);

        InteractionResult collected = interact(helper, CauldronInteraction.LAVA, full, bucket);
        BlockState empty = helper.getBlockState(CAULDRON);
        InteractionResult placed = interact(helper, CauldronInteraction.EMPTY, empty, bucket);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect lava cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill lava cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState full = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3);
        helper.setBlock(CAULDRON, full);

        InteractionResult collected = interact(helper, CauldronInteraction.POWDER_SNOW, full, bucket);
        BlockState empty = helper.getBlockState(CAULDRON);
        InteractionResult placed = interact(helper, CauldronInteraction.EMPTY, empty, bucket);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect powder cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill powder cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.POWDER_SNOW_CAULDRON);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void milk_does_not_fill_empty_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack before = bucket.copy();
        BlockState state = Blocks.CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, state);

        InteractionResult result = interact(helper, CauldronInteraction.EMPTY, state, bucket);

        GameTestSupport.check(!result.consumesAction(), "Milk filled vanilla cauldron");
        GameTestSupport.assertSameStack(before, bucket, "Rejected milk-cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    private static void assertRegistered(Item item) {
        GameTestSupport.check(CauldronInteraction.EMPTY.containsKey(item), "Missing empty-cauldron registration for " + item);
        GameTestSupport.check(CauldronInteraction.WATER.containsKey(item), "Missing water-cauldron registration for " + item);
        GameTestSupport.check(CauldronInteraction.LAVA.containsKey(item), "Missing lava-cauldron registration for " + item);
        GameTestSupport.check(CauldronInteraction.POWDER_SNOW.containsKey(item),
                "Missing powder-cauldron registration for " + item);
    }

    private static InteractionResult interact(GameTestHelper helper, Map<Item, CauldronInteraction> map,
                                              BlockState state, ItemStack stack) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 2, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        CauldronInteraction interaction = map.get(stack.getItem());
        GameTestSupport.check(interaction != null, "No cauldron interaction registered for " + stack.getItem());
        return interaction.interact(state, helper.getLevel(), helper.absolutePos(CAULDRON), player,
                InteractionHand.MAIN_HAND, stack);
    }
}
