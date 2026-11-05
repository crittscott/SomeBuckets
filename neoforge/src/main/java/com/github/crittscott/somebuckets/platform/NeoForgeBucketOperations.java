package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.client.SidedFluidColors;
import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.fluid.NeoForgeFluidPlacement;
import com.github.crittscott.somebuckets.fluid.WorldFluidPickup;
import com.github.crittscott.somebuckets.interaction.BlockFluidTransfers;
import com.github.crittscott.somebuckets.interaction.BucketSounds;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** NeoForge fluid primitives behind the shared bucket interaction flow. */
public final class NeoForgeBucketOperations implements BucketOperations {
    @Override
    public boolean tryHeldTransfer(Level level, Player player, InteractionHand bucketHand, ItemStack bucket,
                                   InteractionHand otherHand, ItemStack other) {
        return Transfers.tryTransferEither(level, player, bucketHand, bucket, otherHand, other);
    }

    @Override
    public boolean hasBlockStorage(Level level, BlockPos pos, Direction face) {
        return BlockFluidTransfers.hasBlockHandler(level, pos, face);
    }

    @Override
    public boolean carriesItemContainer(ItemStack stack) {
        return stack.getCapability(Capabilities.ItemHandler.ITEM) != null;
    }

    @Override
    public boolean firesWorldBucketEvent() {
        return false;
    }

    /**
     * Records a real player's placement with NeoForge's block-snapshot capture and posts
     * {@code EntityPlaceEvent} for it, as {@code CommonHooks.onPlaceItemIntoWorld} does around
     * {@code useOn}: a refusal restores the recorded blocks, and an accepted placement then runs the
     * block placement and neighbor updates that capture deferred. Capture the loader armed around an
     * enclosing {@code useOn} is suspended with its snapshots set aside, so that hook sees none of
     * this placement.
     */
    private static boolean placeChecked(Level level, ProtectionContext context, Direction face,
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
                    ? EventHooks.onMultiBlockPlace(player, snapshots, face)
                    : snapshots.size() == 1 && EventHooks.onBlockPlace(player, snapshots.getFirst(), face));
            if (refused) {
                for (BlockSnapshot snapshot : snapshots.reversed()) {
                    boolean restoring = level.restoringBlockSnapshots;
                    level.restoringBlockSnapshots = true;
                    try {
                        snapshot.restore(snapshot.getFlags() | Block.UPDATE_CLIENTS);
                    } finally {
                        level.restoringBlockSnapshots = restoring;
                    }
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
    public boolean placeFluidChecked(Level level, ProtectionContext context, BlockPos pos,
                                     Direction face, Fluid fluid, BooleanSupplier place) {
        if (level.isClientSide || !(context.player() instanceof ServerPlayer)) return place.getAsBoolean();
        return FluidPlacement.placeFluidChecked(level, context, pos, fluid,
                placement -> placeChecked(level, context, face, placement), place);
    }

    @Override
    public boolean permitsBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled();
    }

    @Override
    public boolean allowsItemPickup(ItemEntity item, ServerPlayer player) {
        return !NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(player, item)).canPickup().isFalse();
    }

    @Override
    public InteractionResultHolder<ItemStack> beforeWorldBucketUse(Player player, Level level,
                                                                   ItemStack stack, BlockHitResult hit) {
        // NeoForge 1.21.1 exposes no pre-dispatch bucket-use event (Forge's FillBucketEvent has no
        // successor here); nothing claims the interaction ahead of common processing, as on Fabric.
        return null;
    }

    @Override
    public Component fluidDisplayName(StoredFluid fluid) {
        return NeoForgeFluidStacks.of(fluid.fluid(), fluid.amount(), fluid.variantTag()).getHoverName();
    }

    @Override
    public int fluidColor(StoredFluid fluid, int fallback) {
        return SidedFluidColors.getColorRgb(
                NeoForgeFluidStacks.of(fluid.fluid(), fluid.amount(), fluid.variantTag()), fallback);
    }

    @Override
    public SoundEvent fillSound(StoredFluid fluid) {
        return BucketSounds.resolveFillSound(fluid.fluid());
    }

    @Override
    public SoundEvent emptySound(StoredFluid fluid) {
        return BucketSounds.resolveEmptySound(fluid.fluid());
    }

    @Override
    public boolean takeAquaticSourceWater(Level level, BlockPos pos, StoredFluid expected, Player player) {
        StoredFluid available = WorldFluidPickup.sourceAt(level, pos);
        return !available.isEmpty() && available.fluid().isSame(expected.fluid())
                && WorldFluidPickup.take(level, pos, available, player,
                        BucketSounds.resolveFillSound(available.fluid()));
    }

