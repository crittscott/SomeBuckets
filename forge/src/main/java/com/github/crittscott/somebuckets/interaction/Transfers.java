package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-to-hand transfer between one of this mod's buckets and whatever the other hand holds.
 *
 * <p>Any item exposing {@link IFluidHandlerItem} is a valid partner. Forge's standard
 * {@link FluidBucketWrapper} supplies the same contract for {@link BucketItem}s, including vanilla
 * buckets, which do not expose the capability directly on this Forge branch. Milk is not a Forge
 * fluid here, so it has its own branch.
 *
 * <p>A held stack is worked through one item at a time, moving as much as each pair allows. The
 * hand keeps one stack, preferring one that still holds something, and the remainder is dropped:
 * a filled container and the empties it left behind cannot occupy the same slot.
 */
public final class Transfers {

    private Transfers() {}

    /**
     * Attempts one direction of held-item transfer from {@code fromStack} to {@code toStack}.
     *
     * <p>At least one side must be a finite Big/Huge Bucket or a Source Bucket. Forge fluid
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
        play(level, player, BucketSounds.resolveEmptySound(movedFluid));
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
        play(level, player, BucketSounds.resolveFillSound(movedFluid));
        award(player, destination);
        return true;
    }

    /** Moves as much as the finite pair allows through Forge's single-round transfer contract. */
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

        FluidStack toOffer = ForgeFluidStacks.resized(probe, offer);
        int room = destination.fill(toOffer, IFluidHandler.FluidAction.SIMULATE);
        if (room <= 0) return 0;

        return destination.fill(ForgeFluidStacks.resized(probe, room),
                IFluidHandler.FluidAction.EXECUTE);
    }

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
        IFluidHandlerItem capability = stack.getCapability(
                ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (capability != null) return capability;

        return stack.getItem() instanceof BucketItem
                ? new FluidBucketWrapper(stack)
                : null;
    }

    /** Whether a stack still carries content. Milk is not a Forge fluid, so it is named directly. */
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
