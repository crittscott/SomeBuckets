package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.register.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class CauldronGameTests {
    private static final BlockPos CAULDRON = new BlockPos(4, 2, 4);

    private CauldronGameTests() {}

    /**
     * No in-game form: guards that both Big Bucket tiers are registered in the empty, water, lava
     * and powder-snow cauldron interaction maps, so a click on any vanilla cauldron reaches the bucket.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void both_big_bucket_tiers_are_registered(GameTestHelper helper) {
        assertRegistered(ModItems.BIG_BUCKET_8.get());
        assertRegistered(ModItems.BIG_BUCKET_64.get());
        helper.succeed();
    }

    /**
     * In game: right-click a full water cauldron with an empty Big Bucket; the bucket holds one
     * unit of water and the cauldron is empty.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void full_water_cauldron_fills_empty_big_bucket(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, state);

        ItemInteractionResult result = interact(helper, CauldronInteraction.WATER, state, bucket);

        GameTestSupport.check(result.consumesAction(), "Full water cauldron interaction did not succeed");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, FluidType.BUCKET_VOLUME);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: right-click a full water cauldron with a Big Bucket holding one unit of water; the
     * bucket takes the water (two units) instead of pouring, and the cauldron is empty.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_big_bucket_takes_from_full_water_cauldron_first(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(
                GameTestSupport.big8(), Fluids.WATER, FluidType.BUCKET_VOLUME);
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, state);

        ItemInteractionResult result = interact(helper, CauldronInteraction.WATER, state, bucket);

        GameTestSupport.check(result.consumesAction(), "Partial Big Bucket did not take cauldron water");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, FluidType.BUCKET_VOLUME * 2);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: right-click a two-thirds full water cauldron with an empty Big Bucket; nothing
     * happens.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_water_cauldron_is_not_collected(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState state = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
        helper.setBlock(CAULDRON, state);

        ItemInteractionResult result = interact(helper, CauldronInteraction.WATER, state, bucket);

        GameTestSupport.check(!result.consumesAction(), "Partial water cauldron was collected");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(helper.getBlockState(CAULDRON).equals(state), "Rejected interaction changed cauldron");
        helper.succeed();
    }

    /**
     * In game: right-click an empty cauldron with a Big Bucket holding two units of water; the
     * cauldron fills to the top and the bucket keeps one unit.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_fills_empty_water_cauldron_and_consumes_one_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(
                GameTestSupport.big8(), Fluids.WATER, FluidType.BUCKET_VOLUME * 2);
        BlockState state = Blocks.CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, state);

        ItemInteractionResult result = interact(helper, CauldronInteraction.EMPTY, state, bucket);

        GameTestSupport.check(result.consumesAction(), "Big Bucket did not fill empty water cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.check(helper.getBlockState(CAULDRON).getValue(LayeredCauldronBlock.LEVEL)
                        == LayeredCauldronBlock.MAX_FILL_LEVEL,
                "Water cauldron was not filled to level 3");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, FluidType.BUCKET_VOLUME);
        helper.succeed();
    }

    /**
     * In game: right-click a lava cauldron with an empty Big Bucket, then right-click the emptied
     * cauldron; the cauldron is full of lava again and the bucket is an ordinary empty Big Bucket.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void lava_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState full = Blocks.LAVA_CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, full);

        ItemInteractionResult collected = interact(helper, CauldronInteraction.LAVA, full, bucket);
        BlockState empty = helper.getBlockState(CAULDRON);
        ItemInteractionResult placed = interact(helper, CauldronInteraction.EMPTY, empty, bucket);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect lava cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill lava cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: right-click a full powder snow cauldron with an empty Big Bucket, then right-click the
     * emptied cauldron; the cauldron is full of powder snow again and the bucket is an ordinary empty
     * Big Bucket.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_cauldron_round_trip_normalizes_final_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BlockState full = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, full);

        ItemInteractionResult collected = interact(helper, CauldronInteraction.POWDER_SNOW, full, bucket);
        BlockState empty = helper.getBlockState(CAULDRON);
        ItemInteractionResult placed = interact(helper, CauldronInteraction.EMPTY, empty, bucket);

        GameTestSupport.check(collected.consumesAction(), "Big Bucket did not collect powder cauldron");
        GameTestSupport.check(placed.consumesAction(), "Big Bucket did not refill powder cauldron");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.POWDER_SNOW_CAULDRON);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    /**
     * In game: right-click an empty cauldron with a Big Bucket of milk; the cauldron stays empty
     * and the bucket keeps its milk.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void milk_does_not_fill_empty_cauldron(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.milk(GameTestSupport.big8(), FluidType.BUCKET_VOLUME);
        ItemStack before = bucket.copy();
        BlockState state = Blocks.CAULDRON.defaultBlockState();
        helper.setBlock(CAULDRON, state);

        ItemInteractionResult result = interact(helper, CauldronInteraction.EMPTY, state, bucket);

        GameTestSupport.check(!result.consumesAction(), "Milk filled vanilla cauldron");
        GameTestSupport.assertSameStack(before, bucket, "Rejected milk-cauldron interaction mutated bucket");
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.CAULDRON);
        helper.succeed();
    }

    /**
     * In game: take the water from a full water cauldron with an empty Big Bucket and pour it back;
     * as with a vanilla bucket, the Big Bucket use and cauldron use statistics each rise by two and
     * the filled-bucket advancement trigger fires. The test also checks one fluid-pickup and one
     * fluid-place game event at the cauldron.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_big_bucket_cauldron_round_trip_has_vanilla_accounting(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, CAULDRON.north(2));
        ItemStack bucket = GameTestSupport.big8();
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        BlockState full = Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
        helper.setBlock(CAULDRON, full);

        GameTestSupport.GameEventRecorder recorder = new GameTestSupport.GameEventRecorder(helper, CAULDRON);
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()));
        int cauldronUsesBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON));

        ItemInteractionResult[] results = new ItemInteractionResult[2];
        boolean filled;
        recorder.add(helper.getLevel());
        try {
            filled = GameTestSupport.firesFilledBucket(player, () -> {
                results[0] = interact(helper, CauldronInteraction.WATER, full, bucket, player);
                BlockState empty = helper.getBlockState(CAULDRON);
                results[1] = interact(helper, CauldronInteraction.EMPTY, empty, bucket, player);
            });
        } finally {
            recorder.remove(helper.getLevel());
        }
        ItemInteractionResult pickup = results[0];
        ItemInteractionResult placement = results[1];

        GameTestSupport.check(pickup.consumesAction() && placement.consumesAction(),
                "Player Big Bucket cauldron round trip failed");
        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.WATER_CAULDRON);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(bucket.getItem()))
                        == itemUsesBefore + 2,
                "Cauldron round trip did not award exactly two item-use statistics");
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON))
                        == cauldronUsesBefore + 2,
                "Cauldron round trip did not award exactly two cauldron-use statistics");
        GameTestSupport.check(filled, "Cauldron pickup did not fire the filled-bucket criterion");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PICKUP) == 1,
                "Cauldron pickup did not emit exactly one fluid-pickup game event");
        GameTestSupport.check(recorder.count(GameEvent.FLUID_PLACE) == 1,
                "Cauldron placement did not emit exactly one fluid-place game event");
        helper.succeed();
    }

    /**
     * In game: look down at a lava cauldron and right-click it with an empty Source Bucket, then
     * right-click the emptied cauldron; the bucket is a lava Source Bucket, the cauldron is full of
     * lava again, and the Source Bucket use and cauldron use statistics each rose by two.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_source_cauldron_round_trip_assigns_and_remains_infinite(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.serverPlayer(helper, CAULDRON.above());
        player.setXRot(90.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source());
        helper.setBlock(CAULDRON, Blocks.LAVA_CAULDRON);
        Item item = player.getMainHandItem().getItem();
        int itemUsesBefore = player.getStats().getValue(Stats.ITEM_USED.get(item));
        int cauldronUsesBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON));

        boolean pickedUp = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND).consumesAction();
        boolean placed = GameTestSupport.useItem(player, InteractionHand.MAIN_HAND).consumesAction();

        GameTestSupport.check(pickedUp && placed, "Player Source Bucket cauldron round trip failed");
        GameTestSupport.assertFluid(player.getMainHandItem(), Fluids.LAVA, FluidType.BUCKET_VOLUME);
        GameTestSupport.assertBlock(helper, CAULDRON, Blocks.LAVA_CAULDRON);
        GameTestSupport.check(player.getStats().getValue(Stats.ITEM_USED.get(item))
                        == itemUsesBefore + 2,
                "Source cauldron round trip did not award exactly two item-use statistics");
        GameTestSupport.check(player.getStats().getValue(Stats.CUSTOM.get(Stats.USE_CAULDRON))
                        == cauldronUsesBefore + 2,
                "Source cauldron round trip did not award exactly two cauldron-use statistics");
        helper.succeed();
    }

    private static void assertRegistered(Item item) {
        GameTestSupport.check(CauldronInteraction.EMPTY.map().containsKey(item), "Missing empty-cauldron registration for " + item);
        GameTestSupport.check(CauldronInteraction.WATER.map().containsKey(item), "Missing water-cauldron registration for " + item);
        GameTestSupport.check(CauldronInteraction.LAVA.map().containsKey(item), "Missing lava-cauldron registration for " + item);
        GameTestSupport.check(CauldronInteraction.POWDER_SNOW.map().containsKey(item),
                "Missing powder-cauldron registration for " + item);
    }

    private static ItemInteractionResult interact(GameTestHelper helper,
                                                  CauldronInteraction.InteractionMap interactions,
                                                  BlockState state, ItemStack stack) {
        Player player = GameTestSupport.survivalPlayer(helper, new BlockPos(4, 2, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return interact(helper, interactions, state, stack, player);
    }

    private static ItemInteractionResult interact(GameTestHelper helper,
                                                  CauldronInteraction.InteractionMap interactions,
                                                  BlockState state, ItemStack stack, Player player) {
        CauldronInteraction interaction = interactions.map().get(stack.getItem());
        GameTestSupport.check(interaction != null, "No cauldron interaction registered for " + stack.getItem());
        return interaction.interact(state, helper.getLevel(), helper.absolutePos(CAULDRON), player,
                InteractionHand.MAIN_HAND, stack);
    }
}
