package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.config.SourceBucketPolicy;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-to-hand transfer between one of this mod's buckets and whatever the other hand holds.
 *
 * <p>Any item exposing {@link IFluidHandlerItem} is a valid partner, so vanilla buckets, modded
 * buckets, and tanks all travel the same path and any fluid that defines a bucket is carried.
 * Milk is not a Forge fluid here, so it has its own branch.
 *
 * <p>A held stack is worked through one item at a time, moving as much as each pair allows. The
 * hand keeps one stack, preferring one that still holds something, and the remainder is dropped:
 * a filled container and the empties it left behind cannot occupy the same slot.
 */
public final class Transfers {

    /** Ceiling on one container's fill loop, so a tank reporting an unbounded capacity terminates. */
    private static final int MAX_PUMP_STEPS = 512;

    private Transfers() {}

    /* -------------------------------------------------------------------------
     * Public entry points
     * ---------------------------------------------------------------------- */

    /**
     * Try a one-way transfer from 'fromHand/fromStack' to 'toHand/toStack'.
     * Returns true IFF a transfer occurs and state/sounds/stats were applied.
     */
    public static boolean tryTransferOne(Level level,
                                         Player player,
                                         InteractionHand fromHand, ItemStack fromStack,
                                         InteractionHand toHand,   ItemStack toStack) {
        if (fromStack.isEmpty() || toStack.isEmpty() || fromStack == toStack) return false;

        // One side must be ours; two foreign containers are not this mod's business.
        if (!isOurs(fromStack) && !isOurs(toStack)) return false;

        if (isOurs(fromStack) && NBTUtil.getMode(fromStack) == NBTUtil.Mode.MILK) {
            return pourMilk(level, player, fromStack, toHand, toStack);
        }
        if (isOurs(toStack) && fromStack.getItem() == Items.MILK_BUCKET) {
            return takeMilk(level, player, fromHand, fromStack, toStack);
        }
        if (isOurs(fromStack)) {
            return fillFrom(level, player, fromStack, toHand, toStack);
        }
        return drainInto(level, player, fromHand, fromStack, toStack);
    }

    /**
     * Try main->off; if that fails, try off->main. Mirrors the "either direction" behavior
     * some call sites use today by invoking two one-way attempts.
     */
    public static boolean tryTransferEither(Level level,
                                            Player player,
                                            InteractionHand mainHand, ItemStack mainStack,
                                            InteractionHand offHand,  ItemStack offStack) {
        if (tryTransferOne(level, player, mainHand, mainStack, offHand, offStack)) return true;
        return tryTransferOne(level, player, offHand, offStack, mainHand, mainStack);
    }

    /* -------------------------------------------------------------------------
     * Fluid
     * ---------------------------------------------------------------------- */

    /** Fills the containers in the other hand from one of ours. */
    private static boolean fillFrom(Level level, Player player, ItemStack source,
                                    InteractionHand destinationHand, ItemStack destinationStack) {
        IFluidHandlerItem sourceHandler = handler(source);
        if (sourceHandler == null || sourceHandler.getFluidInTank(0).isEmpty()) return false;

        // An assigned Source Bucket never runs dry, so its public 1,000 mB-per-call capability (a
        // deliberate limit for machines) would otherwise force hundreds of steps to fill a large
        // destination. Fill it in one shot instead of pumping through that limit repeatedly.
        boolean infinite = source.getItem() instanceof SBItem && NBTUtil.getMode(source) == NBTUtil.Mode.FLUID;

        List<ItemStack> filled = new ArrayList<>();
        int untouched = destinationStack.getCount();
        int keep = Integer.MAX_VALUE;

        while (untouched > 0 && filled.size() < keep) {
            ItemStack one = single(destinationStack);
            IFluidHandlerItem target = handler(one);
            int moved = target == null ? 0
                    : infinite ? pumpUnlimited(sourceHandler, target) : pump(sourceHandler, target);
            if (moved <= 0) break;

            ItemStack result = target.getContainer();
            // Only as many as will share the hand; the rest stay behind rather than being filled
            // into a stack that cannot exist.
            if (filled.isEmpty()) keep = Math.max(1, result.getMaxStackSize());
            filled.add(result);
            untouched--;
        }

        if (filled.isEmpty()) return false;

        NBTUtil.normalizeEmptyState(source);
        settle(level, player, destinationHand, destinationStack, filled, untouched);
        play(level, player, SoundEvents.BUCKET_EMPTY);
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
        int keep = Integer.MAX_VALUE;

        while (untouched > 0 && emptied.size() < keep) {
            ItemStack one = single(sourceStack);
            IFluidHandlerItem source = handler(one);
            if (source == null || pump(source, destinationHandler) <= 0) break;

            ItemStack result = source.getContainer();
            if (emptied.isEmpty()) keep = Math.max(1, result.getMaxStackSize());
            emptied.add(result);
            untouched--;
        }

        if (emptied.isEmpty()) return false;

        NBTUtil.normalizeEmptyState(destination);
        settle(level, player, sourceHand, sourceStack, emptied, untouched);
        play(level, player, SoundEvents.BUCKET_FILL);
        award(player, destination);
        return true;
    }

