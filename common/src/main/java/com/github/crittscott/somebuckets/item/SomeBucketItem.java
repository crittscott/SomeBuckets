package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Base of every Some Buckets item. Each stacks like a vanilla empty bucket
 * ({@value #EMPTY_STACK_SIZE}) while empty and like a filled bucket ({@value #FILLED_STACK_SIZE})
 * once it holds any content; {@code BucketState} writes the matching {@code MAX_STACK_SIZE}
 * component whenever content changes. When a stack is decoded, storage-bucket entries that are
 * readable again are restored and malformed state is discarded.
 */
public abstract class SomeBucketItem extends Item {
    public static final int EMPTY_STACK_SIZE = 16;
    public static final int FILLED_STACK_SIZE = 1;

    /** Pixel width of a full item-durability-style bar. */
    public static final int ITEM_BAR_WIDTH = 13;
    /** Fallback bar color used when content has no fluid-derived tint. */
    public static final int DEFAULT_BUCKET_BAR_COLOR = 0x4A90E2;

    protected SomeBucketItem(Properties properties) {
        super(properties.stacksTo(EMPTY_STACK_SIZE));
    }

    /**
     * Restores readable set-aside storage-bucket entries, then discards malformed Some Buckets state,
     * when the stack is decoded from storage or the network.
     */
    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
        BucketState.restoreSetAside(stack);
        BucketState.discardInvalidStructure(stack);
    }

    /** The sided success result: a client prediction, or a completed server action. */
    public static InteractionResult success(Level level) {
        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    /** A bar width proportional to {@code amount} of {@code capacity}, rounded to the nearest pixel. */
    protected static int barWidth(int amount, int capacity) {
        return Math.round(ITEM_BAR_WIDTH * (float) amount / (float) capacity);
    }
}
