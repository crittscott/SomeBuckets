package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * Centralized cross-bucket transfer logic for BB/SB/NB.
 */
public final class Transfers {

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
        Kind fromKind = kindOf(fromStack);
        Kind toKind   = kindOf(toStack);
        if (fromKind == Kind.UNKNOWN || toKind == Kind.UNKNOWN) return false;

        FluidStack fromFluid = fluidOf(fromStack, fromKind);
        if (fromFluid.isEmpty()) return false;

        switch (fromKind) {
            case NB:
                return transferFromNB(level, player, fromHand, fromStack, toHand, toStack, toKind, fromFluid);
            case BB:
                return transferFromBB(level, player, fromHand, fromStack, toHand, toStack, toKind, fromFluid);
            case SB:
                return transferFromSB(level, player, fromHand, fromStack, toHand, toStack, toKind, fromFluid);
            default:
                return false;
        }
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
     * Pair-specific handlers (single place, no duplication)
     * ---------------------------------------------------------------------- */

    private static boolean transferFromNB(Level level, Player player,
                                          InteractionHand nbHand, ItemStack nbStack,
                                          InteractionHand dstHand, ItemStack dstStack,
                                          Kind dstKind, FluidStack fluid) {
        switch (dstKind) {
            case BB: {
                if (!NBTUtil.isNormalBucket(nbStack)) return false; // guard for safety (NB definition)
                // BB empty → set to 1000; NB becomes empty
                String bbMode = NBTUtil.getMode(dstStack);
                BBItem bbItem = (BBItem) dstStack.getItem();
                int capacity  = bbItem.getCapacityMb();

                if ("none".equals(bbMode)) {
                    NBTUtil.setFluidStack(dstStack, new FluidStack(fluid.getFluid(), 1000, fluid.getTag()));
                    player.setItemInHand(nbHand, new ItemStack(Items.BUCKET));
                    play(level, player, SoundEvents.BUCKET_EMPTY);
                    award(player, nbStack);
                    return true;
                }

                // Same-kind compatibility
                if ("milk".equals(bbMode) && isMilkFluid(fluid)) {
                    int bbAmt = NBTUtil.getAmount(dstStack);
                    if (bbAmt + 1000 <= capacity) {
                        NBTUtil.setAmount(dstStack, bbAmt + 1000);
                        player.setItemInHand(nbHand, new ItemStack(Items.BUCKET));
                        play(level, player, SoundEvents.BUCKET_EMPTY);
                        award(player, nbStack);
                        return true;
                    }
                } else if ("fluid".equals(bbMode)) {
                    FluidStack bbFluid = NBTUtil.getFluidStack(dstStack);
                    if (bbFluid.isFluidEqual(fluid)) {
                        int bbAmt = bbFluid.getAmount();
                        if (bbAmt + 1000 <= capacity) {
                            NBTUtil.setFluidStack(dstStack, new FluidStack(fluid.getFluid(), bbAmt + 1000, fluid.getTag()));
                            player.setItemInHand(nbHand, new ItemStack(Items.BUCKET));
                            play(level, player, SoundEvents.BUCKET_EMPTY);
                            award(player, nbStack);
                            return true;
                        }
                    }
                }
                return false;
            }
            case SB: {
                if (!NBTUtil.isNormalBucket(nbStack)) return false;
                String sbMode = NBTUtil.getMode(dstStack);
                if ("none".equals(sbMode)) {
                    // SB becomes infinite source of NB's fluid
                    NBTUtil.setFluidStack(dstStack, new FluidStack(fluid.getFluid(), 1000, fluid.getTag()));
                    player.setItemInHand(nbHand, new ItemStack(Items.BUCKET));
                    play(level, player, SoundEvents.BUCKET_EMPTY);
                    award(player, nbStack);
                    return true;
                } else if (compatibleSB(sbMode, dstStack, fluid)) {
                    // Refill into infinite sink; NB empties regardless
                    player.setItemInHand(nbHand, new ItemStack(Items.BUCKET));
                    play(level, player, SoundEvents.BUCKET_EMPTY);
                    award(player, nbStack);
                    return true;
                }
                return false;
            }
            default:
                return false;
        }
    }

