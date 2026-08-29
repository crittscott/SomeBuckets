package com.github.crittscott.somebuckets.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Copies a bucket's entire persistent stack state between two {@link ItemStack}s in place. That
 * state is the {@code minecraft:custom_data} payload owned by {@link NBTUtil} plus the
 * {@code minecraft:max_stack_size} component {@link NBTUtil} maintains at its write boundary, along
 * with the stack count. Used to settle a working copy back onto the real stack a transaction or a
 * held transfer operates on.
 */
public final class BucketStackState {
    private BucketStackState() {}

    /** Overwrites {@code target}'s count and bucket state with a detached copy of {@code source}'s. */
    public static void copy(ItemStack source, ItemStack target) {
        target.setCount(source.getCount());

        CustomData data = source.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            target.remove(DataComponents.CUSTOM_DATA);
        } else {
            target.set(DataComponents.CUSTOM_DATA, CustomData.of(data.copyTag()));
        }

        Integer maxStackSize = source.get(DataComponents.MAX_STACK_SIZE);
        if (maxStackSize == null) {
            target.remove(DataComponents.MAX_STACK_SIZE);
        } else {
            target.set(DataComponents.MAX_STACK_SIZE, maxStackSize);
        }
    }
}
