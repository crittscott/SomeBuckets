package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.client.JunkBucketRenderer;
import com.github.crittscott.somebuckets.protection.DispenserFakePlayer;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class JBItem extends Item {
    private final int capacity;

    public JBItem(Properties properties, int capacity) {
        super(properties);
        this.capacity = capacity;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return JunkBucketRenderer.getInstance();
            }
        });
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
        tooltip.add(Component.translatable(
                "tooltip.somebuckets.storage_bucket.stacks", getCount(stack), capacity));
    }

    // ----- World interactions -----
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bucket = player.getItemInHand(hand);

        AABB box = player.getBoundingBox().inflate(1.5D, 1.5D, 1.5D);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                JBItem::isIntakeCandidate);
        if (items.isEmpty()) return InteractionResultHolder.pass(bucket);

        if (level.isClientSide) {
            List<ItemStack> stored = NBTUtil.getStoredItems(bucket);
            boolean canAbsorb = items.stream().anyMatch(entity -> canAddStack(stored, entity.getItem()));
            return canAbsorb
                    ? InteractionResultHolder.sidedSuccess(bucket, true)
                    : InteractionResultHolder.pass(bucket);
        }

        ProtectionContext context = ProtectionContext.player(player, hand);
        boolean absorbedAny = absorbItemEntities(level, bucket, items, context, Direction.UP);

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

        // World ejection requires a deliberate alternate-use gesture.
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        List<ItemStack> stored = NBTUtil.getStoredItems(bucket);
        if (stored.isEmpty()) return InteractionResult.PASS;

        if (level.isClientSide) return InteractionResult.sidedSuccess(true);

        BlockPos dropPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 v = Vec3.atCenterOf(dropPos);
        ItemEntity probe = new ItemEntity(level, v.x, v.y + 0.1D, v.z, stored.get(0).copy());
        ProtectionContext protectionContext = ProtectionContext.player(player, context.getHand());
        if (!Protections.mayAct(level, protectionContext, ProtectionAction.ENTITY_RELEASE,
                dropPos, context.getClickedFace(), bucket, probe)) {
            return InteractionResult.PASS;
        }

        ItemStack popped = removeOldest(bucket);
        ItemEntity drop = new ItemEntity(level, v.x, v.y + 0.1D, v.z, popped);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);

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
            // and let vanilla's own interact predict its client-side feedback (particles, sound) the
            // same way it would for a real held food item, without touching the bucket's contents.
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
     * Absorbs as much as possible from the supplied item entities. A non-null protection context
     * authorizes each entity immediately before it and the bucket are mutated.
     */
    public boolean absorbItemEntities(Level level, ItemStack bucket, List<ItemEntity> entities,
                                      @Nullable ProtectionContext context, Direction face) {
        List<ItemStack> stored = NBTUtil.getStoredItems(bucket);
        boolean absorbedAny = false;
        for (ItemEntity entity : entities) {
            if (absorbItemEntity(level, bucket, stored, entity, context, face)) absorbedAny = true;
        }
        if (absorbedAny) {
            NBTUtil.setStoredItems(bucket, stored);
        }
        return absorbedAny;
    }

    protected boolean absorbItemEntity(Level level, ItemStack bucket, List<ItemStack> stored,
                                       ItemEntity entity,
                                       @Nullable ProtectionContext context, Direction face) {
        if (!isIntakeCandidate(entity) || !canAddStack(stored, entity.getItem())) return false;
        if (context != null && !Protections.mayAct(level, context, ProtectionAction.ENTITY_INTERACT,
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
        if (findFoodIndex(animal, NBTUtil.getStoredItems(bucket)) < 0) return false;
        return canBenefitFromFood(animal);
    }

    /** A one-count copy of the animal's matching stored food, or null if there is none. */
    @Nullable
    private static ItemStack buildFoodProbe(ItemStack bucket, Animal animal) {
        List<ItemStack> stored = NBTUtil.getStoredItems(bucket);
        int foodIdx = findFoodIndex(animal, stored);
        if (foodIdx < 0) return null;
        ItemStack probe = stored.get(foodIdx).copy();
        probe.setCount(1);
        return probe;
    }

    /**
     * Feeds one animal from storage by handing it the stored food through a real interaction, so
     * vanilla decides breeding versus growth and applies its own rate for the specific entity type.
     * Automated feeding passes no player owner and drives the interaction through a stable fake
     * player instead.
     */
    public boolean feedAnimal(ItemStack bucket, Animal animal, @Nullable Player feeder, InteractionHand hand,
                              @Nullable ProtectionContext context, Direction face) {
        List<ItemStack> list = NBTUtil.getStoredItems(bucket);
        int foodIdx = findFoodIndex(animal, list);
        if (foodIdx < 0 || !canBenefitFromFood(animal)) return false;
        if (context != null && !Protections.mayAct(animal.level(), context, ProtectionAction.ENTITY_INTERACT,
                animal.blockPosition(), face, bucket, animal)) {
            return false;
        }

        Player actor = feeder;
        if (actor == null) {
            ServerPlayer fake = DispenserFakePlayer.get((ServerLevel) animal.level());
            Vec3 pos = Vec3.atCenterOf(animal.blockPosition());
            fake.setPos(pos.x, pos.y, pos.z);
            actor = fake;
        }

        ItemStack probe = list.get(foodIdx).copy();
        probe.setCount(1);
        ItemStack previous = actor.getItemInHand(hand);
        actor.setItemInHand(hand, probe);
        InteractionResult result;
        ItemStack remaining;
        try {
            result = animal.interact(actor, hand);
            remaining = actor.getItemInHand(hand);
        } finally {
            actor.setItemInHand(hand, previous);
        }
        if (!result.consumesAction()) return false;

        if (remaining.isEmpty()) {
            ItemStack food = list.get(foodIdx);
            food.shrink(1);
            if (food.isEmpty()) list.remove(foodIdx);
            NBTUtil.setStoredItems(bucket, list);
        }
        return true;
    }

    private static boolean canBenefitFromFood(Animal animal) {
        return animal.isBaby() ? animal.getAge() < 0 : animal.getAge() == 0 && animal.canFallInLove();
    }

    /** Removes and returns the oldest stored stack, or an empty stack when there is none. */
    public static ItemStack removeOldest(ItemStack bucket) {
        List<ItemStack> list = NBTUtil.getStoredItems(bucket);
        if (list.isEmpty()) return ItemStack.EMPTY;
        ItemStack popped = list.remove(0);
        NBTUtil.setStoredItems(bucket, list);
        return popped;
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

    private boolean canAddStack(List<ItemStack> storedItems, ItemStack incoming) {
        if (!canStore(incoming)) return false;

        for (ItemStack stored : storedItems) {
            if (ItemStack.isSameItemSameTags(stored, incoming)
                    && stored.getCount() < stored.getMaxStackSize()) {
                return true;
            }
        }
        return storedItems.size() < capacity;
    }

    // Merge as much of 'incoming' into the bucket's list as possible. Returns number of items moved and shrinks 'incoming'.
    protected int addStack(ItemStack bucket, ItemStack incoming) {
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

    private static int findFoodIndex(Animal animal, List<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (!s.isEmpty() && animal.isFood(s)) return i;
        }
        return -1;
    }
}
