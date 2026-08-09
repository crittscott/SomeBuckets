package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.FabricBucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Fabric fluid and powder dispenser behavior backed by the Fabric transaction adapter. */
public final class FabricFluidDispensers {
    private FabricFluidDispensers() {}

    public static void register(Item big8, Item big64, Item sourceBucket,
                                FabricBucketOperations operations) {
        DefaultDispenseItemBehavior finite = new FiniteBehavior(operations);
        DispenserBlock.registerBehavior(big8, finite);
        DispenserBlock.registerBehavior(big64, finite);
        DispenserBlock.registerBehavior(sourceBucket, new SourceBehavior(operations));
    }

    private record Target(ServerLevel level, BlockPos front, Direction face,
                          BlockHitResult hit, ProtectionContext context) {
        static Target from(BlockSource source) {
            Direction outward = source.getBlockState().getValue(DispenserBlock.FACING);
            BlockPos front = source.getPos().relative(outward);
            Direction face = outward.getOpposite();
            return new Target(source.getLevel(), front, face,
                    new BlockHitResult(Vec3.atCenterOf(front), face, front, false),
                    ProtectionContext.dispenser(source.getPos()));
        }
    }

    private static final class FiniteBehavior extends DefaultDispenseItemBehavior {
        private final FabricBucketOperations operations;

        private FiniteBehavior(FabricBucketOperations operations) { this.operations = operations; }

        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            Target target = Target.from(source);
            NBTUtil.Mode mode = NBTUtil.getMode(stack);
            if (mode == NBTUtil.Mode.POWDER_SNOW) {
                if (operations.tryPowderPlaceWithContext(target.level(), target.hit(), stack,
                        target.context(), false)) return stack;
            }
            if (mode == NBTUtil.Mode.NONE || mode == NBTUtil.Mode.POWDER_SNOW) {
                if (takePowderCauldron(target, stack)) return stack;
            }

            int amount = NBTUtil.getStoredFluid(stack).amount();
            int capacity = ((BBItem) stack.getItem()).getCapacityMb();
            if (mode == NBTUtil.Mode.NONE || mode == NBTUtil.Mode.FLUID && amount < capacity) {
                if (operations.tryBigTakeWithContext(target.level(), target.hit(), stack,
                        target.context())) return stack;
                if (operations.tryPowderTakeWithContext(target.level(), target.hit(), stack,
                        target.context())) return stack;
            }
            if (mode == NBTUtil.Mode.FLUID && amount >= FluidBucketItem.BUCKET_VOLUME_MB) {
                operations.tryBigPlaceWithContext(target.level(), target.hit(), stack,
                        target.context(), false);
            }
            return stack;
        }
    }

    private static final class SourceBehavior extends DefaultDispenseItemBehavior {
        private final FabricBucketOperations operations;

        private SourceBehavior(FabricBucketOperations operations) { this.operations = operations; }

        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            Target target = Target.from(source);
            if (NBTUtil.getMode(stack) == NBTUtil.Mode.FLUID) {
                BlockState state = target.level().getBlockState(target.front());
                boolean supportedFullCauldron = state.is(Blocks.LAVA_CAULDRON)
                        || state.is(Blocks.WATER_CAULDRON)
                        && state.getValue(LayeredCauldronBlock.LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL;
                if (!supportedFullCauldron
                        || !operations.trySourceSinkFromStorage(target.level(), target.hit(), stack,
                        target.context())) {
                    operations.trySourcePlaceWithContext(target.level(), target.hit(), stack,
                            target.context(), false);
                }
            } else if (NBTUtil.getMode(stack) == NBTUtil.Mode.NONE
                    && !operations.trySourceMilk(target.level(), target.front(), target.face(), stack,
                    target.context())) {
                operations.trySourceTakeWithContext(target.level(), target.hit(), stack,
                        target.context());
            }
            return stack;
        }
    }

    private static boolean takePowderCauldron(Target target, ItemStack stack) {
        BlockState state = target.level().getBlockState(target.front());
        if (!state.is(Blocks.POWDER_SNOW_CAULDRON)
                || state.getValue(LayeredCauldronBlock.LEVEL) != LayeredCauldronBlock.MAX_FILL_LEVEL) {
            return false;
        }
        BBItem item = (BBItem) stack.getItem();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int units = NBTUtil.getPowderUnits(stack);
        if (mode != NBTUtil.Mode.NONE && mode != NBTUtil.Mode.POWDER_SNOW || units >= item.getCapacityUnits()) {
            return false;
        }
        if (!Protections.mayAct(target.level(), target.context(), ProtectionAction.BLOCK_INTERACT,
                target.front(), target.face(), stack, null)) return false;
        NBTUtil.setPowderUnits(stack, units + 1);
        target.level().setBlock(target.front(), Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
        target.level().playSound(null, target.front(), SoundEvents.BUCKET_FILL_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        target.level().gameEvent(null, GameEvent.FLUID_PICKUP, target.front());
        return true;
    }

}
