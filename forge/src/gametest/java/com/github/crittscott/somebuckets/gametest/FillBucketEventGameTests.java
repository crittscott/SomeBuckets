package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Coverage for the player world-use {@code FillBucketEvent} contract: fires exactly once per
 * supported world/cauldron interaction, at the position that will actually be mutated, with
 * cancellation and {@code ALLOW} handled honestly. Capability transactions and native powder-block
 * output do not post it. See {@code ForgeBucketOperations.beforeWorldBucketUse}.
 */
@GameTestHolder(SomeBuckets.MODID)
public final class FillBucketEventGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private FillBucketEventGameTests() {}

    /** Registers {@code listener}, runs {@code action}, and always unregisters it afterward. */
    private static void withFillBucketListener(Consumer<FillBucketEvent> listener, Runnable action) {
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            action.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }

    private static Consumer<FillBucketEvent> cancelling() {
        return new Consumer<FillBucketEvent>() {
            @Override public void accept(FillBucketEvent event) { event.setCanceled(true); }
        };
    }

    private static Consumer<FillBucketEvent> allowing(ItemStack result) {
        return new Consumer<FillBucketEvent>() {
            @Override public void accept(FillBucketEvent event) {
                event.setFilledBucket(result);
                event.setResult(Event.Result.ALLOW);
            }
        };
    }

    private static Consumer<FillBucketEvent> denying() {
        return new Consumer<FillBucketEvent>() {
            @Override public void accept(FillBucketEvent event) { event.setResult(Event.Result.DENY); }
        };
    }

    private static Consumer<FillBucketEvent> capturing(List<BlockPos> captured) {
        return new Consumer<FillBucketEvent>() {
            @Override public void accept(FillBucketEvent event) {
                if (event.getTarget() instanceof BlockHitResult bhr) captured.add(bhr.getBlockPos());
            }
        };
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void cancelled_event_prevents_big_bucket_take(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BBItem item = (BBItem) bucket.getItem();
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        withFillBucketListener(cancelling(), () ->
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND));

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.WATER);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void cancelled_event_prevents_source_bucket_place(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        SBItem item = (SBItem) bucket.getItem();
        helper.setBlock(TARGET, Blocks.OAK_FENCE);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        withFillBucketListener(cancelling(), () ->
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND));

        GameTestSupport.assertSameStack(before, bucket, "Cancelled placement mutated the Source Bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(TARGET).getFluidState().isEmpty(),
                "Cancelled placement waterlogged the fence anyway");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void compatible_allow_result_handles_unsupported_pickup(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BBItem item = (BBItem) bucket.getItem();
        ItemStack supplied = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        helper.setBlock(TARGET, Blocks.STONE);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        List<InteractionResultHolder<ItemStack>> results = new ArrayList<>();

        withFillBucketListener(allowing(supplied), () -> results.add(
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND)));

        GameTestSupport.check(results.size() == 1, "Bucket use did not return its handled result");
        GameTestSupport.check(results.get(0).getResult() == InteractionResult.SUCCESS,
                "Compatible ALLOW did not report success");
        GameTestSupport.assertSameStack(supplied, results.get(0).getObject(),
                "Compatible ALLOW did not return the listener-supplied bucket");
        GameTestSupport.check(bucket.isEmpty(), "Consumed Big Bucket stack was not exhausted");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void different_allow_result_uses_forge_settlement(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BBItem item = (BBItem) bucket.getItem();
        helper.setBlock(TARGET, Blocks.STONE);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        List<InteractionResultHolder<ItemStack>> results = new ArrayList<>();

        withFillBucketListener(allowing(new ItemStack(Items.WATER_BUCKET)), () -> results.add(
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND)));

        GameTestSupport.check(results.size() == 1, "Bucket use did not return its handled result");
        GameTestSupport.check(results.get(0).getResult() == InteractionResult.SUCCESS,
                "Different ALLOW result did not succeed");
        GameTestSupport.check(results.get(0).getObject().is(Items.WATER_BUCKET),
                "Different ALLOW result was not returned as the held stack");
        GameTestSupport.check(bucket.isEmpty(), "Consumed Big Bucket stack was not exhausted");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void allow_result_consumes_one_stacked_empty_bucket(GameTestHelper helper) {
        ItemStack buckets = GameTestSupport.big8();
        buckets.setCount(3);
        BBItem item = (BBItem) buckets.getItem();
        helper.setBlock(TARGET, Blocks.STONE);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, buckets);
        List<InteractionResultHolder<ItemStack>> results = new ArrayList<>();

        withFillBucketListener(allowing(new ItemStack(Items.WATER_BUCKET)), () -> results.add(
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND)));

        GameTestSupport.check(results.size() == 1, "Bucket use did not return its handled result");
        GameTestSupport.check(results.get(0).getResult() == InteractionResult.SUCCESS,
                "Stacked ALLOW result did not succeed");
        GameTestSupport.check(results.get(0).getObject() == buckets && buckets.getCount() == 2,
                "Forge settlement did not retain the two unconsumed empty buckets");
        int waterBuckets = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack inventoryStack = player.getInventory().getItem(slot);
            if (inventoryStack.is(Items.WATER_BUCKET)) waterBuckets += inventoryStack.getCount();
        }
        GameTestSupport.check(waterBuckets == 1,
                "Forge settlement did not add exactly one listener-supplied water bucket");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void non_canceling_deny_uses_normal_bucket_dispatch(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        BBItem item = (BBItem) bucket.getItem();
        helper.setBlock(TARGET, Blocks.WATER);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        withFillBucketListener(denying(), () ->
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND));

        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void partial_bucket_posts_event_at_place_target_when_take_impossible(GameTestHelper helper) {
        BlockPos fallback = TARGET.below();
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 4000);
        BBItem item = (BBItem) bucket.getItem();
        helper.setBlock(TARGET, Blocks.LAVA);
        helper.setBlock(fallback, Blocks.OAK_FENCE);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        List<BlockPos> captured = new ArrayList<>();

        withFillBucketListener(capturing(captured), () ->
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(captured.size() == 1, "Expected exactly one FillBucketEvent, got " + captured.size());
        GameTestSupport.check(captured.get(0).equals(helper.absolutePos(fallback)),
                "Event posted at " + captured.get(0) + " instead of the actual place target " + fallback);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.LAVA);
        GameTestSupport.assertBlock(helper, fallback, Blocks.OAK_FENCE);
        GameTestSupport.check(helper.getBlockState(fallback).getFluidState().isSource(),
                "Fence below the unreachable lava was not waterlogged by the fallback placement");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 3000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void full_bucket_placement_posts_event_at_fallthrough_neighbor(GameTestHelper helper) {
        BlockPos neighbor = TARGET.north();
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000);
        BBItem item = (BBItem) bucket.getItem();
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(neighbor, Blocks.AIR);
        helper.setBlock(TARGET.north(2), Blocks.AIR);
        Player player = GameTestSupport.survivalPlayerLookingAt(
                helper, TARGET.north(3), TARGET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        List<BlockPos> captured = new ArrayList<>();

        withFillBucketListener(capturing(captured), () ->
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(captured.size() == 1, "Expected exactly one FillBucketEvent, got " + captured.size());
        GameTestSupport.check(captured.get(0).equals(helper.absolutePos(neighbor)),
                "Event posted at " + captured.get(0) + " instead of the fall-through neighbor " + neighbor);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, neighbor, Blocks.WATER);
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 7000);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_output_uses_block_place_event_instead_of_fill_bucket_event(
            GameTestHelper helper) {
        BlockPos placeTarget = TARGET.above();
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        helper.setBlock(TARGET, Blocks.STONE);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        List<BlockPos> captured = new ArrayList<>();

        withFillBucketListener(capturing(captured), () ->
                bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        GameTestSupport.hit(helper, TARGET, Direction.UP))));

        GameTestSupport.check(captured.isEmpty(),
                "Powder-snow block output posted FillBucketEvent instead of using block placement hooks");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.STONE);
        GameTestSupport.assertBlock(helper, placeTarget, Blocks.POWDER_SNOW);
        GameTestSupport.assertEmpty(bucket);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_bucket_block_interaction_posts_event_through_use(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.source();
        SBItem item = (SBItem) bucket.getItem();
        helper.setBlock(TARGET, Blocks.LAVA);
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        List<BlockPos> captured = new ArrayList<>();

        withFillBucketListener(capturing(captured), () ->
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(captured.size() == 1,
                "Source Bucket block interaction did not post FillBucketEvent through use()");
        GameTestSupport.check(captured.get(0).equals(helper.absolutePos(TARGET)),
                "Event posted at " + captured.get(0) + " instead of " + TARGET);
        GameTestSupport.assertFluid(bucket, Fluids.LAVA, 1000);
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void capability_drain_does_not_post_fill_bucket_event(GameTestHelper helper) {
        GameTestSupport.SidedFluidBlockEntity tank = GameTestSupport.fluidTank(helper, TARGET,
                Direction.UP, 4000, new FluidStack(Fluids.WATER, 2000));
        ItemStack bucket = GameTestSupport.big8();
        BBItem item = (BBItem) bucket.getItem();
        Player player = GameTestSupport.survivalPlayerLookingDown(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        List<BlockPos> captured = new ArrayList<>();

        withFillBucketListener(capturing(captured), () ->
                item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND));

        GameTestSupport.check(captured.isEmpty(),
                "Capability-mediated drain posted FillBucketEvent");
        GameTestSupport.assertFluid(bucket, Fluids.WATER, 1000);
        GameTestSupport.check(tank.contents().getAmount() == 1000,
                "Capability drain did not remove exactly one bucket volume");
        helper.succeed();
    }
}
