package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.fluid.FabricBucketStorage;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.FabricDispenserFakePlayer;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import eu.pb4.common.protection.api.CommonProtection;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Fabric Transfer API implementation of the shared bucket fluid primitives. */
public final class FabricBucketOperations implements BucketOperations {
    private static final long BUCKET = FluidConstants.BUCKET;
    private static final int MAX_CONTEXT_REPLACEMENTS = 64;
    /** Whether Patbox's Common Protection API, the shared Fabric claim-check API, is installed. */
    private static final boolean COMMON_PROTECTION =
            FabricLoader.getInstance().isModLoaded("common-protection-api");

    @Override
    public ServerPlayer automationPlayer(ServerLevel level) {
        return FabricDispenserFakePlayer.get(level);
    }

    /*
     * A Some Buckets container is moved through its own storage over the exact stack, edited in
     * place; any other item through the storage its item context exposes, which may exchange the
     * item. Each pass re-finds both storages, since a context's item can change mid-transfer.
     * A Source Bucket destination sinks one bucket volume per move.
     */
    @Nullable
    @Override
    public HeldMove moveHeldFluid(ItemStack from, ItemStack to, boolean unlimited) {
        HeldSide source = new HeldSide(from);
        HeldSide destination = new HeldSide(to);
        FluidVariant movedResource = null;
        long movedTotal = 0;
        for (int pass = 0; pass < MAX_CONTEXT_REPLACEMENTS; pass++) {
            Storage<FluidVariant> fromStorage = source.storage();
            Storage<FluidVariant> toStorage = destination.storage();
            if (fromStorage == null || toStorage == null) break;

            FluidVariant resource = unlimited ? FabricFluidVariants.toVariant(BucketState.getStoredFluid(from))
                    : StorageUtil.findExtractableResource(fromStorage, null);
            if (resource == null) break;
            long moved;
            try (Transaction transaction = Transaction.openOuter()) {
                moved = unlimited ? toStorage.insert(resource, Long.MAX_VALUE, transaction)
                        : StorageUtil.move(fromStorage, toStorage, resource::equals, Long.MAX_VALUE, transaction);
                if (moved <= 0) break;
                transaction.commit();
            }
            movedResource = resource;
            movedTotal += moved;
            if (to.getItem() instanceof SBItem) break;
        }
        if (movedResource == null) return null;
        int movedMb = (int) Math.max(1, Math.min(
                Integer.MAX_VALUE, movedTotal / FabricBucketStorage.DROPLETS_PER_MB));
        return new HeldMove(new StoredFluid(movedResource.getFluid(), movedMb, movedResource.getComponents()),
                source.result(), destination.result());
    }

    @Override
    public boolean holdsFluid(ItemStack stack) {
        ContainerItemContext context = ContainerItemContext.ofSingleSlot(
                InventoryStorage.of(new SimpleContainer(stack), null).getSlot(0));
        Storage<FluidVariant> storage = FluidStorage.ITEM.find(stack, context);
        return storage != null && StorageUtil.findExtractableResource(storage, null) != null;
    }

    @Override
    public boolean carriesItemContainer(ItemStack stack) {
        return ContainerItemContext.withConstant(stack).find(ItemStorage.ITEM) != null;
    }

    /** Fabric API has no player item-pickup event, so nothing can veto the pickup. */
    @Override
    public boolean allowsItemPickup(ItemEntity entity, Player player) {
        return true;
    }

    /** Fabric API has no post-pickup event. */
    @Override
    public void afterItemPickup(Player player, ItemEntity entity, ItemStack original, int count) {
    }

    /** Fabric API has no toss event; this is the vanilla drop-item throw. */
    @Override
    public boolean tossFromPlayer(Player player, ItemStack stack) {
        return player.drop(stack, false, true) != null;
    }

