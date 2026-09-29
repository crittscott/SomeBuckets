package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.fluid.NeoForgeFluidPlacement;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.NeoForgeBucketSounds;
import com.github.crittscott.somebuckets.protection.NeoForgeDispenserFakePlayer;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** NeoForge fluid primitives behind the shared bucket interaction flow. */
public final class NeoForgeBucketOperations implements BucketOperations {
    @Override
    public ServerPlayer automationPlayer(ServerLevel level) {
        return NeoForgeDispenserFakePlayer.get(level);
    }

    /*
     * Any item exposing the fluid-handler-item capability is a valid partner. NeoForge registers
     * that capability for vanilla buckets; unregistered BucketItem subclasses remain unsupported.
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
        return new HeldMove(NeoForgeFluidStacks.stored(moved), source.getContainer(), target.getContainer());
    }

    @Override
    public boolean holdsFluid(ItemStack stack) {
        IFluidHandlerItem handler = heldHandler(stack);
        return handler != null && !handler.getFluidInTank(0).isEmpty();
    }

    /*
     * NeoForge exposes vanilla cauldrons as fluid handlers, but Cauldrons owns them on every loader
     * so they award the cauldron statistics and emit the cauldron game events. Modded cauldron
     * blocks keep their capability.
     */
    @Nullable
    @Override
    public BlockFluidStore blockFluidStore(Level level, BlockPos pos, Direction face) {
        if (Cauldrons.isVanillaCauldron(level.getBlockState(pos))) return null;
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        return handler == null ? null : new HandlerStore(handler);
    }

    @Override
    public boolean carriesItemContainer(ItemStack stack) {
        return stack.getCapability(Capabilities.ItemHandler.ITEM) != null;
    }

    @Override
    public boolean allowsItemPickup(ItemEntity entity, Player player) {
        return !NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(player, entity)).canPickup().isFalse();
    }

    @Override
    public void afterItemPickup(Player player, ItemEntity entity, ItemStack original, int count) {
        NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Post(player, entity, original));
    }

    @Override
    public boolean tossFromPlayer(Player player, ItemStack stack) {
        return CommonHooks.onPlayerTossEvent(player, stack, true) != null;
    }

    @Override
    public boolean permitsBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return !NeoForge.EVENT_BUS.post(
                new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled();
    }

    @Override
    public boolean permitsBlockPlace(ServerLevel level, ServerPlayer player, BlockPos pos, Direction face) {
        return !EventHooks.onBlockPlace(player, BlockSnapshot.create(level.dimension(), level, pos), face);
    }

    @Override
    public InteractionResult beforeWorldBucketUse(Player player, Level level,
                                                  ItemStack stack, Supplier<BlockHitResult> hit) {
        // NeoForge exposes no pre-dispatch bucket-use event (Forge's FillBucketEvent has no successor
        // here); nothing claims the interaction ahead of common processing, as on Fabric.
        return null;
    }

    @Override
    public boolean releasedFluidVariantIsRawTag() {
        return false;
    }

    @Override
    public Component fluidDisplayName(StoredFluid fluid) {
        return NeoForgeFluidStacks.of(fluid).getHoverName();
    }

    @Override
    public SoundEvent fillSound(StoredFluid fluid) {
        return NeoForgeBucketSounds.resolveFillSound(fluid.fluid());
    }

    @Override
    public Optional<SoundEvent> pickupSound(BucketPickup pickup, BlockState state) {
        return pickup.getPickupSound(state);
    }

    @Override
    public SoundEvent emptySound(StoredFluid fluid) {
        return NeoForgeBucketSounds.resolveEmptySound(fluid.fluid());
    }

    @Override
    public boolean placeArbitraryFluid(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, StoredFluid stored,
                                       boolean allowFaceOffset) {
        IFluidHandlerItem handler = requireBucketHandler(stack);
        return NeoForgeFluidPlacement.place(level, hit, stack, handler, context,
                NeoForgeFluidStacks.of(stored), allowFaceOffset);
    }

    @Override
    public BlockPos resolveArbitraryPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                Player player, InteractionHand hand,
                                                StoredFluid stored, boolean allowFaceOffset) {
        return NeoForgeFluidPlacement.resolveTarget(level, hit, stack, player, hand,
                NeoForgeFluidStacks.of(stored), allowFaceOffset);
    }

    /**
     * On the player-use path NeoForge arms block-snapshot capture around {@code useOn} and normally
     * fires {@code EntityPlaceEvent} only after it returns, too late to prevent the powder debit.
     * The event is therefore posted explicitly before placement for a real player, then capture is
     * suspended so the outer hook neither defers nor duplicates it. Automation posts no player event.
     */
    @Override
    public InteractionResult placePowderBlock(BlockItem item, BlockPlaceContext placement,
                                              ProtectionContext context) {
        Level level = placement.getLevel();
        if (!level.isClientSide && context.player() instanceof ServerPlayer player
                && !permitsBlockPlace((ServerLevel) level, player, placement.getClickedPos(),
                placement.getClickedFace())) {
            return InteractionResult.FAIL;
        }
        boolean capturing = level.captureBlockSnapshots;
        level.captureBlockSnapshots = false;
        try {
            return item.place(placement);
        } finally {
            level.captureBlockSnapshots = capturing;
        }
    }

    /* The bucket's own fluid handler, an invariant of every Big, Huge, and Source Bucket. */
    private static IFluidHandlerItem requireBucketHandler(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) throw new IllegalStateException("Some Buckets item is missing its fluid capability");
        return handler;
    }

    /* A sided block fluid handler in common terms. */
    private record HandlerStore(IFluidHandler handler) implements BlockFluidStore {
        @Override
        public List<StoredFluid> offered() {
            FluidStack first = handler.drain(FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
            return first.isEmpty() ? List.of() : List.of(NeoForgeFluidStacks.stored(first));
        }

        @Override
        public StoredFluid drain(StoredFluid request, boolean simulate) {
            return NeoForgeFluidStacks.stored(handler.drain(NeoForgeFluidStacks.of(request), action(simulate)));
        }

        @Override
        public int fill(StoredFluid offered, boolean simulate) {
            return handler.fill(NeoForgeFluidStacks.of(offered), action(simulate));
        }

        private static IFluidHandler.FluidAction action(boolean simulate) {
            return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        }
    }

    @Nullable
    private static IFluidHandlerItem heldHandler(ItemStack stack) {
        return stack.getCapability(Capabilities.FluidHandler.ITEM);
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

        int room = destination.fill(probe.copyWithAmount((int) Math.min(budget, Integer.MAX_VALUE)),
                IFluidHandler.FluidAction.SIMULATE);
        if (room <= 0) return FluidStack.EMPTY;
        int filled = destination.fill(probe.copyWithAmount(room), IFluidHandler.FluidAction.EXECUTE);
        return filled <= 0 ? FluidStack.EMPTY : probe.copyWithAmount(filled);
    }
}
