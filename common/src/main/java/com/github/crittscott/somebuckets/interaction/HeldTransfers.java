package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.BucketOperations.HeldMove;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Held-hand bucket operations: transfer between one of this mod's fluid buckets and whatever the
 * other hand holds, milk handling, and settling the hand afterward.
 *
 * <p>Any item exposing its loader's fluid storage is a valid transfer partner; the loader moves the
 * fluid through {@link BucketOperations#moveHeldFluid}. Milk is not a loader fluid, so it moves as
 * {@link BucketState} amounts and vanilla milk buckets. A held stack is worked through one item at a
 * time, moving as much as each pair allows. The hand keeps one stack, preferring one that still
 * holds something, and the remainder goes back into the inventory, dropping only what does not fit:
 * a filled container and the empties it left behind cannot occupy the same slot. The same transfer
 * and settlement run client-side for prediction; the server remains authoritative.
 */
public final class HeldTransfers {
    /** Result of an entity interaction performed with a temporary hand stack. */
    public record HeldInteraction(InteractionResult result, ItemStack remaining) {}

    private HeldTransfers() {}

    // ---- Held transfer ----

    /**
     * Gives an off-hand Some Buckets fluid bucket transfer priority on a main-hand use against air
     * while the main hand holds another item, which would otherwise use itself first.
     *
     * @param player acting player
     * @param hand hand the use was made with; only the main hand qualifies
     * @return {@code true} when a transfer happened and the use should be consumed
     */
    public static boolean tryOffHandPriority(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return false;
        ItemStack main = player.getMainHandItem();
        ItemStack bucket = player.getOffhandItem();
        if (main.isEmpty() || isOurs(main) || !isOurs(bucket)) return false;
        if (player.pick(player.blockInteractionRange(), 1.0F, false).getType() != HitResult.Type.MISS) {
            return false;
        }
        return tryTransfer(player.level(), player, InteractionHand.OFF_HAND, bucket,
                InteractionHand.MAIN_HAND, main);
    }

    /**
     * Attempts {@code bucket} to {@code other} first, then {@code other} to {@code bucket} only when
     * the first direction accepts no transfer.
     *
     * @param bucketHand hand holding the Some Buckets container
     * @param bucket the Some Buckets container
     * @param otherHand hand holding the other container
     * @param other the other container
     * @return {@code true} when either ordered one-way attempt succeeds
     */
    public static boolean tryTransfer(Level level, Player player, InteractionHand bucketHand, ItemStack bucket,
                                      InteractionHand otherHand, ItemStack other) {
        if (tryTransferOne(level, player, bucketHand, bucket, otherHand, other)) return true;
        return tryTransferOne(level, player, otherHand, other, bucketHand, bucket);
    }

    /**
     * Attempts one direction of held-item transfer from {@code fromStack} to {@code toStack}. At
     * least one side must be a Big, Huge, or Source Bucket. Success settles the partner hand, plays
     * the transfer sound, and awards the Some Buckets item-use statistic.
     *
     * @param fromHand hand holding the source stack
     * @param fromStack stack drawn from
     * @param toHand hand holding the destination stack
     * @param toStack stack filled into
     * @return {@code true} only when content was accepted and transfer side effects were applied;
     *         {@code false} means no stack, hand, sound, statistic, or world drop changed
     */
    private static boolean tryTransferOne(Level level, Player player, InteractionHand fromHand, ItemStack fromStack,
                                          InteractionHand toHand, ItemStack toStack) {
        if (fromStack == toStack) return false;
        // One side must be ours; two foreign containers are not this mod's business.
        if (isOurs(fromStack)) {
            return BucketState.getMode(fromStack) == BucketState.Mode.MILK
                    ? pourMilk(level, player, fromStack, toHand, toStack)
                    : fillFrom(level, player, fromStack, toHand, toStack);
        }
        if (!isOurs(toStack)) return false;
        // A foreign container emptying into ours is an intake into the held bucket.
        return fillFromHand(level, player, toHand, toStack, bucket -> fromStack.is(Items.MILK_BUCKET)
                ? takeMilk(level, player, fromHand, fromStack, bucket)
                : drainInto(level, player, fromHand, fromStack, bucket));
    }

    /* Fills the containers in the other hand from one of ours. */
    private static boolean fillFrom(Level level, Player player, ItemStack source,
                                    InteractionHand destinationHand, ItemStack destinationStack) {
        if (BucketState.getMode(source) != BucketState.Mode.FLUID) return false;
        // An assigned Source Bucket never runs dry. Its one-bucket-per-call storage is a deliberate
        // machine limit; direct held transfer fills a large destination in one shot.
        boolean unlimited = source.getItem() instanceof SBItem;
        if (unlimited && !SBPolicy.allows(BucketState.getStoredFluid(source).fluid())) return false;

        List<ItemStack> filled = new ArrayList<>();
        int untouched = destinationStack.getCount();
        StoredFluid moved = StoredFluid.EMPTY;
        while (untouched > 0) {
            HeldMove move = BucketOperations.get().moveHeldFluid(source, single(destinationStack), unlimited);
            if (move == null) break;
            moved = move.fluid();
            filled.add(move.to());
            untouched--;
        }
        if (filled.isEmpty()) return false;

        settle(level, player, destinationHand, destinationStack, filled, untouched, HeldTransfers::holdsSomething);
        BucketOperations.get().emptySound(moved).ifPresent(sound -> play(level, player, sound));
        award(player, source);
        return true;
    }

    /* Empties the containers in the other hand into one of ours. */
    private static boolean drainInto(Level level, Player player, InteractionHand sourceHand, ItemStack sourceStack,
                                     ItemStack destination) {
        List<ItemStack> emptied = new ArrayList<>();
        int untouched = sourceStack.getCount();
        StoredFluid moved = StoredFluid.EMPTY;
        while (untouched > 0) {
            HeldMove move = BucketOperations.get().moveHeldFluid(single(sourceStack), destination, false);
            if (move == null) break;
            moved = move.fluid();
            emptied.add(move.from());
            untouched--;
        }
        if (emptied.isEmpty()) return false;

        settle(level, player, sourceHand, sourceStack, emptied, untouched, HeldTransfers::holdsSomething);
        BucketOperations.get().fillSound(moved).ifPresent(sound -> play(level, player, sound));
        award(player, destination);
        return true;
    }

    private static boolean isOurs(ItemStack stack) {
        return stack.getItem() instanceof FluidBucketItem;
    }

    /* Whether a settled stack still carries content. Milk is not a loader fluid, so it is named directly. */
    private static boolean holdsSomething(ItemStack stack) {
        return stack.is(Items.MILK_BUCKET) || BucketOperations.get().holdsFluid(stack);
    }

    /*
     * One item to work on. A stack of one is handed back as-is: our own buckets never stack once
     * filled, and their storage edits the held stack in place. Copying it strands the caller's
     * reference on a stale copy.
     */
    private static ItemStack single(ItemStack stack) {
        return stack.getCount() == 1 ? stack : stack.copyWithCount(1);
    }

    // ---- Milk ----

    /**
     * Pours milk from an assigned milk Source Bucket or a milk-holding Big/Huge Bucket into another
     * Some Buckets container or a stack of empty vanilla buckets.
     *
     * @param source milk-supplying bucket, drained unless it is an infinite Source Bucket
     * @param destinationHand hand holding the destination stack, for settlement
     * @param destinationStack container or empty-bucket stack receiving the milk
     * @return {@code true} when milk was moved and the transfer's side effects were applied
     */
    private static boolean pourMilk(Level level, Player player, ItemStack source,
                                    InteractionHand destinationHand, ItemStack destinationStack) {
        boolean infinite = source.getItem() instanceof SBItem;
        if ((infinite || destinationStack.getItem() instanceof SBItem) && !SBPolicy.allowsMilk()) return false;
        int stored = BucketState.getAmount(source);

        if (destinationStack.getItem() instanceof FluidBucketItem destinationItem) {
            BucketState.Mode mode = BucketState.getMode(destinationStack);
            if (mode != BucketState.Mode.NONE && mode != BucketState.Mode.MILK) return false;

            // An assigned milk Source Bucket sinks a unit and keeps nothing. Two unlimited
            // supplies have nothing to exchange, so an infinite source is excluded.
            if (isInfiniteMilkSink(destinationStack) && !infinite) {
                BucketState.drainFiniteContent(source, FluidBucketItem.BUCKET_VOLUME_MB);
                play(level, player, SoundEvents.BUCKET_EMPTY);
                award(player, source);
                return true;
            }

            int held = mode == BucketState.Mode.MILK ? BucketState.getAmount(destinationStack) : 0;
            int room = destinationItem.getCapacityMb() - held;
            int moved = Math.min(room, infinite ? room : stored)
                    / FluidBucketItem.BUCKET_VOLUME_MB * FluidBucketItem.BUCKET_VOLUME_MB;
            if (moved <= 0) return false;

            BucketState.setMilkAmount(destinationStack, held + moved);
            if (!infinite) {
                BucketState.drainFiniteContent(source, moved);
            }
            play(level, player, SoundEvents.BUCKET_FILL);
            award(player, source);
            return true;
        }

        if (destinationStack.getItem() != Items.BUCKET) return false;

        int units = infinite ? destinationStack.getCount()
                : Math.min(destinationStack.getCount(), stored / FluidBucketItem.BUCKET_VOLUME_MB);
        units = Math.min(units, new ItemStack(Items.MILK_BUCKET).getMaxStackSize());
        if (units <= 0) return false;

        List<ItemStack> filled = new ArrayList<>();
        for (int i = 0; i < units; i++) filled.add(new ItemStack(Items.MILK_BUCKET));

        if (!infinite) {
            BucketState.drainFiniteContent(source, units * FluidBucketItem.BUCKET_VOLUME_MB);
        }
        settle(level, player, destinationHand, destinationStack, filled,
                destinationStack.getCount() - units, HeldTransfers::holdsMilk);
        play(level, player, SoundEvents.BUCKET_EMPTY);
        award(player, source);
        return true;
    }

    /**
     * Takes milk from a stack of filled vanilla milk buckets into an assigned milk Source Bucket or
     * a milk-holding Big/Huge Bucket.
     *
     * @param sourceHand hand holding the milk-bucket stack, for settlement
     * @param sourceStack milk-bucket stack drawn from
     * @param destination bucket receiving the milk
     * @return {@code true} when milk was moved and the transfer's side effects were applied
     */
    private static boolean takeMilk(Level level, Player player, InteractionHand sourceHand, ItemStack sourceStack,
                                    ItemStack destination) {
        if (destination.getItem() instanceof SBItem && !SBPolicy.allowsMilk()) return false;
        BucketState.Mode mode = BucketState.getMode(destination);
        if (mode != BucketState.Mode.NONE && mode != BucketState.Mode.MILK) return false;

        // An assigned milk Source Bucket has no room to report but takes a unit all the same.
        boolean sink = isInfiniteMilkSink(destination);
        int held = mode == BucketState.Mode.MILK ? BucketState.getAmount(destination) : 0;
        int room = ((FluidBucketItem) destination.getItem()).getCapacityMb() - held;

        int units = Math.min(sourceStack.getCount(), sink ? 1 : room / FluidBucketItem.BUCKET_VOLUME_MB);
        if (units <= 0) return false;

        if (!sink) BucketState.setMilkAmount(destination, held + units * FluidBucketItem.BUCKET_VOLUME_MB);

        List<ItemStack> emptied = new ArrayList<>();
        for (int i = 0; i < units; i++) emptied.add(new ItemStack(Items.BUCKET));

        settle(level, player, sourceHand, sourceStack, emptied,
                sourceStack.getCount() - units, HeldTransfers::holdsMilk);
        play(level, player, SoundEvents.BUCKET_FILL);
        award(player, destination);
        return true;
    }

    /**
     * Milks {@code cow} through its own {@link net.minecraft.world.entity.Mob#interact interaction},
     * so vanilla and modded {@code mobInteract} behavior — the milking sound, any cooldown, a
     * replaced drop — decides the outcome. A one-count vanilla bucket stands in for the caller's
     * bucket for the duration of the call and is swapped back afterward; the caller records the milk
     * unit on its own stack. A milk bucket that {@code Cow.mobInteract} hands a creative player via
     * {@code ItemUtils.createFilledResult} is removed again, since this helper only reports the
     * milking.
     *
     * @param cow cow to milk
     * @param player acting player; a one-count vanilla bucket is swapped into {@code hand} for the call
     * @param hand hand holding the caller's bucket
     * @return {@code true} iff the cow interaction consumed the action
     */
    public static boolean milkCow(Cow cow, Player player, InteractionHand hand) {
        boolean creative = player.getAbilities().instabuild;
        int milkBefore = creative ? countMilkBuckets(player) : 0;
        HeldInteraction interaction = interactHolding(player, hand, new ItemStack(Items.BUCKET), cow);
        if (!interaction.result().consumesAction()) return false;
        if (creative && countMilkBuckets(player) > milkBefore) removeOneMilkBucket(player);
        return true;
    }

    /** Interacts with {@code target} while temporarily presenting {@code probe} in {@code hand}. */
    public static HeldInteraction interactHolding(Player player, InteractionHand hand,
                                                  ItemStack probe, Entity target) {
        ItemStack restore = player.getItemInHand(hand);
        player.setItemInHand(hand, probe);
        try {
            InteractionResult result = target.interact(player, hand);
            return new HeldInteraction(result, player.getItemInHand(hand));
        } finally {
            player.setItemInHand(hand, restore);
        }
    }

    private static int countMilkBuckets(Player player) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.MILK_BUCKET)) total += inventory.getItem(slot).getCount();
        }
        return total;
    }

    private static void removeOneMilkBucket(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.MILK_BUCKET)) {
                inventory.removeItem(slot, 1);
                return;
            }
        }
    }

    /* Whether stack still holds milk, used to decide which settled pile entry stays in hand. */
    private static boolean holdsMilk(ItemStack stack) {
        return stack.is(Items.MILK_BUCKET);
    }

    /* An assigned Source Bucket takes without limit and keeps nothing: a unit poured in is gone. */
    private static boolean isInfiniteMilkSink(ItemStack stack) {
        return stack.getItem() instanceof SBItem && BucketState.getMode(stack) == BucketState.Mode.MILK;
    }

    // ---- Settlement ----

    /**
     * Runs a player intake against the held bucket. A bucket with content, or a lone empty bucket
     * of a player without infinite materials, is operated on in place; that is the result
     * {@link ItemUtils#createFilledResult} would produce, and it keeps the held stack from emptying,
     * which {@code Player.interactOn} would treat as a used-up item. Otherwise the empty bucket is
     * operated on as a one-count copy; on success the server settles it through
     * {@code createFilledResult}, which consumes one empty and places the filled copy in the
     * inventory, dropping it when nothing fits, or in creative keeps the empties and adds the filled
     * copy unless an identical one is already held. The client only predicts, since intake never
     * mutates there.
     *
     * @param level acting level; settlement happens on the server only
     * @param player acting player
     * @param hand hand holding {@code held}
     * @param held the held bucket stack
     * @param fill the intake, applied to the stack it is given; returns whether it succeeded
     * @return the intake's result
     */
    public static boolean fillFromHand(Level level, Player player, InteractionHand hand,
                                       ItemStack held, Predicate<ItemStack> fill) {
        if (!BucketState.isEmptyBucket(held)
                || (held.getCount() == 1 && !player.hasInfiniteMaterials())) {
            return fill.test(held);
        }
        ItemStack working = held.copyWithCount(1);
        if (!fill.test(working)) return false;
        if (!level.isClientSide) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, working));
        }
        return true;
    }

    /*
     * Rebuilds the hand contents after a stack-wide transfer. Results pile by matching item and
     * components without exceeding a pile entry's stack limit, and the untouched remainder of
     * original is appended as its own entry. The first entry holdsSomething accepts stays in hand;
     * on the server every other entry is placed back into the inventory, and any part that does not
     * fit is dropped. Runs on both sides for prediction.
     */
    private static void settle(Level level, Player player, InteractionHand hand, ItemStack original,
                               List<ItemStack> results, int untouched, Predicate<ItemStack> holdsSomething) {
        List<ItemStack> pile = new ArrayList<>();
        for (ItemStack result : results) {
            boolean merged = false;
            for (ItemStack existing : pile) {
                if (ItemStack.isSameItemSameComponents(existing, result)
                        && existing.getCount() + result.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(result.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) pile.add(result);
        }

        if (untouched > 0) pile.add(original.copyWithCount(untouched));

        int held = 0;
        for (int i = 0; i < pile.size(); i++) {
            if (holdsSomething.test(pile.get(i))) {
                held = i;
                break;
            }
        }

        player.setItemInHand(hand, pile.get(held));
        if (level.isClientSide) return;
        for (int i = 0; i < pile.size(); i++) {
            if (i != held) player.getInventory().placeItemBackInInventory(pile.get(i));
        }
    }

    private static void play(Level level, Player player, SoundEvent sound) {
        level.playSound(player, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void award(Player player, ItemStack usedStack) {
        player.awardStat(Stats.ITEM_USED.get(usedStack.getItem()));
    }
}
