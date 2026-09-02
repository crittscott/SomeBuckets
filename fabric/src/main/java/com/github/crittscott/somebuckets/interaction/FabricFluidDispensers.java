package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

/** Fabric fluid and powder dispenser behavior backed by the shared bucket fluid logic. */
public final class FabricFluidDispensers {
    private FabricFluidDispensers() {}

    public static void register(Item big8, Item big64, Item sourceBucket) {
        DefaultDispenseItemBehavior finite = new FiniteBehavior();
        DispenserBlock.registerBehavior(big8, finite);
        DispenserBlock.registerBehavior(big64, finite);
        DispenserBlock.registerBehavior(sourceBucket, new SourceBehavior());
    }

    private static final class FiniteBehavior extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            BucketState.Mode mode = BucketState.getMode(stack);
            if (mode == BucketState.Mode.POWDER_SNOW && BBFluidLogic.tryPlacePowder(
                    target.level(), target.hit(), stack, target.context(), false)) {
                return stack;
            }
            if ((mode == BucketState.Mode.NONE || mode == BucketState.Mode.POWDER_SNOW)
                    && PowderSnowCauldrons.take(target.level(), target.front(), target.face(), stack,
                    ((BBItem) stack.getItem()).getCapacityUnits(), target.context())) {
                return stack;
            }

            int amount = BucketState.getStoredFluid(stack).amount();
            int capacity = ((BBItem) stack.getItem()).getCapacityMb();
            if (mode == BucketState.Mode.NONE || (mode == BucketState.Mode.FLUID && amount < capacity)) {
                if (BBFluidLogic.tryTakeWithContext(target.level(), target.hit(), stack,
                        target.context())) return stack;
                if (BBFluidLogic.tryTakePowderWithContext(target.level(), target.hit(), stack,
                        target.context())) return stack;
            }
            if (mode == BucketState.Mode.FLUID && amount >= FluidBucketItem.BUCKET_VOLUME_MB) {
                BBFluidLogic.tryPlace(target.level(), target.hit(), stack, target.context(), false);
            }
            return stack;
        }
    }

    private static final class SourceBehavior extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            if (BucketState.getMode(stack) == BucketState.Mode.FLUID) {
                BucketOperations.SourceTarget sourceTarget = SBFluidLogic.classifyTarget(
                        target.level(), target.hit(), stack);
                if (sourceTarget == BucketOperations.SourceTarget.MATCHING_FLUID) {
                    SBFluidLogic.tryTakeWithContext(target.level(), target.hit(), stack, target.context());
                } else {
                    SBFluidLogic.tryPlace(target.level(), target.hit(), stack, target.context(), false);
                }
            } else if (BucketState.getMode(stack) == BucketState.Mode.NONE
                    && !SBFluidLogic.tryMilkDispenser(target.level(), target.front(), target.face(), stack,
                    target.context())) {
                SBFluidLogic.tryTakeWithContext(target.level(), target.hit(), stack, target.context());
            }
            return stack;
        }
    }
}
