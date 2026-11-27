package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.interaction.BlockFluidTransfers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Consumer;

/**
 * NeoForge-only Big Bucket coverage: powder-snow block output posts NeoForge's
 * {@link BlockEvent.EntityPlaceEvent} and honors its cancellation atomically, and the item fluid
 * capability fills only a single bucket, and drinking milk keeps effects that do not list the milk
 * cure. NeoForge has no
 * fill-bucket event, so the Forge {@code FillBucketEventGameTests} suite has no counterpart here.
 */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class NeoForgeOnlyBBGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private NeoForgeOnlyBBGameTests() {}

    /**
     * In game: with a mod that cancels NeoForge's block-place event at a spot, hold a Big Bucket
     * with one unit of powder snow and place it there; no powder snow appears and the bucket keeps its
     * unit. The test drives the bucket's {@code useOn} at the clicked block directly.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void powder_snow_place_event_cancellation_is_atomic(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 1);
        ItemStack before = bucket.copy();
        Player player = GameTestSupport.survivalPlayer(helper, TARGET.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        int[] eventCalls = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getPos().equals(helper.absolutePos(TARGET))) {
                eventCalls[0]++;
                event.setCanceled(true);
            }
        };
        InteractionResult result;
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        try {
            result = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET, Direction.UP)));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(eventCalls[0] == 1, "Powder placement did not post one NeoForge place event");
        GameTestSupport.check(!result.consumesAction(),
                "Canceled NeoForge place event reported successful powder placement");
        GameTestSupport.assertBlock(helper, TARGET, Blocks.AIR);
        GameTestSupport.assertSameStack(before, player.getItemInHand(InteractionHand.MAIN_HAND),
                "Canceled powder placement debited the Big Bucket");
        helper.succeed();
    }

    /**
     * No in-game form without a machine mod; guards a slot-filling machine from turning one bucket
     * of fluid into a stack of filled Big or Source Buckets through the item fluid capability.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void item_fluid_handler_fills_only_a_single_bucket(GameTestHelper helper) {
        for (ItemStack empty : List.of(GameTestSupport.big8(), GameTestSupport.source())) {
            ItemStack stack = empty.copyWithCount(16);
            int filled = BlockFluidTransfers.requireBucketHandler(stack).fill(
                    new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME), IFluidHandler.FluidAction.EXECUTE);

            GameTestSupport.check(filled == 0, "A stack of 16 " + stack.getItem() + " accepted " + filled + " mB");
            GameTestSupport.check(stack.getCount() == 16, "Refused fill changed the stack count");
            GameTestSupport.assertEmpty(stack);
        }

        ItemStack single = GameTestSupport.big8();
        int filled = BlockFluidTransfers.requireBucketHandler(single).fill(
                new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME), IFluidHandler.FluidAction.EXECUTE);
        GameTestSupport.check(filled == FluidType.BUCKET_VOLUME, "A single Big Bucket did not fill");
        GameTestSupport.assertFluid(single, Fluids.WATER, FluidType.BUCKET_VOLUME);
        helper.succeed();
    }

    /**
     * In game: with a mod effect that NeoForge says milk cannot cure active, plus Speed, drink from a
     * milk Big Bucket, then from a milk Source Bucket: the mod effect remains while Speed is removed.
     * The drink is completed directly rather than by holding right-click.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void milk_drink_keeps_effects_neoforge_says_milk_cannot_cure(GameTestHelper helper) {
        for (ItemStack bucket : List.of(GameTestSupport.milk(GameTestSupport.big8(), 1000),
                GameTestSupport.milk(GameTestSupport.source(), 1000))) {
            Player player = GameTestSupport.connectedServerPlayer(helper, TARGET);
            MobEffectInstance immune = new MobEffectInstance(MobEffects.DIG_SPEED, 600);
            immune.getCures().clear();
            player.addEffect(immune);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));

            bucket.getItem().finishUsingItem(bucket, helper.getLevel(), player);

            GameTestSupport.check(player.hasEffect(MobEffects.DIG_SPEED),
                    bucket.getItem() + " milk removed an effect milk cannot cure");
            GameTestSupport.check(!player.hasEffect(MobEffects.MOVEMENT_SPEED),
                    bucket.getItem() + " milk did not remove an ordinary effect");
        }
        helper.succeed();
    }
}
