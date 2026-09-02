package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/** Loader-neutral Mob, Junk, and Trash Bucket dispenser behavior. */
public final class NonFluidDispensers {
    private static final int STORAGE_EJECTION_SPEED = 6;
    private static final DefaultDispenseItemBehavior MOB_BEHAVIOR = new MobBehavior();
    private static final DefaultDispenseItemBehavior STORAGE_BEHAVIOR = new StorageBehavior();

    private NonFluidDispensers() {}

    /**
     * Installs the shared dispense behaviors for the non-fluid buckets.
     *
     * @param mobBucket the Mob Bucket item
     * @param junkBucket the Junk Bucket item
     * @param trashBucket the Trash Bucket item
     */
    public static void register(Item mobBucket, Item junkBucket, Item trashBucket) {
        DispenserBlock.registerBehavior(mobBucket, MOB_BEHAVIOR);
        DispenserBlock.registerBehavior(junkBucket, STORAGE_BEHAVIOR);
        DispenserBlock.registerBehavior(trashBucket, STORAGE_BEHAVIOR);
    }

    private static final class MobBehavior extends OptionalDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            setSuccess(act(source, stack));
            return stack;
        }

        private boolean act(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            List<Mob> occupyingMobs = target.level().getEntitiesOfClass(
                    Mob.class, target.frontBounds(), mob -> !mob.isRemoved());
            List<Mob> captureCandidates = occupyingMobs.stream()
                    .filter(MBItem::canCapture)
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
            if (BucketState.getEntityCount(stack) > 0
                    && MBItem.releaseOldest(target.level(), target.front(), stack,
                    target.context(), target.face())) {
                target.level().playSound(null, target.front().getX(), target.front().getY(),
                        target.front().getZ(), SoundEvents.SLIME_JUMP, SoundSource.BLOCKS,
                        1.0F, 1.0F);
                return true;
            }
            return false;
        }
    }

    private static final class StorageBehavior extends OptionalDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            setSuccess(act(source, stack));
            return stack;
        }

        private boolean act(BlockSource source, ItemStack stack) {
            JBItem bucketItem = (JBItem) stack.getItem();
            DispenserTarget target = DispenserTarget.from(source);

            List<Animal> animals = target.level().getEntitiesOfClass(
                    Animal.class, target.frontBounds(), animal -> !animal.isRemoved());
            List<Animal> feedCandidates = animals.stream()
                    .filter(animal -> bucketItem.canFeed(stack, animal))
                    .toList();
            if (!feedCandidates.isEmpty()) {
                Animal selected = feedCandidates.get(target.level().random.nextInt(feedCandidates.size()));
                return bucketItem.feedAnimal(stack, selected, null,
                        InteractionHand.MAIN_HAND, target.context(), target.face());
            }

            List<ItemEntity> itemEntities;
            if (bucketItem instanceof TBItem) {
                ItemEntity first = TBItem.findFirstNearby(target.level(), target.frontBounds());
                itemEntities = first == null ? List.of() : List.of(first);
            } else {
                itemEntities = target.level().getEntitiesOfClass(
                        ItemEntity.class, target.frontBounds(), JBItem::isIntakeCandidate);
            }
            if (!itemEntities.isEmpty()) {
                return bucketItem.absorbItemEntities(target.level(), stack, itemEntities,
                        target.context(), target.face());
            }

            if (!animals.isEmpty()) return false;
            List<ItemStack> stored = BucketState.getStoredItems(stack);
            if (stored.isEmpty()) return false;

            Position dispensePosition = DispenserBlock.getDispensePosition(source);
            ItemEntity released = new ItemEntity(target.level(), dispensePosition.x(),
                    dispensePosition.y(), dispensePosition.z(), stored.get(0).copy());
            if (!Protections.mayAct(target.level(), target.context(), ProtectionAction.ENTITY_RELEASE,
                    target.front(), target.face(), stack, released)) {
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
