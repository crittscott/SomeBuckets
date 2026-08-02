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

        Content fromContent = contentOf(fromStack, fromKind);
        if (!fromContent.present) return false;

        switch (fromKind) {
            case NB:
                return transferFromNB(level, player, fromHand, fromStack, toHand, toStack, toKind, fromContent);
            case BB:
                return transferFromBB(level, player, fromHand, fromStack, toHand, toStack, toKind, fromContent);
            case SB:
                return transferFromSB(level, player, fromHand, fromStack, toHand, toStack, toKind, fromContent);
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
                                          Kind dstKind, Content content) {
        switch (dstKind) {
            case BB: {
                if (!NBTUtil.isNormalBucket(nbStack)) return false; // guard for safety (NB definition)
                // BB empty → set to 1000; NB becomes empty
                String bbMode = NBTUtil.getMode(dstStack);
                BBItem bbItem = (BBItem) dstStack.getItem();
                int capacity  = bbItem.getCapacityMb();

                if ("none".equals(bbMode)) {
                    if (content.milk) {
                        NBTUtil.setMilkAmount(dstStack, 1000);
                    } else {
                        NBTUtil.setFluidStack(dstStack, new FluidStack(content.fluid.getFluid(), 1000, content.fluid.getTag()));
                    }
                    player.setItemInHand(nbHand, new ItemStack(Items.BUCKET));
                    play(level, player, SoundEvents.BUCKET_EMPTY);
                    award(player, nbStack);
                    return true;
                }

                // Same-kind compatibility
                if ("milk".equals(bbMode) && content.milk) {
                    int bbAmt = NBTUtil.getAmount(dstStack);
                    if (bbAmt + 1000 <= capacity) {
                        NBTUtil.setAmount(dstStack, bbAmt + 1000);
                        player.setItemInHand(nbHand, new ItemStack(Items.BUCKET));
                        play(level, player, SoundEvents.BUCKET_EMPTY);
                        award(player, nbStack);
                        return true;
                    }
                } else if ("fluid".equals(bbMode) && !content.milk) {
                    FluidStack bbFluid = NBTUtil.getFluidStack(dstStack);
                    if (bbFluid.isFluidEqual(content.fluid)) {
                        int bbAmt = bbFluid.getAmount();
                        if (bbAmt + 1000 <= capacity) {
                            NBTUtil.setFluidStack(dstStack, new FluidStack(content.fluid.getFluid(), bbAmt + 1000, content.fluid.getTag()));
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
                    // SB becomes infinite source of NB's content
                    if (content.milk) {
                        NBTUtil.setMilkAmount(dstStack, 1000);
                    } else {
                        NBTUtil.setFluidStack(dstStack, new FluidStack(content.fluid.getFluid(), 1000, content.fluid.getTag()));
                    }
                    player.setItemInHand(nbHand, new ItemStack(Items.BUCKET));
                    play(level, player, SoundEvents.BUCKET_EMPTY);
                    award(player, nbStack);
                    return true;
                } else if (compatibleSB(sbMode, dstStack, content)) {
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
                                          Kind dstKind, Content content) {
        String bbMode = NBTUtil.getMode(bbStack);
        if (!("fluid".equals(bbMode) || "milk".equals(bbMode))) return false;

        int bbAmt = NBTUtil.getAmount(bbStack);
        if (bbAmt < 1000) return false;

        switch (dstKind) {
            case SB: {
                String sbMode = NBTUtil.getMode(dstStack);
                if ("none".equals(sbMode)) {
                    if (content.milk) {
                        NBTUtil.setMilkAmount(dstStack, 1000);
                    } else {
                        NBTUtil.setFluidStack(dstStack, new FluidStack(content.fluid.getFluid(), 1000, content.fluid.getTag()));
                    }
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
                } else if (compatibleSB(sbMode, dstStack, content)) {
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
                if (dstStack.getItem() == Items.BUCKET) {
                    // Fill empty NB with 1000; BB -1000
                    ItemStack newNB = toNormalBucket(content);
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
                }
                // A filled vanilla bucket is already at its 1000 mB capacity and cannot take more.
                return false;
            }
            default:
                return false;
        }
    }

    private static boolean transferFromSB(Level level, Player player,
                                          InteractionHand sbHand, ItemStack sbStack,
                                          InteractionHand dstHand, ItemStack dstStack,
                                          Kind dstKind, Content content) {
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
                } else if (compatibleBB(bbMode, dstStack, content)) {
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
                if (dstStack.getItem() == Items.BUCKET) {
                    // SB infinite source fills empty NB
                    ItemStack newNB = toNormalBucket(content);
                    if (!newNB.isEmpty()) {
                        player.setItemInHand(dstHand, newNB);
                        play(level, player, SoundEvents.BUCKET_FILL);
                        award(player, sbStack);
                        return true;
                    }
                }
                // A filled vanilla bucket is already at its 1000 mB capacity and cannot take more.
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

    /**
     * A transferable content: either a real Forge fluid, or milk. Milk isn't a Forge fluid
     * (see NBTUtil's "milk" mode), so it can't be carried as a FluidStack the way generic
     * fluids are; this type keeps the two kinds distinct instead of faking milk as one.
     */
    private static final class Content {
        static final Content EMPTY = new Content(false, false, FluidStack.EMPTY);

        final boolean present;
        final boolean milk;
        final FluidStack fluid;

        private Content(boolean present, boolean milk, FluidStack fluid) {
            this.present = present;
            this.milk = milk;
            this.fluid = fluid;
        }

        static Content milk() {
            return new Content(true, true, FluidStack.EMPTY);
        }

        static Content fluid(FluidStack fluid) {
            return fluid.isEmpty() ? EMPTY : new Content(true, false, fluid);
        }
    }

    private static Content contentOf(ItemStack stack, Kind kind) {
        switch (kind) {
            case NB:
                if (stack.getItem() == Items.MILK_BUCKET) return Content.milk();
                return Content.fluid(NBTUtil.getNormalBucketFluidStack(stack));
            case BB:
            case SB:
                String mode = NBTUtil.getMode(stack);
                if ("milk".equals(mode)) {
                    return Content.milk();
                } else if ("fluid".equals(mode)) {
                    return Content.fluid(NBTUtil.getFluidStack(stack));
                }
                return Content.EMPTY;
            default:
                return Content.EMPTY;
        }
    }

    private static boolean compatibleSB(String sbMode, ItemStack sbStack, Content content) {
        if ("milk".equals(sbMode) && content.milk) return true;
        if ("fluid".equals(sbMode) && !content.milk) {
            FluidStack sbFluid = NBTUtil.getFluidStack(sbStack);
            return sbFluid.isFluidEqual(content.fluid);
        }
        return false;
    }

    private static boolean compatibleBB(String bbMode, ItemStack bbStack, Content content) {
        if ("milk".equals(bbMode) && content.milk) return true;
        if ("fluid".equals(bbMode) && !content.milk) {
            FluidStack bbFluid = NBTUtil.getFluidStack(bbStack);
            return bbFluid.isFluidEqual(content.fluid);
        }
        return false;
    }

    private static ItemStack toNormalBucket(Content content) {
        if (content.milk) {
            return new ItemStack(Items.MILK_BUCKET);
        } else if (content.fluid.getFluid() == Fluids.WATER) {
            return new ItemStack(Items.WATER_BUCKET);
        } else if (content.fluid.getFluid() == Fluids.LAVA) {
            return new ItemStack(Items.LAVA_BUCKET);
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