    /**
     * Moves as much as the pair allows, bounded by what the destination declares it can hold.
     * Follows the simulate-then-execute order Forge's own transfer helper uses so nothing is
     * drained that the destination will not take.
     */
    private static int pump(IFluidHandlerItem source, IFluidHandlerItem destination) {
        long budget = 0;
        for (int tank = 0; tank < destination.getTanks(); tank++) {
            budget += Math.max(0, destination.getTankCapacity(tank));
        }
        if (budget <= 0) return 0;

        int moved = 0;
        // A Source Bucket hands out a unit at a time, so a creative-tier tank advertising an
        // effectively unbounded capacity would otherwise spin here.
        for (int step = 0; step < MAX_PUMP_STEPS && moved < budget; step++) {
            int remaining = (int) Math.min(budget - moved, Integer.MAX_VALUE);
            FluidStack available = source.drain(remaining, IFluidHandler.FluidAction.SIMULATE);
            if (available.isEmpty()) break;

            int room = destination.fill(available, IFluidHandler.FluidAction.SIMULATE);
            if (room <= 0) break;

            FluidStack taken = source.drain(new FluidStack(available.getFluid(), room, available.getTag()),
                    IFluidHandler.FluidAction.EXECUTE);
            if (taken.isEmpty()) break;

            int accepted = destination.fill(taken, IFluidHandler.FluidAction.EXECUTE);
            if (accepted <= 0) break;
            moved += accepted;
        }
        return moved;
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
            budget += Math.max(0, destination.getTankCapacity(tank));
        }
        if (budget <= 0) return 0;
        int offer = (int) Math.min(budget, Integer.MAX_VALUE);

        FluidStack toOffer = new FluidStack(probe.getFluid(), offer, probe.getTag());
        int room = destination.fill(toOffer, IFluidHandler.FluidAction.SIMULATE);
        if (room <= 0) return 0;

