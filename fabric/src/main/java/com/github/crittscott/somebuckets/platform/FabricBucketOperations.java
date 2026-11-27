package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.fluid.FabricBucketStorage;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.PlayerEvent;
import eu.pb4.common.protection.api.CommonProtection;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Fabric Transfer API, callback, protection, and placement primitives behind the shared bucket
 * interaction flow.
 *
 * <p>Protection checks post the events a Fabric player's own action would reach, before Some Buckets
 * changes the world. Fabric API covers block breaking and block and entity use, but has no
 * block-place or item-pickup event. Claim mods built on Architectury (such as FTB Chunks) watch
 * Architectury's own events, so breaking, placement, and pickup also post those. Patbox's Common
 * Protection API is consulted for breaking, placing, and block and entity use. Both integrations are optional: each is
 * called only when its mod is installed, from a nested class that loads only then. A use callback
 * that answers anything but {@code PASS} refuses: another mod took the click, which vanilla dispatch
 * would not pass on to the item.
 */
public final class FabricBucketOperations implements BucketOperations {
    private static final boolean ARCHITECTURY = FabricLoader.getInstance().isModLoaded("architectury");
    private static final long BUCKET = FluidConstants.BUCKET;
    private static final boolean COMMON_PROTECTION =
            FabricLoader.getInstance().isModLoaded("common-protection-api");
    private static final int MAX_CONTEXT_REPLACEMENTS = 64;

    /**
     * Names the optional protection integrations this class consults, in a fixed order, for the
     * entry point's startup log.
     */
    public static List<String> activeIntegrations() {
        List<String> integrations = new ArrayList<>();
        if (ARCHITECTURY) integrations.add("Architectury API");
        if (COMMON_PROTECTION) integrations.add("Common Protection API");
        return integrations;
    }

    @Override
    public ServerPlayer automationPlayer(ServerLevel level) {
        return FakePlayer.get(level, DISPENSER_PROFILE);
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
            if (resource == null || resource.isBlank()) break;
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
        // A move under one millibucket still reports its fluid, which selects the transfer sound.
        int movedMb = Math.max(1, FabricFluidVariants.wholeMb(movedTotal));
        return new HeldMove(FabricFluidVariants.stored(movedResource, movedMb), source.result(), destination.result());
    }

    @Override
    public boolean holdsFluid(ItemStack stack) {
        Storage<FluidVariant> storage = ContainerItemContext.withConstant(stack).find(FluidStorage.ITEM);
        return storage != null && StorageUtil.findExtractableResource(storage, null) != null;
    }

