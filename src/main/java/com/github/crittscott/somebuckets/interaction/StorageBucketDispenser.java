package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Dispenser behavior shared by Junk and Trash Buckets. */
public final class StorageBucketDispenser extends DefaultDispenseItemBehavior {
    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        if (!(stack.getItem() instanceof JBItem bucketItem)) return stack;

        Level level = source.getLevel();
        Direction direction = source.getBlockState().getValue(DispenserBlock.FACING);
        BlockPos front = source.getPos().relative(direction);
        AABB frontBounds = new AABB(front);
        ProtectionContext context = ProtectionContext.dispenser(source.getPos());

        List<Animal> animals = level.getEntitiesOfClass(Animal.class, frontBounds,
                animal -> !animal.isRemoved());
        List<Animal> feedCandidates = animals.stream()
                .filter(animal -> bucketItem.canFeed(stack, animal))
                .toList();
        if (!feedCandidates.isEmpty()) {
            Animal target = feedCandidates.get(level.random.nextInt(feedCandidates.size()));
            bucketItem.feedAnimal(stack, target, null, InteractionHand.MAIN_HAND, context, direction);
            return stack;
        }

        // Trash only ever consumes one entity per pulse, so it stops the world query at the first
        // match instead of collecting every eligible entity in the front block each pulse.
        List<ItemEntity> itemEntities;
        if (bucketItem instanceof TBItem) {
            ItemEntity first = TBItem.findFirstNearby(level, frontBounds);
            itemEntities = first == null ? List.of() : List.of(first);
        } else {
            itemEntities = level.getEntitiesOfClass(ItemEntity.class, frontBounds, JBItem::isIntakeCandidate);
        }
        if (!itemEntities.isEmpty()) {
            bucketItem.absorbItemEntities(level, stack, itemEntities, context, direction);
            return stack;
        }

        if (!animals.isEmpty()) return stack;

        List<ItemStack> stored = NBTUtil.getStoredItems(stack);
        if (stored.isEmpty()) return stack;

        Position dispensePosition = DispenserBlock.getDispensePosition(source);
        ItemEntity released = new ItemEntity(level, dispensePosition.x(), dispensePosition.y(),
                dispensePosition.z(), stored.get(0).copy());
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_RELEASE,
                front, direction, stack, released)) {
            return stack;
        }

        ItemStack popped = JBItem.removeOldest(stack);
        spawnItem(level, popped, 6, direction, dispensePosition);
        return stack;
    }
}
