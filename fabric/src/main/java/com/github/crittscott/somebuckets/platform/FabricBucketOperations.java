package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FabricBucketStorage;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.fluid.WorldFluidPickup;
import com.github.crittscott.somebuckets.interaction.HeldTransferSettlement;
import com.github.crittscott.somebuckets.interaction.MilkTransfers;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketStackState;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
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
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
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
        if (bucket.isEmpty() || other.isEmpty()) return false;
        if (tryMilkTransfer(level, player, bucketHand, bucket, otherHand, other)) return true;

        // Any fluid container that stacks while empty, vanilla or modded, is worked through one unit
        // at a time: a stack cannot hold a mix of filled and empty entries, so each unit is peeled
        // off and moved individually, and the results are piled back together afterward.
        ItemStack stackedOthers = ItemStack.EMPTY;
        if (!level.isClientSide && !player.getAbilities().instabuild && other.getCount() > 1) {
            stackedOthers = other;
            other = other.copy();
            other.setCount(1);
            player.setItemInHand(otherHand, other);
        }

        boolean infiniteSource = bucket.getItem() instanceof SBItem && NBTUtil.getMode(bucket) == NBTUtil.Mode.FLUID;
        int remaining = stackedOthers.isEmpty() ? 1 : stackedOthers.getCount();
        List<ItemStack> produced = new ArrayList<>();
        FluidVariant movedResource = null;
        Boolean fillsOther = null;
        boolean emptiedBucket = false;

        while (remaining > 0) {
            ContainerItemContext bucketContext = ContainerItemContext.forPlayerInteraction(player, bucketHand);
            ContainerItemContext otherContext = ContainerItemContext.forPlayerInteraction(player, otherHand);

            FluidVariant result;
            boolean thisFillsOther;
            if (fillsOther == null || fillsOther) {
                result = infiniteSource
                        ? moveInfiniteHeld(level, bucket, otherContext)
                        : moveHeld(level, bucketContext, otherContext);
                thisFillsOther = result != null;
                if (result == null && fillsOther == null) {
                    result = moveHeld(level, otherContext, bucketContext);
                    thisFillsOther = false;
                }
            } else {
                result = moveHeld(level, otherContext, bucketContext);
                thisFillsOther = false;
            }
            if (result == null) break;

            if (fillsOther == null) {
                fillsOther = thisFillsOther;
                emptiedBucket = thisFillsOther;
            }
            movedResource = result;
            produced.add(player.getItemInHand(otherHand).copy());
            remaining--;

            if (remaining > 0) {
                ItemStack next = stackedOthers.copy();
                next.setCount(1);
                player.setItemInHand(otherHand, next);
            }
        }

        if (produced.isEmpty()) {
            if (!stackedOthers.isEmpty()) player.setItemInHand(otherHand, stackedOthers);
            return false;
        }

        // Keep the shared Item.use return value and the context-updated hand on the same object.
        ItemStack updatedBucket = player.getItemInHand(bucketHand);
        if (updatedBucket.getItem() == bucket.getItem()) {
            BucketStackState.copy(updatedBucket, bucket);
            player.setItemInHand(bucketHand, bucket);
        }
        if (!stackedOthers.isEmpty()) {
            HeldTransferSettlement.settle(level, player, otherHand, stackedOthers, produced,
                    stackedOthers.getCount() - produced.size(), FabricBucketOperations::holdsFluid);
        }
        player.awardStat(Stats.ITEM_USED.get(bucket.getItem()));
        level.playSound(player, player.blockPosition(), emptiedBucket
                        ? FluidVariantAttributes.getEmptySound(movedResource)
                        : FluidVariantAttributes.getFillSound(movedResource),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** Whether an arbitrary produced or leftover stack still exposes extractable fluid content. */
    private static boolean holdsFluid(ItemStack stack) {
        ContainerItemContext context = ContainerItemContext.ofSingleSlot(
                InventoryStorage.of(new SimpleContainer(stack), null).getSlot(0));
        Storage<FluidVariant> storage = FluidStorage.ITEM.find(stack, context);
        return storage != null && StorageUtil.findExtractableResource(storage, null) != null;
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

    @Override
    public boolean carriesItemContainer(ItemStack stack) {
        return ContainerItemContext.withConstant(stack).find(ItemStorage.ITEM) != null;
    }

    @Nullable
    @Override
    public InteractionResultHolder<ItemStack> beforeWorldBucketUse(Player player, Level level,
                                                                   ItemStack stack, BlockHitResult hit) {
        // Forge-only seam: Fabric has no FillBucketEvent successor, so nothing claims the
        // interaction ahead of common processing. Same as NeoForge.
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
    public boolean takeAquaticSourceWater(Level level, BlockPos pos, StoredFluid expected,
                                          Player player) {
        return takeWorldFluid(level, pos, expected, player);
    }

    @Override
    public boolean placeAquaticSourceWater(Level level, BlockPos pos, ItemStack stack,
                                           ProtectionContext context, Direction face) {
        return FluidPlacement.emptyContents(level, context, stack, pos, face, false, Fluids.WATER);
    }

    @Override
    public boolean canAttemptBigTake(Level level, BlockHitResult hit, ItemStack stack) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block != null) return findOneBucket(block, bucketStorage(stack, false)) != null;
        StoredFluid available = worldFluid(level, hit.getBlockPos());
        return !available.isEmpty() && BBItem.canAcceptFluidUnit(stack, available);
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
        if (available.isEmpty() || !BBItem.canAcceptFluidUnit(stack, available)) {
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

        if (!FabricFluidPlacement.place(
                level, hit, stack, context, stored, allowFaceOffset)) return false;
        if (!level.isClientSide) {
            NBTUtil.drainFiniteContent(stack, FluidBucketItem.BUCKET_VOLUME_MB);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    @Override
    public BlockPos resolveBigPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                          Player player, InteractionHand hand,
                                          boolean allowFaceOffset) {
        if (blockStorage(level, hit) != null) return hit.getBlockPos();
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        return FabricFluidPlacement.resolveTarget(level, hit, stored, allowFaceOffset);
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
        if (!WorldFluidPickup.takeBlock(level, pos, context.player(),
                SoundEvents.BUCKET_FILL_POWDER_SNOW)) return false;
        if (!level.isClientSide) {
            int oldUnits = NBTUtil.getMode(stack) == NBTUtil.Mode.POWDER_SNOW
                    ? NBTUtil.getPowderUnits(stack) : 0;
            NBTUtil.setPowderUnits(stack, oldUnits + 1);
            completePickup(context.player(), stack);
        }
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
        BlockPlaceContext placement = powderContext(level, player, hand, stack, hit);
        if (!allowFaceOffset && !placement.replacingClickedOnBlock()) return false;
        BlockPos placePos = placement.getClickedPos();
        if (!Protections.mayAct(level, context,
                ProtectionAction.BLOCK_EDIT, placePos, hit.getDirection(), stack, null)) return false;
        if (!((BlockItem) Items.POWDER_SNOW_BUCKET).place(placement).consumesAction()) return false;
        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, NBTUtil.getPowderUnits(stack) - 1);
        }
        return true;
    }

    @Override
    public boolean trySourceTake(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                 InteractionHand hand) {
        return trySourceTakeWithContext(level, hit, stack, ProtectionContext.player(player, hand));
    }

    /**
     * Attempts to assign an empty Source Bucket or sink matching fluid using explicit identity.
     *
     * <p>Fabric block storage owns dispatch when present. Protection precedes mutation. Server
     * success consumes one source volume. An empty bucket records its identity; an assigned bucket
     * accepts only matching input and remains unchanged. The client only predicts acceptance.
     *
     * @return {@code true} for an accepted prediction or completed server intake
     */
    public boolean trySourceTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                             ProtectionContext context) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode != NBTUtil.Mode.NONE && mode != NBTUtil.Mode.FLUID) return false;
        boolean assigning = mode == NBTUtil.Mode.NONE;
        StoredFluid assigned = assigning ? StoredFluid.EMPTY : NBTUtil.getStoredFluid(stack);
        if (!assigning && (assigned.isEmpty() || !SBPolicy.allows(assigned.fluid()))) return false;
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block != null) return takeFromStorage(level, hit, stack, context, true, block);

        StoredFluid available = worldFluid(level, hit.getBlockPos());
        if (available.isEmpty() || !SBPolicy.allows(available.fluid())
                || !assigning && !available.fluid().isSame(assigned.fluid())) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, hit.getBlockPos(),
                hit.getDirection(), stack, null)) return false;
        if (!takeWorldFluid(level, hit.getBlockPos(), available, context.player())) return false;
        if (!level.isClientSide) {
            if (assigning) {
                NBTUtil.setStoredFluid(stack, available.withAmount(FluidBucketItem.BUCKET_VOLUME_MB));
                completePickup(context.player(), stack);
            } else if (context.player() != null) {
                context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            }
        }
        return true;
    }

    @Override
    public SourceTarget classifySourceTarget(Level level, BlockHitResult hit, ItemStack stack) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.FLUID) return SourceTarget.BLOCKING_FLUID;
        StoredFluid assigned = NBTUtil.getStoredFluid(stack);
        if (assigned.isEmpty() || !SBPolicy.allows(assigned.fluid())) {
            return SourceTarget.BLOCKING_FLUID;
        }

        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block != null) {
            FluidVariant expected = variant(assigned);
            if (canMoveExactly(block, bucketStorage(stack, true), expected)) {
                return SourceTarget.MATCHING_FLUID;
            }
            for (StorageView<FluidVariant> view : block.nonEmptyViews()) {
                if (!view.isResourceBlank() && view.getAmount() > 0) {
                    return SourceTarget.BLOCKING_FLUID;
                }
            }
            return SourceTarget.NO_FLUID;
        }

        BlockState state = level.getBlockState(hit.getBlockPos());
        if (state.is(Blocks.WATER_CAULDRON)) {
            boolean full = state.getValue(LayeredCauldronBlock.LEVEL)
                    == LayeredCauldronBlock.MAX_FILL_LEVEL;
            return full && assigned.fluid().isSame(Fluids.WATER)
                    ? SourceTarget.MATCHING_FLUID : SourceTarget.BLOCKING_FLUID;
        }
        if (state.is(Blocks.LAVA_CAULDRON)) {
            return assigned.fluid().isSame(Fluids.LAVA)
                    ? SourceTarget.MATCHING_FLUID : SourceTarget.BLOCKING_FLUID;
        }
        if (!state.getFluidState().isEmpty()) {
            StoredFluid available = worldFluid(level, hit.getBlockPos());
            return !available.isEmpty() && available.fluid().isSame(assigned.fluid())
                    ? SourceTarget.MATCHING_FLUID : SourceTarget.BLOCKING_FLUID;
        }
        return SourceTarget.NO_FLUID;
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
        if (block != null) {
            return placeIntoStorage(level, hit, stack, context, true, block)
                    || placeOntoFullCauldron(level, hit, stack, context, stored);
        }

        if (!FabricFluidPlacement.place(
                level, hit, stack, context, stored, allowFaceOffset)) return false;
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
    public boolean trySourceMilk(ServerLevel level, BlockPos front,
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

    @Override
    public BlockPos resolveSourcePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                             Player player, InteractionHand hand,
                                             boolean allowFaceOffset) {
        if (blockStorage(level, hit) != null) return hit.getBlockPos();
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        return FabricFluidPlacement.resolveTarget(level, hit, stored, allowFaceOffset);
    }

    @Nullable
    private static Storage<FluidVariant> blockStorage(Level level, BlockHitResult hit) {
        return FluidStorage.SIDED.find(level, hit.getBlockPos(), hit.getDirection());
    }

    private static boolean takeFromStorage(Level level, BlockHitResult hit, ItemStack stack,
                                           ProtectionContext context, boolean source,
                                           Storage<FluidVariant> block) {
        Storage<FluidVariant> bucket = bucketStorage(stack, source);
        FluidVariant available = findOneBucket(block, bucket);
        if (available == null) return false;
        if (!Protections.mayAct(level, context,
                ProtectionAction.BLOCK_INTERACT, hit.getBlockPos(), hit.getDirection(), stack, null)) {
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
                                            ProtectionContext context, boolean source,
                                            Storage<FluidVariant> block) {
        StoredFluid fluid = NBTUtil.getStoredFluid(stack);
        FluidVariant available = variant(fluid);
        Storage<FluidVariant> bucket = bucketStorage(stack, source);
        if (!canMoveExactly(bucket, block, available)) return false;
        if (!Protections.mayAct(level, context,
                ProtectionAction.BLOCK_INTERACT, hit.getBlockPos(), hit.getDirection(), stack, null)) {
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

    /**
     * A full cauldron of the assigned fluid accepts nothing, but a normal place gesture still
     * reports success with the empty sound, matching placement onto an existing source block.
     */
    private static boolean placeOntoFullCauldron(Level level, BlockHitResult hit, ItemStack stack,
                                                 ProtectionContext context, StoredFluid stored) {
        BlockState state = level.getBlockState(hit.getBlockPos());
        boolean matching = state.is(Blocks.WATER_CAULDRON)
                ? stored.fluid().isSame(Fluids.WATER)
                        && state.getValue(LayeredCauldronBlock.LEVEL)
                                == LayeredCauldronBlock.MAX_FILL_LEVEL
                : state.is(Blocks.LAVA_CAULDRON) && stored.fluid().isSame(Fluids.LAVA);
        if (!matching) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, hit.getBlockPos(),
                hit.getDirection(), stack, null)) return false;
        if (!level.isClientSide) {
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, hit.getBlockPos());
            if (context.player() != null) {
                context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            }
        }
        play(level, hit.getBlockPos(), FluidVariantAttributes.getEmptySound(variant(stored)));
        return true;
    }

    @Nullable
    private static FluidVariant findOneBucket(Storage<FluidVariant> from,
                                              Storage<FluidVariant> to) {
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

    private static Storage<FluidVariant> bucketStorage(ItemStack stack, boolean source) {
        return source ? FabricBucketStorage.source(stack)
                : FabricBucketStorage.finite(stack, (BBItem) stack.getItem());
    }

    private static void creditFinite(ItemStack stack, StoredFluid incoming, int amountMb) {
        StoredFluid current = NBTUtil.getStoredFluid(stack);
        NBTUtil.setStoredFluid(stack, incoming.withAmount(current.amount() + amountMb));
    }

    private static StoredFluid worldFluid(Level level, BlockPos pos) {
        return WorldFluidPickup.sourceAt(level, pos);
    }

    private static boolean takeWorldFluid(Level level, BlockPos pos, StoredFluid expected,
                                          Player player) {
        return WorldFluidPickup.take(level, pos, expected, player,
                FluidVariantAttributes.getFillSound(variant(expected)));
    }

    private static BlockPlaceContext powderContext(Level level, @Nullable Player player, InteractionHand hand,
                                                   ItemStack stack, BlockHitResult hit) {
        ItemStack placementStack = stack.copy();
        placementStack.setCount(1);
        return new BlockPlaceContext(level, player, hand, placementStack, hit);
    }

    private static FluidVariant variant(StoredFluid fluid) {
        return FabricFluidVariants.toVariant(fluid);
    }

    private static void completePickup(@Nullable Player player, ItemStack stack) {
        if (player == null) return;
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, stack);
        }
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound) {
        if (!level.isClientSide) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /* Milk has no Fabric FluidVariant, so it goes through the shared loader-neutral milk rules. */
    private static boolean tryMilkTransfer(Level level, Player player,
                                           InteractionHand bucketHand, ItemStack bucket,
                                           InteractionHand otherHand, ItemStack other) {
        if (NBTUtil.getMode(bucket) == NBTUtil.Mode.MILK) {
            if (other.getItem() instanceof FluidBucketItem || other.is(Items.BUCKET)) {
                return MilkTransfers.pourMilk(level, player, bucket, otherHand, other);
            }
        }
        if (other.getItem() instanceof FluidBucketItem && NBTUtil.getMode(other) == NBTUtil.Mode.MILK) {
            return MilkTransfers.pourMilk(level, player, other, bucketHand, bucket);
        }
        if (other.is(Items.MILK_BUCKET)) return MilkTransfers.takeMilk(level, player, otherHand, other, bucket);
        return false;
    }
}
