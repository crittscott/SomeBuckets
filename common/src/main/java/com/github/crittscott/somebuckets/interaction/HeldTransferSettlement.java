package com.github.crittscott.somebuckets.interaction;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Settles a stack-wide held-item transfer: piles same-item-and-tag results without exceeding any
 * pile entry's stack limit, appends the untouched remainder of {@code original} as its own entry,
 * keeps the first pile entry {@code holdsSomething} accepts in {@code hand}, and drops everything
 * else at the player's feet on the server.
 */
public final class HeldTransferSettlement {
    private HeldTransferSettlement() {}

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
