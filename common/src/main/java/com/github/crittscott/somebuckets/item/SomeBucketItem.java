package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Base of every Some Buckets item. Each stacks like a vanilla empty bucket
 * ({@value #EMPTY_STACK_SIZE}) while empty and like a filled bucket ({@value #FILLED_STACK_SIZE})
 * once it holds any content; each loader applies {@link #variableMaxStackSize} through its
 * stack-size hook. When a stack is loaded from saved NBT, {@link BucketState#admitLoadedTag} sets
 * aside unloadable storage-bucket entries, restores readable ones, and removes malformed state.
 */
public abstract class SomeBucketItem extends Item {
    public static final int EMPTY_STACK_SIZE = 16;
    public static final int FILLED_STACK_SIZE = 1;

    /** Pixel width of a full item-durability-style bar. */
    public static final int ITEM_BAR_WIDTH = 13;
    /** Bar color of the storage and Mob Buckets. */
    public static final int DEFAULT_BUCKET_BAR_COLOR = 0x3F76E4;

    protected SomeBucketItem(Properties properties) {
        super(properties.stacksTo(EMPTY_STACK_SIZE));
    }

    /** The stack-size limit for {@code stack}: one once it holds any content. */
    public int variableMaxStackSize(ItemStack stack) {
        return BucketState.isEmptyBucket(stack) ? EMPTY_STACK_SIZE : FILLED_STACK_SIZE;
    }

    @Override
    public void verifyTagAfterLoad(CompoundTag tag) {
        BucketState.admitLoadedTag(this, tag);
    }

    /** The sided success result: a client prediction, or a completed server action. */
    public static InteractionResult success(Level level) {
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A bar width proportional to {@code amount} of {@code capacity}, rounded to the nearest pixel. */
    protected static int barWidth(int amount, int capacity) {
        return Math.round(ITEM_BAR_WIDTH * (float) amount / (float) capacity);
    }
}
