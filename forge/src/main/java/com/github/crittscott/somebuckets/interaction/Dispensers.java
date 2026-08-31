package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

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
            NBTUtil.Mode mode = NBTUtil.getMode(stack);
            int capacityMb = bucketItem.getCapacityMb();
            FluidStack currentFluid = ForgeFluidStacks.get(stack);
            int amount = currentFluid.getAmount();
            IFluidHandlerItem handler = BlockFluidTransfers.requireBucketHandler(stack);

            if (mode == NBTUtil.Mode.POWDER_SNOW
                    && BBFluidLogic.getInstance().tryPlacePowder(
                    target.level(), target.hit(), stack, target.context(), false)) {
                return stack;
            }

            if (mode == NBTUtil.Mode.FLUID && amount >= FluidType.BUCKET_VOLUME) {
                if (currentFluid.getFluid() == Fluids.WATER
                        && Cauldrons.placeWater(target.level(), target.front(), target.face(), stack,
                        handler, target.context())) {
                    return stack;
                }
                if (currentFluid.getFluid() == Fluids.LAVA
                        && Cauldrons.placeLava(target.level(), target.front(), target.face(), stack,
                        handler, target.context())) {
                    return stack;
                }
            }

            if ((mode == NBTUtil.Mode.NONE || mode == NBTUtil.Mode.FLUID)
                    && (Cauldrons.takeWater(target.level(), target.front(), target.face(), stack,
                    handler, target.context())
                    || Cauldrons.takeLava(target.level(), target.front(), target.face(), stack,
                    handler, target.context()))) {
                return stack;
            }
            if ((mode == NBTUtil.Mode.NONE || mode == NBTUtil.Mode.POWDER_SNOW)
                    && PowderSnowCauldrons.take(target.level(), target.front(), target.face(), stack,
                    bucketItem.getCapacityUnits(), target.context())) {
                return stack;
            }

            if (mode == NBTUtil.Mode.NONE
                    || (mode == NBTUtil.Mode.FLUID && amount < capacityMb)) {
                if (BBFluidLogic.getInstance().tryTakeWithContext(
                        target.level(), target.hit(), stack, target.context())) {
                    return stack;
                }
                if (BBFluidLogic.getInstance().tryTakePowderWithContext(
                        target.level(), target.hit(), stack, target.context())) {
                    return stack;
                }
            }
            if (amount >= FluidType.BUCKET_VOLUME) {
                BBFluidLogic.getInstance().tryPlace(
                        target.level(), target.hit(), stack, target.context(), false);
            }
            return stack;
        }
    }

    private static final class SBBehavior extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            NBTUtil.Mode mode = NBTUtil.getMode(stack);

            if (mode == NBTUtil.Mode.FLUID) {
                FluidStack fluidStack = ForgeFluidStacks.get(stack);
                if (!SBPolicy.allows(fluidStack.getFluid())) return stack;

                BucketOperations.SourceTarget sourceTarget = SBFluidLogic.getInstance()
                        .classifyTarget(target.level(), target.hit(), stack);
                if (sourceTarget == BucketOperations.SourceTarget.MATCHING_FLUID) {
                    SBFluidLogic.getInstance().tryTakeWithContext(
                            target.level(), target.hit(), stack, target.context());
                } else {
                    SBFluidLogic.getInstance().tryPlace(
                            target.level(), target.hit(), stack, target.context(), false);
                }
                return stack;
            }

            if (mode == NBTUtil.Mode.NONE) {
                if (SBFluidLogic.getInstance().tryMilkDispenser(target.level(), target.front(),
                        target.face(), stack, target.context())) {
                    return stack;
                }
                SBFluidLogic.getInstance().tryTakeWithContext(
                        target.level(), target.hit(), stack, target.context());
            }
            return stack;
        }
    }

}
