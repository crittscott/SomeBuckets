package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FabricBucketStorage;
import com.github.crittscott.somebuckets.fluid.FabricFluidPlacement;
import com.github.crittscott.somebuckets.fluid.FabricFluidVariants;
import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.fluid.WorldFluidPickup;
import com.github.crittscott.somebuckets.interaction.HeldTransferSettlement;
import com.github.crittscott.somebuckets.interaction.MilkTransfers;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketStackState;
import com.github.crittscott.somebuckets.util.BucketState;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Fabric Transfer API implementation of the shared bucket fluid primitives. */
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

        boolean infiniteSource = bucket.getItem() instanceof SBItem && BucketState.getMode(bucket) == BucketState.Mode.FLUID;
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
        StoredFluid stored = BucketState.getStoredFluid(source);
        if (stored.isEmpty() || !SBPolicy.allows(stored.fluid())) return null;
        FluidVariant resource = variant(stored);
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

    @Override
    public boolean firesWorldBucketEvent() {
        return false;
    }

    @Nullable
    @Override
    public InteractionResultHolder<ItemStack> beforeWorldBucketUse(Player player, Level level,
                                                                   ItemStack stack, BlockHitResult hit) {
        // Forge-only seam: Fabric has no FillBucketEvent successor. Same as NeoForge.
        return null;
    }

    @Override
    public Component fluidDisplayName(StoredFluid fluid) {
        return FluidVariantAttributes.getName(variant(fluid));
    }

    @Override
    public int fluidColor(StoredFluid fluid, int fallback) {
        return FabricFluidColors.color(fluid, fallback);
    }

    @Override
    public SoundEvent fillSound(StoredFluid fluid) {
        return FluidVariantAttributes.getFillSound(variant(fluid));
    }

    @Override
    public SoundEvent emptySound(StoredFluid fluid) {
        return FluidVariantAttributes.getEmptySound(variant(fluid));
    }

    @Override
    public boolean takeAquaticSourceWater(Level level, BlockPos pos, StoredFluid expected, Player player) {
        return WorldFluidPickup.take(level, pos, expected, player,
                FluidVariantAttributes.getFillSound(variant(expected)));
    }

    @Override
    public boolean placeAquaticSourceWater(Level level, BlockPos pos, ItemStack stack,
                                           ProtectionContext context, Direction face) {
        return FluidPlacement.emptyContents(level, context, stack, pos, face, false, Fluids.WATER);
    }

    @Override
    public BlockFluidOutcome previewBlockTake(Level level, BlockHitResult hit, ItemStack stack) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block == null) return BlockFluidOutcome.NO_STORE;
        return findOneBucket(block, bucketStorage(stack, false)) != null
                ? BlockFluidOutcome.SUCCESS : BlockFluidOutcome.REFUSED;
    }

    @Override
    public BlockFluidOutcome blockTake(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, boolean asSource) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block == null) return BlockFluidOutcome.NO_STORE;
        return takeFromStorage(level, hit, stack, context, asSource, block)
                ? BlockFluidOutcome.SUCCESS : BlockFluidOutcome.REFUSED;
    }

    @Override
    public BlockFluidOutcome blockPlace(Level level, BlockHitResult hit, ItemStack stack,
                                        ProtectionContext context, boolean asSource) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block == null) return BlockFluidOutcome.NO_STORE;
        return placeIntoStorage(level, hit, stack, context, asSource, block)
                ? BlockFluidOutcome.SUCCESS : BlockFluidOutcome.REFUSED;
    }

    @Nullable
    @Override
    public SourceTarget classifyBlockTarget(Level level, BlockHitResult hit, ItemStack stack) {
        Storage<FluidVariant> block = blockStorage(level, hit);
        if (block == null) return null;
        FluidVariant expected = variant(BucketState.getStoredFluid(stack));
        if (canMoveExactly(block, bucketStorage(stack, true), expected)) {
            return SourceTarget.MATCHING_FLUID;
        }
        for (StorageView<FluidVariant> view : block.nonEmptyViews()) {
            if (!view.isResourceBlank() && view.getAmount() > 0) return SourceTarget.BLOCKING_FLUID;
        }
        return SourceTarget.NO_FLUID;
    }

    @Override
    public boolean cauldronTake(Level level, BlockPos pos, Direction face, ItemStack stack, Fluid fluid,
                                ProtectionContext context) {
        // Fabric exposes vanilla water/lava cauldrons as sided fluid storage, so cauldron takes are
        // served by blockTake; nothing reaches here.
        return false;
    }

    @Override
    public boolean cauldronPlace(Level level, BlockPos pos, Direction face, ItemStack stack, Fluid fluid,
                                 ProtectionContext context) {
        // Reached only for a full matching cauldron (an empty one is served by blockPlace). A normal
        // place gesture still reports success with the empty sound, matching placement onto a source.
        BlockState state = level.getBlockState(pos);
        boolean matching = fluid == Fluids.WATER
                ? state.is(Blocks.WATER_CAULDRON)
                        && state.getValue(LayeredCauldronBlock.LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL
                : fluid == Fluids.LAVA && state.is(Blocks.LAVA_CAULDRON);
        if (!matching) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos, face, stack, null)) return false;
        if (!level.isClientSide) {
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, pos);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        play(level, pos, FluidVariantAttributes.getEmptySound(variant(BucketState.getStoredFluid(stack))));
        return true;
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
                                                @Nullable Player player, InteractionHand hand,
                                                StoredFluid stored, boolean allowFaceOffset) {
        return FabricFluidPlacement.resolveTarget(level, hit, stored, allowFaceOffset);
    }

    @Override
    public boolean placeStoredPowder(Level level, BlockHitResult hit, ItemStack stack,
                                     ProtectionContext context, boolean allowFaceOffset) {
        int units = BucketState.getPowderUnits(stack);
        ItemStack placementStack = stack.copy();
        placementStack.setCount(1);
        Player player = context.player();
        InteractionHand hand = player == null ? InteractionHand.MAIN_HAND : context.hand();
        BlockPlaceContext placement = new BlockPlaceContext(level, player, hand, placementStack, hit);
        if (!allowFaceOffset && !placement.replacingClickedOnBlock()) return false;

        BlockPos placePos = placement.getClickedPos();
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, placePos,
                hit.getDirection(), stack, null)) return false;

        if (!((BlockItem) Items.POWDER_SNOW_BUCKET).place(placement).consumesAction()) return false;
        if (!level.isClientSide) BucketState.setPowderUnits(stack, units - 1);
        return true;
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
        FluidVariant available = variant(BucketState.getStoredFluid(stack));
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

    private static Storage<FluidVariant> bucketStorage(ItemStack stack, boolean source) {
        return source ? FabricBucketStorage.source(stack)
                : FabricBucketStorage.finite(stack, (com.github.crittscott.somebuckets.item.BBItem) stack.getItem());
    }

    private static FluidVariant variant(StoredFluid fluid) {
        return FabricFluidVariants.toVariant(fluid);
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
        if (BucketState.getMode(bucket) == BucketState.Mode.MILK) {
            if (other.getItem() instanceof FluidBucketItem || other.is(Items.BUCKET)) {
                return MilkTransfers.pourMilk(level, player, bucket, otherHand, other);
            }
        }
        if (other.getItem() instanceof FluidBucketItem && BucketState.getMode(other) == BucketState.Mode.MILK) {
            return MilkTransfers.pourMilk(level, player, other, bucketHand, bucket);
        }
        if (other.is(Items.MILK_BUCKET)) return MilkTransfers.takeMilk(level, player, otherHand, other, bucket);
        return false;
    }
}
