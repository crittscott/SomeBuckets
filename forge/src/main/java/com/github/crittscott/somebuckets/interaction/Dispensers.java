package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.material.Fluids;

/** Registers and implements every Some Buckets dispenser behavior. */
public final class Dispensers {
    private static final DefaultDispenseItemBehavior BB_BEHAVIOR = new BBBehavior();
    private static final DefaultDispenseItemBehavior SB_BEHAVIOR = new SBBehavior();

    private Dispensers() {}

    /** Installs every Some Buckets dispenser behavior. Called once during mod setup. */
    public static void register() {
        DispenserBlock.registerBehavior(ModItems.BIG_BUCKET_8.get(), BB_BEHAVIOR);
        DispenserBlock.registerBehavior(ModItems.BIG_BUCKET_64.get(), BB_BEHAVIOR);
        DispenserBlock.registerBehavior(ModItems.SOURCE_BUCKET.get(), SB_BEHAVIOR);
        NonFluidDispensers.register(ModItems.MOB_BUCKET.get(), ModItems.JUNK_BUCKET.get(),
                ModItems.TRASH_BUCKET.get());
    }

    private static final class BBBehavior extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            BBItem bucketItem = (BBItem) stack.getItem();
            DispenserTarget target = DispenserTarget.from(source);
            BucketState.Mode mode = BucketState.getMode(stack);
            int capacityMb = bucketItem.getCapacityMb();
            StoredFluid currentFluid = BucketState.getStoredFluid(stack);
            int amount = currentFluid.amount();

            if (mode == BucketState.Mode.POWDER_SNOW
                    && BBFluidLogic.tryPlacePowder(
                    target.level(), target.hit(), stack, target.context(), false)) {
                return stack;
            }

            if (mode == BucketState.Mode.FLUID && amount >= FluidBucketItem.BUCKET_VOLUME_MB) {
                if (currentFluid.fluid() == Fluids.WATER
                        && Cauldrons.placeWater(target.level(), target.front(), target.face(), stack,
                        target.context())) {
                    return stack;
                }
                if (currentFluid.fluid() == Fluids.LAVA
                        && Cauldrons.placeLava(target.level(), target.front(), target.face(), stack,
                        target.context())) {
                    return stack;
                }
            }

            if ((mode == BucketState.Mode.NONE || mode == BucketState.Mode.FLUID)
                    && (Cauldrons.takeWater(target.level(), target.front(), target.face(), stack,
                    target.context())
                    || Cauldrons.takeLava(target.level(), target.front(), target.face(), stack,
                    target.context()))) {
                return stack;
            }
            if ((mode == BucketState.Mode.NONE || mode == BucketState.Mode.POWDER_SNOW)
                    && PowderSnowCauldrons.take(target.level(), target.front(), target.face(), stack,
                    bucketItem.getCapacityUnits(), target.context())) {
                return stack;
            }

            if (mode == BucketState.Mode.NONE
                    || (mode == BucketState.Mode.FLUID && amount < capacityMb)) {
                if (BBFluidLogic.tryTakeWithContext(
                        target.level(), target.hit(), stack, target.context())) {
                    return stack;
                }
                if (BBFluidLogic.tryTakePowderWithContext(
                        target.level(), target.hit(), stack, target.context())) {
                    return stack;
                }
            }
            if (amount >= FluidBucketItem.BUCKET_VOLUME_MB) {
                BBFluidLogic.tryPlace(
                        target.level(), target.hit(), stack, target.context(), false);
            }
            return stack;
        }
    }

    private static final class SBBehavior extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            BucketState.Mode mode = BucketState.getMode(stack);

            if (mode == BucketState.Mode.FLUID) {
                if (!SBPolicy.allows(BucketState.getStoredFluid(stack).fluid())) return stack;

                BucketOperations.SourceTarget sourceTarget = SBFluidLogic.classifyTarget(
                        target.level(), target.hit(), stack);
                if (sourceTarget == BucketOperations.SourceTarget.MATCHING_FLUID) {
                    SBFluidLogic.tryTakeWithContext(
                            target.level(), target.hit(), stack, target.context());
                } else {
                    SBFluidLogic.tryPlace(
                            target.level(), target.hit(), stack, target.context(), false);
                }
                return stack;
            }

            if (mode == BucketState.Mode.NONE) {
                if (SBFluidLogic.tryMilkDispenser(target.level(), target.front(),
                        target.face(), stack, target.context())) {
                    return stack;
                }
                SBFluidLogic.tryTakeWithContext(
                        target.level(), target.hit(), stack, target.context());
            }
            return stack;
        }
    }

}
