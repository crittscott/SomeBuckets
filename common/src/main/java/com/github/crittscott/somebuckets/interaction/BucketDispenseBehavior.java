package com.github.crittscott.somebuckets.interaction;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

/**
 * Runs one bucket operation against one item and settles its result back into the dispenser.
 * Empty Some Buckets items may be stacked, but any successful intake makes the operated item
 * unstackable; the remaining empty items therefore stay in the selected slot while the result is
 * inserted elsewhere or ejected when the dispenser is full.
 */
public abstract class BucketDispenseBehavior extends OptionalDispenseItemBehavior {
    private static final int RESULT_EJECTION_SPEED = 6;

    @Override
    protected final ItemStack execute(BlockSource source, ItemStack stack) {
        ItemStack working = stack.copyWithCount(1);
        boolean success = executeBucket(source, working);
        setSuccess(success);
        if (!success) return stack;
        if (stack.getCount() == 1) return working;

        stack.shrink(1);
        if (!insertIntoEmptySlot(source, working)) {
            Position position = DispenserBlock.getDispensePosition(source);
            Direction direction = source.state().getValue(DispenserBlock.FACING);
            spawnItem(source.level(), working, RESULT_EJECTION_SPEED, direction, position);
        }
        return stack;
    }

    private static boolean insertIntoEmptySlot(BlockSource source, ItemStack result) {
        for (int slot = 0; slot < source.blockEntity().getContainerSize(); slot++) {
            if (source.blockEntity().getItem(slot).isEmpty()) {
                source.blockEntity().setItem(slot, result);
                return true;
            }
        }
        return false;
    }

    /** Mutates {@code stack} only when the corresponding world operation succeeds. */
    protected abstract boolean executeBucket(BlockSource source, ItemStack stack);
}
