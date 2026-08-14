package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One-entry storage bucket whose intake merges only when the complete incoming stack fits.
 * Every other accepted input destroys the old entry and replaces it with one legal stack, leaving
 * any oversized incoming remainder at its source.
 */
public class TBItem extends JBItem {
    private static final double PICKUP_RADIUS = 2.25D; // one-entity-per-click within this radius

    public TBItem(Item.Properties properties) {
        super(properties.rarity(Rarity.RARE), 1);
    }

    // ----------------------------
    // Inventory stack-on overrides
    // ----------------------------

    /**
     * Applies merge-or-replace intake from a secondary-clicked slot while the bucket is on the
     * cursor.
     *
     * @return {@code true} iff at least one incoming item was consumed and both storages were set
     *         to the computed result
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack mine, Slot other, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        if (!other.hasItem()) return false;

        ItemStack incoming = other.getItem();
        StorageResult result = mergeOrReplace(getStored(mine), incoming);
        if (!result.consumedAnyFrom(incoming)) return false;

        setStored(mine, result.stored());
        other.set(result.remainder());
        other.setChanged();
        return true;
    }

    /**
     * Extracts through the FIFO base behavior when the cursor is empty; otherwise applies
     * merge-or-replace intake from the cursor.
     *
     * @return {@code true} iff extraction was accepted or at least one incoming item was consumed
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack mine, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY) return false;

        if (other.isEmpty()) {
            // Keep standard JB behavior (extract to cursor, etc.)
            return super.overrideOtherStackedOnMe(mine, other, slot, action, player, access);
        }
        StorageResult result = mergeOrReplace(getStored(mine), other);
        if (!result.consumedAnyFrom(other)) return false;

        setStored(mine, result.stored());
        access.set(result.remainder());
        slot.setChanged();
        return true;
    }

    // ----------------------------
    // World right-click (use in air)
    // ----------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack mine = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) return trySneakEject(level, player, hand, mine);

        // Attempt a single-entity transfer; only act on server, but mirror the result client-side.
        // Trash has no other use() behavior to fall back to, so a miss is a plain pass.
        boolean acted = tryAbsorbOneNearby(level, player, hand, mine);
        return acted
                ? InteractionResultHolder.sidedSuccess(mine, level.isClientSide())
                : InteractionResultHolder.pass(mine);
    }

    /*
     * Process at most one nearby item entity. The server mutates on success; the client only checks
     * for a candidate so its interaction result predicts the server path.
     */
    private boolean tryAbsorbOneNearby(Level level, Player player, InteractionHand hand, ItemStack mine) {
        AABB box = player.getBoundingBox().inflate(PICKUP_RADIUS);
        ItemEntity found = findFirstNearby(level, box);
        if (found == null) return false;

        if (level.isClientSide) {
            playIntakeSound(level, player);
            return true;
        }

        ProtectionContext context = ProtectionContext.player(player, hand);
        boolean absorbed = absorbItemEntities(level, mine, List.of(found), context, Direction.UP);
        if (absorbed) playIntakeSound(level, player);
        return absorbed;
    }

    // ----------------------------
    // Sound feedback
    // ----------------------------

    /** The vanilla water-evaporating-in-the-nether sound, reused for Trash Bucket intake. */
    @Override
    protected void playIntakeSound(Level level, Player player) {
        level.playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                FluidPlacement.hissPitch(level.random));
    }

    /** That same evaporation sound, reversed, for the destructive-replacement ejection it mirrors. */
    @Override
    protected void playEjectSound(Level level, Player player, Vec3 pos) {
        level.playSound(player, pos.x, pos.y, pos.z,
                BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(SomeBuckets.MODID, "tb_eject")),
                SoundSource.BLOCKS, 0.5F, FluidPlacement.hissPitch(level.random));
    }

    /**
     * The first eligible {@link ItemEntity} returned by the world's query for {@code box}, or
     * {@code null}. This is iteration order, not a nearest-entity guarantee; the query stops after
     * one match because a Trash Bucket processes only one entity per interaction.
     */
    @Nullable
    public static ItemEntity findFirstNearby(Level level, AABB box) {
        List<ItemEntity> result = new ArrayList<>(1);
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box, JBItem::isIntakeCandidate, result, 1);
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * Applies Trash Bucket intake to only the first supplied entity.
     *
     * @return {@code true} iff some of that entity's stack was merged or installed as replacement
     */
    @Override
    public boolean absorbItemEntities(Level level, ItemStack bucket, List<ItemEntity> entities,
                                      ProtectionContext context, Direction face) {
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
                                       ProtectionContext context, Direction face) {
        if (!isIntakeCandidate(entity)) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_INTERACT,
                entity.blockPosition(), face, mine, entity)) {
            return false;
        }

        ItemStack incoming = entity.getItem();
        StorageResult result = mergeOrReplace(getStored(storedItems), incoming);
        if (!result.consumedAnyFrom(incoming)) return false;

        setStored(storedItems, result.stored());
        if (result.remainder().isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(result.remainder());
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
        if (!stack.isEmpty()) storedItems.add(stack.copy());
    }

    /*
     * Apply the Trash Bucket intake rule without mutating either input. Compatible content merges
     * only when all incoming items fit; otherwise accepted content replaces the old entry and any
     * excess remains in the result.
     */
    private static StorageResult mergeOrReplace(ItemStack stored, ItemStack incoming) {
        if (!canStore(incoming)) return new StorageResult(stored.copy(), incoming.copy());

        if (!stored.isEmpty() && ItemStack.isSameItemSameTags(stored, incoming)
                && stored.getCount() + incoming.getCount() <= stored.getMaxStackSize()) {
            ItemStack merged = stored.copy();
            merged.grow(incoming.getCount());
            return new StorageResult(merged, ItemStack.EMPTY);
        }

        int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
        ItemStack replacement = incoming.copy();
        replacement.setCount(moved);
        ItemStack remainder = incoming.copy();
        remainder.shrink(moved);
        return new StorageResult(replacement, remainder);
    }

    private record StorageResult(ItemStack stored, ItemStack remainder) {
        private boolean consumedAnyFrom(ItemStack incoming) {
            return remainder.getCount() < incoming.getCount();
        }
    }
}
