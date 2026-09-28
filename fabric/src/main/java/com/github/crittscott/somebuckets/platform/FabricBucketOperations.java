package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.fluid.FabricBucketStorage;
import com.github.crittscott.somebuckets.fluid.FabricFluidPlacement;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.FabricDispenserFakePlayer;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Optional;

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

            FluidVariant resource = unlimited ? variant(BucketState.getStoredFluid(from))
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
        int movedMb = (int) Math.max(1, Math.min(Integer.MAX_VALUE, movedTotal / (BUCKET / FluidBucketItem.BUCKET_VOLUME_MB)));
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
    public boolean hasBlockStorage(Level level, BlockPos pos, Direction face) {
        return blockStorage(level, pos, face) != null;
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

    @Override
    public boolean firesWorldBucketEvent() {
        return false;
    }

    @Nullable
    @Override
    public InteractionResult beforeWorldBucketUse(Player player, Level level,
                                                  ItemStack stack, BlockHitResult hit) {
        // Forge-only seam: Fabric has no FillBucketEvent successor. Same as NeoForge.
        return null;
    }

    @Override
    public boolean releasedFluidVariantIsRawTag() {
        return false;
    }

    @Override
    public Component fluidDisplayName(StoredFluid fluid) {
        return FluidVariantAttributes.getName(variant(fluid));
    }

    @Override
    public SoundEvent fillSound(StoredFluid fluid) {
        return FluidVariantAttributes.getFillSound(variant(fluid));
    }

    @Override
    public Optional<SoundEvent> pickupSound(BucketPickup pickup, BlockState state) {
        return pickup.getPickupSound();
    }

    @Override
    public SoundEvent emptySound(StoredFluid fluid) {
        return FluidVariantAttributes.getEmptySound(variant(fluid));
    }

    @Override
    public BlockFluidOutcome previewBlockTake(Level level, BlockHitResult hit, ItemStack stack) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block == null) return BlockFluidOutcome.NO_STORE;
        return findOneBucket(block, bucketStorage(stack)) != null
                ? BlockFluidOutcome.SUCCESS : BlockFluidOutcome.REFUSED;
    }

    @Override
    public BlockFluidOutcome blockTake(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, boolean asSource) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block == null) return BlockFluidOutcome.NO_STORE;
        return takeFromStorage(level, hit, stack, context, block)
                ? BlockFluidOutcome.SUCCESS : BlockFluidOutcome.REFUSED;
    }

    @Override
    public BlockFluidOutcome blockPlace(Level level, BlockHitResult hit, ItemStack stack,
                                        ProtectionContext context, boolean asSource) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block == null) return BlockFluidOutcome.NO_STORE;
        return placeIntoStorage(level, hit, stack, context, block)
                ? BlockFluidOutcome.SUCCESS : BlockFluidOutcome.REFUSED;
    }

    @Nullable
    @Override
    public SourceTarget classifyBlockTarget(Level level, BlockHitResult hit, ItemStack stack) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block == null) return null;
        FluidVariant expected = variant(BucketState.getStoredFluid(stack));
        if (canMoveExactly(block, bucketStorage(stack), expected)) {
            return SourceTarget.MATCHING_FLUID;
        }
        for (StorageView<FluidVariant> view : block.nonEmptyViews()) {
            if (!view.isResourceBlank() && view.getAmount() > 0) return SourceTarget.BLOCKING_FLUID;
        }
        return SourceTarget.NO_FLUID;
    }

    @Override
    public boolean placeArbitraryFluid(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, StoredFluid stored, boolean asSource,
                                       boolean allowFaceOffset) {
        if (!FabricFluidPlacement.place(level, hit, stack, context, stored, allowFaceOffset)) return false;
        if (!asSource && !level.isClientSide) {
            BucketState.drainFiniteContent(stack, FluidBucketItem.BUCKET_VOLUME_MB);
        }
        return true;
    }

    @Override
    public BlockPos resolveArbitraryPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                Player player, InteractionHand hand,
                                                StoredFluid stored, boolean allowFaceOffset) {
        return FabricFluidPlacement.resolveTarget(level, hit, stored, allowFaceOffset);
    }

    /**
     * Fabric's {@link BlockItem#place} posts no block-place event, so a real player's placement is
     * first checked against Common Protection API when it is installed.
     */
    @Override
    public InteractionResult placePowderBlock(BlockItem item, BlockPlaceContext placement) {
        if (COMMON_PROTECTION && placement.getPlayer() instanceof ServerPlayer player
                && !(player instanceof FakePlayer)
                && !CommonProtectionChecks.canPlace(placement.getLevel(), placement.getClickedPos(), player)) {
            return InteractionResult.FAIL;
        }
        return item.place(placement);
    }

    @Nullable
    private static Storage<FluidVariant> blockStorage(Level level, BlockHitResult hit) {
        return blockStorage(level, hit.getBlockPos(), hit.getDirection());
    }

    @Nullable
    private static Storage<FluidVariant> blockStorage(Level level, BlockPos pos, Direction face) {
        // Fabric exposes vanilla cauldrons as fluid storage, but Some Buckets routes vanilla cauldron
        // interactions through the dedicated Cauldrons path so they award the cauldron statistics
        // and emit the cauldron game events on every loader.
        // Modded cauldron blocks keep their storage.
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.LAVA_CAULDRON)
                || state.is(Blocks.POWDER_SNOW_CAULDRON)) return null;
        return FluidStorage.SIDED.find(level, pos, face);
    }

    private static boolean takeFromStorage(Level level, BlockHitResult hit, ItemStack stack,
                                           ProtectionContext context, Storage<FluidVariant> block) {
        Storage<FluidVariant> bucket = bucketStorage(stack);
        FluidVariant available = findOneBucket(block, bucket);
        if (available == null) return false;
        if (!Protections.mayModify(level, context, hit.getBlockPos(), hit.getDirection(), stack)) {
            return false;
        }
        if (!level.isClientSide) {
            try (Transaction transaction = Transaction.openOuter()) {
                if (StorageUtil.move(block, bucket, available::equals, BUCKET, transaction) != BUCKET) {
                    return false;
                }
                transaction.commit();
            }
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            level.gameEvent(context.player(), GameEvent.FLUID_PICKUP, hit.getBlockPos());
        }
        play(level, hit.getBlockPos(), FluidVariantAttributes.getFillSound(available));
        return true;
    }

    private static boolean placeIntoStorage(Level level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context, Storage<FluidVariant> block) {
        FluidVariant available = variant(BucketState.getStoredFluid(stack));
        Storage<FluidVariant> bucket = bucketStorage(stack);
        if (!canMoveExactly(bucket, block, available)) return false;
        if (!Protections.mayModify(level, context, hit.getBlockPos(), hit.getDirection(), stack)) {
            return false;
        }
        if (!level.isClientSide) {
            try (Transaction transaction = Transaction.openOuter()) {
                if (StorageUtil.move(bucket, block, available::equals, BUCKET, transaction) != BUCKET) {
                    return false;
                }
                transaction.commit();
            }
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, hit.getBlockPos());
        }
        play(level, hit.getBlockPos(), FluidVariantAttributes.getEmptySound(available));
        return true;
    }

    @Nullable
    private static FluidVariant findOneBucket(Storage<FluidVariant> from, Storage<FluidVariant> to) {
        for (StorageView<FluidVariant> view : from.nonEmptyViews()) {
            FluidVariant candidate = view.getResource();
            if (canMoveExactly(from, to, candidate)) return candidate;
        }
        return null;
    }

    private static boolean canMoveExactly(Storage<FluidVariant> from, Storage<FluidVariant> to,
                                          FluidVariant resource) {
        try (Transaction transaction = Transaction.openOuter()) {
            return StorageUtil.move(from, to, resource::equals, BUCKET, transaction) == BUCKET;
        }
    }

    private static Storage<FluidVariant> bucketStorage(ItemStack stack) {
        return FabricBucketStorage.of(stack);
    }

    private static FluidVariant variant(StoredFluid fluid) {
        return FabricFluidVariants.toVariant(fluid);
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound) {
        if (!level.isClientSide) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
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
