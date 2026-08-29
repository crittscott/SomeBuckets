package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-to-hand transfer between one of this mod's buckets and whatever the other hand holds.
 *
 * <p>Any item exposing {@link IFluidHandlerItem} is a valid partner. NeoForge's standard
 * {@link FluidBucketWrapper} supplies the same contract for {@link BucketItem}s. Milk is not a
 * NeoForge fluid here, so it has its own branch.
 *
 * <p>A held stack is worked through one item at a time, moving as much as each pair allows. The
 * hand keeps one stack, preferring one that still holds something, and the remainder is dropped:
 * a filled container and the empties it left behind cannot occupy the same slot.
 */
public final class Transfers {

    /**
     * Result of dispatching a fluid operation to a block capability. A present capability owns the
     * interaction even when it refuses, so callers may fall back to world behavior only for
     * {@link #NO_HANDLER}.
     */
    public enum BlockTransferResult {
        /** The clicked face exposes no fluid handler; world fallback is permitted. */
        NO_HANDLER,
        /** A fluid handler exists but cannot complete the requested operation. */
        REFUSED,
        /** The handler accepted the preview or completed the server transaction. */
        SUCCESS;

        /** Returns whether a block handler, rather than world fallback, owns this operation. */
        public boolean handled() {
            return this != NO_HANDLER;
        }

        /** Returns whether the block handler accepted the operation. */
        public boolean succeeded() {
            return this == SUCCESS;
        }
    }

    private Transfers() {}

    /* -------------------------------------------------------------------------
     * Public entry points
     * ---------------------------------------------------------------------- */

    /**
     * Attempts one direction of held-item transfer from {@code fromStack} to {@code toStack}.
     *
     * <p>At least one side must be a finite Big/Huge Bucket or a Source Bucket. NeoForge fluid
     * capabilities handle fluid; vanilla milk buckets use the separate milk path. A successful
     * stacked-container transfer rebuilds the foreign container hand into legal stacks, keeps one
     * useful result in that hand, and drops overflow only on the server. The same transfer and hand
     * settlement run client-side for prediction; the server remains authoritative. Success also
     * plays the matching feedback and awards the Some Buckets item-use statistic.
     *
     * @return {@code true} only when content was accepted and transfer side effects were applied;
     *         {@code false} means no stack, hand, sound, statistic, or world drop changed
     */
    public static boolean tryTransferOne(Level level,
                                         Player player,
                                         InteractionHand fromHand, ItemStack fromStack,
                                         InteractionHand toHand,   ItemStack toStack) {
        if (fromStack.isEmpty() || toStack.isEmpty() || fromStack == toStack) return false;

        // One side must be ours; two foreign containers are not this mod's business.
        if (!isOurs(fromStack) && !isOurs(toStack)) return false;

        if (isOurs(fromStack) && NBTUtil.getMode(fromStack) == NBTUtil.Mode.MILK) {
            return MilkTransfers.pourMilk(level, player, fromStack, toHand, toStack);
        }
        if (isOurs(toStack) && fromStack.getItem() == Items.MILK_BUCKET) {
            return MilkTransfers.takeMilk(level, player, fromHand, fromStack, toStack);
        }
        if (isOurs(fromStack)) {
            return fillFrom(level, player, fromStack, toHand, toStack);
        }
        return drainInto(level, player, fromHand, fromStack, toStack);
    }

    /**
     * Attempts main-hand to offhand first, then offhand to main-hand only when the first direction
     * accepts no transfer.
     *
     * @return {@code true} when either ordered one-way attempt succeeds
     */
    public static boolean tryTransferEither(Level level,
                                            Player player,
                                            InteractionHand mainHand, ItemStack mainStack,
                                            InteractionHand offHand,  ItemStack offStack) {
        if (tryTransferOne(level, player, mainHand, mainStack, offHand, offStack)) return true;
        return tryTransferOne(level, player, offHand, offStack, mainHand, mainStack);
    }

