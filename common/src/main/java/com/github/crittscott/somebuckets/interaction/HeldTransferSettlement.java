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
 * Settles what a player's intake or held-item transfer leaves in hand. A player intake onto a held
 * Some Buckets item goes through {@link #fillFromHand}, so a stack of empty buckets fills one. A
 * stack-wide transfer goes through {@link #settle}: it piles same-item-and-tag results without
 * exceeding any pile entry's stack limit, appends the untouched remainder of {@code original} as its
 * own entry, keeps the first pile entry {@code holdsSomething} accepts in {@code hand}, and drops
 * everything else at the player's feet on the server.
 */
public final class HeldTransferSettlement {
    private HeldTransferSettlement() {}

    /**
     * Runs a player intake against the held bucket, filling one bucket from a stack of empty ones
     * as a vanilla bucket does. A bucket with content, or a lone empty bucket of a survival player,
     * is operated on in place; that matches what {@link ItemUtils#createFilledResult} would produce
     * and keeps the held stack from emptying, which {@code Player.interactOn} would treat as a
     * used-up item. Otherwise the intake runs on a one-count copy, and on success the server settles
     * it through {@code createFilledResult}: survival takes one empty from the stack and puts the
     * filled copy in the inventory, dropping it when nothing fits; creative keeps the empties and adds
     * the filled copy unless an identical one is already there. The client only predicts.
     *
     * @param level acting level; the hand is settled on the server only
     * @param player acting player
     * @param hand hand holding {@code held}
     * @param held the stack the intake was aimed at; may be the copy {@code Player.interactOn} hands a
     *             creative player, and is then placed back in {@code hand}
     * @param fill the intake, applied to the stack it is given; returns whether it succeeded
     * @return the intake's result
     */
    public static boolean fillFromHand(Level level, Player player, InteractionHand hand, ItemStack held,
                                       Predicate<ItemStack> fill) {
        if (!BucketState.isEmptyBucket(held)
                || (held.getCount() == 1 && !player.getAbilities().instabuild)) {
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
     * dropped at the player's feet. Runs on both sides for prediction.
     *
     * @param level acting level; drops happen on the server only
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
            if (i != held) player.drop(pile.get(i), false);
        }
    }
}
