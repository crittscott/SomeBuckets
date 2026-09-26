package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.LegacyBucketMigration;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;

/**
 * Runs one bucket operation against one item and settles its result back into the dispenser.
 * Empty Some Buckets items may be stacked, but any successful intake makes the operated item
 * unstackable; the remaining empty items therefore stay in the selected slot while vanilla's
 * {@code consumeWithRemainder} inserts the result elsewhere or ejects it when the dispenser is full.
 */
public abstract class BucketDispenseBehavior extends OptionalDispenseItemBehavior {
    @Override
    protected final ItemStack execute(BlockSource source, ItemStack stack) {
        if (!BucketState.discardInvalidState(stack)) {
            setSuccess(false);
            return stack;
        }
        LegacyBucketMigration.migrate(stack, source.level(),
                () -> "dispenser " + source.pos() + " in " + source.level().dimension().location());
        ItemStack working = stack.copyWithCount(1);
        boolean success = executeBucket(source, working);
        setSuccess(success);
        return success ? consumeWithRemainder(source, stack, working) : stack;
    }

    /** Mutates {@code stack} only when the corresponding world operation succeeds. */
    protected abstract boolean executeBucket(BlockSource source, ItemStack stack);
}
