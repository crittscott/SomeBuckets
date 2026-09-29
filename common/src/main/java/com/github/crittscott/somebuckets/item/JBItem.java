package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TamableAnimal;
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
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * FIFO storage for item-stack entries and the shared interaction base for Junk and Trash Buckets.
 * Contents live on the bucket stack. Every intake path, from item entities, dispensers, and
 * inventory clicks, applies {@link #canStore(ItemStack)} and then this bucket's {@link #intake}
 * rule; the Junk Bucket's rule merges into compatible entries before allocating new ones.
 */
public class JBItem extends SomeBucketItem {
    private static final double PICKUP_RADIUS = 1.5D;

    private final int capacity;
    private final double pickupRadius;
    private final int entityLimit;

    /**
     * Creates a Junk Bucket that collects every eligible item entity within its pickup radius.
     *
     * @param properties base item properties
     * @param capacity maximum number of stored stack entries
     */
    public JBItem(Properties properties, int capacity) {
        this(properties, capacity, PICKUP_RADIUS, Integer.MAX_VALUE);
    }

    /**
     * Creates an item-storage bucket.
     *
     * @param properties base item properties
     * @param capacity maximum number of stored stack entries
     * @param pickupRadius how far around the player a use collects item entities
     * @param entityLimit most item entities one use or dispenser pulse processes
     */
    protected JBItem(Properties properties, int capacity, double pickupRadius, int entityLimit) {
        super(properties);
        this.capacity = capacity;
        this.pickupRadius = pickupRadius;
        this.entityLimit = entityLimit;
    }

    /** Returns the maximum number of stored stack entries. */
    public int getCapacity() { return capacity; }

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
        return canStoreByVanillaRules(stack) && !BucketOperations.get().carriesItemContainer(stack);
    }

    /**
     * {@link #canStore} without the loader item-inventory lookup: only emptiness, the vanilla nesting
     * opt-out, and vanilla inventory components are checked.
     *
     * @param stack candidate stack
     * @return {@code false} when {@link #canStore} would refuse the stack for a vanilla reason
     */
    public static boolean canStoreByVanillaRules(ItemStack stack) {
        if (stack.isEmpty() || !stack.getItem().canFitInsideContainerItems()) return false;
        return !stack.has(DataComponents.BUNDLE_CONTENTS)
                && !stack.has(DataComponents.CONTAINER)
                && !stack.has(DataComponents.CONTAINER_LOOT);
    }

    /**
     * Whether {@code stack} may be held as one stored entry under vanilla rules:
     * {@link #canStoreByVanillaRules} and within its own stack limit. Decode-time admission relies on
     * this alone, since the loader item-inventory lookup is not consulted while a stack is decoded.
     *
     * @param stack candidate entry
     * @return {@code true} when the entry is storable and not oversized
     */
    public static boolean isStorableEntry(ItemStack stack) {
        return canStoreByVanillaRules(stack) && stack.getCount() <= stack.getMaxStackSize();
    }

    // ----- UI bar -----
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BucketState.getStoredItemCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return barWidth(BucketState.getStoredItemCount(stack), capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return DEFAULT_BUCKET_BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "tooltip.somebuckets.storage_bucket.stacks", BucketState.getStoredItemCount(stack), capacity));
        int setAside = BucketState.getSetAsideCount(stack);
        if (setAside > 0) {
            tooltip.add(Component.translatable("tooltip.somebuckets.storage_bucket.set_aside", setAside));
        }
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

        if (player.isShiftKeyDown()) return trySneakEject(level, player, bucket);

        List<ItemEntity> items = findIntakeCandidates(level, player.getBoundingBox().inflate(pickupRadius));
        if (items.isEmpty()) return InteractionResult.PASS;

        if (level.isClientSide) {
            List<ItemStack> stored = BucketState.getStoredItems(bucket);
            boolean canAbsorb = items.stream().anyMatch(entity -> canIntakeCandidate(stored, entity.getItem()));
            if (!canAbsorb) return InteractionResult.PASS;
            playIntakeSound(level, player);
            return InteractionResult.SUCCESS;
        }

        ProtectionContext context = ProtectionContext.player(player, hand);
        boolean absorbedAny = HeldTransfers.fillFromHand(level, player, hand, bucket,
                working -> absorbItemEntities(level, working, items, context));

        if (absorbedAny) {
            playIntakeSound(level, player);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    /**
     * Throws the oldest stored stack the way vanilla's drop-item key throws a held item, so a
     * sneaking player can eject without a block to target. A cancelled toss leaves the stack stored.
     *
     * @param level acting level
     * @param player acting player
     * @param bucket the bucket stack
     * @return a success result when a stack was thrown, otherwise a pass result
     */
    protected final InteractionResult trySneakEject(Level level, Player player, ItemStack bucket) {
        List<ItemStack> stored = BucketState.getStoredItems(bucket);
        if (stored.isEmpty()) return InteractionResult.PASS;

        Vec3 pos = player.position();

        if (level.isClientSide) {
            playEjectSound(level, player, pos);
            return InteractionResult.SUCCESS;
        }

        if (!BucketOperations.get().tossFromPlayer(player, stored.get(0).copy())) {
            return InteractionResult.PASS;
        }
        removeOldest(bucket);
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
        if (!(target instanceof Animal animal)) return InteractionResult.PASS;
        if (!canFeed(bucket, animal)) return InteractionResult.PASS;

        Level level = player.level();
        if (level.isClientSide) {
            // The server performs the actual mutation. Locally swap in a probe of the stored food
            // and let vanilla's own interact predict the client-side feedback of a real held food
            // item without touching the bucket's contents.
            FoodProbe food = buildFoodProbe(bucket, animal);
            if (food != null) {
                HeldTransfers.interactHolding(player, hand, food.stack(), animal);
            }
            return InteractionResult.SUCCESS;
        }

        ProtectionContext context = ProtectionContext.player(player, hand);
        if (feedAnimal(bucket, animal, player, hand, context)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

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
     * Finds the eligible item entities in {@code box} that one use or dispenser pulse processes, at
     * most this bucket's entity limit, in iteration order.
     *
     * @param level level to query
     * @param box search volume
     * @return the intake candidates
     */
    public List<ItemEntity> findIntakeCandidates(Level level, AABB box) {
        List<ItemEntity> result = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box, JBItem::isIntakeCandidate,
                result, entityLimit);
        return result;
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
        return (entity.target == null || entity.target.equals(player.getUUID()))
                && BucketOperations.get().allowsItemPickup(entity, player);
    }

    /**
     * Records a real player's intake the way {@code ItemEntity#playerTouch} records a pickup: the
     * loader's post-pickup event, the pickup animation, the picked-up statistic, and the pickup
     * criterion. Runs after the entity's stack has been shrunk and before the entity is discarded,
     * so the animation can find it.
     *
     * @param entity item entity collected from
     * @param player acting real player, or {@code null} for automation
     * @param original copy of the entity's stack before intake
     * @param count number of items collected
     */
    static void completePlayerCollect(ItemEntity entity, @Nullable Player player, ItemStack original, int count) {
        if (player == null) return;
        BucketOperations.get().afterItemPickup(player, entity, original, count);
        player.take(entity, count);
        player.awardStat(Stats.ITEM_PICKED_UP.get(original.getItem()), count);
        player.onItemPickup(entity);
    }

    /**
     * Absorbs from the supplied item entities, at most this bucket's entity limit of them, through
     * its intake rule. Each entity is authorized immediately before that entity and the bucket are
     * changed; rejected, protected, delayed, or incompatible entities remain untouched.
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
        boolean hasRoom = canIntakeAnything(stored);
        for (ItemEntity entity : entities.subList(0, Math.min(entities.size(), entityLimit))) {
            if (!hasRoom) break;
            ItemStack incoming = entity.getItem().copy();
            int before = incoming.getCount();
            if (absorbItemEntity(level, bucket, stored, entity, context)) {
                int remaining = entity.isAlive() ? entity.getItem().getCount() : 0;
                layoutSeed = BucketState.nextJunkLayoutSeed(
                        layoutSeed, incoming, before - remaining, stored.size());
                absorbedAny = true;
                hasRoom = canIntakeAnything(stored);
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
    private boolean absorbItemEntity(Level level, ItemStack bucket, List<ItemStack> stored,
                                     ItemEntity entity,
                                     ProtectionContext context) {
        if (!isIntakeCandidate(entity) || !canIntakeCandidate(stored, entity.getItem())) return false;
        if (!Protections.mayInteract(level, entity.blockPosition())
                || !playerMayCollect(entity, context.player())) {
            return false;
        }

        ItemStack entityStack = entity.getItem();
        // A player pickup listener may have replaced the entity's stack while authorizing it.
        if (context.player() != null
                && (!canStore(entityStack) || !canIntakeCandidate(stored, entityStack))) {
            return false;
        }
        ItemStack original = entityStack.copy();
        int moved = intake(stored, entityStack);
        if (moved <= 0) return false;
        entityStack.shrink(moved);
        completePlayerCollect(entity, context.player(), original, moved);

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
     * Finds the animal's matching stored food and builds the one-count interaction probe.
     *
     * @param bucket the bucket stack
     * @param animal animal whose food preference selects the entry
     * @return the stored-list context and probe, or {@code null} when no stored entry is food
     */
    @Nullable
    private static FoodProbe buildFoodProbe(ItemStack bucket, Animal animal) {
        List<ItemStack> stored = BucketState.getStoredItems(bucket);
        int foodIdx = findFoodIndex(animal, stored);
        if (foodIdx < 0) return null;
        return new FoodProbe(stored, foodIdx, stored.get(foodIdx).copyWithCount(1));
    }

    private record FoodProbe(List<ItemStack> stored, int index, ItemStack stack) {}

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
        FoodProbe food = buildFoodProbe(bucket, animal);
        if (food == null || !canBenefitFromFood(animal)) return false;
        if (context.isAutomation() && !automationMayFeed(animal)) return false;
        if (!Protections.mayInteract(animal.level(), animal.blockPosition())) {
            return false;
        }

        HeldTransfers.HeldInteraction interaction = HeldTransfers.interactHolding(
                feeder, hand, food.stack(), animal);
        if (!interaction.result().consumesAction()) return false;

        if (interaction.remaining().isEmpty()) {
            consumeStoredFood(bucket, food.stored(), food.index());
        }
        return true;
    }

    private static void consumeStoredFood(ItemStack bucket, List<ItemStack> stored, int foodIdx) {
        ItemStack food = stored.get(foodIdx);
        food.shrink(1);
        if (food.isEmpty()) stored.remove(foodIdx);
        BucketState.setStoredItems(bucket, stored);
    }

    /**
     * Reports whether automation may feed the animal. An untamed {@link TamableAnimal} is refused,
     * since its food can tame it, and automation must not become an animal's owner.
     *
     * @param animal candidate animal
     * @return {@code false} for an untamed tamable animal
     */
    public static boolean automationMayFeed(Animal animal) {
        return !(animal instanceof TamableAnimal tamable) || tamable.isTame();
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
     * {@code other} into storage. Items leave the slot through {@link Slot#safeTake}, as with a
     * bundle, so the slot's pickup rules and take handling (crafting, trading, smelting rewards)
     * apply; a slot that refuses the partial or complete take leaves both stacks unchanged.
     *
     * @param mine the bucket stack on the cursor
     * @param other the clicked slot
     * @param action click action; only {@link ClickAction#SECONDARY} acts
     * @param player interacting player
     * @return {@code true} iff at least one item moved
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack mine, Slot other, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        if (mine.getCount() > 1) return false;
        if (!other.hasItem()) return false;

        ItemStack otherStack = other.getItem();
        int fit = accept(BucketState.getStoredItems(mine), otherStack);
        if (fit <= 0) return false;

        ItemStack taken = other.safeTake(otherStack.getCount(), fit, player);
        if (taken.isEmpty()) return false;
        addStack(mine, taken);
        playIntakeSound(player.level(), player);
        return true;
    }

    /**
     * On a secondary click with the bucket in a slot, inserts from a nonempty cursor or extracts the
     * oldest stored entry to an empty cursor. A slot that does not allow modification, such as a
     * result slot, is left alone.
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
        if (action != ClickAction.SECONDARY) return false;
        if (mine.getCount() > 1) return false;
        if (!slot.allowModification(player)) return false;

        // Extract to cursor when cursor is empty
        if (other.isEmpty()) {
            ItemStack out = removeOldest(mine);
            if (out.isEmpty()) return false;

            access.set(out); // put into cursor
            slot.setChanged();
            playEjectSound(player.level(), player, player.position());
            return true;
        }

        // Insert (cursor has items)
        int moved = addStack(mine, other);
        if (moved > 0) {
            slot.setChanged();
            playIntakeSound(player.level(), player);
            return true;
        }
        return false;
    }

    // ----- storage helpers -----
    /**
     * This bucket's intake rule: applies {@code incoming} to the detached {@code stored} list without
     * changing {@code incoming}. The Junk Bucket merges into compatible entries, then allocates new
     * entries while capacity remains. Called only with a storable, non-empty stack.
     *
     * @param stored detached working list of stored stacks, updated in place
     * @param incoming offered stack; left unchanged
     * @return number of items accepted from {@code incoming}; zero leaves {@code stored} unchanged
     */
    protected int intake(List<ItemStack> stored, ItemStack incoming) {
        return mergeInto(stored, incoming, capacity);
    }

    /* The intake rule behind the single storability gate. */
    private int accept(List<ItemStack> stored, ItemStack incoming) {
        return canStore(incoming) ? intake(stored, incoming) : 0;
    }

    /** Whether this already-storable candidate can contribute anything without changing storage. */
    protected boolean canIntakeCandidate(List<ItemStack> stored, ItemStack incoming) {
        if (stored.size() < capacity) return true;
        for (ItemStack entry : stored) {
            if (ItemStack.isSameItemSameComponents(entry, incoming)
                    && entry.getCount() < entry.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** Whether some possible storable input could still contribute to the current contents. */
    protected boolean canIntakeAnything(List<ItemStack> stored) {
        if (stored.size() < capacity) return true;
        for (ItemStack entry : stored) {
            if (entry.getCount() < entry.getMaxStackSize()) return true;
        }
        return false;
    }

    /**
     * Applies the intake rule to {@code incoming}, persisting the new bucket contents and shrinking
     * {@code incoming} by the number of items accepted.
     *
     * @param bucket storage-bucket stack to mutate in place
     * @param incoming source stack, shrunk by the number of items moved
     * @return number of items moved; zero means neither stack changed
     */
    private int addStack(ItemStack bucket, ItemStack incoming) {
        List<ItemStack> list = BucketState.getStoredItems(bucket);
        int moved = accept(list, incoming);
        if (moved > 0) {
            BucketState.setStoredItems(bucket, list);
            BucketState.advanceJunkLayout(bucket, incoming, moved);
            incoming.shrink(moved);
        }
        return moved;
    }

    private static int mergeInto(List<ItemStack> list, ItemStack incoming, int capacity) {
        int remaining = incoming.getCount();

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
            if (animal.isFood(list.get(i))) return i;
        }
        return -1;
    }
}
