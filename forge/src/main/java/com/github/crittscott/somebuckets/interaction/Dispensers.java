package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

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
        NonFluidDispensers.register();
    }

    /**
     * A Big or Huge Bucket tries, in order: placing powder snow; pouring water or lava into a
     * cauldron; taking water or lava from a cauldron; taking powder snow from a cauldron; when empty
     * or holding fluid, taking world fluid or powder snow in front; then placing one unit in front.
     */
    private static final class BBBehavior extends BucketDispenseBehavior {
        @Override
        protected boolean executeBucket(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            BucketState.Mode mode = BucketState.getMode(stack);

            if (BBFluidLogic.tryPlacePowder(target.level(), target.hit(), stack, target.context(), false)) {
                return true;
            }
            if (Cauldrons.placeWater(target.level(), target.front(), target.face(), stack, target.context())
                    || Cauldrons.placeLava(target.level(), target.front(), target.face(), stack,
                    target.context())) {
                return true;
            }
            if (Cauldrons.takeWater(target.level(), target.front(), target.face(), stack, target.context())
                    || Cauldrons.takeLava(target.level(), target.front(), target.face(), stack,
                    target.context())) {
                return true;
            }
            if (PowderSnowCauldrons.take(target.level(), target.front(), target.face(), stack,
                    target.context())) {
                return true;
            }

            if (mode == BucketState.Mode.NONE || mode == BucketState.Mode.FLUID) {
                if (BBFluidLogic.tryTakeWithContext(
                        target.level(), target.hit(), stack, target.context())) {
                    return true;
                }
                if (BBFluidLogic.tryTakePowderWithContext(
                        target.level(), target.hit(), stack, target.context())) {
                    return true;
                }
            }
            return BBFluidLogic.tryPlace(target.level(), target.hit(), stack, target.context(), false);
        }
    }

    /**
     * An assigned Source Bucket whose fluid is still allowed takes matching fluid in front and
     * otherwise places; an empty one milks a cow in front, else assigns itself from the fluid in
     * front.
     */
    private static final class SBBehavior extends BucketDispenseBehavior {
        @Override
        protected boolean executeBucket(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            BucketState.Mode mode = BucketState.getMode(stack);

            if (mode == BucketState.Mode.FLUID) {
                if (!SBPolicy.allows(BucketState.getStoredFluid(stack).fluid())) return false;

                BucketOperations.SourceTarget sourceTarget = SBFluidLogic.classifyTarget(
                        target.level(), target.hit(), stack);
                if (sourceTarget == BucketOperations.SourceTarget.MATCHING_FLUID) {
                    return SBFluidLogic.tryTakeWithContext(
                            target.level(), target.hit(), stack, target.context());
                }
                return SBFluidLogic.tryPlace(
                        target.level(), target.hit(), stack, target.context(), false);
            }

            if (mode == BucketState.Mode.NONE) {
                if (SBFluidLogic.tryMilkDispenser(target.level(), target.front(),
                        target.face(), stack, target.context())) {
                    return true;
                }
                return SBFluidLogic.tryTakeWithContext(
                        target.level(), target.hit(), stack, target.context());
            }
            return false;
        }
    }

}