    private static boolean transferFromBB(Level level, Player player,
                                          InteractionHand bbHand, ItemStack bbStack,
                                          InteractionHand dstHand, ItemStack dstStack,
                                          Kind dstKind, FluidStack fluid) {
        String bbMode = NBTUtil.getMode(bbStack);
        if (!("fluid".equals(bbMode) || "milk".equals(bbMode))) return false;

        int bbAmt = NBTUtil.getAmount(bbStack);
        if (bbAmt < 1000) return false;

        switch (dstKind) {
            case SB: {
                String sbMode = NBTUtil.getMode(dstStack);
                if ("none".equals(sbMode)) {
                    NBTUtil.setFluidStack(dstStack, new FluidStack(fluid.getFluid(), 1000, fluid.getTag()));
                    // SB is an infinite sink; BB loses exactly 1000
                    if ("fluid".equals(bbMode)) {
                        FluidStack bbFluid = NBTUtil.getFluidStack(bbStack);
                        NBTUtil.setFluidStack(bbStack, new FluidStack(bbFluid.getFluid(), bbAmt - 1000, bbFluid.getTag()));
                    } else {
                        NBTUtil.setAmount(bbStack, bbAmt - 1000);
                    }
                    NBTUtil.normalizeEmptyState(bbStack);
                    play(level, player, SoundEvents.BUCKET_FILL);
                    award(player, bbStack);
                    return true;
                } else if (compatibleSB(sbMode, dstStack, fluid)) {
                    // Already same fluid; drain 1000 from BB into infinite sink
                    if ("fluid".equals(bbMode)) {
                        FluidStack bbFluid = NBTUtil.getFluidStack(bbStack);
                        NBTUtil.setFluidStack(bbStack, new FluidStack(bbFluid.getFluid(), bbAmt - 1000, bbFluid.getTag()));
                    } else {
                        NBTUtil.setAmount(bbStack, bbAmt - 1000);
                    }
                    NBTUtil.normalizeEmptyState(bbStack);
                    play(level, player, SoundEvents.BUCKET_FILL);
                    award(player, bbStack);
                    return true;
                }
                return false;
            }
            case NB: {
                if (!NBTUtil.isNormalBucket(dstStack)) return false;
                FluidStack nbFluid = NBTUtil.getNormalBucketFluidStack(dstStack);
                if (nbFluid.isEmpty()) {
                    // Fill empty NB with 1000; BB -1000
                    ItemStack newNB = toNormalBucket(fluid);
                    if (!newNB.isEmpty()) {
                        player.setItemInHand(dstHand, newNB);
                        if ("fluid".equals(bbMode)) {
                            FluidStack bbFluid = NBTUtil.getFluidStack(bbStack);
                            NBTUtil.setFluidStack(bbStack, new FluidStack(bbFluid.getFluid(), bbAmt - 1000, bbFluid.getTag()));
                        } else {
                            NBTUtil.setAmount(bbStack, bbAmt - 1000);
                        }
                        NBTUtil.normalizeEmptyState(bbStack);
                        play(level, player, SoundEvents.BUCKET_FILL);
                        award(player, bbStack);
                        return true;
                    }
                } else if (nbFluid.isFluidEqual(fluid) ||
                        ("milk".equals(bbMode) && isMilkFluid(nbFluid))) {
                    // Refill NB; NB stays same, BB -1000
                    if ("fluid".equals(bbMode)) {
                        FluidStack bbFluid = NBTUtil.getFluidStack(bbStack);
                        NBTUtil.setFluidStack(bbStack, new FluidStack(bbFluid.getFluid(), bbAmt - 1000, bbFluid.getTag()));
                    } else {
                        NBTUtil.setAmount(bbStack, bbAmt - 1000);
                    }
                    NBTUtil.normalizeEmptyState(bbStack);
                    play(level, player, SoundEvents.BUCKET_FILL);
                    award(player, bbStack);
                    return true;
                }
                return false;
            }
            default:
                return false;
        }
    }