    @Override
    public boolean permitsBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos))
                && (!COMMON_PROTECTION || CommonProtectionChecks.canBreak(level, pos, player));
    }

    /** Fabric API has no block-place event; Common Protection API is consulted when installed. */
    @Override
    public boolean permitsBlockPlace(ServerLevel level, ServerPlayer player, BlockPos pos, Direction face) {
        return !COMMON_PROTECTION || CommonProtectionChecks.canPlace(level, pos, player);
    }

    @Nullable
    @Override
    public InteractionResult beforeWorldBucketUse(Player player, Level level,
                                                  ItemStack stack, Supplier<BlockHitResult> hit) {
        // Forge-only seam: Fabric has no FillBucketEvent successor. Same as NeoForge.
        return null;
    }

    @Override
    public boolean releasedFluidVariantIsRawTag() {
        return false;
    }

    @Override
    public Component fluidDisplayName(StoredFluid fluid) {
        return FluidVariantAttributes.getName(FabricFluidVariants.toVariant(fluid));
    }

    @Override
    public Optional<SoundEvent> fillSound(StoredFluid fluid) {
        return Optional.of(FluidVariantAttributes.getFillSound(FabricFluidVariants.toVariant(fluid)));
    }

    @Override
    public Optional<SoundEvent> pickupSound(BucketPickup pickup, BlockState state) {
        return pickup.getPickupSound();
    }

    @Override
    public Optional<SoundEvent> emptySound(StoredFluid fluid) {
        return Optional.of(FluidVariantAttributes.getEmptySound(FabricFluidVariants.toVariant(fluid)));
    }

    /*
     * Fabric exposes vanilla cauldrons as fluid storage, but Cauldrons owns them on every loader so
     * they award the cauldron statistics and emit the cauldron game events. Modded cauldron blocks
     * keep their storage.
     */
    @Nullable
    @Override
    public BlockFluidStore blockFluidStore(Level level, BlockPos pos, Direction face) {
        if (Cauldrons.isVanillaCauldron(level.getBlockState(pos))) return null;
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, face);
        return storage == null ? null : new StorageStore(storage);
    }

    /* The bucket is debited through its own transactional storage, which applies the item's rules. */
    @Override
    public boolean placeArbitraryFluid(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, StoredFluid stored,
                                       boolean allowFaceOffset) {
        Storage<FluidVariant> bucket = FabricBucketStorage.of(stack);
        FluidVariant variant = FabricFluidVariants.toVariant(stored);
        try (Transaction transaction = Transaction.openOuter()) {
            boolean placed = FluidTransactions.emptyFluid(level, context, stack, hit.getBlockPos(),
                    hit.getDirection(), allowFaceOffset, stored,
                    () -> bucket.extract(variant, BUCKET, transaction) == BUCKET);
            if (!placed) return false;
            if (!level.isClientSide) transaction.commit();
            return true;
        }
    }

    @Override
    public BlockPos resolveArbitraryPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                Player player, InteractionHand hand,
                                                StoredFluid stored, boolean allowFaceOffset) {
        return FluidTransactions.resolveWorldTarget(level, player, hit.getBlockPos(), hit.getDirection(),
                allowFaceOffset, stored.fluid());
    }

    /**
     * Fabric's {@link BlockItem#place} posts no block-place event, so a real player's placement is
     * first checked against Common Protection API when it is installed.
     */
    @Override
    public InteractionResult placePowderBlock(BlockItem item, BlockPlaceContext placement,
                                              ProtectionContext context) {
        if (COMMON_PROTECTION && placement.getPlayer() instanceof ServerPlayer player
                && !(player instanceof FakePlayer)
                && !CommonProtectionChecks.canPlace(placement.getLevel(), placement.getClickedPos(), player)) {
            return InteractionResult.FAIL;
        }
        return item.place(placement);
    }

    /* Sided Transfer API storage in common terms; each call runs in its own outer transaction. */
    private record StorageStore(Storage<FluidVariant> storage) implements BlockFluidStore {
        @Override
        public List<StoredFluid> offered() {
            List<StoredFluid> offered = new ArrayList<>();
            for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
                StoredFluid fluid = FabricFluidVariants.stored(view.getResource(),
                        (int) Math.min(Integer.MAX_VALUE, view.getAmount() / FabricBucketStorage.DROPLETS_PER_MB));
                if (!fluid.isEmpty()) offered.add(fluid);
            }
            return offered;
        }

        @Override
        public StoredFluid drain(StoredFluid request, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                long extracted = storage.extract(FabricFluidVariants.toVariant(request),
                        (long) request.amount() * FabricBucketStorage.DROPLETS_PER_MB, transaction);
                if (!simulate) transaction.commit();
                return request.withAmount((int) (extracted / FabricBucketStorage.DROPLETS_PER_MB));
            }
        }

        @Override
        public int fill(StoredFluid offered, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage.insert(FabricFluidVariants.toVariant(offered),
                        (long) offered.amount() * FabricBucketStorage.DROPLETS_PER_MB, transaction);
                if (!simulate) transaction.commit();
                return (int) (inserted / FabricBucketStorage.DROPLETS_PER_MB);
            }
        }
    }

    /*
     * One side of a held move: our stack edited in place, or another item worked on as a copy in a
     * detached one-slot container, whose slot holds the resulting item afterward.
     */
    private static final class HeldSide {
        private final ItemStack stack;
        @Nullable
        private final SimpleContainer container;
        @Nullable
        private final ContainerItemContext context;

        private HeldSide(ItemStack stack) {
            this.stack = stack;
            if (stack.getItem() instanceof FluidBucketItem) {
                this.container = null;
                this.context = null;
            } else {
                this.container = new SimpleContainer(stack.copy());
                this.context = ContainerItemContext.ofSingleSlot(InventoryStorage.of(container, null).getSlot(0));
            }
        }

        @Nullable
        Storage<FluidVariant> storage() {
            return context == null ? FabricBucketStorage.of(stack) : context.find(FluidStorage.ITEM);
        }

        ItemStack result() {
            return container == null ? stack : container.getItem(0).copy();
        }
    }

    /* Holds every Common Protection API reference, so its classes load only once the API is known present. */
    private static final class CommonProtectionChecks {
        static boolean canBreak(Level level, BlockPos pos, Player player) {
            return CommonProtection.canBreakBlock(level, pos, player.getGameProfile(), player);
        }

        static boolean canPlace(Level level, BlockPos pos, Player player) {
            return CommonProtection.canPlaceBlock(level, pos, player.getGameProfile(), player);
        }
    }
}