    /** The mod bucket's own capability is an invariant, not an optional dispatch signal. */
    public static IFluidHandlerItem requireBucketHandler(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            throw new IllegalStateException("Some Buckets item is missing its fluid capability");
        }
        return handler;
    }

    /** Read-only preview of an exact one-bucket-volume block drain. */
    public static BlockTransferResult previewTakeFromBlock(Level level, BlockPos pos, Direction face,
                                                           IFluidHandlerItem bucketHandler) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BlockTransferResult.NO_HANDLER;

        FluidStack available = blockHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (!isBucketVolume(available)) return BlockTransferResult.REFUSED;

        int accepted = bucketHandler.fill(available, IFluidHandler.FluidAction.SIMULATE);
        return accepted == FluidType.BUCKET_VOLUME
                ? BlockTransferResult.SUCCESS
                : BlockTransferResult.REFUSED;
    }

    /** Classifies the contents of a present sided block handler for Source Bucket dispatch. */
    public static BucketOperations.SourceTarget classifySourceTarget(Level level, BlockPos pos,
                                                                      Direction face,
                                                                      IFluidHandlerItem bucketHandler) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BucketOperations.SourceTarget.NO_FLUID;

        FluidStack available = blockHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (isBucketVolume(available)
                && bucketHandler.fill(available, IFluidHandler.FluidAction.SIMULATE)
                == FluidType.BUCKET_VOLUME) {
            return BucketOperations.SourceTarget.MATCHING_FLUID;
        }

        FluidStack any = blockHandler.drain(1, IFluidHandler.FluidAction.SIMULATE);
        return any.isEmpty() ? BucketOperations.SourceTarget.NO_FLUID
                : BucketOperations.SourceTarget.BLOCKING_FLUID;
    }

    /**
     * Takes exactly one bucket volume from the sided block capability into the supplied BB/SB item
     * handler. A present handler owns dispatch even when it refuses the transaction.
     */
    public static BlockTransferResult tryTakeFromBlock(Level level, BlockPos pos, Direction face,
                                                       ItemStack bucketStack,
                                                       IFluidHandlerItem bucketHandler,
                                                       ProtectionContext context) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BlockTransferResult.NO_HANDLER;

        FluidStack available = blockHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (!isBucketVolume(available)) return BlockTransferResult.REFUSED;
        if (bucketHandler.fill(available, IFluidHandler.FluidAction.SIMULATE)
                != FluidType.BUCKET_VOLUME) return BlockTransferResult.REFUSED;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face,
                bucketStack, null)) return BlockTransferResult.REFUSED;

        if (!level.isClientSide) {
            FluidStack removed = blockHandler.drain(
                    NeoForgeFluidStacks.resized(available, FluidType.BUCKET_VOLUME),
                    IFluidHandler.FluidAction.EXECUTE);
            if (!isBucketVolume(removed) || !NeoForgeFluidStacks.sameFluid(removed, available)) {
                reportFluidContractViolation(level, pos, context, "block drain", blockHandler,
                        available, removed);
                return BlockTransferResult.REFUSED;
            }
            int accepted = bucketHandler.fill(removed, IFluidHandler.FluidAction.EXECUTE);
            if (accepted != FluidType.BUCKET_VOLUME) {
                reportFluidContractViolation(level, pos, context, "bucket fill", bucketHandler,
                        FluidType.BUCKET_VOLUME, accepted);
                return BlockTransferResult.REFUSED;
            }
            if (context.player() != null) {
                context.player().awardStat(Stats.ITEM_USED.get(bucketStack.getItem()));
            }
            level.gameEvent(context.player(), GameEvent.FLUID_PICKUP, pos);
        }

        playBucketSound(level, context, pos, resolveFillSound(available.getFluid()));
        return BlockTransferResult.SUCCESS;
    }

    /**
     * Places exactly one bucket volume from the supplied BB/SB item handler into the sided block
     * capability. Finite versus infinite consumption is expressed by that item handler's drain.
     */
    public static BlockTransferResult tryPlaceIntoBlock(Level level, BlockPos pos, Direction face,
                                                        ItemStack bucketStack,
                                                        IFluidHandlerItem bucketHandler,
                                                        ProtectionContext context) {
        IFluidHandler blockHandler = blockHandler(level, pos, face);
        if (blockHandler == null) return BlockTransferResult.NO_HANDLER;

        FluidStack available = bucketHandler.drain(FluidType.BUCKET_VOLUME,
                IFluidHandler.FluidAction.SIMULATE);
        if (!isBucketVolume(available)) return BlockTransferResult.REFUSED;
        if (blockHandler.fill(available, IFluidHandler.FluidAction.SIMULATE)
                != FluidType.BUCKET_VOLUME) return BlockTransferResult.REFUSED;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face,
                bucketStack, null)) return BlockTransferResult.REFUSED;

        if (!level.isClientSide) {
            int accepted = blockHandler.fill(available, IFluidHandler.FluidAction.EXECUTE);
            if (accepted != FluidType.BUCKET_VOLUME) {
                reportFluidContractViolation(level, pos, context, "block fill", blockHandler,
                        FluidType.BUCKET_VOLUME, accepted);
                return BlockTransferResult.REFUSED;
            }
            FluidStack removed = bucketHandler.drain(
                    NeoForgeFluidStacks.resized(available, FluidType.BUCKET_VOLUME),
                    IFluidHandler.FluidAction.EXECUTE);
            if (!isBucketVolume(removed) || !NeoForgeFluidStacks.sameFluid(removed, available)) {
                reportFluidContractViolation(level, pos, context, "bucket drain", bucketHandler,
                        available, removed);
                return BlockTransferResult.REFUSED;
            }
            if (context.player() != null) {
                context.player().awardStat(Stats.ITEM_USED.get(bucketStack.getItem()));
            }
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, pos);
        }

        playBucketSound(level, context, pos, resolveEmptySound(available.getFluid()));
        return BlockTransferResult.SUCCESS;
    }

    public static boolean hasBlockHandler(Level level, BlockPos pos, Direction face) {
        return blockHandler(level, pos, face) != null;
    }

    @Nullable
    private static IFluidHandler blockHandler(Level level, BlockPos pos, Direction face) {
        // NeoForge exposes a fluid handler for vanilla cauldrons, but Some Buckets routes every
        // cauldron interaction through the dedicated Cauldrons path so it awards the cauldron-use
        // statistic, fires the filled-bucket criterion, and emits the cauldron game events. Forge
        // has no such capability, so this keeps cauldron behavior identical on both loaders.
        if (level.getBlockState(pos).getBlock() instanceof AbstractCauldronBlock) return null;
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
    }

    static void reportFluidContractViolation(Level level, BlockPos pos, ProtectionContext context,
                                             String operation, Object handler,
                                             Object expected, Object actual) {
        SomeBuckets.LOGGER.error(
                "Fluid handler contract violation during {} at {} in {} (block {}, handler {}): expected {}, got {}",
                operation, pos, level.dimension().location(), level.getBlockState(pos),
                handler.getClass().getName(), expected, actual);
        if (context.player() != null) {
            context.player().displayClientMessage(
                    Component.translatable("message.somebuckets.fluid_transfer_inconsistent"), false);
        }
    }

    private static boolean isBucketVolume(FluidStack stack) {
        return !stack.isEmpty() && stack.getAmount() == FluidType.BUCKET_VOLUME;
    }

    public static SoundEvent resolveFillSound(Fluid fluid) {
        return resolveBucketSound(fluid.getFluidType().getSound(SoundActions.BUCKET_FILL),
                fluid.defaultFluidState().is(FluidTags.LAVA), true);
    }

    public static SoundEvent resolveEmptySound(Fluid fluid) {
        return resolveBucketSound(fluid.getFluidType().getSound(SoundActions.BUCKET_EMPTY),
                fluid.defaultFluidState().is(FluidTags.LAVA), false);
    }

    /** Broadcasts one server-authoritative bucket sound, including the acting player. */
    public static void playBucketSound(Level level, ProtectionContext context, BlockPos pos,
                                       SoundEvent sound) {
        playBucketSound(level, context.player(), pos, sound);
    }

    /** Broadcasts one server-authoritative bucket sound for a nullable player identity. */
    public static void playBucketSound(Level level, @Nullable Player player, BlockPos pos,
                                       SoundEvent sound) {
        if (level.isClientSide) return;
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        notifyActor(player, sound);
    }

    /** Sends the actor the sound excluded from a normal server broadcast. */
    public static void notifyActor(@Nullable Player player, SoundEvent sound) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            serverPlayer.playNotifySound(sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * Selects a bucket sound using the common registered-sound, lava-fallback, and direction
     * precedence contract.
     */
    public static SoundEvent resolveBucketSound(@Nullable SoundEvent registeredSound,
                                                boolean lava, boolean filling) {
        return FluidPlacement.resolveBucketSound(registeredSound, lava, filling);
    }

    public static SoundEvent automatedMilkingSound() {
        return SoundEvents.COW_MILK;
    }

    /** Shared "raspy hiss" pitch for the vanilla evaporation sound and its Trash Bucket reuse. */
    public static float hissPitch(RandomSource random) {
        return FluidPlacement.hissPitch(random);
    }

    /* -------------------------------------------------------------------------
     * Fluid
     * ---------------------------------------------------------------------- */

    /** Fills the containers in the other hand from one of ours. */
    private static boolean fillFrom(Level level, Player player, ItemStack source,
                                    InteractionHand destinationHand, ItemStack destinationStack) {
        IFluidHandlerItem sourceHandler = handler(source);
        if (sourceHandler == null || sourceHandler.getFluidInTank(0).isEmpty()) return false;
        Fluid movedFluid = sourceHandler.getFluidInTank(0).getFluid();

        // An assigned Source Bucket never runs dry. Its public one-bucket-per-call capability is a
        // deliberate machine limit; direct held transfer fills a large destination in one shot.
        boolean infinite = source.getItem() instanceof SBItem && NBTUtil.getMode(source) == NBTUtil.Mode.FLUID;

        List<ItemStack> filled = new ArrayList<>();
        int untouched = destinationStack.getCount();

        while (untouched > 0) {
            ItemStack one = single(destinationStack);
            IFluidHandlerItem target = handler(one);
            int moved = target == null ? 0
                    : infinite ? pumpUnlimited(sourceHandler, target) : pump(sourceHandler, target);
            if (moved <= 0) break;

            filled.add(target.getContainer());
            untouched--;
        }

        if (filled.isEmpty()) return false;

        settle(level, player, destinationHand, destinationStack, filled, untouched);
        play(level, player, resolveEmptySound(movedFluid));
        award(player, source);
        return true;
    }

    /** Empties the containers in the other hand into one of ours. */
    private static boolean drainInto(Level level, Player player,
                                     InteractionHand sourceHand, ItemStack sourceStack,
                                     ItemStack destination) {
        IFluidHandlerItem destinationHandler = handler(destination);
        if (destinationHandler == null) return false;

        List<ItemStack> emptied = new ArrayList<>();
        int untouched = sourceStack.getCount();

        while (untouched > 0) {
            ItemStack one = single(sourceStack);
            IFluidHandlerItem source = handler(one);
            if (source == null || pump(source, destinationHandler) <= 0) break;

            emptied.add(source.getContainer());
            untouched--;
        }

        if (emptied.isEmpty()) return false;
        Fluid movedFluid = destinationHandler.getFluidInTank(0).getFluid();

        settle(level, player, sourceHand, sourceStack, emptied, untouched);
        play(level, player, resolveFillSound(movedFluid));
        award(player, destination);
        return true;
    }

    /** Moves as much as the finite pair allows through NeoForge's single-round transfer contract. */
    private static int pump(IFluidHandlerItem source, IFluidHandlerItem destination) {
        return FluidUtil.tryFluidTransfer(destination, source, Integer.MAX_VALUE, true).getAmount();
    }

    /**
     * Fills the destination to its real capacity in one simulate/execute round, for a source that is
     * known not to run dry. The public capability is left untouched (still 1,000 mB per call for
     * machines); this bypasses it only for our own confirmed-infinite Source Bucket, since nothing is
     * actually drained from an infinite source.
     */
    private static int pumpUnlimited(IFluidHandlerItem source, IFluidHandlerItem destination) {
        // A 1-unit probe still routes through the Source Bucket's own policy check, so an unassigned
        // or now-disallowed content correctly transfers nothing, same as the stepped path.
        FluidStack probe = source.drain(1, IFluidHandler.FluidAction.SIMULATE);
        if (probe.isEmpty()) return 0;

        long budget = 0;
        for (int tank = 0; tank < destination.getTanks(); tank++) {
            budget += destination.getTankCapacity(tank);
        }
        if (budget <= 0) return 0;
        int offer = (int) Math.min(budget, Integer.MAX_VALUE);

        FluidStack toOffer = NeoForgeFluidStacks.resized(probe, offer);
        int room = destination.fill(toOffer, IFluidHandler.FluidAction.SIMULATE);
        if (room <= 0) return 0;

        return destination.fill(NeoForgeFluidStacks.resized(probe, room),
                IFluidHandler.FluidAction.EXECUTE);
    }

    /* -------------------------------------------------------------------------
     * Helpers
     * ---------------------------------------------------------------------- */

    /**
     * Settles a stack-wide transfer. The hand keeps one stack, preferring one that still holds
     * fluid, and everything else is dropped at the player's feet.
     */
    private static void settle(Level level, Player player, InteractionHand hand, ItemStack original,
                               List<ItemStack> results, int untouched) {
        HeldTransferSettlement.settle(level, player, hand, original, results, untouched, Transfers::holdsSomething);
    }

    private static boolean isOurs(ItemStack stack) {
        return stack.getItem() instanceof FluidBucketItem;
    }

    @Nullable
    private static IFluidHandlerItem handler(ItemStack stack) {
        IFluidHandlerItem capability = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (capability != null) return capability;

        return stack.getItem() instanceof BucketItem
                ? new FluidBucketWrapper(stack)
                : null;
    }

    /** Whether a stack still carries content. Milk is not a NeoForge fluid, so it is named directly. */
    private static boolean holdsSomething(ItemStack stack) {
        if (stack.getItem() == Items.MILK_BUCKET) return true;
        IFluidHandlerItem handler = handler(stack);
        return handler != null && !handler.getFluidInTank(0).isEmpty();
    }

    /**
     * One item to work on. A stack of one is handed back as-is: our own buckets never stack, and
     * their handlers edit the held stack in place. Copying it strands the caller's reference on a
     * stale copy.
     */
    private static ItemStack single(ItemStack stack) {
        if (stack.getCount() == 1) return stack;
        ItemStack one = stack.copy();
        one.setCount(1);
        return one;
    }

    private static void play(Level level, Player player, SoundEvent evt) {
        level.playSound(player, player.blockPosition(), evt, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void award(Player player, ItemStack usedStack) {
        player.awardStat(Stats.ITEM_USED.get(usedStack.getItem()));
    }
}
