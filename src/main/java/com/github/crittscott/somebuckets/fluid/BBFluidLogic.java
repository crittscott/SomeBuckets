package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;

public class BBFluidLogic {
    private static final BBFluidLogic INSTANCE = new BBFluidLogic();

    private BBFluidLogic() {}

    public static BBFluidLogic getInstance() {
        return INSTANCE;
    }

    public boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        return tryTakeWithContext(level, hit, stack, player == null
                ? ProtectionContext.unownedAutomation()
                : ProtectionContext.player(player, stack));
    }

    /**
     * The block-entity fluid-handler capability at {@code pos}/{@code face} that {@code stack} could
     * transfer with, or null if the block exposes none or the stack itself exposes no fluid-handler
     * capability. Presence alone decides dispatch (see {@link #tryTakeWithContext}/{@link #tryPlace}):
     * whether a transfer would actually move anything is a separate, later question.
     */
    @Nullable
    private static IFluidHandler compatibleBlockCapability(Level level, BlockPos pos, net.minecraft.core.Direction face,
                                                            ItemStack stack) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        IFluidHandler blockHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
        if (blockHandler == null) return null;
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent() ? blockHandler : null;
    }

    /**
     * Whether {@link #tryTakeWithContext} would attempt a take at {@code hit}: a compatible block
     * capability transfer, or a world pickup compatible with the stack's current mode and capacity.
     * Read-only: does not check protection or touch the world. Used to pick the correct
     * {@code FillBucketEvent} target before dispatch; the real attempt re-derives this independently.
     */
    public static boolean canAttemptTakeAt(Level level, BlockHitResult hit, ItemStack stack) {
        BlockPos pos = hit.getBlockPos();
        IFluidHandler blockHandler = compatibleBlockCapability(level, pos, hit.getDirection(), stack);
        if (blockHandler != null) {
            IFluidHandlerItem itemHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            FluidStack drained = blockHandler.drain(1000, IFluidHandler.FluidAction.SIMULATE);
            return itemHandler != null && !drained.isEmpty()
                    && itemHandler.fill(drained, IFluidHandler.FluidAction.SIMULATE) >= 1000;
        }

        FluidStack available = FluidPickup.available(level, pos);
        if (available.isEmpty()) return false;

        int capMb = ((BBItem) stack.getItem()).getCapacityMb();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        FluidStack current = NBTUtil.getFluidStack(stack);
        return mode == NBTUtil.Mode.NONE ||
                (mode == NBTUtil.Mode.FLUID && (current.isEmpty() ||
                        (current.isFluidEqual(available) && current.getAmount() + 1000 <= capMb)));
    }

    /**
     * The position {@link #tryPlace} would actually act on: the clicked block if it exposes a
     * compatible fluid-handler capability, otherwise wherever {@link FluidPlacement#resolveTarget}
     * resolves for generic world placement (which may fall through to the neighbor along the clicked
     * face). Read-only: does not check protection or touch the world. Used to pick the correct
     * {@code FillBucketEvent} target before dispatch.
     */
    public static BlockPos resolvePlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                              boolean allowFaceOffset) {
        BlockPos clickedPos = hit.getBlockPos();
        if (compatibleBlockCapability(level, clickedPos, hit.getDirection(), stack) != null) return clickedPos;
        FluidStack fluidStack = NBTUtil.getFluidStack(stack);
        return FluidPlacement.resolveTarget(level, clickedPos, hit.getDirection(), allowFaceOffset,
                fluidStack.getFluid());
    }

    public boolean tryTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                      ProtectionContext context) {
        if (!canAttemptTakeAt(level, hit, stack)) return false;

        BlockPos pos = hit.getBlockPos();

        // First try block entity capability
        IFluidHandler blockHandler = compatibleBlockCapability(level, pos, hit.getDirection(), stack);
        if (blockHandler != null) {
            IFluidHandlerItem itemHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            return tryTransferFromBlock(level, pos, hit.getDirection(), blockHandler, itemHandler,
                    context, stack);
        }

        // Fall back to the world block's own pickup contract
        FluidStack available = FluidPickup.available(level, pos);
        if (available.isEmpty()) return false;

        int capMb = ((BBItem) stack.getItem()).getCapacityMb();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        FluidStack current = NBTUtil.getFluidStack(stack);

        boolean canTake = mode == NBTUtil.Mode.NONE ||
                (mode == NBTUtil.Mode.FLUID && (current.isEmpty() ||
                        (current.isFluidEqual(available) && current.getAmount() + 1000 <= capMb)));
        if (!canTake) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        FluidStack taken = FluidPickup.take(level, pos, available, context.player());
        if (taken.isEmpty()) return false;

        if (!level.isClientSide) {
            boolean merging = mode == NBTUtil.Mode.FLUID && !current.isEmpty();
            NBTUtil.setFluidStack(stack, merging
                    ? new FluidStack(current.getFluid(), current.getAmount() + 1000, current.getTag())
                    : new FluidStack(taken.getFluid(), 1000, taken.getTag()));
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            awardPickup(context, stack);
        }
        return true;
    }

    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        return tryPlace(level, hit, stack, player == null
                ? ProtectionContext.unownedAutomation()
                : ProtectionContext.player(player, stack), true);
    }

    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                            boolean allowFaceOffset) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.FLUID) return false;

        FluidStack fluidStack = NBTUtil.getFluidStack(stack);
        if (fluidStack.isEmpty() || fluidStack.getAmount() < 1000) return false;

        BlockPos clickedPos = hit.getBlockPos();

        // First try block entity capability
        IFluidHandler blockHandler = compatibleBlockCapability(level, clickedPos, hit.getDirection(), stack);
        if (blockHandler != null) {
            IFluidHandlerItem itemHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            return tryTransferToBlock(level, clickedPos, hit.getDirection(), blockHandler, itemHandler,
                    context, stack);
        }

        // Fall back to world placement
        return tryPlaceInWorld(level, hit, stack, context, fluidStack, allowFaceOffset);
    }

    private boolean tryTransferFromBlock(Level level, BlockPos pos, net.minecraft.core.Direction face,
                                         IFluidHandler blockHandler, IFluidHandlerItem itemHandler,
                                         ProtectionContext context, ItemStack stack) {
        // Try to drain 1000mB from the block into our item
        FluidStack drained = blockHandler.drain(1000, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty()) return false;

        int filled = itemHandler.fill(drained, IFluidHandler.FluidAction.SIMULATE);
        if (filled < 1000) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face, stack, null)) {
            return false;
        }

        if (!level.isClientSide) {
            blockHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            itemHandler.fill(new FluidStack(drained.getFluid(), 1000, drained.getTag()), IFluidHandler.FluidAction.EXECUTE);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }

        level.playSound(context.player(), pos, fillSound(drained.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private boolean tryTransferToBlock(Level level, BlockPos pos, net.minecraft.core.Direction face,
                                       IFluidHandler blockHandler, IFluidHandlerItem itemHandler,
                                       ProtectionContext context, ItemStack stack) {
        // Try to fill 1000mB from our item into the block
        FluidStack current = itemHandler.getFluidInTank(0);
        if (current.isEmpty() || current.getAmount() < 1000) return false;

        FluidStack toTransfer = new FluidStack(current.getFluid(), 1000, current.getTag());
        int filled = blockHandler.fill(toTransfer, IFluidHandler.FluidAction.SIMULATE);
        if (filled < 1000) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face, stack, null)) {
            return false;
        }

        if (!level.isClientSide) {
            blockHandler.fill(toTransfer, IFluidHandler.FluidAction.EXECUTE);
            itemHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }

        level.playSound(context.player(), pos, emptySound(current.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private static SoundEvent fillSound(Fluid fluid) {
        SoundEvent sound = fluid.getFluidType().getSound(SoundActions.BUCKET_FILL);
        if (sound != null) return sound;
        return fluid.defaultFluidState().is(FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL;
    }

    private static SoundEvent emptySound(Fluid fluid) {
        SoundEvent sound = fluid.getFluidType().getSound(SoundActions.BUCKET_EMPTY);
        if (sound != null) return sound;
        return fluid.defaultFluidState().is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
    }

    private boolean tryPlaceInWorld(Level level, BlockHitResult hit, ItemStack stack,
                                    ProtectionContext context, FluidStack fluidStack,
                                    boolean allowFaceOffset) {
        if (!FluidPlacement.emptyContents(level, context, stack, hit.getBlockPos(), hit,
                fluidStack.getFluid(), allowFaceOffset)) return false;

        if (!level.isClientSide) {
            NBTUtil.drainFluid(stack, 1000);
            NBTUtil.normalizeEmptyState(stack);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    public boolean tryTakePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        return tryTakePowderWithContext(level, hit, stack, player == null
                ? ProtectionContext.unownedAutomation()
                : ProtectionContext.player(player, stack));
    }

    /**
     * Whether {@link #tryTakePowderWithContext} would attempt a take at {@code hit}: the block is
     * powder snow and the stack's mode/capacity accepts another unit. Read-only: does not check
     * protection or touch the world. Used to pick the correct {@code FillBucketEvent} target before
     * dispatch; the real attempt re-derives this independently.
     */
    public static boolean canAttemptTakePowderAt(Level level, BlockHitResult hit, ItemStack stack) {
        if (!level.getBlockState(hit.getBlockPos()).is(Blocks.POWDER_SNOW)) return false;
        int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int units = NBTUtil.getPowderUnits(stack);
        return mode == NBTUtil.Mode.NONE || (mode == NBTUtil.Mode.POWDER_SNOW && units < capUnits);
    }

    public boolean tryTakePowderWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                            ProtectionContext context) {
        if (!canAttemptTakePowderAt(level, hit, stack)) return false;

        BlockPos pos = hit.getBlockPos();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int units = NBTUtil.getPowderUnits(stack);
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, (mode == NBTUtil.Mode.POWDER_SNOW ? units : 0) + 1);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            awardPickup(context, stack);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_FILL_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(context.player(), GameEvent.FLUID_PICKUP, pos);
        return true;
    }

    public boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        return tryPlacePowder(level, hit, stack, player == null
                ? ProtectionContext.unownedAutomation()
                : ProtectionContext.player(player, stack), true);
    }

    /**
     * The position powder snow would actually be placed at: {@code hit}'s clicked block if it's
     * replaceable or face offset isn't allowed, otherwise the neighbor along the clicked face.
     * Read-only: does not check protection, world acceptance at the resolved position, or touch the
     * world. Used to pick the correct {@code FillBucketEvent} target before dispatch.
     */
    public static BlockPos resolvePowderPlaceTarget(Level level, BlockHitResult hit, boolean allowFaceOffset) {
        BlockPos clickedPos = hit.getBlockPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        return clickedState.canBeReplaced() || !allowFaceOffset
                ? clickedPos : clickedPos.relative(hit.getDirection());
    }

    public boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack,
                                  ProtectionContext context, boolean allowFaceOffset) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.POWDER_SNOW) return false;
        int units = NBTUtil.getPowderUnits(stack);
        if (units <= 0) return false;

        BlockPos placePos = resolvePowderPlaceTarget(level, hit, allowFaceOffset);
        BlockState placeState = level.getBlockState(placePos);
        if (!placeState.canBeReplaced()) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, placePos,
                hit.getDirection(), stack, null)) return false;

        if (!level.isClientSide) {
            int newUnits = units - 1;
            level.setBlock(placePos, Blocks.POWDER_SNOW.defaultBlockState(), Block.UPDATE_ALL);
            NBTUtil.setPowderUnits(stack, newUnits);
            if (newUnits <= 0) NBTUtil.normalizeEmptyState(stack);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        level.playSound(context.player(), placePos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(context.player(), GameEvent.FLUID_PLACE, placePos);
        return true;
    }

    /** Fires the filled-bucket criterion for a real player completing a pickup. */
    private static void awardPickup(ProtectionContext context, ItemStack stack) {
        if (context.player() instanceof ServerPlayer sp) {
            CriteriaTriggers.FILLED_BUCKET.trigger(sp, stack);
        }
    }
}
