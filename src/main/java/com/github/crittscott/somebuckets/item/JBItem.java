package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class JBItem extends Item {
    private final int capacity;

    public JBItem(Properties properties, int capacity) {
        super(properties);
        this.capacity = capacity;
    }

    /** Keeps these buckets out of bundles, shulker boxes, and each other. */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /**
     * The single gate on what these buckets accept. Storage does not nest, so this defers to the
     * flag vanilla bundles and shulker boxes already use to exclude one another.
     */
    public static boolean canStore(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().canFitInsideContainerItems();
    }

    // ----- UI bar -----
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int c = getCount(stack);
        if (capacity <= 0) return 0;
        float f = (float) c / (float) capacity;
        f = Mth.clamp(f, 0.0F, 1.0F);
        return Mth.ceil(13.0F * f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3F76E4; // blue
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Stacks: " + getCount(stack) + " / " + capacity));
    }

    // ----- World interactions -----
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bucket = player.getItemInHand(hand);

        var box = player.getBoundingBox().inflate(1.5D, 1.5D, 1.5D);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> canStore(e.getItem()) && e.isAlive() && !e.hasPickUpDelay());
        if (items.isEmpty()) return InteractionResultHolder.pass(bucket);

        if (level.isClientSide) {
            boolean canAbsorb = items.stream().anyMatch(entity -> canAddStack(bucket, entity.getItem()));
            return canAbsorb
                    ? InteractionResultHolder.sidedSuccess(bucket, true)
                    : InteractionResultHolder.pass(bucket);
        }

        boolean absorbedAny = false;

        for (ItemEntity entity : items) {
            ItemStack entityStack = entity.getItem();
            int moved = addStack(bucket, entityStack);
            if (moved > 0) absorbedAny = true;

            if (entityStack.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(entityStack);
            }
        }

        if (absorbedAny) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.0F);
            return InteractionResultHolder.sidedSuccess(bucket, level.isClientSide);
        }
        return InteractionResultHolder.pass(bucket);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        ItemStack bucket = context.getItemInHand();

        // Keep existing shift-right-click gate
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        List<ItemStack> list = NBTUtil.getStoredItems(bucket);
        if (list.isEmpty()) return InteractionResult.PASS;

        if (level.isClientSide) return InteractionResult.sidedSuccess(true);

        ItemStack popped = list.remove(0); // FIFO: oldest stored entry first, matching Mob Bucket release order
        NBTUtil.setStoredItems(bucket, list);

        BlockPos dropPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 v = Vec3.atCenterOf(dropPos);
        ItemEntity drop = new ItemEntity(level, v.x, v.y + 0.1D, v.z, popped);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);

        return InteractionResult.sidedSuccess(false);
    }

    // NEW: Feed animals directly from items inside the bucket on right-click
    @Override
    public InteractionResult interactLivingEntity(ItemStack bucket, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Animal animal)) return InteractionResult.PASS;

        List<ItemStack> list = NBTUtil.getStoredItems(bucket);
        if (list.isEmpty()) return InteractionResult.PASS;

        int foodIdx = findFoodIndex(animal, list);
        if (foodIdx < 0) return InteractionResult.PASS;

        Level level = player.level();
        if (level.isClientSide) {
            // Let server perform the actual mutation; indicate success so the hand swing animates
            return InteractionResult.sidedSuccess(true);
        }

        ItemStack food = list.get(foodIdx);

        // Babies: speed up growth (vanilla parity: ~10% of remaining ticks)
        if (animal.isBaby()) {
            int remaining = -animal.getAge(); // babies have negative age
            if (remaining > 0) {
                int growTicks = Math.max(1, remaining / 10);
                animal.ageUp(growTicks, true);
                if (!player.getAbilities().instabuild) {
                    food.shrink(1);
                    if (food.isEmpty()) list.remove(foodIdx);
                    NBTUtil.setStoredItems(bucket, list);
                }
                return InteractionResult.sidedSuccess(false);
            }
            return InteractionResult.PASS;
        }

        // Adults: enter love if allowed
        if (animal.getAge() == 0 && animal.canFallInLove()) {
            if (!player.getAbilities().instabuild) {
                food.shrink(1);
                if (food.isEmpty()) list.remove(foodIdx);
                NBTUtil.setStoredItems(bucket, list);
            }
            animal.setInLove(player);
            return InteractionResult.sidedSuccess(false);
        }

        return InteractionResult.PASS;
    }

    // ----- Inventory stack-on overrides -----
    // Bucket ON cursor, right-clicking another slot -> insert from that slot into bucket
    @Override
    public boolean overrideStackedOnOther(ItemStack mine, Slot other, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        if (!other.hasItem()) return false;

        ItemStack otherStack = other.getItem();
        int moved = addStack(mine, otherStack);
        if (moved > 0) {
            if (otherStack.isEmpty()) {
                other.set(ItemStack.EMPTY);
            } else {
                other.set(otherStack);
            }
            other.setChanged();
            return true;
        }
        return false;
    }

    // Right-clicking the bucket in a slot with the cursor stack (or empty cursor)
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack mine, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY) return false;

        // Extract to cursor when cursor is empty
        if (other.isEmpty()) {
            List<ItemStack> list = NBTUtil.getStoredItems(mine);
            if (list.isEmpty()) return false;

            if (player.level().isClientSide) return true; // server performs the mutation and syncs it back

            ItemStack out = list.remove(0); // FIFO: oldest stored entry first, matching Mob Bucket release order
            NBTUtil.setStoredItems(mine, list);

            access.set(out); // put into cursor
            slot.setChanged();
            return true;
        }

        // Insert (cursor has items)
        int moved = addStack(mine, other);
        if (moved > 0) {
            slot.setChanged();
            return true;
        }
        return false;
    }

    // ----- storage helpers -----
    private static int getCount(ItemStack stack) {
        return NBTUtil.getStoredItems(stack).size();
    }

    private boolean canAddStack(ItemStack bucket, ItemStack incoming) {
        if (!canStore(incoming)) return false;

        List<ItemStack> list = NBTUtil.getStoredItems(bucket);
        for (ItemStack stored : list) {
            if (ItemStack.isSameItemSameTags(stored, incoming)
                    && stored.getCount() < stored.getMaxStackSize()) {
                return true;
            }
        }
        return list.size() < capacity;
    }

    // Merge as much of 'incoming' into the bucket's list as possible. Returns number of items moved and shrinks 'incoming'.
    private int addStack(ItemStack bucket, ItemStack incoming) {
        if (!canStore(incoming)) return 0;

        List<ItemStack> list = NBTUtil.getStoredItems(bucket);
        int moved = mergeInto(list, incoming, capacity);
        if (moved > 0) {
            NBTUtil.setStoredItems(bucket, list);
            incoming.shrink(moved);
        }
        return moved;
    }

    private static int mergeInto(List<ItemStack> list, ItemStack incoming, int capacity) {
        int remaining = incoming.getCount();
        if (remaining <= 0) return 0;

        // Merge into existing compatible stacks
        for (ItemStack s : list) {
            if (remaining <= 0) break;
            if (ItemStack.isSameItemSameTags(s, incoming)) {
                int canAdd = Math.min(remaining, s.getMaxStackSize() - s.getCount());
                if (canAdd > 0) {
                    s.grow(canAdd);
                    remaining -= canAdd;
                }
            }
        }

        // Create new stacks if there is free stack capacity
        while (remaining > 0 && list.size() < capacity) {
            int toAdd = Math.min(remaining, incoming.getMaxStackSize());
            ItemStack add = incoming.copy();
            add.setCount(toAdd);
            list.add(add);
            remaining -= toAdd;
        }

        return incoming.getCount() - remaining;
    }

    // ----- new helper -----
    private static int findFoodIndex(Animal animal, List<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (!s.isEmpty() && animal.isFood(s)) return i;
        }
        return -1;
    }
}
