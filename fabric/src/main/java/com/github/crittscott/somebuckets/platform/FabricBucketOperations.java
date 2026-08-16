package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Fabric Transfer API and vanilla-world implementation of the shared bucket interaction seam. */
public final class FabricBucketOperations implements BucketOperations {
    private static final long BUCKET = FluidConstants.BUCKET;
    private static final int MAX_CONTEXT_REPLACEMENTS = 64;

    @Override
    public boolean tryHeldTransfer(Level level, Player player, InteractionHand bucketHand, ItemStack bucket,
                                   InteractionHand otherHand, ItemStack other) {
        if (bucket.isEmpty() || other.isEmpty() || bucket == other) return false;
        if (tryMilkTransfer(level, player, bucketHand, bucket, otherHand, other)) return true;

        ItemStack stackedEmpties = ItemStack.EMPTY;
        if (!level.isClientSide && !player.getAbilities().instabuild
                && other.is(Items.BUCKET) && other.getCount() > 1) {
            stackedEmpties = other;
            other = other.copy();
            other.setCount(1);
            player.setItemInHand(otherHand, other);
        }

        ContainerItemContext bucketContext = ContainerItemContext.forPlayerInteraction(player, bucketHand);
        ContainerItemContext otherContext = ContainerItemContext.forPlayerInteraction(player, otherHand);
        FluidVariant movedResource = null;
        boolean emptiedBucket = false;
        if (bucket.getItem() instanceof SBItem && NBTUtil.getMode(bucket) == NBTUtil.Mode.FLUID) {
            movedResource = moveInfiniteHeld(level, bucket, otherContext);
            emptiedBucket = movedResource != null;
        } else {
            movedResource = moveHeld(level, bucketContext, otherContext);
            emptiedBucket = movedResource != null;
        }
        if (movedResource == null) movedResource = moveHeld(level, otherContext, bucketContext);
        if (movedResource == null) {
            if (!stackedEmpties.isEmpty()) player.setItemInHand(otherHand, stackedEmpties);
            return false;
        }

        // Keep the shared Item.use return value and the context-updated hand on the same object.
        ItemStack updatedBucket = player.getItemInHand(bucketHand);
        if (updatedBucket.getItem() == bucket.getItem()) {
            bucket.setTag(updatedBucket.hasTag() ? updatedBucket.getTag().copy() : null);
            player.setItemInHand(bucketHand, bucket);
        }
        if (!stackedEmpties.isEmpty()) {
            settle(level, player, otherHand, stackedEmpties,
                    List.of(player.getItemInHand(otherHand).copy()), stackedEmpties.getCount() - 1);
        }
        player.awardStat(Stats.ITEM_USED.get(bucket.getItem()));
        level.playSound(player, player.blockPosition(), emptiedBucket
                        ? FluidVariantAttributes.getEmptySound(movedResource)
                        : FluidVariantAttributes.getFillSound(movedResource),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    @Nullable
    private static FluidVariant moveHeld(Level level, ContainerItemContext fromContext,
                                         ContainerItemContext toContext) {
        FluidVariant movedResource = null;
        // Each move drains the current storage; further passes only process a replacement item
        // exposed by the container context. This budget bounds the work done by one interaction.
        for (int pass = 0; pass < MAX_CONTEXT_REPLACEMENTS; pass++) {
            Storage<FluidVariant> from = fromContext.find(FluidStorage.ITEM);
            Storage<FluidVariant> to = toContext.find(FluidStorage.ITEM);
            if (from == null || to == null) break;

            FluidVariant resource = StorageUtil.findExtractableResource(from, null);
            if (resource == null) break;
            long moved;
            try (Transaction transaction = Transaction.openOuter()) {
                moved = StorageUtil.move(from, to, candidate -> candidate.equals(resource),
                        Long.MAX_VALUE, transaction);
                if (moved <= 0) break;
                if (!level.isClientSide) transaction.commit();
            }
            movedResource = resource;
            if (level.isClientSide
                    || (fromContext.getItemVariant().getItem() instanceof FluidBucketItem
                    && toContext.getItemVariant().getItem() instanceof SBItem)) break;
        }
        return movedResource;
    }

    @Nullable
    private static FluidVariant moveInfiniteHeld(Level level, ItemStack source,
                                                 ContainerItemContext toContext) {
        StoredFluid stored = NBTUtil.getStoredFluid(source);
        if (stored.isEmpty() || !SBPolicy.allows(stored.fluid())) return null;
        FluidVariant resource = variant(stored);
        // An inserting context may expose another replacement item after each committed move.
        for (int pass = 0; pass < MAX_CONTEXT_REPLACEMENTS; pass++) {
            Storage<FluidVariant> to = toContext.find(FluidStorage.ITEM);
            if (to == null) return pass == 0 ? null : resource;
            long inserted;
            try (Transaction transaction = Transaction.openOuter()) {
                inserted = to.insert(resource, Long.MAX_VALUE, transaction);
                if (inserted <= 0) return pass == 0 ? null : resource;
                if (!level.isClientSide) transaction.commit();
            }
            if (level.isClientSide || toContext.getItemVariant().getItem() instanceof SBItem) break;
        }
        return resource;
    }

    @Override
    public boolean hasBlockStorage(Level level, BlockPos pos, Direction face) {
        return FluidStorage.SIDED.find(level, pos, face) != null;
    }

    @Nullable
    @Override
    public InteractionResultHolder<ItemStack> beforeWorldBucketUse(Player player, Level level,
                                                                   ItemStack stack, BlockHitResult hit) {
        return null;
    }

    @Override
    public net.minecraft.network.chat.Component fluidDisplayName(StoredFluid fluid) {
        return FluidVariantAttributes.getName(variant(fluid));
    }

    @Override
    public int fluidColor(StoredFluid fluid, int fallback) {
        return FabricFluidColors.color(fluid, fallback);
    }

    @Override
    public boolean canAttemptBigTake(Level level, BlockHitResult hit, ItemStack stack) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block != null) return findOneBucket(block, stack, false) != null;
        StoredFluid available = worldFluid(level, hit.getBlockPos());
        return !available.isEmpty() && acceptsFinite(stack, available, FluidBucketItem.BUCKET_VOLUME_MB);
    }

