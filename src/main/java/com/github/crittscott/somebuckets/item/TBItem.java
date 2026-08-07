package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TBItem extends JBItem {
    private static final double PICKUP_RADIUS = 2.25D; // one-entity-per-click within this radius

    public TBItem(Item.Properties properties) {
        super(properties.stacksTo(1), 1);
    }

    // ----------------------------
    // Inventory stack-on overrides
    // ----------------------------

    // Bucket ON cursor, right-clicking another slot.
    @Override
    public boolean overrideStackedOnOther(ItemStack mine, Slot other, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        if (!other.hasItem()) return false;

        ItemStack incoming = other.getItem();
        if (!canStore(incoming)) return false;

        ItemStack stored = getStored(mine);
        if (stored.isEmpty()) {
            // Keep standard JB behavior when empty.
            return super.overrideStackedOnOther(mine, other, action, player);
        }

        // Full-fit merge?
        if (ItemStack.isSameItemSameTags(stored, incoming)
                && stored.getCount() + incoming.getCount() <= stored.getMaxStackSize()) {
            ItemStack merged = stored.copy();
            merged.grow(incoming.getCount());
            setStored(mine, merged);

            other.set(ItemStack.EMPTY); // consumed entirely
            other.setChanged();
            return true;
        }

        // Replace: delete current stored, take entire incoming; clear the slot.
        setStored(mine, incoming.copy());
        other.set(ItemStack.EMPTY);
        other.setChanged();
        return true;
    }

    // Bucket IN a slot, right-click with an item on the cursor.
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack mine, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY) return false;

        if (other.isEmpty()) {
            // Keep standard JB behavior (extract to cursor, etc.)
            return super.overrideOtherStackedOnMe(mine, other, slot, action, player, access);
        }
        if (!canStore(other)) return false;

        ItemStack stored = getStored(mine);
        if (stored.isEmpty()) {
            // Empty TB: standard JB insert/merge
            return super.overrideOtherStackedOnMe(mine, other, slot, action, player, access);
        }

        // Full-fit merge?
        if (ItemStack.isSameItemSameTags(stored, other)
                && stored.getCount() + other.getCount() <= stored.getMaxStackSize()) {
            ItemStack merged = stored.copy();
            merged.grow(other.getCount());
            setStored(mine, merged);

            access.set(ItemStack.EMPTY); // cursor consumed
            slot.setChanged();
            return true;
        }

        // Replace: delete current stored, take entire cursor stack; cursor becomes empty.
        setStored(mine, other.copy());
        access.set(ItemStack.EMPTY);
        slot.setChanged();
        return true;
    }

    // ----------------------------
    // World right-click (use in air)
    // ----------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack mine = player.getItemInHand(hand);

        // Attempt a single-entity transfer; only act on server, but mirror the result client-side.
        // Trash has no other use() behavior to fall back to, so a miss is a plain pass.
        boolean acted = tryAbsorbOneNearby(level, player, hand, mine);
        return acted
                ? InteractionResultHolder.sidedSuccess(mine, level.isClientSide())
                : InteractionResultHolder.pass(mine);
    }

    /**
     * Process at most one nearby ItemEntity according to TrashBucket semantics.
     * Server-side: performs mutations and returns true on success.
     * Client-side: performs a dry-run presence check to keep result parity (no mutations).
     */
    private boolean tryAbsorbOneNearby(Level level, Player player, InteractionHand hand, ItemStack mine) {
        AABB box = player.getBoundingBox().inflate(PICKUP_RADIUS);
        ItemEntity found = findFirstNearby(level, box);
        if (found == null) return false;
        if (level.isClientSide) return true;
        ProtectionContext context = ProtectionContext.player(player, hand);
        return absorbItemEntities(level, mine, List.of(found), context, Direction.UP);
    }

    /**
     * The single eligible {@link ItemEntity} nearest a spatial scan of {@code box}, or {@code null}.
     * Trash only ever consumes one entity per interaction, so this stops the world query at the first
     * match instead of collecting every eligible entity in range only to discard all but one of them.
     */
    @Nullable
    public static ItemEntity findFirstNearby(Level level, AABB box) {
        List<ItemEntity> result = new ArrayList<>(1);
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box, JBItem::isIntakeCandidate, result, 1);
        return result.isEmpty() ? null : result.get(0);
    }

    /** Trash Buckets deliberately process only the first eligible entity per interaction. */
    @Override
    public boolean absorbItemEntities(Level level, ItemStack bucket, List<ItemEntity> entities,
                                      @Nullable ProtectionContext context, Direction face) {
        if (entities.isEmpty()) return false;

        List<ItemStack> storedItems = NBTUtil.getStoredItems(bucket);
        boolean absorbed = absorbItemEntity(
                level, bucket, storedItems, entities.get(0), context, face);
        if (absorbed) {
            NBTUtil.setStoredItems(bucket, storedItems);
        }
        return absorbed;
    }

    @Override
    protected boolean absorbItemEntity(Level level, ItemStack mine, List<ItemStack> storedItems,
                                       ItemEntity entity,
                                       @Nullable ProtectionContext context, Direction face) {
        if (!isIntakeCandidate(entity)) return false;
        if (context != null && !Protections.mayAct(level, context, ProtectionAction.ENTITY_INTERACT,
                entity.blockPosition(), face, mine, entity)) {
            return false;
        }

        ItemStack stored = getStored(storedItems);
        ItemStack incoming = entity.getItem();
        if (stored.isEmpty()) {
            int move = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            ItemStack placed = incoming.copy();
            placed.setCount(move);
            setStored(storedItems, placed);

            incoming.shrink(move);
            if (incoming.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(incoming);
            }
            return true;
        }

        if (ItemStack.isSameItemSameTags(stored, incoming)
                && stored.getCount() + incoming.getCount() <= stored.getMaxStackSize()) {
            ItemStack merged = stored.copy();
            merged.grow(incoming.getCount());
            setStored(storedItems, merged);

            entity.discard();
            return true;
        }

        int move = Math.min(incoming.getCount(), incoming.getMaxStackSize());
        ItemStack placed = incoming.copy();
        placed.setCount(move);
        setStored(storedItems, placed);

        incoming.shrink(move);
        if (incoming.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(incoming);
        }
        return true;
    }

    // ----------------------------
    // Minimal local storage helpers
    // ----------------------------

    private static ItemStack getStored(ItemStack bucket) {
        return getStored(NBTUtil.getStoredItems(bucket));
    }

    private static ItemStack getStored(List<ItemStack> storedItems) {
        return storedItems.isEmpty() ? ItemStack.EMPTY : storedItems.get(0).copy();
    }

    private static void setStored(ItemStack bucket, ItemStack stack) {
        List<ItemStack> list = new ArrayList<>(1);
        setStored(list, stack);
        NBTUtil.setStoredItems(bucket, list);
    }

    private static void setStored(List<ItemStack> storedItems, ItemStack stack) {
        storedItems.clear();
        if (!stack.isEmpty()) {
            ItemStack one = stack.copy();
            if (one.getCount() > one.getMaxStackSize()) {
                one.setCount(one.getMaxStackSize());
            }
            storedItems.add(one);
        }
    }
}
