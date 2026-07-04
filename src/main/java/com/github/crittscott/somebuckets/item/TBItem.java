package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.util.NBTUtil;
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
import net.minecraft.world.phys.AABB;

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
        if (incoming.isEmpty()) return false;

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
        boolean acted = tryAbsorbOneNearby(level, player, mine);
        if (acted) {
            return InteractionResultHolder.sidedSuccess(mine, level.isClientSide());
        }

        // Fall back to JB behavior if nothing was absorbed (preserves other interactions)
        return super.use(level, player, hand);
    }

    /**
     * Process at most one nearby ItemEntity according to TrashBucket semantics.
     * Server-side: performs mutations and returns true on success.
     * Client-side: performs a dry-run presence check to keep result parity (no mutations).
     */
    private boolean tryAbsorbOneNearby(Level level, Player player, ItemStack mine) {
        // Find nearby item entities
        AABB box = player.getBoundingBox().inflate(PICKUP_RADIUS);
        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e != null && e.isAlive() && !e.getItem().isEmpty() && !e.hasPickUpDelay());

        if (entities.isEmpty()) return false;

        ItemStack stored = getStored(mine);

        // Choose the first valid entity (one-entity-per-click)
        ItemEntity entity = entities.get(0);
        ItemStack incoming = entity.getItem();
        if (incoming.isEmpty()) return false;

        // On client, only indicate that we would act if conditions allow; no mutations.
        if (level.isClientSide) {
            if (stored.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(stored, incoming)
                    && stored.getCount() + incoming.getCount() <= stored.getMaxStackSize()) {
                return true; // full-fit merge would happen
            }
            return true; // replace would happen
        }

        // Server-side: perform mutations.

        // Case 1: TB empty -> take up to a full legal stack from the entity
        if (stored.isEmpty()) {
            int move = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            ItemStack placed = incoming.copy();
            placed.setCount(move);
            setStored(mine, placed);

            incoming.shrink(move);
            if (incoming.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(incoming);
            }
            return true;
        }

        // Case 2: TB not empty and full-fit merge is possible
        if (ItemStack.isSameItemSameTags(stored, incoming)
                && stored.getCount() + incoming.getCount() <= stored.getMaxStackSize()) {
            ItemStack merged = stored.copy();
            merged.grow(incoming.getCount());
            setStored(mine, merged);

            entity.discard(); // consumed entirely
            return true;
        }

        // Case 3: Replace (delete current stored, take from entity)
        int move = Math.min(incoming.getCount(), incoming.getMaxStackSize());
        ItemStack placed = incoming.copy();
        placed.setCount(move);
        setStored(mine, placed);

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
        List<ItemStack> list = NBTUtil.getStoredItems(bucket);
        return list.isEmpty() ? ItemStack.EMPTY : list.get(0).copy();
    }

    private static void setStored(ItemStack bucket, ItemStack stack) {
        List<ItemStack> list = new ArrayList<>(1);
        if (!stack.isEmpty()) {
            ItemStack one = stack.copy();
            if (one.getCount() > one.getMaxStackSize()) {
                one.setCount(one.getMaxStackSize());
            }
            list.add(one);
        }
        NBTUtil.setStoredItems(bucket, list);
    }
}
