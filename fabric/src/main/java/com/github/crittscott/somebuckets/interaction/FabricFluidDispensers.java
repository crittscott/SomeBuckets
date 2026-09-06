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

    private static final class FiniteBehavior extends BucketDispenseBehavior {
        @Override
        protected boolean executeBucket(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            BucketState.Mode mode = BucketState.getMode(stack);
            if (mode == BucketState.Mode.POWDER_SNOW && BBFluidLogic.tryPlacePowder(
                    target.level(), target.hit(), stack, target.context(), false)) {
                return true;
            }
            if ((mode == BucketState.Mode.NONE || mode == BucketState.Mode.POWDER_SNOW)
                    && PowderSnowCauldrons.take(target.level(), target.front(), target.face(), stack,
                    ((BBItem) stack.getItem()).getCapacityUnits(), target.context())) {
                return true;
            }

            int amount = BucketState.getStoredFluid(stack).amount();
            int capacity = ((BBItem) stack.getItem()).getCapacityMb();
            if (mode == BucketState.Mode.NONE || (mode == BucketState.Mode.FLUID && amount < capacity)) {
                if (BBFluidLogic.tryTakeWithContext(target.level(), target.hit(), stack,
                        target.context())) return true;
                if (BBFluidLogic.tryTakePowderWithContext(target.level(), target.hit(), stack,
                        target.context())) return true;
            }
            if (mode == BucketState.Mode.FLUID && amount >= FluidBucketItem.BUCKET_VOLUME_MB) {
                return BBFluidLogic.tryPlace(target.level(), target.hit(), stack, target.context(), false);
            }
            return false;
        }
    }

    private static final class SourceBehavior extends BucketDispenseBehavior {
        @Override
        protected boolean executeBucket(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            BucketState.Mode mode = BucketState.getMode(stack);
            if (mode == BucketState.Mode.FLUID) {
                BucketOperations.SourceTarget sourceTarget = SBFluidLogic.classifyTarget(
                        target.level(), target.hit(), stack);
                if (sourceTarget == BucketOperations.SourceTarget.MATCHING_FLUID) {
                    return SBFluidLogic.tryTakeWithContext(target.level(), target.hit(), stack, target.context());
                }
                return SBFluidLogic.tryPlace(target.level(), target.hit(), stack, target.context(), false);
            }
            if (mode == BucketState.Mode.NONE) {
                if (SBFluidLogic.tryMilkDispenser(target.level(), target.front(), target.face(), stack,
                        target.context())) {
                    return true;
                }
                return SBFluidLogic.tryTakeWithContext(target.level(), target.hit(), stack, target.context());
            }
            return false;
        }
    }
}
