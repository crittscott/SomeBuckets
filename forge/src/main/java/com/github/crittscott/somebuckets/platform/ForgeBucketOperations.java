package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.client.SidedFluidColors;
import com.github.crittscott.somebuckets.fluid.ForgeFluidPlacement;
import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.fluid.WorldFluidPickup;
import com.github.crittscott.somebuckets.interaction.BlockFluidTransfers;
import com.github.crittscott.somebuckets.interaction.BucketSounds;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;

/** Forge fluid primitives behind the shared bucket interaction flow. */
public final class ForgeBucketOperations implements BucketOperations {
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
        return stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    @Override
    public boolean firesWorldBucketEvent() {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> beforeWorldBucketUse(Player player, Level level,
                                                                   ItemStack stack, BlockHitResult hit) {
        return ForgeEventFactory.onBucketUse(player, level, stack, hit);
    }

    @Override
    public Component fluidDisplayName(StoredFluid fluid) {
        return ForgeFluidStacks.of(fluid.fluid(), fluid.amount(), fluid.variantTag()).getDisplayName();
    }

    @Override
    public int fluidColor(StoredFluid fluid, int fallback) {
        return SidedFluidColors.getColorRgb(
                ForgeFluidStacks.of(fluid.fluid(), fluid.amount(), fluid.variantTag()), fallback);
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
        return ForgeFluidPlacement.place(level, hit, stack, handler, context,
                ForgeFluidStacks.of(stored.fluid(), stored.amount(), stored.variantTag()), allowFaceOffset);
    }

    @Override
    public BlockPos resolveArbitraryPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                                @Nullable Player player, InteractionHand hand,
                                                StoredFluid stored, boolean allowFaceOffset) {
        return ForgeFluidPlacement.resolveTarget(level, hit, stack, player, hand,
                ForgeFluidStacks.of(stored.fluid(), stored.amount(), stored.variantTag()), allowFaceOffset);
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

    private static BlockFluidOutcome map(BlockFluidTransfers.BlockTransferResult result) {
        return switch (result) {
            case NO_HANDLER -> BlockFluidOutcome.NO_STORE;
            case REFUSED -> BlockFluidOutcome.REFUSED;
            case SUCCESS -> BlockFluidOutcome.SUCCESS;
        };
    }
}
