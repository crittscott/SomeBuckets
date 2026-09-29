package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.fluid.ForgeFluidPlacement;
import com.github.crittscott.somebuckets.protection.ForgeAutomationPlayer;
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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Forge fluid primitives behind the shared bucket interaction flow. */
public final class ForgeBucketOperations implements BucketOperations {
    @Override
    public ServerPlayer automationPlayer(ServerLevel level) {
        return ForgeAutomationPlayer.get(level);
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

    /* Present only after a mod calls ForgeMod.enableMilkFluid(). */
    @Nullable
    @Override
    public Fluid milkFluid() {
        return ForgeMod.MILK.isPresent() ? ForgeMod.MILK.get() : null;
    }

    @Nullable
    @Override
    public BlockFluidStore blockFluidStore(Level level, BlockPos pos, Direction face) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        IFluidHandler handler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
        return handler == null ? null : new HandlerStore(handler);
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

    /**
     * Records a real player's placement with Forge's block-snapshot capture and posts
     * {@code EntityPlaceEvent} for it, as {@code ForgeHooks.onPlaceItemIntoWorld} does around
     * {@code useOn}: a refusal restores the recorded blocks, and an accepted placement then runs the
     * block placement and neighbor updates that capture deferred. Capture the loader armed around an
     * enclosing {@code useOn} is suspended with its snapshots set aside, so that hook sees none of
     * this placement.
     */
    @Override
    public boolean placeChecked(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                BooleanSupplier place) {
        if (level.isClientSide || !(context.player() instanceof ServerPlayer player)) return place.getAsBoolean();
        boolean outerCapture = level.captureBlockSnapshots;
        List<BlockSnapshot> outerSnapshots = new ArrayList<>(level.capturedBlockSnapshots);
        level.capturedBlockSnapshots.clear();
        try {
            boolean placed;
            level.captureBlockSnapshots = true;
            try {
                placed = place.getAsBoolean();
            } finally {
                level.captureBlockSnapshots = false;
            }
            List<BlockSnapshot> snapshots = firstPerPosition(level.capturedBlockSnapshots);
            level.capturedBlockSnapshots.clear();

            boolean refused = !placed || (snapshots.size() > 1
                    ? ForgeEventFactory.onMultiBlockPlace(player, snapshots, face)
                    : snapshots.size() == 1 && ForgeEventFactory.onBlockPlace(player, snapshots.getFirst(), face));
            if (refused) {
                for (BlockSnapshot snapshot : snapshots.reversed()) {
                    level.restoringBlockSnapshots = true;
                    snapshot.restore(true, false);
                    level.restoringBlockSnapshots = false;
                }
                return false;
            }
            for (BlockSnapshot snapshot : snapshots) {
                BlockPos at = snapshot.getPos();
                BlockState replaced = snapshot.getReplacedBlock();
                BlockState placedState = level.getBlockState(at);
                placedState.onPlace(level, at, replaced, false);
                level.markAndNotifyBlock(at, level.getChunkAt(at), replaced, placedState,
                        snapshot.getFlag(), Block.UPDATE_LIMIT);
            }
            return true;
        } finally {
            level.capturedBlockSnapshots.clear();
            level.capturedBlockSnapshots.addAll(outerSnapshots);
            level.captureBlockSnapshots = outerCapture;
        }
    }

    /* Each position's earliest snapshot, which holds the state from before the whole placement. */
    private static List<BlockSnapshot> firstPerPosition(List<BlockSnapshot> captured) {
        Map<BlockPos, BlockSnapshot> first = new LinkedHashMap<>();
        for (BlockSnapshot snapshot : captured) first.putIfAbsent(snapshot.getPos(), snapshot);
        return new ArrayList<>(first.values());
    }

    /**
     * Posts {@link FillBucketEvent} directly rather than through {@code ForgeEventFactory.onBucketUse},
     * whose ALLOW handling swaps one held bucket for the listener's filled bucket. A multi-unit or
     * infinite bucket cannot be exchanged that way, so only cancellation is honored.
     */
    @Override
    public InteractionResult beforeWorldBucketUse(Player player, Level level,
                                                  ItemStack stack, Supplier<BlockHitResult> hitSupplier) {
        BlockHitResult hit = hitSupplier.get();
        if (hit == null) return null;
        return MinecraftForge.EVENT_BUS.post(new FillBucketEvent(player, stack, level, hit))
                ? InteractionResult.FAIL : null;
    }

    @Override
    public boolean releasedFluidVariantIsRawTag() {
        return true;
    }

    @Override
    public Component fluidDisplayName(StoredFluid fluid) {
        return ForgeFluidStacks.of(fluid).getDisplayName();
    }

    @Override
    public Optional<SoundEvent> fillSound(StoredFluid fluid) {
        return Optional.ofNullable(fluid.fluid().getFluidType().getSound(SoundActions.BUCKET_FILL));
    }

    @Override
    public Optional<SoundEvent> pickupSound(BucketPickup pickup, BlockState state) {
        return pickup.getPickupSound(state);
    }

    @Override
    public Optional<SoundEvent> emptySound(StoredFluid fluid) {
        return Optional.ofNullable(fluid.fluid().getFluidType().getSound(SoundActions.BUCKET_EMPTY));
    }

    @Override
    public boolean placeArbitraryFluid(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, StoredFluid stored,
                                       boolean allowFaceOffset) {
        IFluidHandlerItem handler = requireBucketHandler(stack);
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

    /* The bucket's own fluid handler, an invariant of every Big, Huge, and Source Bucket. */
    private static IFluidHandlerItem requireBucketHandler(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(
                () -> new IllegalStateException("Some Buckets item is missing its fluid capability"));
    }

    /* A sided block fluid handler in common terms. */
    private record HandlerStore(IFluidHandler handler) implements BlockFluidStore {
        @Override
        public List<StoredFluid> offered() {
            FluidStack first = handler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
            return first.isEmpty() ? List.of() : List.of(ForgeFluidStacks.stored(first));
        }

        @Override
        public StoredFluid drain(StoredFluid request, boolean simulate) {
            return ForgeFluidStacks.stored(handler.drain(ForgeFluidStacks.of(request), action(simulate)));
        }

        @Override
        public int fill(StoredFluid offered, boolean simulate) {
            return handler.fill(ForgeFluidStacks.of(offered), action(simulate));
        }

        private static IFluidHandler.FluidAction action(boolean simulate) {
            return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        }
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
