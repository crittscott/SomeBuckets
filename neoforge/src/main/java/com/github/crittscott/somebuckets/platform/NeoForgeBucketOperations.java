package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.fluid.NeoForgeFluidPlacement;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
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
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** NeoForge fluid primitives behind the shared bucket interaction flow. */
public final class NeoForgeBucketOperations implements BucketOperations {
    @Override
    public ServerPlayer automationPlayer(ServerLevel level) {
        return NeoForgeDispenserFakePlayer.get(level);
    }

    @Nullable
    @Override
    public HeldMove moveHeldFluid(ItemStack from, ItemStack to, boolean unlimited) {
        SimpleContainer fromContainer = new SimpleContainer(from);
        SimpleContainer toContainer = new SimpleContainer(to);
        ItemAccess fromAccess = heldAccess(from, fromContainer);
        ItemAccess toAccess = heldAccess(to, toContainer);
        ResourceHandler<FluidResource> source = fromAccess.getCapability(Capabilities.Fluid.ITEM);
        ResourceHandler<FluidResource> target = toAccess.getCapability(Capabilities.Fluid.ITEM);
        if (source == null || target == null) return null;
        for (int index = 0; index < source.size(); index++) {
            FluidResource resource = source.getResource(index);
            if (resource.isEmpty()) continue;
            int limit;
            try (Transaction preview = Transaction.open(null)) {
                limit = source.extract(resource, unlimited ? 1 : Integer.MAX_VALUE, preview);
            }
            if (limit == 0) continue;
            if (unlimited) limit = Integer.MAX_VALUE;
            int accepted;
            try (Transaction preview = Transaction.open(null)) {
                accepted = target.insert(resource, limit, preview);
            }
            if (accepted == 0) continue;
            try (Transaction transaction = Transaction.open(null)) {
                int extracted = unlimited ? accepted : source.extract(resource, accepted, transaction);
                if (extracted == 0 || target.insert(resource, extracted, transaction) != extracted) continue;
                transaction.commit();
                ItemStack fromResult = from.getItem() instanceof FluidBucketItem ? from : fromContainer.getItem(0);
                ItemStack toResult = to.getItem() instanceof FluidBucketItem ? to : toContainer.getItem(0);
                return new HeldMove(NeoForgeFluidStacks.stored(resource.toStack(extracted)), fromResult, toResult);
            }
        }
        return null;
    }

    private static ItemAccess heldAccess(ItemStack stack, SimpleContainer container) {
        return stack.getItem() instanceof FluidBucketItem ? ItemAccess.forStack(stack)
                : ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(container), 0);
    }

    @Override
    public boolean holdsFluid(ItemStack stack) {
        ResourceHandler<FluidResource> handler = ItemAccess.forStack(stack).getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) return false;
        for (int index = 0; index < handler.size(); index++) {
            if (!handler.getResource(index).isEmpty() && handler.getAmountAsLong(index) > 0) return true;
        }
        return false;
    }

    /* Bound only after a mod calls NeoForgeMod.enableMilkFluid(). */
    @Nullable
    @Override
    public Fluid milkFluid() {
        return NeoForgeMod.MILK.isBound() ? NeoForgeMod.MILK.value() : null;
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
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, face);
        return handler == null ? null : new HandlerStore(handler);
    }

    @Override
    public boolean carriesItemContainer(ItemStack stack) {
        return ItemAccess.forStack(stack).getCapability(Capabilities.Item.ITEM) != null;
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
        return CommonHooks.onPlayerTossEvent(player, stack, false, Prediction.SERVER_ONLY) != null;
    }

    @Override
    public boolean permitsBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return !NeoForge.EVENT_BUS.post(
                new BreakBlockEvent(level, pos, level.getBlockState(pos), player)).isCanceled();
    }

    /**
     * Records a real player's placement with NeoForge's block-snapshot capture and posts
     * {@code EntityPlaceEvent} for it, as {@code CommonHooks.onPlaceItemIntoWorld} does around
     * {@code useOn}: a refusal restores the recorded blocks, and an accepted placement then runs the
     * block placement and neighbor updates that capture deferred. Capture the loader armed around an
     * enclosing {@code useOn} is suspended with its snapshots set aside, so that hook sees none of
     * this placement.
     */
    @Override
    public boolean placeChecked(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                BooleanSupplier place) {
        if (level.isClientSide() || !(context.player() instanceof ServerPlayer player)) return place.getAsBoolean();
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
                    ? EventHooks.onMultiBlockPlace(player, snapshots, face)
                    : snapshots.size() == 1 && EventHooks.onBlockPlace(player, snapshots.getFirst(), face));
            if (refused) {
                for (BlockSnapshot snapshot : snapshots.reversed()) {
                    level.restoringBlockSnapshots = true;
                    snapshot.restore(snapshot.getFlags() | Block.UPDATE_CLIENTS);
                    level.restoringBlockSnapshots = false;
                }
                return false;
            }
            for (BlockSnapshot snapshot : snapshots) {
                BlockPos at = snapshot.getPos();
                BlockState placedState = level.getBlockState(at);
                placedState.onPlace(level, at, snapshot.getState(), false);
                level.markAndNotifyBlock(at, level.getChunkAt(at), snapshot.getState(), placedState,
                        snapshot.getFlags(), Block.UPDATE_LIMIT);
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
        ResourceHandler<FluidResource> handler = ItemAccess.forStack(stack).getCapability(Capabilities.Fluid.ITEM);
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

    /* Each preview aborts its transaction; each executed operation commits exactly what moved. */
    private record HandlerStore(ResourceHandler<FluidResource> handler) implements BlockFluidStore {
        @Override
        public List<StoredFluid> offered() {
            List<StoredFluid> offered = new ArrayList<>();
            for (int index = 0; index < handler.size(); index++) {
                FluidResource resource = handler.getResource(index);
                if (!resource.isEmpty() && handler.getAmountAsLong(index) > 0) {
                    offered.add(NeoForgeFluidStacks.stored(resource.toStack(FluidType.BUCKET_VOLUME)));
                }
            }
            return offered;
        }

        @Override
        public StoredFluid drain(StoredFluid request, boolean simulate) {
            if (request.isEmpty()) return StoredFluid.EMPTY;
            FluidResource resource = FluidResource.of(NeoForgeFluidStacks.of(request));
            try (Transaction transaction = Transaction.open(null)) {
                int extracted = handler.extract(resource, request.amount(), transaction);
                if (!simulate) transaction.commit();
                return NeoForgeFluidStacks.stored(resource.toStack(extracted));
            }
        }

        @Override
        public int fill(StoredFluid offered, boolean simulate) {
            if (offered.isEmpty()) return 0;
            try (Transaction transaction = Transaction.open(null)) {
                int inserted = handler.insert(FluidResource.of(NeoForgeFluidStacks.of(offered)), offered.amount(), transaction);
                if (!simulate) transaction.commit();
                return inserted;
            }
        }
    }
}
