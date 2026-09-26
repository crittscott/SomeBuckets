package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.BucketOperations.CauldronFluid;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;

/**
 * Big, Huge, and Source Bucket dispenser behavior backed by the shared bucket fluid logic. Vanilla
 * water and lava cauldrons go through {@link BucketOperations#cauldronTake} and
 * {@link BucketOperations#cauldronPlace}; loaders that expose them as sided fluid storage decline
 * those calls and serve the cauldron through the generic block path instead.
 */
public final class FluidDispensers {
    private FluidDispensers() {}

    /**
     * Registers the finite-bucket behavior and Source Bucket behavior with the dispenser. Called
     * once during mod setup.
     *
     * @param big8 Big Bucket item
     * @param big64 Huge Bucket item
     * @param sourceBucket Source Bucket item
     */
    public static void register(Item big8, Item big64, Item sourceBucket) {
        DefaultDispenseItemBehavior finite = new FiniteBehavior();
        DispenserBlock.registerBehavior(big8, finite);
        DispenserBlock.registerBehavior(big64, finite);
        DispenserBlock.registerBehavior(sourceBucket, new SourceBehavior());
    }

    private static final class FiniteBehavior extends BucketDispenseBehavior {
        @Override
        protected boolean executeBucket(BlockSource source, ItemStack stack) {
            BBItem bucketItem = (BBItem) stack.getItem();
            DispenserTarget target = DispenserTarget.from(source);
            BucketOperations operations = BucketOperations.get();
            BucketState.Mode mode = BucketState.getMode(stack);
            StoredFluid currentFluid = BucketState.getStoredFluid(stack);
            int amount = currentFluid.amount();

            if (mode == BucketState.Mode.POWDER_SNOW && BBFluidLogic.tryPlacePowder(
                    target.level(), target.hit(), stack, target.context(), false)) {
                return true;
            }

            if (mode == BucketState.Mode.FLUID && amount >= FluidBucketItem.BUCKET_VOLUME_MB) {
                CauldronFluid cauldronFluid = CauldronFluid.of(currentFluid.fluid());
                // cauldronPlace also answers for a full cauldron with Source Bucket semantics.
                if (cauldronFluid != null
                        && target.level().getBlockState(target.front()).is(Blocks.CAULDRON)
                        && operations.cauldronPlace(target.level(), target.front(), target.face(),
                        stack, cauldronFluid, target.context())) {
                    return true;
                }
            }

            if (mode == BucketState.Mode.NONE || mode == BucketState.Mode.FLUID) {
                for (CauldronFluid cauldronFluid : CauldronFluid.values()) {
                    if (operations.cauldronTake(target.level(), target.front(), target.face(), stack,
                            cauldronFluid, target.context())) {
                        return true;
                    }
                }
            }
            if ((mode == BucketState.Mode.NONE || mode == BucketState.Mode.POWDER_SNOW)
                    && Cauldrons.takePowder(target.level(), target.front(), target.face(), stack,
                    bucketItem.getCapacityUnits(), target.context())) {
                return true;
            }

            if (mode == BucketState.Mode.NONE
                    || (mode == BucketState.Mode.FLUID && amount < bucketItem.getCapacityMb())) {
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
                if (SBFluidLogic.tryMilkDispenser(target.level(), target.front(), stack, target.context())) {
                    return true;
                }
                return SBFluidLogic.tryTakeWithContext(target.level(), target.hit(), stack, target.context());
            }
            return false;
        }
    }
}
