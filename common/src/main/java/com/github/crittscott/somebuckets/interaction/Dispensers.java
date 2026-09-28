package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.Cauldrons.CauldronFluid;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Dispenser behavior for all six Some Buckets items. Each activation operates one item on the block
 * or entities directly in front of the dispenser, acting as the level's stable automation player.
 * Big, Huge, and Source Buckets run the shared bucket fluid logic, with vanilla water and lava
 * cauldrons going through {@link Cauldrons}; Mob, Junk, and Trash Buckets run their item logic.
 */
public final class Dispensers {
    private static final int STORAGE_EJECTION_SPEED = 6;

    private Dispensers() {}

    /**
     * Registers every bucket's dispenser behavior. Called once during mod setup.
     *
     * @param big8 Big Bucket item
     * @param big64 Huge Bucket item
     * @param sourceBucket Source Bucket item
     * @param mobBucket Mob Bucket item
     * @param junkBucket Junk Bucket item
     * @param trashBucket Trash Bucket item
     */
    public static void register(Item big8, Item big64, Item sourceBucket,
                                Item mobBucket, Item junkBucket, Item trashBucket) {
        BucketBehavior finite = new FiniteBehavior();
        BucketBehavior storage = new StorageBehavior();
        DispenserBlock.registerBehavior(big8, finite);
        DispenserBlock.registerBehavior(big64, finite);
        DispenserBlock.registerBehavior(sourceBucket, new SourceBehavior());
        DispenserBlock.registerBehavior(mobBucket, new MobBehavior());
        DispenserBlock.registerBehavior(junkBucket, storage);
        DispenserBlock.registerBehavior(trashBucket, storage);
    }

    /** Positions and action context derived from one dispenser activation. */
    record Target(ServerLevel level, Direction outward, BlockPos front, Direction face,
                  BlockHitResult hit, ProtectionContext context) {
        /**
         * Derives the target geometry for a dispenser activation: the block directly in front along
         * the dispenser's facing, the face pointing back at the dispenser, a centered
         * {@link BlockHitResult} on that face, and a {@link ProtectionContext} acting as the level's
         * stable automation player. The automation player is moved to the dispenser's center, facing
         * outward, so interactions it performs, and the sounds they play from the player's position,
         * originate at the dispenser.
         */
        static Target from(BlockSource source) {
            ServerLevel level = source.level();
            BlockPos sourcePos = source.pos();
            Direction outward = source.state().getValue(DispenserBlock.FACING);
            BlockPos front = sourcePos.relative(outward);
            Direction face = outward.getOpposite();
            ServerPlayer actor = BucketOperations.get().automationPlayer(level);
            Vec3 center = Vec3.atCenterOf(sourcePos);
            float pitch = outward == Direction.UP ? -90.0F : outward == Direction.DOWN ? 90.0F : 0.0F;
            actor.moveTo(center.x, center.y, center.z, outward.toYRot(), pitch);
            return new Target(level, outward, front, face,
                    new BlockHitResult(Vec3.atCenterOf(front), face, front, false),
                    ProtectionContext.dispenser(actor));
        }

        /** The one-block bounding box of the space directly in front of the dispenser. */
        AABB frontBounds() {
            return new AABB(front);
        }
    }

    /**
     * Runs one bucket operation against one item and settles its result back into the dispenser.
     * Empty Some Buckets items may be stacked, but any successful intake makes the operated item
     * unstackable; the remaining empty items therefore stay in the selected slot while vanilla's
     * {@code consumeWithRemainder} inserts the result elsewhere or ejects it when the dispenser is
     * full.
     */
    private abstract static class BucketBehavior extends OptionalDispenseItemBehavior {
        @Override
        protected final ItemStack execute(BlockSource source, ItemStack stack) {
            ItemStack working = stack.copyWithCount(1);
            boolean success = executeBucket(source, Target.from(source), working);
            setSuccess(success);
            return success ? consumeWithRemainder(source, stack, working) : stack;
        }

        /** Mutates {@code stack} only when the corresponding world operation succeeds. */
        protected abstract boolean executeBucket(BlockSource source, Target target, ItemStack stack);
    }