        return destination.fill(new FluidStack(probe.getFluid(), room, probe.getTag()),
                IFluidHandler.FluidAction.EXECUTE);
    }

    /* -------------------------------------------------------------------------
     * Milk
     * ---------------------------------------------------------------------- */

    private static boolean pourMilk(Level level, Player player, ItemStack source,
                                    InteractionHand destinationHand, ItemStack destinationStack) {
        boolean infinite = source.getItem() instanceof SBItem;
        if ((infinite || destinationStack.getItem() instanceof SBItem)
                && !SourceBucketPolicy.allowsMilk()) return false;
        int stored = NBTUtil.getAmount(source);
        if (!infinite && stored < 1000) return false;

        if (isOurs(destinationStack)) {
            NBTUtil.Mode mode = NBTUtil.getMode(destinationStack);
            if (mode != NBTUtil.Mode.NONE && mode != NBTUtil.Mode.MILK) return false;

            // An assigned milk Source Bucket sinks a unit and keeps nothing. Two unlimited
            // supplies have nothing to exchange, so an infinite source is excluded.
            if (isInfiniteSink(destinationStack, NBTUtil.Mode.MILK) && !infinite) {
                NBTUtil.setAmount(source, stored - 1000);
                NBTUtil.normalizeEmptyState(source);
                play(level, player, SoundEvents.BUCKET_EMPTY);
                award(player, source);
                return true;
            }

            int held = mode == NBTUtil.Mode.MILK ? NBTUtil.getAmount(destinationStack) : 0;
            int room = capacityOf(destinationStack) - held;
            int moved = Math.min(room, infinite ? room : stored) / 1000 * 1000;
            if (moved <= 0) return false;

            NBTUtil.setMilkAmount(destinationStack, held + moved);
            if (!infinite) {
                NBTUtil.setAmount(source, stored - moved);
                NBTUtil.normalizeEmptyState(source);
            }
            play(level, player, SoundEvents.BUCKET_FILL);
            award(player, source);
            return true;
        }

        if (destinationStack.getItem() != Items.BUCKET) return false;

        int units = infinite ? destinationStack.getCount()
                : Math.min(destinationStack.getCount(), stored / 1000);
        units = Math.min(units, new ItemStack(Items.MILK_BUCKET).getMaxStackSize());
        if (units <= 0) return false;

        List<ItemStack> filled = new ArrayList<>();
        for (int i = 0; i < units; i++) filled.add(new ItemStack(Items.MILK_BUCKET));

        if (!infinite) {
            NBTUtil.setAmount(source, stored - units * 1000);
            NBTUtil.normalizeEmptyState(source);
        }
        settle(level, player, destinationHand, destinationStack, filled, destinationStack.getCount() - units);
        play(level, player, SoundEvents.BUCKET_EMPTY);
        award(player, source);
        return true;
    }

    private static boolean takeMilk(Level level, Player player, InteractionHand sourceHand, ItemStack sourceStack,
                                    ItemStack destination) {
        if (destination.getItem() instanceof SBItem && !SourceBucketPolicy.allowsMilk()) return false;
        NBTUtil.Mode mode = NBTUtil.getMode(destination);
        if (mode != NBTUtil.Mode.NONE && mode != NBTUtil.Mode.MILK) return false;

        // An assigned milk Source Bucket has no room to report but takes a unit all the same.
        boolean sink = isInfiniteSink(destination, NBTUtil.Mode.MILK);
        int held = mode == NBTUtil.Mode.MILK ? NBTUtil.getAmount(destination) : 0;
        int room = capacityOf(destination) - held;

        int units = Math.min(sourceStack.getCount(), sink ? 1 : room / 1000);
        if (units <= 0) return false;

        if (!sink) NBTUtil.setMilkAmount(destination, held + units * 1000);

        List<ItemStack> emptied = new ArrayList<>();
        for (int i = 0; i < units; i++) emptied.add(new ItemStack(Items.BUCKET));

        settle(level, player, sourceHand, sourceStack, emptied, sourceStack.getCount() - units);
        play(level, player, SoundEvents.BUCKET_FILL);
        award(player, destination);
        return true;
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
        List<ItemStack> pile = new ArrayList<>();
        for (ItemStack result : results) {
            boolean merged = false;
            for (ItemStack existing : pile) {
                if (ItemStack.isSameItemSameTags(existing, result)
                        && existing.getCount() + result.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(result.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) pile.add(result);
        }

        if (untouched > 0) {
            ItemStack rest = original.copy();
            rest.setCount(untouched);
            pile.add(rest);
        }

        int held = 0;
        for (int i = 0; i < pile.size(); i++) {
            if (holdsSomething(pile.get(i))) {
                held = i;
                break;
            }
        }

        player.setItemInHand(hand, pile.get(held));
        if (level.isClientSide) return;
        for (int i = 0; i < pile.size(); i++) {
            if (i != held) player.drop(pile.get(i), false);
        }
    }

    private static boolean isOurs(ItemStack stack) {
        return stack.getItem() instanceof BBItem || stack.getItem() instanceof SBItem;
    }

    /** An assigned Source Bucket takes without limit and keeps nothing: a unit poured in is gone. */
    private static boolean isInfiniteSink(ItemStack stack, NBTUtil.Mode mode) {
        return stack.getItem() instanceof SBItem && NBTUtil.getMode(stack) == mode;
    }

    private static int capacityOf(ItemStack stack) {
        return stack.getItem() instanceof BBItem big ? big.getCapacityMb() : 1000;
    }

    @Nullable
    private static IFluidHandlerItem handler(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
    }

    /** Whether a stack still carries content. Milk is not a Forge fluid, so it is named directly. */
    private static boolean holdsSomething(ItemStack stack) {
        if (stack.getItem() == Items.MILK_BUCKET) return true;
        IFluidHandlerItem handler = handler(stack);
        return handler != null && !handler.getFluidInTank(0).isEmpty();
    }

    /**
     * One item to work on. A stack of one is handed back as-is: our own buckets never stack, and
     * their handlers edit the held stack in place, so copying it would strand the caller's
     * reference on a stale copy.
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
