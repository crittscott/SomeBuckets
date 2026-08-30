package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * FIFO storage for full mob snapshots, limited to one exact entity type per bucket.
 * Capture appends an eligible live mob only after authorization; release recreates the oldest
 * snapshot and removes it from storage only after the entity enters the world.
 * {@link #FILLED_PROPERTY} exposes the empty-versus-occupied item-model state.
 */
public class MBItem extends Item implements VariableStackItem {
    public static final int MAX_MOBS = 8;
    public static final ResourceLocation FILLED_PROPERTY =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "filled");
    public static final float MODEL_EMPTY = 0.0F;
    public static final float MODEL_FILLED = 1.0F;

    private static final TagKey<EntityType<?>> MB_BLACKLIST =
            TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "mb_blacklist"));

    public MBItem(Properties properties) {
        super(properties.stacksTo(EMPTY_STACK_SIZE).rarity(Rarity.RARE));
    }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return NBTUtil.getEntityCount(stack) == 0;
    }

    /**
     * Evaluates the {@link #FILLED_PROPERTY} protocol for the Mob Bucket model.
     *
     * @return {@link #MODEL_EMPTY} when no snapshot is stored, otherwise {@link #MODEL_FILLED}
     */
    public static float getFilledProperty(ItemStack stack) {
        return NBTUtil.getEntityCount(stack) > 0 ? MODEL_FILLED : MODEL_EMPTY;
    }

    /**
     * Whether an entity may be stored in a Mob Bucket at all, independent of what the bucket already holds.
     * Excludes players and other non-{@link Mob} living entities, types that cannot be serialized, blacklisted
     * types, and entities involved in a ride, since the other half of the pair is not part of the snapshot.
     */
    public static boolean canCapture(Entity entity) {
        if (!(entity instanceof Mob mob)) return false;
        if (mob.isRemoved()) return false;
        if (!mob.getType().canSerialize()) return false;
        if (mob.getType().is(MB_BLACKLIST)) return false;
        return !mob.isPassenger() && !mob.isVehicle();
    }

    /** Whether the bucket has room for another snapshot of the exact stored entity type. */
    public static boolean canAccept(ItemStack stack, EntityType<?> entityType) {
        int count = NBTUtil.getEntityCount(stack);
        if (count >= MAX_MOBS) return false;
        return count == 0 || NBTUtil.getCurrentEntityType(stack) == entityType;
    }

    /**
     * Captures one eligible, type-compatible mob after authorizing the supplied interaction face.
     * A water-dwelling mob also takes the water source block it occupies, so a release followed by
     * a recapture cannot generate a free-standing water block.
     *
     * @return {@code true} only after the snapshot is appended and the live mob is discarded;
     *         {@code false} leaves the mob, bucket, and world unchanged
     */
    public static boolean capture(ItemStack stack, Mob mob, ProtectionContext context, Direction face) {
        if (!canCapture(mob) || !canAccept(stack, mob.getType())) return false;
        Level level = mob.level();
        BlockPos pos = mob.blockPosition();
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_INTERACT, pos, face, stack, mob)) {
            return false;
        }
        if (needsWater(mob) && !removeSourceWaterAt(level, pos, stack, context, face, mob)) return false;

        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());

        CompoundTag entityTag = new CompoundTag();
        mob.saveWithoutId(entityTag);

        NBTUtil.addEntitySnapshot(stack, entityTypeId.toString(), entityTag);
        mob.discard();
        if (context.player() instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, stack);
        }
        return true;
    }

    /** Whether the entity is bucketable or belongs to Minecraft's aquatic entity-type category. */
    public static boolean needsWater(Entity entity) {
        if (entity instanceof Bucketable) return true;
        return entity.getType().is(EntityTypeTags.AQUATIC);
    }

    /** Gives a water-dwelling mob the exact-target water placement used by a vanilla bucket of fish. */
    private static boolean placeWaterFor(Level level, BlockPos pos, ItemStack stack,
                                         ProtectionContext context, Direction face) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) return true;
        return BucketOperations.get().placeAquaticSourceWater(level, pos, stack, context, face);
    }

    /**
     * Removes the water source block at {@code pos} through the loader's native pickup contract. A
     * non-source or non-water block at {@code pos} is left alone.
     *
     * @return {@code true} when there was nothing to remove or removal was authorized and applied;
     *         {@code false} only when removal was required but denied
     */
    private static boolean removeSourceWaterAt(Level level, BlockPos pos, ItemStack stack,
                                               ProtectionContext context, Direction face, Mob mob) {
        if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource()) return true;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos, face, stack, mob)) return false;
        StoredFluid expected = new StoredFluid(level.getFluidState(pos).getType(),
                FluidBucketItem.BUCKET_VOLUME_MB, null);
        return BucketOperations.get().takeAquaticSourceWater(level, pos, expected, context.player());
    }

    private static boolean isUuidInUse(ServerLevel level, UUID uuid) {
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            Entity existing = serverLevel.getEntity(uuid);
            if (existing != null && !existing.isRemoved()) return true;
        }
        return false;
    }

    /**
     * Attempts to recreate and release the oldest stored mob at {@code pos}.
     *
     * <p>A stored entity-type header that no longer resolves to a registered {@link EntityType}
     * (for example after the mob's mod is removed) is a no-op that preserves the entry.
     *
     * <p>The recreated entity retains its saved UUID unless that UUID belongs to another loaded
     * entity in any server level, in which case a fresh UUID is chosen. Collision failure and
     * entity-release or required-fluid protection denial preserve the stored entry and destination.
     * The entry is removed only after {@link ServerLevel#addFreshEntity(Entity)} succeeds. Required
     * water is committed before that final insertion; if another mod rejects insertion afterward,
     * the snapshot remains stored but the water is not rolled back.
     *
     * @return {@code true} only after the entity enters the world and the oldest snapshot is removed
     */
    public static boolean releaseOldest(ServerLevel level, BlockPos pos, ItemStack stack,
                                        ProtectionContext context, Direction face) {
        CompoundTag storedTag = NBTUtil.copyFirstEntitySnapshot(stack);
        if (storedTag.isEmpty()) return false;

        EntityType<?> entityType = NBTUtil.getCurrentEntityType(stack);
        if (entityType == null) return false;
        Entity entity = entityType.create(level);
        if (entity == null) return false;

        CompoundTag loadTag = storedTag.copy();
        entity.load(loadTag);
        while (isUuidInUse(level, entity.getUUID())) {
            entity.setUUID(UUID.randomUUID());
        }
        Vec3 spawnVec = Vec3.atCenterOf(pos);
        entity.setPos(spawnVec.x, spawnVec.y, spawnVec.z);

        if (!level.noCollision(entity)) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_RELEASE, pos, face, stack, entity)) {
            return false;
        }
        if (needsWater(entity) && !placeWaterFor(level, pos, stack, context, face)) return false;
        if (!level.addFreshEntity(entity)) return false;
        level.gameEvent(context.player(), GameEvent.ENTITY_PLACE, pos);

        NBTUtil.removeFirstEntitySnapshot(stack);
        if (context.player() != null) {
            context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        int count = NBTUtil.getEntityCount(stack);
        if (count > 0) {
            EntityType<?> type = NBTUtil.getCurrentEntityType(stack);
            if (type != null) {
                tooltip.add(Component.translatable(
                        "tooltip.somebuckets.mob_bucket.contents",
                        Component.translatable(type.getDescriptionId()), count, MAX_MOBS));
            }
        }
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob) || !canCapture(mob)) {
            return InteractionResult.PASS;
        }

        // Check if we can accept this entity type
        if (!canAccept(stack, mob.getType())) {
            return InteractionResult.PASS;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        if (!capture(stack, mob, ProtectionContext.player(player, hand), Direction.UP)) {
            return InteractionResult.PASS;
        }

        // Update the ItemStack in the player's hand to reflect NBT changes
        player.setItemInHand(hand, stack);

        // Play sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);

        return InteractionResult.sidedSuccess(false);
    }

    /* ------------------------- UI bar ------------------------- */

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return NBTUtil.getEntityCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(ItemBars.ITEM_BAR_WIDTH * (float) NBTUtil.getEntityCount(stack) / (float) MAX_MOBS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ItemBars.DEFAULT_BUCKET_BAR_COLOR;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();

        // Must have stored entity to release
        if (NBTUtil.getEntityCount(stack) <= 0) {
            return InteractionResult.PASS;
        }

        // Only act on shift-right-click (matching JBItem pattern)
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        ProtectionContext protectionContext = ProtectionContext.player(player, context.getHand());
        if (!releaseOldest((ServerLevel) level, spawnPos, stack, protectionContext, context.getClickedFace())) {
            return InteractionResult.PASS;
        }

        // Play sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 1.0F);

        return InteractionResult.sidedSuccess(false);
    }
}