    private static final class FiniteBehavior extends BucketBehavior {
        @Override
        protected boolean executeBucket(BlockSource source, Target target, ItemStack stack) {
            BBItem bucketItem = (BBItem) stack.getItem();
            BucketState.Mode mode = BucketState.getMode(stack);
            StoredFluid currentFluid = BucketState.getStoredFluid(stack);
            int amount = currentFluid.amount();

            if (mode == BucketState.Mode.POWDER_SNOW && FluidTransactions.tryPlacePowder(
                    target.level(), target.hit(), stack, target.context(), false)) {
                return true;
            }

            if (mode == BucketState.Mode.FLUID && amount >= FluidBucketItem.BUCKET_VOLUME_MB) {
                CauldronFluid cauldronFluid = CauldronFluid.of(currentFluid.fluid());
                if (cauldronFluid != null
                        && Cauldrons.isEmptyCauldron(target.level().getBlockState(target.front()))
                        && Cauldrons.place(target.level(), target.front(), target.face(),
                        stack, cauldronFluid, target.context())) {
                    return true;
                }
            }

            if (mode == BucketState.Mode.NONE || mode == BucketState.Mode.FLUID) {
                CauldronFluid full = Cauldrons.fullFluidAt(target.level().getBlockState(target.front()));
                if (full != null && Cauldrons.take(target.level(), target.front(), target.face(), stack,
                        full, target.context())) {
                    return true;
                }
            }
            if ((mode == BucketState.Mode.NONE || mode == BucketState.Mode.POWDER_SNOW)
                    && Cauldrons.takePowder(target.level(), target.front(), target.face(), stack,
                    bucketItem.getCapacityUnits(), target.context())) {
                return true;
            }

            if (mode == BucketState.Mode.NONE
                    || (mode == BucketState.Mode.FLUID && amount < bucketItem.getCapacityMb())) {
                if (FluidTransactions.tryTakeFinite(target.level(), target.hit(), stack,
                        target.context())) return true;
                if (FluidTransactions.tryTakePowderWithContext(target.level(), target.hit(), stack,
                        target.context())) return true;
            }
            if (mode == BucketState.Mode.FLUID && amount >= FluidBucketItem.BUCKET_VOLUME_MB) {
                return FluidTransactions.tryPlaceFinite(target.level(), target.hit(), stack, target.context(), false);
            }
            return false;
        }
    }

    private static final class SourceBehavior extends BucketBehavior {
        @Override
        protected boolean executeBucket(BlockSource source, Target target, ItemStack stack) {
            BucketState.Mode mode = BucketState.getMode(stack);
            if (mode == BucketState.Mode.FLUID) {
                BucketOperations.SourceTarget sourceTarget = FluidTransactions.classifySourceTarget(
                        target.level(), target.hit(), stack);
                if (sourceTarget == BucketOperations.SourceTarget.MATCHING_FLUID) {
                    return FluidTransactions.tryTakeSource(target.level(), target.hit(), stack, target.context());
                }
                return FluidTransactions.tryPlaceSource(target.level(), target.hit(), stack, target.context(), false);
            }
            if (mode == BucketState.Mode.NONE) {
                if (FluidTransactions.tryMilkSourceDispenser(target.level(), target.front(), stack, target.context())) {
                    return true;
                }
                return FluidTransactions.tryTakeSource(target.level(), target.hit(), stack, target.context());
            }
            return false;
        }
    }

    private static final class MobBehavior extends BucketBehavior {
        @Override
        protected boolean executeBucket(BlockSource source, Target target, ItemStack stack) {
            List<Mob> occupyingMobs = target.level().getEntitiesOfClass(Mob.class, target.frontBounds());
            List<Mob> captureCandidates = occupyingMobs.stream()
                    .filter(mob -> MBItem.canCapture(mob) && MBItem.mayCaptureAs(mob, null))
                    .filter(mob -> MBItem.canAccept(stack, mob.getType()))
                    .toList();

            if (!captureCandidates.isEmpty()) {
                Mob selected = captureCandidates.get(target.level().random.nextInt(captureCandidates.size()));
                SoundEvent captureSound = MBItem.pickupSound(selected);
                if (MBItem.capture(stack, selected, target.context(), target.face())) {
                    target.level().playSound(null, target.front().getX(), target.front().getY(),
                            target.front().getZ(), captureSound, SoundSource.BLOCKS,
                            1.0F, 1.0F);
                    return true;
                }
                return false;
            }

            if (!occupyingMobs.isEmpty()) return false;
            return MBItem.releaseOldest(target.level(), target.front(), stack,
                    target.context(), target.face());
        }
    }

    private static final class StorageBehavior extends BucketBehavior {
        @Override
        protected boolean executeBucket(BlockSource source, Target target, ItemStack stack) {
            JBItem bucketItem = (JBItem) stack.getItem();

            List<Animal> animals = target.level().getEntitiesOfClass(Animal.class, target.frontBounds());
            List<Animal> feedCandidates = animals.stream()
                    .filter(animal -> JBItem.automationMayFeed(animal) && bucketItem.canFeed(stack, animal))
                    .toList();
            if (!feedCandidates.isEmpty()) {
                Animal selected = feedCandidates.get(target.level().random.nextInt(feedCandidates.size()));
                return bucketItem.feedAnimal(stack, selected, target.context().actor(),
                        InteractionHand.MAIN_HAND, target.context());
            }

            List<ItemEntity> itemEntities = bucketItem.findIntakeCandidates(target.level(), target.frontBounds());
            if (!itemEntities.isEmpty()) {
                return bucketItem.absorbItemEntities(target.level(), stack, itemEntities,
                        target.context());
            }

            if (!animals.isEmpty()) return false;
            List<ItemStack> stored = BucketState.getStoredItems(stack);
            if (stored.isEmpty()) return false;

            Position dispensePosition = DispenserBlock.getDispensePosition(source);
            if (!Protections.mayModify(target.level(), target.context(), target.front(), target.face(), stack)) {
                return false;
            }

            ItemStack popped = JBItem.removeOldest(stack);
            spawnItem(target.level(), popped, STORAGE_EJECTION_SPEED, target.outward(), dispensePosition);
            target.level().gameEvent(target.context().player(), GameEvent.ITEM_INTERACT_FINISH,
                    target.front());
            return true;
        }
    }
}
