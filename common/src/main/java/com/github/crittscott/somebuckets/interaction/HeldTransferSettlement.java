package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Settles the held hand after a player bucket operation.
 *
 * <p>A stack-wide held-item transfer piles same-item-and-tag results without exceeding any pile
 * entry's stack limit, appends the untouched remainder of {@code original} as its own entry, keeps
 * the first pile entry {@code holdsSomething} accepts in {@code hand}, and on the server returns
 * everything else to the inventory the way vanilla container conversion does, dropping only what
 * does not fit.
 *
 * <p>An intake into an empty, possibly stacked, bucket fills a one-count copy and settles it the way
 * vanilla fills a bucket.
 */
public final class HeldTransferSettlement {
    private HeldTransferSettlement() {}

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

    /**
     * Rebuilds the {@code hand} contents after a stack-wide transfer. The first surviving pile entry
     * {@code holdsSomething} accepts stays in {@code hand}; on the server every other entry is
     * placed back into the inventory, and any part that does not fit is dropped. Runs on both sides
     * for prediction.
     *
     * @param level acting level; inventory placement happens on the server only
     * @param player player whose hand is rebuilt
     * @param hand hand the settled stack is placed back into
     * @param original the pre-transfer hand stack, copied for the untouched remainder
     * @param results the containers the transfer produced, one per processed item; piled by matching
     *                item and components without exceeding a pile entry's stack limit
     * @param untouched count of the original stack the transfer never reached, re-added as a copy of
     *                  {@code original}
     * @param holdsSomething predicate selecting which pile entry is kept in hand
     */
    public static void settle(Level level, Player player, InteractionHand hand, ItemStack original,
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

        if (untouched > 0) {
            ItemStack rest = original.copy();
            rest.setCount(untouched);
            pile.add(rest);
        }

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
}