    @Override
    public boolean tryBigTake(Level level, BlockHitResult hit, ItemStack stack, Player player,
                              InteractionHand hand) {
        return tryBigTakeWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Attempts to transfer one bucket-volume from Fabric block storage or a world source into a
     * finite bucket using explicit player or automation identity.
     *
     * <p>Block storage owns dispatch when present. Protection is checked before mutation. The server
     * commits the storage/world change and credits the bucket atomically; the client only predicts
     * acceptance.
     *
     * @return {@code true} for an accepted prediction or completed server pickup
     */
    public boolean tryBigTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                         ProtectionContext context) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block != null) return takeFromStorage(level, hit, stack, context, false, block);

        StoredFluid available = worldFluid(level, hit.getBlockPos());
        if (available.isEmpty() || !acceptsFinite(stack, available, FluidBucketItem.BUCKET_VOLUME_MB)) {
            return false;
        }
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, hit.getBlockPos(),
                hit.getDirection(), stack, null)) return false;
        if (!takeWorldFluid(level, hit.getBlockPos(), available, context.player())) return false;
        if (!level.isClientSide) {
            creditFinite(stack, available, FluidBucketItem.BUCKET_VOLUME_MB);
            completePickup(context.player(), stack);
        }
        return true;
    }

    @Override
    public boolean tryBigPlace(Level level, BlockHitResult hit, ItemStack stack, Player player,
                               InteractionHand hand) {
        return tryBigPlaceWithContext(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Attempts to transfer one bucket-volume from a finite bucket into Fabric block storage or the
     * world using explicit player or automation identity.
     *
     * <p>Block storage owns dispatch when present. Protection precedes mutation, and server success
     * debits the finite bucket only after the destination accepts the transfer. The client only
     * predicts acceptance.
     *
     * @param allowFaceOffset whether world placement may use the neighbor along the clicked face
     * @return {@code true} for an accepted prediction or completed server placement
     */
    public boolean tryBigPlaceWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                          ProtectionContext context, boolean allowFaceOffset) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.FLUID
                || stored.amount() < FluidBucketItem.BUCKET_VOLUME_MB) return false;
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block != null) return placeIntoStorage(level, hit, stack, context, false, block);

        if (!FluidPlacement.emptyContents(level, context, stack, hit.getBlockPos(), hit,
                stored.fluid(), allowFaceOffset)) return false;
        if (!level.isClientSide) {
            NBTUtil.drainFiniteContent(stack, FluidBucketItem.BUCKET_VOLUME_MB);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    @Override
    public BlockPos resolveBigPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                          boolean allowFaceOffset) {
        if (blockStorage(level, hit) != null) return hit.getBlockPos();
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        return FluidPlacement.resolveTarget(level, hit.getBlockPos(), hit.getDirection(),
                allowFaceOffset, stored.fluid());
    }

    @Override
    public boolean canAttemptPowderTake(Level level, BlockHitResult hit, ItemStack stack) {
        if (!level.getBlockState(hit.getBlockPos()).is(Blocks.POWDER_SNOW)) return false;
        int capacity = ((BBItem) stack.getItem()).getCapacityUnits();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        return mode == NBTUtil.Mode.NONE
                || mode == NBTUtil.Mode.POWDER_SNOW && NBTUtil.getPowderUnits(stack) < capacity;
    }

    @Override
    public boolean tryPowderTake(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                 InteractionHand hand) {
        return tryPowderTakeWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Attempts to collect one powder-snow block using explicit player or automation identity.
     * Protection is checked before the server stores one unit and removes the block; the client only
     * predicts acceptance.
     *
     * @return {@code true} for an accepted prediction or completed server pickup
     */
    public boolean tryPowderTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        if (!canAttemptPowderTake(level, hit, stack)) return false;
        BlockPos pos = hit.getBlockPos();
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, pos,
                hit.getDirection(), stack, null)) return false;
        if (!level.isClientSide) {
            int oldUnits = NBTUtil.getMode(stack) == NBTUtil.Mode.POWDER_SNOW
                    ? NBTUtil.getPowderUnits(stack) : 0;
            NBTUtil.setPowderUnits(stack, oldUnits + 1);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            completePickup(context.player(), stack);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_FILL_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(context.player(), GameEvent.FLUID_PICKUP, pos);
        return true;
    }

    @Override
    public boolean tryPowderPlace(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                  InteractionHand hand) {
        return tryPowderPlaceWithContext(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Attempts native powder-snow placement using explicit player or automation identity. The exact
     * destination is protected before placement; server success then debits one stored block.
     *
     * @param allowFaceOffset whether native placement may use the neighbor along the clicked face
     * @return {@code true} for an accepted client placement or completed server placement
     */
    public boolean tryPowderPlaceWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context, boolean allowFaceOffset) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.POWDER_SNOW
                || NBTUtil.getPowderUnits(stack) <= 0) return false;
        Player player = context.player();
        InteractionHand hand = context.hand() == null ? InteractionHand.MAIN_HAND : context.hand();
        BlockPlaceContext placement = powderContext(level, player, hand, hit);
        if (!allowFaceOffset && !placement.replacingClickedOnBlock()) return false;
        BlockPos placePos = placement.getClickedPos();
        if (!Protections.mayAct(level, context,
                ProtectionAction.BLOCK_EDIT, placePos, hit.getDirection(), stack, null)) return false;
        if (!((BlockItem) Items.POWDER_SNOW_BUCKET).place(placement).consumesAction()) return false;
        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, NBTUtil.getPowderUnits(stack) - 1);
            NBTUtil.normalizeEmptyState(stack);
            if (player != null) player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    @Override
    public BlockPos resolvePowderPlaceTarget(Level level, BlockHitResult hit, Player player,
                                             InteractionHand hand, boolean allowFaceOffset) {
        BlockPlaceContext context = powderContext(level, player, hand, hit);
        return !allowFaceOffset && !context.replacingClickedOnBlock()
                ? hit.getBlockPos() : context.getClickedPos();
    }

    @Override
    public boolean trySourceTake(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                 InteractionHand hand) {
        return trySourceTakeWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Attempts to assign an empty Source Bucket from one allowed bucket-volume using explicit player
     * or automation identity.
     *
     * <p>Fabric block storage owns dispatch when present. Protection precedes mutation. Server
     * success consumes one source volume and records its identity in the Source Bucket; the client
     * only predicts acceptance.
     *
     * @return {@code true} for an accepted prediction or completed server assignment
     */
    public boolean trySourceTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) return false;
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block != null) return takeFromStorage(level, hit, stack, context, true, block);

        StoredFluid available = worldFluid(level, hit.getBlockPos());
        if (available.isEmpty() || !SBPolicy.allows(available.fluid())) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, hit.getBlockPos(),
                hit.getDirection(), stack, null)) return false;
        if (!takeWorldFluid(level, hit.getBlockPos(), available, context.player())) return false;
        if (!level.isClientSide) {
            NBTUtil.setStoredFluid(stack, available.withAmount(FluidBucketItem.BUCKET_VOLUME_MB));
            completePickup(context.player(), stack);
        }
        return true;
    }

    @Override
    public boolean trySourcePlace(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                  InteractionHand hand) {
        return trySourcePlaceWithContext(level, hit, stack, ProtectionContext.player(player, hand), true);
    }

    /**
     * Attempts infinite output from an assigned, allowed Source Bucket using explicit player or
     * automation identity.
     *
     * <p>Fabric block storage owns dispatch when present. Protection precedes the server transaction
     * or world placement, and the Source Bucket's stored identity is never consumed.
     *
     * @param allowFaceOffset whether world placement may use the neighbor along the clicked face
     * @return {@code true} for an accepted prediction or completed server placement
     */
    public boolean trySourcePlaceWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context, boolean allowFaceOffset) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        if (stored.isEmpty() || !SBPolicy.allows(stored.fluid())) return false;
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block != null) return placeIntoStorage(level, hit, stack, context, true, block);

        if (!FluidPlacement.emptyContents(level, context, stack,
                hit.getBlockPos(), hit, stored.fluid(), allowFaceOffset)) return false;
        if (!level.isClientSide && context.player() != null) {
            context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    /**
     * Assigns milk to an empty Source Bucket from the first adult cow in {@code front} after entity
     * interaction protection permits the supplied identity.
     *
     * @return {@code true} when milk was assigned; {@code false} leaves the bucket and cows unchanged
     */
    public boolean trySourceMilk(net.minecraft.server.level.ServerLevel level, BlockPos front,
                                 Direction face, ItemStack stack, ProtectionContext context) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE || !SBPolicy.allowsMilk()) return false;
        List<Cow> cows = level.getEntitiesOfClass(Cow.class, new AABB(front), cow -> !cow.isBaby());
        if (cows.isEmpty()) return false;
        Cow cow = cows.get(0);
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_INTERACT,
                cow.blockPosition(), face, stack, cow)) return false;
        NBTUtil.setMilkAmount(stack, FluidBucketItem.BUCKET_VOLUME_MB);
        level.playSound(context.player(), front, SoundEvents.COW_MILK, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /**
     * Consumes one matching bucket-volume from Fabric block storage without changing an assigned
     * Source Bucket. This automation-only sink protects the block interaction before committing the
     * server transaction.
     *
     * @return {@code true} for an accepted prediction or completed server extraction
     */
    public boolean trySourceSinkFromStorage(Level level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        if (stored.isEmpty() || !SBPolicy.allows(stored.fluid())) return false;
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block == null) return false;
        FluidVariant expected = variant(stored);
        if (StorageUtil.simulateExtract(block, expected, BUCKET, null) != BUCKET) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT,
                hit.getBlockPos(), hit.getDirection(), stack, null)) return false;
        if (!level.isClientSide) {
            try (Transaction transaction = Transaction.openOuter()) {
                if (block.extract(expected, BUCKET, transaction) != BUCKET) return false;
                transaction.commit();
            }
            level.gameEvent(context.player(), GameEvent.FLUID_PICKUP, hit.getBlockPos());
        }
        play(level, context.player(), hit.getBlockPos(), FluidVariantAttributes.getFillSound(expected));
        return true;
    }

    @Override
    public BlockPos resolveSourcePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                             boolean allowFaceOffset) {
        if (blockStorage(level, hit) != null) return hit.getBlockPos();
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        return FluidPlacement.resolveTarget(level, hit.getBlockPos(), hit.getDirection(),
                allowFaceOffset, stored.fluid());
    }

    @Nullable
    private static Storage<FluidVariant> blockStorage(Level level, BlockHitResult hit) {
        return FluidStorage.SIDED.find(level, hit.getBlockPos(), hit.getDirection());
    }

    private static boolean takeFromStorage(Level level, BlockHitResult hit, ItemStack stack,
                                           ProtectionContext context, boolean source,
                                           Storage<FluidVariant> block) {
        FluidVariant available = findOneBucket(block, stack, source);
        if (available == null) return false;
        if (!Protections.mayAct(level, context,
                ProtectionAction.BLOCK_INTERACT, hit.getBlockPos(), hit.getDirection(), stack, null)) {
            return false;
        }
        if (!level.isClientSide) {
            try (Transaction transaction = Transaction.openOuter()) {
                if (block.extract(available, BUCKET, transaction) != BUCKET) return false;
                transaction.commit();
            }
            StoredFluid incoming = stored(available, FluidBucketItem.BUCKET_VOLUME_MB);
            if (source) NBTUtil.setStoredFluid(stack, incoming);
            else creditFinite(stack, incoming, FluidBucketItem.BUCKET_VOLUME_MB);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            level.gameEvent(context.player(), GameEvent.FLUID_PICKUP, hit.getBlockPos());
        }
        play(level, context.player(), hit.getBlockPos(), FluidVariantAttributes.getFillSound(available));
        return true;
    }

    private static boolean placeIntoStorage(Level level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context, boolean source,
                                            Storage<FluidVariant> block) {
        StoredFluid fluid = NBTUtil.getStoredFluid(stack);
        FluidVariant available = variant(fluid);
        if (StorageUtil.simulateInsert(block, available, BUCKET, null) != BUCKET) return false;
        if (!Protections.mayAct(level, context,
                ProtectionAction.BLOCK_INTERACT, hit.getBlockPos(), hit.getDirection(), stack, null)) {
            return false;
        }
        if (!level.isClientSide) {
            try (Transaction transaction = Transaction.openOuter()) {
                if (block.insert(available, BUCKET, transaction) != BUCKET) return false;
                transaction.commit();
            }
            if (!source) NBTUtil.drainFiniteContent(stack, FluidBucketItem.BUCKET_VOLUME_MB);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, hit.getBlockPos());
        }
        play(level, context.player(), hit.getBlockPos(), FluidVariantAttributes.getEmptySound(available));
        return true;
    }

    @Nullable
    private static FluidVariant findOneBucket(Storage<FluidVariant> storage, ItemStack stack,
                                              boolean source) {
        for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
            FluidVariant candidate = view.getResource();
            if (source && !SBPolicy.allows(candidate.getFluid())) continue;
            StoredFluid incoming = stored(candidate, FluidBucketItem.BUCKET_VOLUME_MB);
            if (!source && !acceptsFinite(stack, incoming, FluidBucketItem.BUCKET_VOLUME_MB)) continue;
            if (StorageUtil.simulateExtract(view, candidate, BUCKET, null) == BUCKET) return candidate;
        }
        return null;
    }

    private static boolean acceptsFinite(ItemStack stack, StoredFluid incoming, int amountMb) {
        if (!(stack.getItem() instanceof BBItem item)) return false;
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        StoredFluid current = NBTUtil.getStoredFluid(stack);
        return mode == NBTUtil.Mode.NONE
                || mode == NBTUtil.Mode.FLUID && (current.isEmpty() || current.isSameVariant(incoming))
                && current.amount() + amountMb <= item.getCapacityMb();
    }

    private static void creditFinite(ItemStack stack, StoredFluid incoming, int amountMb) {
        StoredFluid current = NBTUtil.getStoredFluid(stack);
        NBTUtil.setStoredFluid(stack, incoming.withAmount(current.amount() + amountMb));
    }

    private static StoredFluid worldFluid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup) || !state.getFluidState().isSource()) {
            return StoredFluid.EMPTY;
        }
        Fluid fluid = state.getFluidState().getType();
        return fluid == Fluids.EMPTY ? StoredFluid.EMPTY
                : new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB, null);
    }

    private static boolean takeWorldFluid(Level level, BlockPos pos, StoredFluid expected,
                                          Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup pickup) || !state.getFluidState().isSource()
                || !state.getFluidState().getType().isSame(expected.fluid())) return false;
        if (!level.isClientSide && pickup.pickupBlock(level, pos, state).isEmpty()) return false;
        FluidVariant fluid = variant(expected);
        play(level, player, pos, FluidVariantAttributes.getFillSound(fluid));
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        return true;
    }

    private static BlockPlaceContext powderContext(Level level, @Nullable Player player, InteractionHand hand,
                                                   BlockHitResult hit) {
        return new BlockPlaceContext(level, player, hand, new ItemStack(Items.POWDER_SNOW_BUCKET), hit);
    }

    private static FluidVariant variant(StoredFluid fluid) {
        return FluidVariant.of(fluid.fluid(), fluid.variantTag());
    }

    private static StoredFluid stored(FluidVariant variant, int amountMb) {
        return new StoredFluid(variant.getFluid(), amountMb, variant.copyNbt());
    }

    private static void completePickup(@Nullable Player player, ItemStack stack) {
        if (player == null) return;
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, stack);
        }
    }

    private static void play(Level level, @Nullable Player player, BlockPos pos, SoundEvent sound) {
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /* Milk has no Fabric FluidVariant, so it keeps the same explicit container semantics as Forge. */
    private static boolean tryMilkTransfer(Level level, Player player,
                                           InteractionHand bucketHand, ItemStack bucket,
                                           InteractionHand otherHand, ItemStack other) {
        if (NBTUtil.getMode(bucket) == NBTUtil.Mode.MILK) {
            if (other.getItem() instanceof FluidBucketItem) return pourMilkToOurs(level, player, bucket, other);
            if (other.is(Items.BUCKET)) return pourMilkToVanilla(level, player, bucket, otherHand, other);
        }
        if (other.getItem() instanceof FluidBucketItem
                && NBTUtil.getMode(other) == NBTUtil.Mode.MILK) {
            return pourMilkToOurs(level, player, other, bucket);
        }
        if (other.is(Items.MILK_BUCKET)) return takeVanillaMilk(level, player, otherHand, other, bucket);
        return false;
    }

    private static boolean pourMilkToOurs(Level level, Player player, ItemStack source,
                                          ItemStack destination) {
        boolean infiniteSource = source.getItem() instanceof SBItem;
        if ((infiniteSource || destination.getItem() instanceof SBItem) && !SBPolicy.allowsMilk()) return false;
        int stored = NBTUtil.getAmount(source);
        if (!infiniteSource && stored < FluidBucketItem.BUCKET_VOLUME_MB) return false;
        NBTUtil.Mode destinationMode = NBTUtil.getMode(destination);
        if (destinationMode != NBTUtil.Mode.NONE && destinationMode != NBTUtil.Mode.MILK) return false;
        boolean infiniteSink = destination.getItem() instanceof SBItem
                && destinationMode == NBTUtil.Mode.MILK;
        if (infiniteSink) {
            if (infiniteSource) return false;
            NBTUtil.drainFiniteContent(source, FluidBucketItem.BUCKET_VOLUME_MB);
        } else {
            int held = destinationMode == NBTUtil.Mode.MILK ? NBTUtil.getAmount(destination) : 0;
            int capacity = destination.getItem() instanceof BBItem big
                    ? big.getCapacityMb() : FluidBucketItem.BUCKET_VOLUME_MB;
            int available = infiniteSource ? capacity : stored;
            int moved = Math.min(capacity - held, available)
                    / FluidBucketItem.BUCKET_VOLUME_MB * FluidBucketItem.BUCKET_VOLUME_MB;
            if (moved <= 0) return false;
            NBTUtil.setMilkAmount(destination, held + moved);
            if (!infiniteSource) NBTUtil.drainFiniteContent(source, moved);
        }
        level.playSound(player, player.blockPosition(), infiniteSink
                        ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(source.getItem()));
        return true;
    }

    private static boolean pourMilkToVanilla(Level level, Player player, ItemStack source,
                                              InteractionHand hand, ItemStack empties) {
        boolean infinite = source.getItem() instanceof SBItem;
        if (infinite && !SBPolicy.allowsMilk()) return false;
        int units = infinite ? empties.getCount()
                : Math.min(empties.getCount(), NBTUtil.getAmount(source) / FluidBucketItem.BUCKET_VOLUME_MB);
        units = Math.min(units, new ItemStack(Items.MILK_BUCKET).getMaxStackSize());
        if (units <= 0) return false;
        List<ItemStack> results = new ArrayList<>();
        for (int i = 0; i < units; i++) results.add(new ItemStack(Items.MILK_BUCKET));
        if (!infinite) NBTUtil.drainFiniteContent(source, units * FluidBucketItem.BUCKET_VOLUME_MB);
        settle(level, player, hand, empties, results, empties.getCount() - units);
        level.playSound(player, player.blockPosition(), SoundEvents.BUCKET_EMPTY,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(source.getItem()));
        return true;
    }

    private static boolean takeVanillaMilk(Level level, Player player, InteractionHand hand,
                                           ItemStack milkBuckets, ItemStack destination) {
        if (destination.getItem() instanceof SBItem && !SBPolicy.allowsMilk()) return false;
        NBTUtil.Mode mode = NBTUtil.getMode(destination);
        if (mode != NBTUtil.Mode.NONE && mode != NBTUtil.Mode.MILK) return false;
        boolean sink = destination.getItem() instanceof SBItem && mode == NBTUtil.Mode.MILK;
        int held = mode == NBTUtil.Mode.MILK ? NBTUtil.getAmount(destination) : 0;
        int capacity = destination.getItem() instanceof BBItem big
                ? big.getCapacityMb() : FluidBucketItem.BUCKET_VOLUME_MB;
        int units = Math.min(milkBuckets.getCount(), sink ? 1
                : (capacity - held) / FluidBucketItem.BUCKET_VOLUME_MB);
        if (units <= 0) return false;
        if (!sink) NBTUtil.setMilkAmount(destination,
                held + units * FluidBucketItem.BUCKET_VOLUME_MB);
        List<ItemStack> results = new ArrayList<>();
        for (int i = 0; i < units; i++) results.add(new ItemStack(Items.BUCKET));
        settle(level, player, hand, milkBuckets, results, milkBuckets.getCount() - units);
        level.playSound(player, player.blockPosition(), SoundEvents.BUCKET_FILL,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(destination.getItem()));
        return true;
    }

    private static void settle(Level level, Player player, InteractionHand hand, ItemStack original,
                               List<ItemStack> results, int untouched) {
        List<ItemStack> pile = new ArrayList<>();
        for (ItemStack result : results) {
            ItemStack match = pile.stream().filter(existing -> ItemStack.isSameItemSameTags(existing, result)
                    && existing.getCount() < existing.getMaxStackSize()).findFirst().orElse(null);
            if (match == null) pile.add(result.copy());
            else match.grow(result.getCount());
        }
        if (untouched > 0) {
            ItemStack rest = original.copy();
            rest.setCount(untouched);
            pile.add(rest);
        }
        int held = 0;
        for (int i = 0; i < pile.size(); i++) {
            if (pile.get(i).is(Items.MILK_BUCKET)) { held = i; break; }
        }
        player.setItemInHand(hand, pile.get(held));
        if (!level.isClientSide) {
            for (int i = 0; i < pile.size(); i++) if (i != held) player.drop(pile.get(i), false);
        }
    }
}
