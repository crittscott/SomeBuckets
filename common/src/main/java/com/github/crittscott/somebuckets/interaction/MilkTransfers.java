package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Milk-specific held-transfer rules shared by every loader. Milk is neither a Forge fluid nor a
 * Fabric {@code FluidVariant}, so it is moved as plain {@link BucketState} amounts and vanilla milk
 * buckets instead of through either loader's fluid system.
 */
public final class MilkTransfers {
    private MilkTransfers() {}

    /**
     * Pours milk from an assigned milk Source Bucket or a milk-holding Big/Huge Bucket into another
     * Some Buckets container or a stack of empty vanilla buckets. A stack of empty Some Buckets
     * containers fills one.
     *
     * @param level acting level
     * @param player acting player, credited with the item-use statistic and hearing the sound
     * @param source milk-supplying bucket, drained unless it is an infinite Source Bucket
     * @param destinationHand hand holding the destination stack, for settlement
     * @param destinationStack container or empty-bucket stack receiving the milk
     * @return {@code true} when milk was moved and the transfer's side effects were applied
     */
    public static boolean pourMilk(Level level, Player player, ItemStack source,
                                   InteractionHand destinationHand, ItemStack destinationStack) {
        boolean infinite = source.getItem() instanceof SBItem;
        if ((infinite || destinationStack.getItem() instanceof SBItem) && !SBPolicy.allowsMilk()) return false;
        int stored = BucketState.getAmount(source);
        if (!infinite && stored < FluidBucketItem.BUCKET_VOLUME_MB) return false;

        if (destinationStack.getItem() instanceof FluidBucketItem) {
            BucketState.Mode mode = BucketState.getMode(destinationStack);
            if (mode != BucketState.Mode.NONE && mode != BucketState.Mode.MILK) return false;

            // An assigned milk Source Bucket sinks a unit and keeps nothing. Two unlimited
            // supplies have nothing to exchange, so an infinite source is excluded.
            if (isInfiniteSink(destinationStack) && !infinite) {
                BucketState.drainFiniteContent(source, FluidBucketItem.BUCKET_VOLUME_MB);
                play(level, player, SoundEvents.BUCKET_EMPTY);
                award(player, source);
                return true;
            }

            int room = milkRoomMb(destinationStack);
            int moved = Math.min(room, infinite ? room : stored)
                    / FluidBucketItem.BUCKET_VOLUME_MB * FluidBucketItem.BUCKET_VOLUME_MB;
            if (moved <= 0) return false;

            HeldTransferSettlement.fillFromHand(level, player, destinationHand, destinationStack, bucket -> {
                addMilk(bucket, moved);
                return true;
            });
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
        HeldTransferSettlement.settle(level, player, destinationHand, destinationStack, filled,
                destinationStack.getCount() - units, MilkTransfers::holdsMilk);
        play(level, player, SoundEvents.BUCKET_EMPTY);
        award(player, source);
        return true;
    }

    /**
     * Takes milk from a stack of filled vanilla milk buckets into an assigned milk Source Bucket or
     * a milk-holding Big/Huge Bucket. A stack of empty buckets fills one.
     *
     * @param level acting level
     * @param player acting player, credited with the item-use statistic and hearing the sound
     * @param sourceHand hand holding the milk-bucket stack, for settlement
     * @param sourceStack milk-bucket stack drawn from
     * @param destinationHand hand holding the destination bucket
     * @param destination bucket receiving the milk
     * @return {@code true} when milk was moved and the transfer's side effects were applied
     */
    public static boolean takeMilk(Level level, Player player, InteractionHand sourceHand, ItemStack sourceStack,
                                   InteractionHand destinationHand, ItemStack destination) {
        if (destination.getItem() instanceof SBItem && !SBPolicy.allowsMilk()) return false;
        BucketState.Mode mode = BucketState.getMode(destination);
        if (mode != BucketState.Mode.NONE && mode != BucketState.Mode.MILK) return false;

        // An assigned milk Source Bucket has no room to report but takes a unit all the same.
        boolean sink = isInfiniteSink(destination);
        int room = milkRoomMb(destination);

        int units = Math.min(sourceStack.getCount(), sink ? 1 : room / FluidBucketItem.BUCKET_VOLUME_MB);
        if (units <= 0) return false;

        if (!sink) {
            HeldTransferSettlement.fillFromHand(level, player, destinationHand, destination, bucket -> {
                addMilk(bucket, units * FluidBucketItem.BUCKET_VOLUME_MB);
                return true;
            });
        }

        List<ItemStack> emptied = new ArrayList<>();
        for (int i = 0; i < units; i++) emptied.add(new ItemStack(Items.BUCKET));

        HeldTransferSettlement.settle(level, player, sourceHand, sourceStack, emptied,
                sourceStack.getCount() - units, MilkTransfers::holdsMilk);
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
        ItemStack restore = player.getItemInHand(hand);
        boolean creative = player.getAbilities().instabuild;
        int milkBefore = creative ? countMilkBuckets(player) : 0;
        player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        InteractionResult result;
        try {
            result = cow.interact(player, hand);
        } finally {
            player.setItemInHand(hand, restore);
        }
        if (!result.consumesAction()) return false;
        if (creative && countMilkBuckets(player) > milkBefore) removeOneMilkBucket(player);
        return true;
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

    /** Whether {@code stack} still holds milk, used to decide which settled pile entry stays in hand. */
    private static boolean holdsMilk(ItemStack stack) {
        return stack.is(Items.MILK_BUCKET);
    }

    /** An assigned Source Bucket takes without limit and keeps nothing: a unit poured in is gone. */
    private static boolean isInfiniteSink(ItemStack stack) {
        return stack.getItem() instanceof SBItem && BucketState.getMode(stack) == BucketState.Mode.MILK;
    }

    private static int capacityOf(ItemStack stack) {
        return stack.getItem() instanceof BBItem big ? big.getCapacityMb() : FluidBucketItem.BUCKET_VOLUME_MB;
    }

    /**
     * Adds milk to a Some Buckets container, selecting milk mode on an empty one. The caller has
     * checked {@link #milkRoomMb}.
     */
    public static void addMilk(ItemStack stack, int mb) {
        int held = BucketState.getMode(stack) == BucketState.Mode.MILK ? BucketState.getAmount(stack) : 0;
        BucketState.setMilkAmount(stack, held + mb);
    }

    /**
     * Room for milk in a Some Buckets container, in millibuckets: its whole capacity when empty, the
     * unfilled part when it holds milk, and none when it holds anything else. A Source Bucket's
     * capacity is one bucket, so an assigned milk Source Bucket reports none.
     */
    public static int milkRoomMb(ItemStack stack) {
        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode != BucketState.Mode.NONE && mode != BucketState.Mode.MILK) return 0;
        return capacityOf(stack) - (mode == BucketState.Mode.MILK ? BucketState.getAmount(stack) : 0);
    }

    private static void play(Level level, Player player, SoundEvent sound) {
        level.playSound(player, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void award(Player player, ItemStack usedStack) {
        player.awardStat(Stats.ITEM_USED.get(usedStack.getItem()));
    }
}