    private static boolean transferFromSB(Level level, Player player,
                                          InteractionHand sbHand, ItemStack sbStack,
                                          InteractionHand dstHand, ItemStack dstStack,
                                          Kind dstKind, FluidStack fluid) {
        String sbMode = NBTUtil.getMode(sbStack);
        if (!("fluid".equals(sbMode) || "milk".equals(sbMode))) return false;

        switch (dstKind) {
            case BB: {
                String bbMode = NBTUtil.getMode(dstStack);
                BBItem bbItem = (BBItem) dstStack.getItem();
                int capacity  = bbItem.getCapacityMb();

                if ("none".equals(bbMode)) {
                    // SB is infinite source: fill BB to capacity
                    if ("fluid".equals(sbMode)) {
                        FluidStack sbFluid = NBTUtil.getFluidStack(sbStack);
                        NBTUtil.setFluidStack(dstStack, new FluidStack(sbFluid.getFluid(), capacity, sbFluid.getTag()));
                    } else {
                        NBTUtil.setMilkAmount(dstStack, capacity);
                    }
                    play(level, player, SoundEvents.BUCKET_FILL);
                    award(player, sbStack);
                    return true;
                } else if (compatibleBB(bbMode, dstStack, fluid)) {
                    int bbAmt = NBTUtil.getAmount(dstStack);
                    if (bbAmt < capacity) {
                        // Top off to capacity
                        if ("fluid".equals(sbMode)) {
                            FluidStack sbFluid = NBTUtil.getFluidStack(sbStack);
                            NBTUtil.setFluidStack(dstStack, new FluidStack(sbFluid.getFluid(), capacity, sbFluid.getTag()));
                        } else {
                            NBTUtil.setMilkAmount(dstStack, capacity);
                        }
                        play(level, player, SoundEvents.BUCKET_FILL);
                        award(player, sbStack);
                        return true;
                    }
                }
                return false;
            }
            case NB: {
                if (!NBTUtil.isNormalBucket(dstStack)) return false;
                FluidStack nbFluid = NBTUtil.getNormalBucketFluidStack(dstStack);
                if (nbFluid.isEmpty()) {
                    // SB infinite source fills empty NB
                    ItemStack newNB = toNormalBucket(fluid);
                    if (!newNB.isEmpty()) {
                        player.setItemInHand(dstHand, newNB);
                        play(level, player, SoundEvents.BUCKET_FILL);
                        award(player, sbStack);
                        return true;
                    }
                } else if (nbFluid.isFluidEqual(fluid) ||
                        ("milk".equals(sbMode) && isMilkFluid(nbFluid))) {
                    // Refill NB from infinite source (no item change needed)
                    play(level, player, SoundEvents.BUCKET_FILL);
                    award(player, sbStack);
                    return true;
                }
                return false;
            }
            default:
                return false;
        }
    }

    /* -------------------------------------------------------------------------
     * Helpers
     * ---------------------------------------------------------------------- */

    private enum Kind { BB, SB, NB, UNKNOWN }

    private static Kind kindOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Kind.UNKNOWN;
        if (stack.getItem() instanceof BBItem) return Kind.BB;
        if (stack.getItem() instanceof SBItem) return Kind.SB;
        if (NBTUtil.isNormalBucket(stack))     return Kind.NB;
        return Kind.UNKNOWN;
    }

    private static FluidStack fluidOf(ItemStack stack, Kind kind) {
        switch (kind) {
            case NB:
                return NBTUtil.getNormalBucketFluidStack(stack);
            case BB:
            case SB:
                String mode = NBTUtil.getMode(stack);
                if ("fluid".equals(mode)) {
                    return NBTUtil.getFluidStack(stack);
                } else if ("milk".equals(mode)) {
                    // Represent milk as a pseudo-FluidStack for transfer logic
                    return new FluidStack(Fluids.EMPTY, 1000);
                }
                return FluidStack.EMPTY;
            default:
                return FluidStack.EMPTY;
        }
    }

    private static boolean compatibleSB(String sbMode, ItemStack sbStack, FluidStack fluid) {
        if ("milk".equals(sbMode) && isMilkFluid(fluid)) return true;
        if ("fluid".equals(sbMode)) {
            FluidStack sbFluid = NBTUtil.getFluidStack(sbStack);
            return sbFluid.isFluidEqual(fluid);
        }
        return false;
    }

    private static boolean compatibleBB(String bbMode, ItemStack bbStack, FluidStack fluid) {
        if ("milk".equals(bbMode) && isMilkFluid(fluid)) return true;
        if ("fluid".equals(bbMode)) {
            FluidStack bbFluid = NBTUtil.getFluidStack(bbStack);
            return bbFluid.isFluidEqual(fluid);
        }
        return false;
    }

    private static boolean isMilkFluid(FluidStack fluid) {
        // Since milk isn't a real fluid, we represent it as empty fluid for transfer logic
        return fluid.getFluid() == Fluids.EMPTY;
    }

    private static ItemStack toNormalBucket(FluidStack fluid) {
        if (fluid.getFluid() == Fluids.WATER) {
            return new ItemStack(Items.WATER_BUCKET);
        } else if (fluid.getFluid() == Fluids.LAVA) {
            return new ItemStack(Items.LAVA_BUCKET);
        } else if (isMilkFluid(fluid)) {
            return new ItemStack(Items.MILK_BUCKET);
        }
        return ItemStack.EMPTY; // Can't convert generic fluids to normal buckets
    }

    private static void play(Level level, Player player, SoundEvent evt) {
        level.playSound(player, player.blockPosition(), evt, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void award(Player player, ItemStack usedStack) {
        player.awardStat(Stats.ITEM_USED.get(usedStack.getItem()));
    }
}
