package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.fluid.ForgeFluidPlacement;
import com.github.crittscott.somebuckets.interaction.BlockFluidTransfers;
import com.github.crittscott.somebuckets.interaction.BucketSounds;
import com.github.crittscott.somebuckets.protection.ForgeDispenserFakePlayer;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

import javax.annotation.Nullable;
import java.util.Optional;

/** Forge fluid primitives behind the shared bucket interaction flow. */
public final class ForgeBucketOperations implements BucketOperations {
    @Override
    public ServerPlayer automationPlayer(ServerLevel level) {
        return ForgeDispenserFakePlayer.get(level);
    }

    /*
     * Any item exposing the fluid-handler-item capability is a valid partner. Forge's standard
     * FluidBucketWrapper supplies the same contract for BucketItems, including vanilla buckets,
     * which do not expose the capability directly on this Forge branch.
     */
    @Nullable
    @Override
    public HeldMove moveHeldFluid(ItemStack from, ItemStack to, boolean unlimited) {
        IFluidHandlerItem source = heldHandler(from);
        IFluidHandlerItem target = heldHandler(to);
        if (source == null || target == null) return null;
        FluidStack moved = unlimited ? pumpUnlimited(source, target)
                : FluidUtil.tryFluidTransfer(target, source, Integer.MAX_VALUE, true);
        if (moved.isEmpty()) return null;
        return new HeldMove(ForgeFluidStacks.stored(moved), source.getContainer(), target.getContainer());
    }

    @Override
    public boolean holdsFluid(ItemStack stack) {
        IFluidHandlerItem handler = heldHandler(stack);
        return handler != null && !handler.getFluidInTank(0).isEmpty();
    }

    @Override
    public boolean hasBlockStorage(Level level, BlockPos pos, Direction face) {
        return BlockFluidTransfers.hasBlockHandler(level, pos, face);
    }

    @Override
    public boolean carriesItemContainer(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    @Override
    public boolean allowsItemPickup(ItemEntity entity, Player player) {
        return !MinecraftForge.EVENT_BUS.post(new EntityItemPickupEvent(player, entity));
    }

    @Override
    public void afterItemPickup(Player player, ItemEntity entity, ItemStack original, int count) {
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemPickupEvent(player, entity, original.copyWithCount(count)));
    }

    @Override
    public boolean tossFromPlayer(Player player, ItemStack stack) {
        return ForgeHooks.onPlayerTossEvent(player, stack, true) != null;
    }

    @Override
    public boolean permitsBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return !MinecraftForge.EVENT_BUS.post(
                new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player));
    }

    @Override
    public boolean permitsBlockPlace(ServerLevel level, ServerPlayer player, BlockPos pos, Direction face) {
        return !ForgeEventFactory.onBlockPlace(player,
                BlockSnapshot.create(level.dimension(), level, pos), face);
    }

    @Override
    public boolean firesWorldBucketEvent() {
        return true;
    }

    /**
     * Posts {@link FillBucketEvent} directly rather than through {@code ForgeEventFactory.onBucketUse},
     * whose ALLOW handling swaps one held bucket for the listener's filled bucket. A multi-unit or
     * infinite bucket cannot be exchanged that way, so only cancellation is honored.
     */
    @Override
    public InteractionResult beforeWorldBucketUse(Player player, Level level,
                                                  ItemStack stack, BlockHitResult hit) {
        return MinecraftForge.EVENT_BUS.post(new FillBucketEvent(player, stack, level, hit))
                ? InteractionResult.FAIL : null;
    }

    @Override
    public Component fluidDisplayName(StoredFluid fluid) {
        return ForgeFluidStacks.of(fluid).getDisplayName();
    }

    @Override
    public SoundEvent fillSound(StoredFluid fluid) {
        return BucketSounds.resolveFillSound(fluid.fluid());
    }

    @Override
    public Optional<SoundEvent> pickupSound(BucketPickup pickup, BlockState state) {
        return pickup.getPickupSound(state);
    }

    @Override
    public SoundEvent emptySound(StoredFluid fluid) {
        return BucketSounds.resolveEmptySound(fluid.fluid());
    }

    @Override
    public BlockFluidOutcome previewBlockTake(Level level, BlockHitResult hit, ItemStack stack) {
        IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);
        return BlockFluidTransfers.previewTakeFromBlock(
                level, hit.getBlockPos(), hit.getDirection(), handler);
    }

    @Override
    public BlockFluidOutcome blockTake(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, boolean asSource) {
        IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);
        return BlockFluidTransfers.tryTakeFromBlock(
                level, hit.getBlockPos(), hit.getDirection(), stack, handler, context);
    }

    @Override
    public BlockFluidOutcome blockPlace(Level level, BlockHitResult hit, ItemStack stack,
                                        ProtectionContext context, boolean asSource) {
        IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);
        return BlockFluidTransfers.tryPlaceIntoBlock(
                level, hit.getBlockPos(), hit.getDirection(), stack, handler, context);
    }

    @Nullable
    @Override
    public SourceTarget classifyBlockTarget(Level level, BlockHitResult hit, ItemStack stack) {
        if (!BlockFluidTransfers.hasBlockHandler(level, hit.getBlockPos(), hit.getDirection())) return null;
        IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);
        return BlockFluidTransfers.classifySourceTarget(
                level, hit.getBlockPos(), hit.getDirection(), handler);
    }

    @Override
    public boolean placeArbitraryFluid(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, StoredFluid stored, boolean asSource,
                                       boolean allowFaceOffset) {
        IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);
        return ForgeFluidPlacement.place(level, hit, stack, handler, context,
                ForgeFluidStacks.of(stored), allowFaceOffset);
    }

    @Override
    public BlockPos resolveArbitraryPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                Player player, InteractionHand hand,
                                                StoredFluid stored, boolean allowFaceOffset) {
        return ForgeFluidPlacement.resolveTarget(level, hit, stack, player, hand,
                ForgeFluidStacks.of(stored), allowFaceOffset);
    }

    @Override
    public InteractionResult placePowderBlock(BlockItem item, BlockPlaceContext placement) {
        return item.place(placement);
    }

    @Nullable
    private static IFluidHandlerItem heldHandler(ItemStack stack) {
        IFluidHandlerItem capability = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (capability != null) return capability;
        return stack.getItem() instanceof BucketItem ? new FluidBucketWrapper(stack) : null;
    }

    /*
     * Fills the destination to its real capacity in one simulate/execute round from an infinite
     * source. The source's own capability stays at one bucket volume per call for machines; a 1 mB
     * probe still routes through its allowlist check.
     */
    private static FluidStack pumpUnlimited(IFluidHandlerItem source, IFluidHandlerItem destination) {
        FluidStack probe = source.drain(1, IFluidHandler.FluidAction.SIMULATE);
        if (probe.isEmpty()) return FluidStack.EMPTY;

        long budget = 0;
        for (int tank = 0; tank < destination.getTanks(); tank++) {
            budget += destination.getTankCapacity(tank);
        }
        if (budget <= 0) return FluidStack.EMPTY;

        int room = destination.fill(ForgeFluidStacks.resized(probe, (int) Math.min(budget, Integer.MAX_VALUE)),
                IFluidHandler.FluidAction.SIMULATE);
        if (room <= 0) return FluidStack.EMPTY;
        int filled = destination.fill(ForgeFluidStacks.resized(probe, room), IFluidHandler.FluidAction.EXECUTE);
        return filled <= 0 ? FluidStack.EMPTY : ForgeFluidStacks.resized(probe, filled);
    }
}