    @Nullable
    @Override
    public MobBucketContents mobBucketContents(ItemStack stack) {
        if (!(stack.getItem() instanceof MobBucketItem bucket)) return null;
        return new MobBucketContents(bucket.type, bucket.content);
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

    /* Fabric API has no item-stack inventory lookup on 1.20.1; the vanilla storage rules decide. */
    @Override
    public boolean carriesItemContainer(ItemStack stack) {
        return false;
    }

    @Override
    public boolean permitsBlockBreak(ServerLevel level, Player actor, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                level, actor, pos, state, level.getBlockEntity(pos))) {
            return false;
        }
        if (ARCHITECTURY && !Architectury.permitsBreak(level, actor, pos, state)) return false;
        return !COMMON_PROTECTION || CommonProtectionApi.permitsBreak(level, actor, pos);
    }

    @Override
    public boolean permitsBlockInteract(ServerLevel level, Player actor, InteractionHand hand,
                                        BlockHitResult hit) {
        if (UseBlockCallback.EVENT.invoker().interact(actor, level, hand, hit) != InteractionResult.PASS) {
            return false;
        }
        return !COMMON_PROTECTION || CommonProtectionApi.permitsInteract(level, actor, hit.getBlockPos());
    }

    @Override
    public boolean permitsEntityInteract(ServerLevel level, Player actor, InteractionHand hand,
                                         Entity target) {
        if (UseEntityCallback.EVENT.invoker().interact(actor, level, hand, target, null)
                != InteractionResult.PASS) {
            return false;
        }
        return !COMMON_PROTECTION || CommonProtectionApi.permitsInteract(level, actor, target);
    }

    @Override
    public boolean allowsItemPickup(ItemEntity entity, Player actor) {
        return !ARCHITECTURY || Architectury.permitsPickup(actor, entity);
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

    /**
     * Fabric has no block-snapshot recording, so the place checks Fabric's protection integrations
     * offer run before the placement: Architectury's place event and Common Protection API, each
     * when installed.
     */
    @Override
    public boolean placeChecked(Level level, ProtectionContext context, BlockPos pos, Direction face,
                                BlockState placed, BooleanSupplier place) {
        if (level instanceof ServerLevel server) {
            Player actor = context.actor();
            if (ARCHITECTURY && !Architectury.permitsPlace(server, actor, pos, placed)) return false;
            if (COMMON_PROTECTION && !CommonProtectionApi.permitsPlace(server, actor, pos)) return false;
        }
        return place.getAsBoolean();
    }

    /** Fabric does not record block changes around {@code useOn}, so the action just runs. */
    @Override
    public boolean asBlockInteraction(Level level, BooleanSupplier action) {
        return action.getAsBoolean();
    }

    /** Fabric API has no curative-item rule; vanilla's milk bucket removes every effect. */
    @Override
    public void cureAsMilk(LivingEntity entity) {
        entity.removeAllEffects();
    }

    /** Forge-only seam: Fabric has no {@code FillBucketEvent}. */
    @Nullable
    @Override
    public InteractionResult beforeWorldBucketUse(Player player, Level level,
                                                  ItemStack stack, Supplier<BlockHitResult> hit) {
        return null;
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
        return FluidTransactions.resolveWorldTarget(level, hit.getBlockPos(), hit.getDirection(),
                allowFaceOffset, stored.fluid());
    }

    /* Sided Transfer API storage in common terms; each call runs in its own outer transaction. */
    private record StorageStore(Storage<FluidVariant> storage) implements BlockFluidStore {
        @Override
        public List<StoredFluid> offered() {
            List<StoredFluid> offered = new ArrayList<>();
            for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
                StoredFluid fluid = FabricFluidVariants.stored(view.getResource(),
                        FabricFluidVariants.wholeMb(view.getAmount()));
                if (!fluid.isEmpty()) offered.add(fluid);
            }
            return offered;
        }

        @Override
        public StoredFluid drain(StoredFluid request, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                long extracted = storage.extract(FabricFluidVariants.toVariant(request),
                        FabricFluidVariants.droplets(request.amount()), transaction);
                if (!simulate) transaction.commit();
                return request.withAmount(FabricFluidVariants.wholeMb(extracted));
            }
        }

        @Override
        public int fill(StoredFluid offered, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage.insert(FabricFluidVariants.toVariant(offered),
                        FabricFluidVariants.droplets(offered.amount()), transaction);
                if (!simulate) transaction.commit();
                return FabricFluidVariants.wholeMb(inserted);
            }
        }
    }

    /** Architectury event posting; loaded only when Architectury API is installed. */
    private static final class Architectury {
        static boolean permitsBreak(ServerLevel level, Player actor, BlockPos pos, BlockState state) {
            return !BlockEvent.BREAK.invoker().breakBlock(level, pos, state, (ServerPlayer) actor, null).isFalse();
        }

        static boolean permitsPickup(Player actor, ItemEntity item) {
            return !PlayerEvent.PICKUP_ITEM_PRE.invoker().canPickup(actor, item, item.getItem()).isFalse();
        }

        static boolean permitsPlace(ServerLevel level, Player actor, BlockPos pos, BlockState placed) {
            return !BlockEvent.PLACE.invoker().placeBlock(level, pos, placed, actor).isFalse();
        }
    }

    /** Common Protection API checks; loaded only when that API is installed. */
    private static final class CommonProtectionApi {
        static boolean permitsBreak(ServerLevel level, Player actor, BlockPos pos) {
            return CommonProtection.canBreakBlock(level, pos, actor.getGameProfile(), actor);
        }

        static boolean permitsInteract(ServerLevel level, Player actor, BlockPos pos) {
            return CommonProtection.canInteractBlock(level, pos, actor.getGameProfile(), actor);
        }

        static boolean permitsInteract(ServerLevel level, Player actor, Entity target) {
            return CommonProtection.canInteractEntity(level, target, actor.getGameProfile(), actor);
        }

        static boolean permitsPlace(ServerLevel level, Player actor, BlockPos pos) {
            return CommonProtection.canPlaceBlock(level, pos, actor.getGameProfile(), actor);
        }
    }

    /*
     * One side of a held move: our stack edited in place, or another item worked on as a copy in a
     * detached one-slot container, whose slot holds the resulting item afterward.
     * ContainerItemContext.withConstant cannot record the exchanged item, so the copy lives in that
     * container.
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
}
