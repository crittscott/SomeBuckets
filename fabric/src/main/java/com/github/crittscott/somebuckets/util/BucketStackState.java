package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Copies a bucket's entire persistent stack state between two {@link ItemStack}s in place. That
 * state is the registered bucket-state components owned by {@link ModDataComponentTypes} plus the
 * vanilla {@code minecraft:max_stack_size} component {@code BucketState} maintains alongside them, along
 * with the stack count. Used to settle a working copy back onto the real stack a Transfer API
 * transaction or a held transfer operates on.
 */
public final class BucketStackState {
    private BucketStackState() {}

    /**
     * Overwrites {@code target}'s count and bucket state with {@code source}'s.
     *
     * @param source stack to copy state from
     * @param target stack to overwrite in place
     */
    public static void copy(ItemStack source, ItemStack target) {
        target.setCount(source.getCount());
        ModDataComponentTypes.forEach((id, type) -> copyComponent(source, target, type));
        copyComponent(source, target, DataComponents.MAX_STACK_SIZE);
    }

    private static <T> void copyComponent(ItemStack source, ItemStack target, DataComponentType<T> type) {
        T value = source.get(type);
        if (value == null) {
            target.remove(type);
        } else {
            target.set(type, value);
        }
    }
}
