package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.LegacyBucketMigration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.gameevent.GameEvent;
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

    /**
     * Creates an item-storage bucket.
     *
     * @param properties base item properties
     * @param capacity maximum number of stored stack entries
     * @throws IllegalArgumentException when {@code capacity} is less than one
     */
    public JBItem(Properties properties, int capacity) {
        super(properties.stacksTo(EMPTY_STACK_SIZE));
        if (capacity < 1) throw new IllegalArgumentException("Storage bucket capacity must be positive");
        this.capacity = capacity;
    }

    /** Returns the maximum number of stored stack entries. */
    public int getCapacity() { return capacity; }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return getCount(stack) == 0;
    }

    /** Migrates any recognized custom-data payload on the server while the stack is carried. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide) {
            if (!BucketState.discardInvalidState(stack)) return;
            LegacyBucketMigration.migrate(stack, (ServerLevel) level,
                    () -> entity.getScoreboardName() + " at " + entity.blockPosition()
                            + " in " + level.dimension().location());
        }
    }

    /** Keeps these buckets out of bundles, shulker boxes, and each other. */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /**
     * The single gate on what these buckets accept.
     *
     * <p>Content is not inspected: a container item is refused whether it is empty or full. Junk and
     * Trash Buckets opt out of container nesting (see {@link #canFitInsideContainerItems()}) so
     * storage never recurses, and portable containers from other mods stay out even when they leave
     * the vanilla flag set.
     *
     * @param stack candidate stack
     * @return {@code false} when the stack is empty, opts out of container nesting via
     *         {@link Item#canFitInsideContainerItems()} (as shulker boxes do), carries a
     *         vanilla inventory component, or exposes a loader item-inventory handler; {@code true}
     *         otherwise
     */
    public static boolean canStore(ItemStack stack) {
        if (stack.isEmpty() || !stack.getItem().canFitInsideContainerItems()) return false;
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
        return Mth.ceil(VariableStackItem.ITEM_BAR_WIDTH * f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return VariableStackItem.DEFAULT_BUCKET_BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "tooltip.somebuckets.storage_bucket.stacks", getCount(stack), capacity));
    }

    // ----- Sound feedback -----

    /**
     * Plays this bucket's intake feedback at the acting player's position.
     *
     * <p>Called unconditionally on both sides so the acting player hears their own client-predicted
     * result, matching the pattern fluid pickup and placement use; the server broadcasts to everyone
     * else.
     *
     * @param level level to play the sound in
     * @param player acting player and sound origin
     */
    protected void playIntakeSound(Level level, Player player) {
        level.playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /**
     * Plays this bucket's ejection feedback at {@code pos}, with the same dual-side call pattern as
     * {@link #playIntakeSound}.
     *
     * @param level level to play the sound in
     * @param player acting player
     * @param pos world position the sound originates from
     */
    protected void playEjectSound(Level level, Player player, Vec3 pos) {
        level.playSound(player, pos.x, pos.y, pos.z,
                SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    // ----- World interactions -----

    /**
     * Collects nearby eligible item entities into the bucket, or throws the oldest stored stack from
     * the player when sneaking. Runs on both sides so the acting player hears predicted feedback; the
     * server performs the authorized mutation.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack bucket = player.getItemInHand(hand);
        if (!level.isClientSide && !BucketState.discardInvalidState(bucket)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) return trySneakEject(level, player, hand, bucket);

        AABB box = player.getBoundingBox().inflate(PICKUP_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                JBItem::isIntakeCandidate);
        if (items.isEmpty()) return InteractionResult.PASS;

        if (level.isClientSide) {
            List<ItemStack> stored = BucketState.getStoredItems(bucket);
            boolean canAbsorb = items.stream().anyMatch(entity -> canAddStack(stored, entity.getItem()));
            if (!canAbsorb) return InteractionResult.PASS;
            playIntakeSound(level, player);
            return InteractionResult.SUCCESS;
        }

        ProtectionContext context = ProtectionContext.player(player, hand);
        boolean absorbedAny = absorbItemEntities(level, bucket, items, context);

        if (absorbedAny) {
            playIntakeSound(level, player);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    /**
     * Throws the oldest stored stack the way vanilla's drop-item key throws a held item, so a
     * sneaking player can eject without a block to target.
     *
     * @param level acting level
     * @param player acting player
     * @param hand hand holding the bucket
     * @param bucket the bucket stack
     * @return a success result when a stack was thrown, otherwise a pass result
     */
    protected final InteractionResult trySneakEject(Level level, Player player,
                                                     InteractionHand hand, ItemStack bucket) {
        List<ItemStack> stored = BucketState.getStoredItems(bucket);
        if (stored.isEmpty()) return InteractionResult.PASS;

        Vec3 pos = player.position();

        if (level.isClientSide) {
            playEjectSound(level, player, pos);
            return InteractionResult.SUCCESS;
        }

        ProtectionContext context = ProtectionContext.player(player, hand);
        if (!Protections.mayModify(level, context, player.blockPosition(), Direction.UP, bucket)) {
            return InteractionResult.PASS;
        }

        ItemStack popped = removeOldest(bucket);
        player.drop(popped, false, true);
        playEjectSound(level, player, pos);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * On a sneak-use against a block, ejects the oldest stored stack into the space adjacent to the
     * clicked face. A non-sneaking use passes so the block's own interaction still runs. Client-side
     * play is prediction; the server authorizes the release and adds the dropped item.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        ItemStack bucket = context.getItemInHand();
        if (!level.isClientSide && !BucketState.discardInvalidState(bucket)) return InteractionResult.PASS;

        // World ejection requires a deliberate alternate-use gesture.
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        List<ItemStack> stored = BucketState.getStoredItems(bucket);
        if (stored.isEmpty()) return InteractionResult.PASS;

        BlockPos dropPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 v = Vec3.atCenterOf(dropPos);

        if (level.isClientSide) {
            playEjectSound(level, player, v);
            return InteractionResult.SUCCESS;
        }

        ProtectionContext protectionContext = ProtectionContext.player(player, context.getHand());
        if (!Protections.mayModify(level, protectionContext, dropPos, context.getClickedFace(), bucket)) {
            return InteractionResult.PASS;
        }

        ItemStack popped = removeOldest(bucket);
        ItemEntity drop = new ItemEntity(level, v.x, v.y + 0.1D, v.z, popped);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        level.gameEvent(player, GameEvent.ITEM_INTERACT_FINISH, dropPos);
        playEjectSound(level, player, v);

        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Feeds a target animal from stored food. The client swaps a one-count probe of the stored food
     * into the hand so vanilla interaction predicts the feedback without touching bucket contents;
     * the server runs the authorized feeding and consumes one stored item when the interaction takes
     * it.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack bucket, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!player.level().isClientSide && !BucketState.discardInvalidState(bucket)) {
            return InteractionResult.PASS;
        }
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
            return InteractionResult.SUCCESS;
        }

        ProtectionContext context = ProtectionContext.player(player, hand);
        if (feedAnimal(bucket, animal, player, hand, context)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    /** Saved-data key under which vanilla stores the player an item entity was dropped for. */
    private static final String ITEM_TARGET_TAG = "Owner";

    /**
     * Reports whether an item entity is a legal, currently collectible storage-bucket input.
     *
     * @param entity item entity to test
     * @return {@code true} when the entity is alive, past its pickup delay, and holds a storable stack
     */
    public static boolean isIntakeCandidate(ItemEntity entity) {
        return entity.isAlive() && !entity.hasPickUpDelay() && canStore(entity.getItem());
    }

    /**
     * Applies vanilla's player pickup rules to intake by a real player: an item dropped for another
     * player stays theirs, and the loader's pickup event may veto. Automation, like a hopper, is
     * subject to neither.
     *
     * @param entity item entity to collect from
     * @param player acting real player, or {@code null} for automation
     * @return {@code true} when the player, if any, may collect from {@code entity}
     */
    static boolean playerMayCollect(ItemEntity entity, @Nullable Player player) {
        if (player == null) return true;
        // Vanilla exposes an item's intended recipient only through its saved data.
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        return (!saved.hasUUID(ITEM_TARGET_TAG) || saved.getUUID(ITEM_TARGET_TAG).equals(player.getUUID()))
                && BucketOperations.get().allowsItemPickup(entity, player);
    }

    /**
     * Records a real player's intake the way {@code ItemEntity#playerTouch} records a pickup: the
     * pickup animation, the picked-up statistic, and the pickup criterion. Must run before the entity
     * is discarded so the animation can find it.
     *
     * @param entity item entity collected from
     * @param player acting real player, or {@code null} for automation
     * @param item item collected
     * @param count number of items collected
     */
    static void completePlayerCollect(ItemEntity entity, @Nullable Player player, Item item, int count) {
        if (player == null) return;
        player.take(entity, count);
        player.awardStat(Stats.ITEM_PICKED_UP.get(item), count);
        player.onItemPickup(entity);
    }

    /**
     * Absorbs as much as capacity permits from the supplied item entities. Each entity is authorized
     * immediately before that entity and the bucket are changed; rejected, protected, delayed, or
     * incompatible entities remain untouched.
     *
     * @param level acting level
     * @param bucket the bucket stack, mutated in place when anything is absorbed
     * @param entities candidate item entities
     * @param context authorization identity applied per entity
     * @return {@code true} iff at least one item count moved into the bucket
     */
    public boolean absorbItemEntities(Level level, ItemStack bucket, List<ItemEntity> entities,
                                      ProtectionContext context) {
        List<ItemStack> stored = BucketState.getStoredItems(bucket);
        long layoutSeed = BucketState.getJunkLayoutSeed(bucket);
        boolean absorbedAny = false;
        for (ItemEntity entity : entities) {
            ItemStack incoming = entity.getItem().copy();
            int before = incoming.getCount();
            if (absorbItemEntity(level, bucket, stored, entity, context)) {
                int remaining = entity.isAlive() ? entity.getItem().getCount() : 0;
                layoutSeed = BucketState.nextJunkLayoutSeed(
                        layoutSeed, incoming, before - remaining, stored.size());
                absorbedAny = true;
            }
        }
        if (absorbedAny) {
            BucketState.setStoredItems(bucket, stored);
            BucketState.setJunkLayoutSeed(bucket, layoutSeed);
        }
        return absorbedAny;
    }

    /**
     * Applies one authorized item-entity intake to the detached {@code stored} list.
     *
     * @param level acting level
     * @param bucket the bucket stack driving the action
     * @param stored detached working list of stored stacks, updated on success
     * @param entity item entity to draw from, shrunk or discarded on success
     * @param context authorization identity
     * @return {@code true} iff at least one item count moved; on failure neither input changes
     */
    protected boolean absorbItemEntity(Level level, ItemStack bucket, List<ItemStack> stored,
                                       ItemEntity entity,
                                       ProtectionContext context) {
        if (!isIntakeCandidate(entity) || !canAddStack(stored, entity.getItem())) return false;
        if (!Protections.mayInteract(level, context, entity.blockPosition())
                || !playerMayCollect(entity, context.player())) {
            return false;
        }

        ItemStack entityStack = entity.getItem();
        Item item = entityStack.getItem();
        int moved = mergeInto(stored, entityStack, capacity);
        if (moved <= 0) return false;
        completePlayerCollect(entity, context.player(), item, moved);
        entityStack.shrink(moved);

        if (entityStack.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(entityStack);
        }
        level.gameEvent(context.player(), GameEvent.ITEM_INTERACT_FINISH, entity.blockPosition());
        return true;
    }

    /**
     * Reports whether the animal has stored food and can benefit from it on this activation.
     *
     * @param bucket the bucket stack
     * @param animal candidate animal
     * @return {@code true} when a matching stored food exists and the animal can currently use it
     */
    public boolean canFeed(ItemStack bucket, Animal animal) {
        if (findFoodIndex(animal, BucketState.getStoredItems(bucket)) < 0) return false;
        return canBenefitFromFood(animal);
    }

    /**
     * Builds a one-count copy of the animal's matching stored food.
     *
     * @param bucket the bucket stack
     * @param animal animal whose food preference selects the entry
     * @return the probe stack, or {@code null} when no stored entry is food for the animal
     */
    @Nullable
    private static ItemStack buildFoodProbe(ItemStack bucket, Animal animal) {
        List<ItemStack> stored = BucketState.getStoredItems(bucket);
        int foodIdx = findFoodIndex(animal, stored);
        if (foodIdx < 0) return null;
        ItemStack probe = stored.get(foodIdx).copy();
        probe.setCount(1);
        return probe;
    }

    /**
     * Attempts one authorized feeding action with matching stored food.
     *
     * <p>The feeder, a real player or a dispenser's automation player, presents one stored food item
     * to the animal's own interaction, so vanilla or modded behavior decides the outcome and item
     * consumption.
     *
     * @param bucket the bucket stack
     * @param animal animal to feed
     * @param feeder acting player or automation player
     * @param hand hand used to present the probe food
     * @param context authorization identity
     * @return {@code true} iff the animal interaction consumed the action, not merely because a
     *         food candidate existed
     */
    public boolean feedAnimal(ItemStack bucket, Animal animal, Player feeder, InteractionHand hand,
                              ProtectionContext context) {
        List<ItemStack> list = BucketState.getStoredItems(bucket);
        int foodIdx = findFoodIndex(animal, list);
        if (foodIdx < 0 || !canBenefitFromFood(animal)) return false;
        if (!Protections.mayInteract(animal.level(), context, animal.blockPosition())) {
            return false;
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
            consumeStoredFood(bucket, list, foodIdx);
        }
        return true;
    }

    private static void consumeStoredFood(ItemStack bucket, List<ItemStack> stored, int foodIdx) {
        ItemStack food = stored.get(foodIdx);
        food.shrink(1);
        if (food.isEmpty()) stored.remove(foodIdx);
        BucketState.setStoredItems(bucket, stored);
    }

    private static boolean canBenefitFromFood(Animal animal) {
        return animal.isBaby() ? animal.getAge() < 0 : animal.getAge() == 0 && animal.canFallInLove();
    }

    /**
     * Removes and returns the oldest stored stack.
     *
     * @param bucket storage-bucket stack to mutate in place
     * @return the removed stack, or {@link ItemStack#EMPTY} when nothing is stored
     */
    public static ItemStack removeOldest(ItemStack bucket) {
        List<ItemStack> list = BucketState.getStoredItems(bucket);
        if (list.isEmpty()) return ItemStack.EMPTY;
        ItemStack popped = list.remove(0);
        BucketState.setStoredItems(bucket, list);
        return popped;
    }

    // ----- Inventory stack-on overrides -----

    /**
     * On a secondary click with the bucket on the cursor, moves as much as possible from
     * {@code other} into storage.
     *
     * @param mine the bucket stack on the cursor
     * @param other the clicked slot
     * @param action click action; only {@link ClickAction#SECONDARY} acts
     * @param player interacting player
     * @return {@code true} iff at least one item moved and both slot states were updated
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack mine, Slot other, ClickAction action, Player player) {
        if (!player.level().isClientSide && !BucketState.discardInvalidState(mine)) return false;
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

    /**
     * On a secondary click with the bucket in a slot, inserts from a nonempty cursor or extracts the
     * oldest stored entry to an empty cursor.
     *
     * @param mine the bucket stack in the slot
     * @param other the cursor stack
     * @param slot the slot holding the bucket
     * @param action click action; only {@link ClickAction#SECONDARY} acts
     * @param player interacting player
     * @param access accessor for the cursor stack
     * @return {@code true} iff an insertion moved items or an extraction was accepted
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack mine, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (!player.level().isClientSide && !BucketState.discardInvalidState(mine)) return false;
        if (action != ClickAction.SECONDARY) return false;

        // Extract to cursor when cursor is empty
        if (other.isEmpty()) {
            List<ItemStack> list = BucketState.getStoredItems(mine);
            if (list.isEmpty()) return false;

            ItemStack out = list.remove(0); // FIFO: oldest stored entry first, matching Mob Bucket release order
            BucketState.setStoredItems(mine, list);

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
        return BucketState.getStoredItemCount(stack);
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
     * @param bucket storage-bucket stack to mutate in place
     * @param incoming source stack, shrunk by the number of items moved
     * @return number of items moved; zero means neither stack changed
     */
    protected int addStack(ItemStack bucket, ItemStack incoming) {
        if (!canStore(incoming)) return 0;

        List<ItemStack> list = BucketState.getStoredItems(bucket);
        int moved = mergeInto(list, incoming, capacity);
        if (moved > 0) {
            BucketState.setStoredItems(bucket, list);
            BucketState.advanceJunkLayout(bucket, incoming, moved);
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
