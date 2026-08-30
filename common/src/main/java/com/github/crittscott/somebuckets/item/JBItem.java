package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * FIFO storage for item-stack entries and the shared interaction base for Junk and Trash Buckets.
 * Contents live on the bucket stack, compatible entries merge before new entries are allocated,
 * and every intake path applies {@link #canStore(ItemStack)} before mutation.
 */
public class JBItem extends Item implements VariableStackItem {
    private static final double PICKUP_RADIUS = 1.5D;

    private final int capacity;

    public JBItem(Properties properties, int capacity) {
        super(properties.stacksTo(EMPTY_STACK_SIZE));
        if (capacity < 1) throw new IllegalArgumentException("Storage bucket capacity must be positive");
        this.capacity = capacity;
    }

    public int getCapacity() { return capacity; }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return getCount(stack) == 0;
    }

    /** Keeps these buckets out of bundles, shulker boxes, and each other. */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /**
     * The single gate on what these buckets accept. A stack is refused when it is empty, opts out of
     * container nesting via {@link Item#canFitInsideContainerItems()}, is a bundle or shulker box,
     * carries a vanilla inventory component, or exposes a loader item-inventory handler. Junk and
     * Trash Buckets opt out (see {@link #canFitInsideContainerItems()}) so storage never recurses,
     * and portable containers from other mods stay out even when they leave the vanilla flag set.
     * Content is not inspected: a container item is refused whether it is empty or full.
     */
    public static boolean canStore(ItemStack stack) {
        if (stack.isEmpty() || !stack.getItem().canFitInsideContainerItems()) return false;
        if (stack.getItem() instanceof BundleItem
                || (stack.getItem() instanceof BlockItem blockItem
                        && blockItem.getBlock() instanceof ShulkerBoxBlock)) return false;
        if (stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(DataComponents.CONTAINER)
                || stack.has(DataComponents.CONTAINER_LOOT)) return false;
        return !BucketOperations.get().carriesItemContainer(stack);
    }

    // ----- UI bar -----
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int c = getCount(stack);
        float f = (float) c / (float) capacity;
        f = Mth.clamp(f, 0.0F, 1.0F);
        return Mth.ceil(ItemBars.ITEM_BAR_WIDTH * f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ItemBars.DEFAULT_BUCKET_BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "tooltip.somebuckets.storage_bucket.stacks", getCount(stack), capacity));
    }

    // ----- Sound feedback -----

    /**
     * Plays this bucket's intake feedback at the acting player's position. Called unconditionally on
     * both sides so the acting player hears their own client-predicted result, matching the pattern
     * fluid pickup and placement use; the server broadcasts to everyone else.
     */
    protected void playIntakeSound(Level level, Player player) {
        level.playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /** Plays this bucket's ejection feedback at {@code pos}, with the same dual-side call pattern. */
    protected void playEjectSound(Level level, Player player, Vec3 pos) {
        level.playSound(player, pos.x, pos.y, pos.z,
                SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    // ----- World interactions -----
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bucket = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) return trySneakEject(level, player, hand, bucket);

        AABB box = player.getBoundingBox().inflate(PICKUP_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                JBItem::isIntakeCandidate);
        if (items.isEmpty()) return InteractionResultHolder.pass(bucket);

        if (level.isClientSide) {
            List<ItemStack> stored = NBTUtil.getStoredItems(bucket, level.registryAccess());
            boolean canAbsorb = items.stream().anyMatch(entity -> canAddStack(stored, entity.getItem()));
            if (!canAbsorb) return InteractionResultHolder.pass(bucket);
            playIntakeSound(level, player);
            return InteractionResultHolder.sidedSuccess(bucket, true);
        }

        ProtectionContext context = ProtectionContext.player(player, hand);
        boolean absorbedAny = absorbItemEntities(level, bucket, items, context, Direction.UP);

        if (absorbedAny) {
            playIntakeSound(level, player);
            return InteractionResultHolder.sidedSuccess(bucket, level.isClientSide);
        }
        return InteractionResultHolder.pass(bucket);
    }

    /**
     * Throws the oldest stored stack the way vanilla's drop-item key throws a held item, so a
     * sneaking player can eject without a block to target.
     */
    protected final InteractionResultHolder<ItemStack> trySneakEject(Level level, Player player,
                                                                      InteractionHand hand, ItemStack bucket) {
        List<ItemStack> stored = NBTUtil.getStoredItems(bucket, level.registryAccess());
        if (stored.isEmpty()) return InteractionResultHolder.pass(bucket);

        Vec3 pos = player.position();

        if (level.isClientSide) {
            playEjectSound(level, player, pos);
            return InteractionResultHolder.sidedSuccess(bucket, true);
        }

        ItemEntity probe = new ItemEntity(level, pos.x, pos.y, pos.z, stored.get(0).copy());
        ProtectionContext context = ProtectionContext.player(player, hand);
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_RELEASE,
                player.blockPosition(), Direction.UP, bucket, probe)) {
            return InteractionResultHolder.pass(bucket);
        }

        ItemStack popped = removeOldest(bucket, level.registryAccess());
        player.drop(popped, false, true);
        playEjectSound(level, player, pos);
        return InteractionResultHolder.sidedSuccess(bucket, false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        ItemStack bucket = context.getItemInHand();

        // World ejection requires a deliberate alternate-use gesture.
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        List<ItemStack> stored = NBTUtil.getStoredItems(bucket, level.registryAccess());
        if (stored.isEmpty()) return InteractionResult.PASS;

        BlockPos dropPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 v = Vec3.atCenterOf(dropPos);

        if (level.isClientSide) {
            playEjectSound(level, player, v);
            return InteractionResult.sidedSuccess(true);
        }

        ItemEntity probe = new ItemEntity(level, v.x, v.y + 0.1D, v.z, stored.get(0).copy());
        ProtectionContext protectionContext = ProtectionContext.player(player, context.getHand());
        if (!Protections.mayAct(level, protectionContext, ProtectionAction.ENTITY_RELEASE,
                dropPos, context.getClickedFace(), bucket, probe)) {
            return InteractionResult.PASS;
        }

        ItemStack popped = removeOldest(bucket, level.registryAccess());
        ItemEntity drop = new ItemEntity(level, v.x, v.y + 0.1D, v.z, popped);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        playEjectSound(level, player, v);

        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack bucket, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Animal animal)) return InteractionResult.PASS;
        if (!canFeed(bucket, animal)) return InteractionResult.PASS;

        Level level = player.level();
        if (level.isClientSide) {
            // The server performs the actual mutation. Locally swap in a probe of the stored food
            // and let vanilla's own interact predict the client-side feedback of a real held food
            // item without touching the bucket's contents.
            ItemStack probe = buildFoodProbe(bucket, animal);
            if (probe != null) {
                ItemStack previous = player.getItemInHand(hand);
                player.setItemInHand(hand, probe);
                try {
                    animal.interact(player, hand);
                } finally {
                    player.setItemInHand(hand, previous);
                }
            }
            return InteractionResult.sidedSuccess(true);
        }

        ProtectionContext context = ProtectionContext.player(player, hand);
        if (feedAnimal(bucket, animal, player, hand, context, Direction.UP)) {
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    /** Whether an item entity is a legal, currently collectible storage-bucket input. */
    public static boolean isIntakeCandidate(ItemEntity entity) {
        return entity.isAlive() && !entity.hasPickUpDelay() && canStore(entity.getItem());
    }

    /**
     * Absorbs as much as capacity permits from the supplied item entities.
     *
     * <p>The required context authorizes each entity immediately before that entity and the bucket
     * are changed. Rejected, protected, delayed, or incompatible entities remain untouched.
     *
     * @return {@code true} iff at least one item count moved into the bucket
     */
    public boolean absorbItemEntities(Level level, ItemStack bucket, List<ItemEntity> entities,
                                      ProtectionContext context, Direction face) {
        List<ItemStack> stored = NBTUtil.getStoredItems(bucket, level.registryAccess());
        boolean absorbedAny = false;
        for (ItemEntity entity : entities) {
            if (absorbItemEntity(level, bucket, stored, entity, context, face)) absorbedAny = true;
        }
        if (absorbedAny) {
            NBTUtil.setStoredItems(bucket, stored, level.registryAccess());
            NBTUtil.rerollJunkLayout(bucket);
        }
        return absorbedAny;
    }

    /**
     * Applies one authorized item-entity intake to the detached {@code stored} list.
     *
     * @return {@code true} iff at least one item count moved; on failure neither input changes
     */
    protected boolean absorbItemEntity(Level level, ItemStack bucket, List<ItemStack> stored,
                                       ItemEntity entity,
                                       ProtectionContext context, Direction face) {
        if (!isIntakeCandidate(entity) || !canAddStack(stored, entity.getItem())) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_INTERACT,
                entity.blockPosition(), face, bucket, entity)) {
            return false;
        }

        ItemStack entityStack = entity.getItem();
        int moved = mergeInto(stored, entityStack, capacity);
        if (moved <= 0) return false;
        entityStack.shrink(moved);

        if (entityStack.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(entityStack);
        }
        return true;
    }

    /** Whether the animal has stored food and can benefit from it on this activation. */
    public boolean canFeed(ItemStack bucket, Animal animal) {
        if (findFoodIndex(animal, NBTUtil.getStoredItems(bucket, animal.level().registryAccess())) < 0) return false;
        return canBenefitFromFood(animal);
    }

    /** A one-count copy of the animal's matching stored food, or null if there is none. */
    @Nullable
    private static ItemStack buildFoodProbe(ItemStack bucket, Animal animal) {
        List<ItemStack> stored = NBTUtil.getStoredItems(bucket, animal.level().registryAccess());
        int foodIdx = findFoodIndex(animal, stored);
        if (foodIdx < 0) return null;
        ItemStack probe = stored.get(foodIdx).copy();
        probe.setCount(1);
        return probe;
    }

    /**
     * Attempts one authorized feeding action with matching stored food.
     *
     * <p>A real feeder delegates to the animal interaction so vanilla or modded behavior decides
     * the outcome and item consumption. A null feeder represents dispenser automation: it applies
     * vanilla's baby-growth or adult-love outcome directly and consumes one stored food item.
     *
     * @return {@code true} iff the animal interaction consumed the action, not merely because a
     *         food candidate existed
     */
    public boolean feedAnimal(ItemStack bucket, Animal animal, @Nullable Player feeder, InteractionHand hand,
                              ProtectionContext context, Direction face) {
        List<ItemStack> list = NBTUtil.getStoredItems(bucket, animal.level().registryAccess());
        int foodIdx = findFoodIndex(animal, list);
        if (foodIdx < 0 || !canBenefitFromFood(animal)) return false;
        if (!Protections.mayAct(animal.level(), context, ProtectionAction.ENTITY_INTERACT,
                animal.blockPosition(), face, bucket, animal)) {
            return false;
        }

        if (feeder == null) {
            if (animal.isBaby()) {
                animal.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-animal.getAge()), true);
            } else {
                animal.setInLove(null);
            }
            consumeStoredFood(bucket, list, foodIdx, animal.level().registryAccess());
            return true;
        }

        ItemStack probe = list.get(foodIdx).copy();
        probe.setCount(1);
        ItemStack previous = feeder.getItemInHand(hand);
        feeder.setItemInHand(hand, probe);
        InteractionResult result;
        ItemStack remaining;
        try {
            result = animal.interact(feeder, hand);
            remaining = feeder.getItemInHand(hand);
        } finally {
            feeder.setItemInHand(hand, previous);
        }
        if (!result.consumesAction()) return false;

        if (remaining.isEmpty()) {
            consumeStoredFood(bucket, list, foodIdx, animal.level().registryAccess());
        }
        return true;
    }

    private static void consumeStoredFood(ItemStack bucket, List<ItemStack> stored, int foodIdx,
                                          HolderLookup.Provider registries) {
        ItemStack food = stored.get(foodIdx);
        food.shrink(1);
        if (food.isEmpty()) stored.remove(foodIdx);
        NBTUtil.setStoredItems(bucket, stored, registries);
    }

    private static boolean canBenefitFromFood(Animal animal) {
        return animal.isBaby() ? animal.getAge() < 0 : animal.getAge() == 0 && animal.canFallInLove();
    }

    /** Removes and returns the oldest stored stack, or an empty stack when there is none. */
    public static ItemStack removeOldest(ItemStack bucket, HolderLookup.Provider registries) {
        List<ItemStack> list = NBTUtil.getStoredItems(bucket, registries);
        if (list.isEmpty()) return ItemStack.EMPTY;
        ItemStack popped = list.remove(0);
        NBTUtil.setStoredItems(bucket, list, registries);
        return popped;
    }

    // ----- Inventory stack-on overrides -----

    /**
     * On a secondary click with the bucket on the cursor, moves as much as possible from
     * {@code other} into storage.
     *
     * @return {@code true} iff at least one item moved and both slot states were updated
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack mine, Slot other, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        if (!other.hasItem()) return false;

        ItemStack otherStack = other.getItem();
        int moved = addStack(mine, otherStack, player.level().registryAccess());
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

    /**
     * On a secondary click with the bucket in a slot, inserts from a nonempty cursor or extracts the
     * oldest stored entry to an empty cursor.
     *
     * @return {@code true} iff an insertion moved items or an extraction was accepted; client-side
     *         empty-cursor extraction is prediction and the server performs the mutation
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack mine, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY) return false;

        // Extract to cursor when cursor is empty
        if (other.isEmpty()) {
            List<ItemStack> list = NBTUtil.getStoredItems(mine, player.level().registryAccess());
            if (list.isEmpty()) return false;

            if (player.level().isClientSide) return true; // server performs the mutation and syncs it back

            ItemStack out = list.remove(0); // FIFO: oldest stored entry first, matching Mob Bucket release order
            NBTUtil.setStoredItems(mine, list, player.level().registryAccess());

            access.set(out); // put into cursor
            slot.setChanged();
            return true;
        }

        // Insert (cursor has items)
        int moved = addStack(mine, other, player.level().registryAccess());
        if (moved > 0) {
            slot.setChanged();
            return true;
        }
        return false;
    }

    // ----- storage helpers -----
    private static int getCount(ItemStack stack) {
        return NBTUtil.getStoredItemCount(stack);
    }

    private boolean canAddStack(List<ItemStack> storedItems, ItemStack incoming) {
        if (!canStore(incoming)) return false;

        for (ItemStack stored : storedItems) {
            if (ItemStack.isSameItemSameComponents(stored, incoming)
                    && stored.getCount() < stored.getMaxStackSize()) {
                return true;
            }
        }
        return storedItems.size() < capacity;
    }

    /**
     * Merges as much of {@code incoming} as capacity permits, persisting the new bucket contents and
     * shrinking {@code incoming} by the same amount.
     *
     * @return number of items moved; zero means neither stack changed
     */
    protected int addStack(ItemStack bucket, ItemStack incoming, HolderLookup.Provider registries) {
        if (!canStore(incoming)) return 0;

        List<ItemStack> list = NBTUtil.getStoredItems(bucket, registries);
        int moved = mergeInto(list, incoming, capacity);
        if (moved > 0) {
            NBTUtil.setStoredItems(bucket, list, registries);
            NBTUtil.rerollJunkLayout(bucket);
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
            if (ItemStack.isSameItemSameComponents(s, incoming)) {
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

    private static int findFoodIndex(Animal animal, List<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (!s.isEmpty() && animal.isFood(s)) return i;
        }
        return -1;
    }
}
