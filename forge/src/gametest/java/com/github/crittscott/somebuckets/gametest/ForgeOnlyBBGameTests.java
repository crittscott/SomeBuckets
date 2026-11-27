package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.interaction.BlockFluidTransfers;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.FilledBucketTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;
import java.util.function.Consumer;

@GameTestHolder(SomeBuckets.MODID)
public final class ForgeOnlyBBGameTests {
    private static final BlockPos TARGET = new BlockPos(4, 2, 4);

    private ForgeOnlyBBGameTests() {}

    /**
     * In game: with a mod that cancels Forge's block-place event at a spot, hold a Big Bucket
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
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            result = bucket.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    GameTestSupport.hit(helper, TARGET, Direction.UP)));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }

        GameTestSupport.check(eventCalls[0] == 1, "Powder placement did not post one Forge place event");
        GameTestSupport.check(!result.consumesAction(),
                "Canceled Forge place event reported successful powder placement");
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
}
