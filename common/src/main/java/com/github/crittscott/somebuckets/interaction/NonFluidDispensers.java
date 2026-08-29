package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

import java.util.List;

/** Loader-neutral Mob, Junk, and Trash Bucket dispenser behavior. */
public final class NonFluidDispensers {
    private static final int STORAGE_EJECTION_SPEED = 6;
    private static final DefaultDispenseItemBehavior MOB_BEHAVIOR = new MobBehavior();
    private static final DefaultDispenseItemBehavior STORAGE_BEHAVIOR = new StorageBehavior();

    private NonFluidDispensers() {}

    public static void register(Item mobBucket, Item junkBucket, Item trashBucket) {
        DispenserBlock.registerBehavior(mobBucket, MOB_BEHAVIOR);
        DispenserBlock.registerBehavior(junkBucket, STORAGE_BEHAVIOR);
        DispenserBlock.registerBehavior(trashBucket, STORAGE_BEHAVIOR);
    }

    private static final class MobBehavior extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            DispenserTarget target = DispenserTarget.from(source);
            List<Mob> occupyingMobs = target.level().getEntitiesOfClass(
                    Mob.class, target.frontBounds(), mob -> !mob.isRemoved());
            List<Mob> captureCandidates = occupyingMobs.stream()
                    .filter(MBItem::canCapture)
                    .filter(mob -> MBItem.canAccept(stack, mob.getType()))
                    .toList();

            if (!captureCandidates.isEmpty()) {
                Mob selected = captureCandidates.get(target.level().random.nextInt(captureCandidates.size()));
                if (MBItem.capture(stack, selected, target.context(), target.face())) {
                    target.level().playSound(null, target.front().getX(), target.front().getY(),
                            target.front().getZ(), SoundEvents.SLIME_ATTACK, SoundSource.BLOCKS,
                            1.0F, 1.0F);
                }
                return stack;
            }

            if (!occupyingMobs.isEmpty()) return stack;
            if (NBTUtil.getEntityCount(stack) > 0
                    && MBItem.releaseOldest(target.level(), target.front(), stack,
                    target.context(), target.face())) {
                target.level().playSound(null, target.front().getX(), target.front().getY(),
                        target.front().getZ(), SoundEvents.SLIME_JUMP, SoundSource.BLOCKS,
                        1.0F, 1.0F);
            }
            return stack;
        }
    }

    private static final class StorageBehavior extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            JBItem bucketItem = (JBItem) stack.getItem();
            DispenserTarget target = DispenserTarget.from(source);

            List<Animal> animals = target.level().getEntitiesOfClass(
                    Animal.class, target.frontBounds(), animal -> !animal.isRemoved());
            List<Animal> feedCandidates = animals.stream()
                    .filter(animal -> bucketItem.canFeed(stack, animal))
                    .toList();
            if (!feedCandidates.isEmpty()) {
                Animal selected = feedCandidates.get(target.level().random.nextInt(feedCandidates.size()));
                bucketItem.feedAnimal(stack, selected, null,
                        InteractionHand.MAIN_HAND, target.context(), target.face());
                return stack;
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
                bucketItem.absorbItemEntities(target.level(), stack, itemEntities,
                        target.context(), target.face());
                return stack;
            }

            if (!animals.isEmpty()) return stack;
            List<ItemStack> stored = NBTUtil.getStoredItems(stack, target.level().registryAccess());
            if (stored.isEmpty()) return stack;

            Position dispensePosition = DispenserBlock.getDispensePosition(source);
            ItemEntity released = new ItemEntity(target.level(), dispensePosition.x(),
                    dispensePosition.y(), dispensePosition.z(), stored.get(0).copy());
            if (!Protections.mayAct(target.level(), target.context(), ProtectionAction.ENTITY_RELEASE,
                    target.front(), target.face(), stack, released)) {
                return stack;
            }

            ItemStack popped = JBItem.removeOldest(stack, target.level().registryAccess());
            spawnItem(target.level(), popped, STORAGE_EJECTION_SPEED, target.outward(), dispensePosition);
            return stack;
        }
    }
}
