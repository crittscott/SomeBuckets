package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Milk-specific held-transfer rules shared by both loaders. Milk is neither a Forge fluid nor a
 * Fabric {@code FluidVariant}, so it is moved as plain {@link NBTUtil} amounts and vanilla milk
 * buckets instead of through either loader's fluid system.
 */
public final class MilkTransfers {
    private MilkTransfers() {}

    /**
     * Pours milk from an assigned milk Source Bucket or a milk-holding Big/Huge Bucket into another
     * Some Buckets container or a stack of empty vanilla buckets.
     *
     * @return {@code true} when milk was moved and the transfer's side effects were applied
     */
    public static boolean pourMilk(Level level, Player player, ItemStack source,
                                   InteractionHand destinationHand, ItemStack destinationStack) {
        boolean infinite = source.getItem() instanceof SBItem;
        if ((infinite || destinationStack.getItem() instanceof SBItem) && !SBPolicy.allowsMilk()) return false;
        int stored = NBTUtil.getAmount(source);
        if (!infinite && stored < FluidBucketItem.BUCKET_VOLUME_MB) return false;

        if (destinationStack.getItem() instanceof FluidBucketItem) {
            NBTUtil.Mode mode = NBTUtil.getMode(destinationStack);
            if (mode != NBTUtil.Mode.NONE && mode != NBTUtil.Mode.MILK) return false;

            // An assigned milk Source Bucket sinks a unit and keeps nothing. Two unlimited
            // supplies have nothing to exchange, so an infinite source is excluded.
            if (isInfiniteSink(destinationStack) && !infinite) {
                NBTUtil.drainFiniteContent(source, FluidBucketItem.BUCKET_VOLUME_MB);
                play(level, player, SoundEvents.BUCKET_EMPTY);
                award(player, source);
                return true;
            }

            int held = mode == NBTUtil.Mode.MILK ? NBTUtil.getAmount(destinationStack) : 0;
            int room = capacityOf(destinationStack) - held;
            int moved = Math.min(room, infinite ? room : stored)
                    / FluidBucketItem.BUCKET_VOLUME_MB * FluidBucketItem.BUCKET_VOLUME_MB;
            if (moved <= 0) return false;

            NBTUtil.setMilkAmount(destinationStack, held + moved);
            if (!infinite) {
                NBTUtil.drainFiniteContent(source, moved);
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
            NBTUtil.drainFiniteContent(source, units * FluidBucketItem.BUCKET_VOLUME_MB);
        }
        HeldTransferSettlement.settle(level, player, destinationHand, destinationStack, filled,
                destinationStack.getCount() - units, MilkTransfers::holdsMilk);
        play(level, player, SoundEvents.BUCKET_EMPTY);
        award(player, source);
        return true;
    }

    /**
     * Takes milk from a stack of filled vanilla milk buckets into an assigned milk Source Bucket or
     * a milk-holding Big/Huge Bucket.
     *
     * @return {@code true} when milk was moved and the transfer's side effects were applied
     */
    public static boolean takeMilk(Level level, Player player, InteractionHand sourceHand, ItemStack sourceStack,
                                   ItemStack destination) {
        if (destination.getItem() instanceof SBItem && !SBPolicy.allowsMilk()) return false;
        NBTUtil.Mode mode = NBTUtil.getMode(destination);
        if (mode != NBTUtil.Mode.NONE && mode != NBTUtil.Mode.MILK) return false;

        // An assigned milk Source Bucket has no room to report but takes a unit all the same.
        boolean sink = isInfiniteSink(destination);
        int held = mode == NBTUtil.Mode.MILK ? NBTUtil.getAmount(destination) : 0;
        int room = capacityOf(destination) - held;

        int units = Math.min(sourceStack.getCount(), sink ? 1 : room / FluidBucketItem.BUCKET_VOLUME_MB);
        if (units <= 0) return false;

        if (!sink) NBTUtil.setMilkAmount(destination, held + units * FluidBucketItem.BUCKET_VOLUME_MB);

        List<ItemStack> emptied = new ArrayList<>();
        for (int i = 0; i < units; i++) emptied.add(new ItemStack(Items.BUCKET));

        HeldTransferSettlement.settle(level, player, sourceHand, sourceStack, emptied,
                sourceStack.getCount() - units, MilkTransfers::holdsMilk);
        play(level, player, SoundEvents.BUCKET_FILL);
        award(player, destination);
        return true;
    }

    /** Whether {@code stack} still holds milk, used to decide which settled pile entry stays in hand. */
    private static boolean holdsMilk(ItemStack stack) {
        return stack.is(Items.MILK_BUCKET);
    }

    /** An assigned Source Bucket takes without limit and keeps nothing: a unit poured in is gone. */
    private static boolean isInfiniteSink(ItemStack stack) {
        return stack.getItem() instanceof SBItem && NBTUtil.getMode(stack) == NBTUtil.Mode.MILK;
    }

    private static int capacityOf(ItemStack stack) {
        return stack.getItem() instanceof BBItem big ? big.getCapacityMb() : FluidBucketItem.BUCKET_VOLUME_MB;
    }

    private static void play(Level level, Player player, SoundEvent sound) {
        level.playSound(player, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void award(Player player, ItemStack usedStack) {
        player.awardStat(Stats.ITEM_USED.get(usedStack.getItem()));
    }
}