    @Override
    public boolean placeAquaticSourceWater(Level level, BlockPos pos, ItemStack stack,
                                           ProtectionContext context, Direction face) {
        return FluidPlacement.emptyContents(level, context, stack, pos, face, false, Fluids.WATER);
    }

    @Override
    public BlockFluidOutcome previewBlockTake(Level level, BlockHitResult hit, ItemStack stack) {
        IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);
        return map(BlockFluidTransfers.previewTakeFromBlock(
                level, hit.getBlockPos(), hit.getDirection(), handler));
    }

    @Override
    public BlockFluidOutcome blockTake(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, boolean asSource) {
        IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);
        return map(BlockFluidTransfers.tryTakeFromBlock(
                level, hit.getBlockPos(), hit.getDirection(), stack, handler, context));
    }

    @Override
    public BlockFluidOutcome blockPlace(Level level, BlockHitResult hit, ItemStack stack,
                                        ProtectionContext context, boolean asSource) {
        IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);
        return map(BlockFluidTransfers.tryPlaceIntoBlock(
                level, hit.getBlockPos(), hit.getDirection(), stack, handler, context));
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
    public boolean cauldronTake(Level level, BlockPos pos, Direction face, ItemStack stack, Fluid fluid,
                                ProtectionContext context) {
        if (fluid == Fluids.WATER) return Cauldrons.takeWater(level, pos, face, stack, context);
        if (fluid == Fluids.LAVA) return Cauldrons.takeLava(level, pos, face, stack, context);
        return false;
    }

    @Override
    public boolean cauldronPlace(Level level, BlockPos pos, Direction face, ItemStack stack, Fluid fluid,
                                 ProtectionContext context) {
        if (level.getBlockState(pos).is(Blocks.CAULDRON)) {
            if (fluid == Fluids.WATER) return Cauldrons.placeWater(level, pos, face, stack, context);
            if (fluid == Fluids.LAVA) return Cauldrons.placeLava(level, pos, face, stack, context);
            return false;
        }
        return Cauldrons.placeOntoFullCauldron(level, pos, face, stack, fluid, context);
    }

    @Override
    public boolean placeArbitraryFluid(Level level, BlockHitResult hit, ItemStack stack,
                                       ProtectionContext context, StoredFluid stored, boolean asSource,
                                       boolean allowFaceOffset) {
        IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);
        return NeoForgeFluidPlacement.place(level, hit, stack, handler, context,
                NeoForgeFluidStacks.of(stored.fluid(), stored.amount(), stored.variantTag()), allowFaceOffset);
    }

    @Override
    public BlockPos resolveArbitraryPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                @Nullable Player player, InteractionHand hand,
                                                StoredFluid stored, boolean allowFaceOffset) {
        return NeoForgeFluidPlacement.resolveTarget(level, hit, stack, player, hand,
                NeoForgeFluidStacks.of(stored.fluid(), stored.amount(), stored.variantTag()), allowFaceOffset);
    }

    @Override
    public boolean placeStoredPowder(Level level, BlockHitResult hit, ItemStack stack,
                                     ProtectionContext context, boolean allowFaceOffset) {
        int currentUnits = BucketState.getPowderUnits(stack);
        ItemStack placementStack = stack.copy();
        placementStack.setCount(1);
        Player player = context.player();
        InteractionHand hand = player == null ? InteractionHand.MAIN_HAND : context.hand();
        BlockPlaceContext placement = new BlockPlaceContext(level, player, hand, placementStack, hit);
        if (!allowFaceOffset && !placement.replacingClickedOnBlock()) return false;

        BlockPos placePos = placement.getClickedPos();
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, placePos,
                hit.getDirection(), stack, null)) return false;

        if (!placeChecked(level, context, hit.getDirection(),
                () -> ((BlockItem) Items.POWDER_SNOW_BUCKET).place(placement).consumesAction())) return false;
        if (!level.isClientSide) BucketState.setPowderUnits(stack, currentUnits - 1);
        return true;
    }

    private static BlockFluidOutcome map(BlockFluidTransfers.BlockTransferResult result) {
        return switch (result) {
            case NO_HANDLER -> BlockFluidOutcome.NO_STORE;
            case REFUSED -> BlockFluidOutcome.REFUSED;
            case SUCCESS -> BlockFluidOutcome.SUCCESS;
        };
    }
}
